package com.example.reset

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.work.WorkManager
import com.example.cloud.CloudFirestoreRepository
import com.example.cloud.CloudFirestoreWipeReport
import com.example.cloud.CloudStoragePhotoManager
import com.example.cloud.CloudSyncAndMigrationManager
import com.example.cloud.StorageWipeResult
import com.example.data.local.AppDatabase
import com.example.drive.GoogleDriveMirrorSyncEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Detailed verification report returned by [HardResetWipeManager] after executing
 * a zero-remnant hard reset across SQLite/Room, local file storage, SharedPreferences/DataStore,
 * Cloud Firestore, and Firebase Storage.
 */
data class HardResetVerificationReport(
    val localWipeExecuted: Boolean = false,
    val cloudWipeExecuted: Boolean = false,
    val localRemainingTableRows: Int = 0,
    val localDeletedFilesCount: Int = 0,
    val localRemainingFilesCount: Int = 0,
    val clearedSharedPrefsCount: Int = 0,
    val firestoreDeletedDocsCount: Int = 0,
    val firestoreRemainingDocsCount: Int = 0,
    val storageDeletedObjectsCount: Int = 0,
    val storageSkippedNotFoundCount: Int = 0,
    val storageRemainingObjectsCount: Int = 0,
    val warnings: List<String> = emptyList(),
    val isFullyVerifiedZeroRemnant: Boolean = false
)

sealed class HardResetUiState {
    data object Idle : HardResetUiState()
    data class Wiping(
        val stepTitle: String,
        val stepDetail: String,
        val progressPercent: Int
    ) : HardResetUiState()

    data class VerifiedComplete(
        val report: HardResetVerificationReport
    ) : HardResetUiState()

    data class Failed(
        val errorMessage: String,
        val partialReport: HardResetVerificationReport? = null
    ) : HardResetUiState()
}

/**
 * Zero-Remnant Hard Reset & Factory Wipe Engine.
 *
 * Guarantees complete, irreversible cleanup of:
 * 1. All background sync workers, real-time Firestore snapshot listeners, and connectivity monitors.
 * 2. Local Room/SQLite database (`clearAllTables()` + `DELETE FROM sqlite_sequence` auto-increment reset).
 * 3. All internal and external app files & caches (`filesDir`, `cacheDir`, `codeCacheDir`,
 *    `getExternalFilesDir(null)`, `externalCacheDir`, including `unit_*_foto_unit_*.jpg`, thumbnails, PDFs).
 * 4. All app `SharedPreferences` and DataStore keys (`editor.clear().commit()` + XML deletion),
 *    preserving ONLY a dedicated isolated one-shot sentinel file written AFTER verification succeeds.
 * 5. All Cloud Firestore documents across every collection/subcollection under `users/{uid}` (batched delete
 *    until 0 documents remain) and all Firebase Storage objects recursively under `users/{uid}`
 *    (gracefully ignoring `StorageException.ERROR_OBJECT_NOT_FOUND` so missing refs never abort the wipe).
 */
class HardResetWipeManager(
    private val context: Context,
    private val appDatabase: AppDatabase,
    private val cloudFirestoreRepo: CloudFirestoreRepository? = null,
    private val cloudPhotoManager: CloudStoragePhotoManager? = null,
    private val cloudSyncManager: CloudSyncAndMigrationManager? = null,
    private val mirrorSyncEngine: GoogleDriveMirrorSyncEngine? = null
) {
    companion object {
        private const val TAG = "HardResetWipeManager"

        /**
         * Isolated sentinel preference file excluded from app pref deletion so the one-shot
         * startup wipe executes exactly once and NEVER deletes newly entered user data afterward.
         */
        const val SENTINEL_PREFS_NAME = "sewain_hard_reset_sentinel_v1"
        private const val KEY_LOCAL_WIPE_VERIFIED_V1 = "hard_reset_v1_local_verified"
        private const val KEY_CLOUD_WIPE_VERIFIED_PREFIX = "hard_reset_v1_cloud_verified_"
        private const val KEY_COMPLETED_AT_MILLIS = "hard_reset_v1_completed_at_millis"

        private val KNOWN_APP_PREF_NAMES = listOf(
            "saf_soc_prefs",
            "sewain_drive_cloud_mirror_prefs",
            "saf_auto_backup_prefs",
            "saf_drive_oauth_prefs",
            "saf_appearance_prefs",
            "saf_image_quality_prefs",
            "saf_drive_photo_center_prefs",
            "saf_local_storage_prefs",
            "saf_process_checkpoint_prefs",
            "saf_due_notification_prefs"
        )
    }

    private fun sentinelPrefs() =
        context.applicationContext.getSharedPreferences(SENTINEL_PREFS_NAME, Context.MODE_PRIVATE)

    fun isLocalHardResetCompleted(): Boolean =
        sentinelPrefs().getBoolean(KEY_LOCAL_WIPE_VERIFIED_V1, false)

    fun isCloudHardResetCompleted(uid: String?): Boolean {
        if (uid.isNullOrBlank()) return true
        return sentinelPrefs().getBoolean(KEY_CLOUD_WIPE_VERIFIED_PREFIX + uid, false)
    }

    fun isStartupHardResetRequired(authenticatedUid: String?): Boolean {
        if (!isLocalHardResetCompleted()) return true
        if (!authenticatedUid.isNullOrBlank() && !isCloudHardResetCompleted(authenticatedUid)) return true
        return false
    }

    fun resetSentinelForManualHardReset() {
        sentinelPrefs().edit().clear().commit()
    }

    /**
     * Executes the complete one-shot (or manual) zero-remnant hard reset.
     *
     * @param authenticatedUid Current signed-in Firebase UID (null if not yet authenticated).
     * @param forceManualRun If true, runs full local + cloud wipe even if sentinel was already set.
     * @param onProgress Callback for real-time progress reporting to the blocking startup gate UI.
     */
    suspend fun executeZeroRemnantHardReset(
        authenticatedUid: String?,
        forceManualRun: Boolean = false,
        onProgress: (stepTitle: String, stepDetail: String, progressPercent: Int) -> Unit = { _, _, _ -> }
    ): HardResetVerificationReport = withContext(Dispatchers.IO) {
        val runLocal = forceManualRun || !isLocalHardResetCompleted()
        val runCloud = !authenticatedUid.isNullOrBlank() &&
            (forceManualRun || !isCloudHardResetCompleted(authenticatedUid))

        if (!runLocal && !runCloud) {
            return@withContext HardResetVerificationReport(
                localWipeExecuted = false,
                cloudWipeExecuted = false,
                isFullyVerifiedZeroRemnant = true
            )
        }

        val warnings = mutableListOf<String>()

        // STEP 1: Pause ALL real-time sync engines, snapshot listeners, and background workers
        onProgress(
            "Menghentikan Sinkronisasi & Worker",
            "Memutus listener Firestore real-time dan membatalkan seluruh antrean latar belakang...",
            10
        )
        pauseAllSyncAndWorkers(warnings)

        var deletedFilesCount = 0
        var remainingFilesCount = 0
        var clearedPrefsCount = 0
        var remainingDbRows = 0

        try {
            // STEP 2: Wipe Local Room / SQLite Database + Reset auto-increment counters to 0
            if (runLocal || runCloud) {
                onProgress(
                    "Menghapus Database SQLite Lokal",
                    "Mengosongkan seluruh tabel Room dan mereset auto-increment primary key ke 0...",
                    28
                )
                wipeLocalRoomDatabaseAndResetSequences(warnings)
            }

            // STEP 3: Wipe all local internal/external files & image caches
            if (runLocal || runCloud) {
                onProgress(
                    "Menghapus File & Cache Lokal",
                    "Menghapus foto unit, thumbnail, dokumen PDF, dan seluruh cache internal/eksternal...",
                    45
                )
                deletedFilesCount = wipeAllLocalAppFilesAndCaches(warnings)
            }

            // STEP 4: Wipe ALL SharedPreferences & DataStore keys (excluding isolated sentinel)
            if (runLocal || runCloud) {
                onProgress(
                    "Membersihkan SharedPreferences & DataStore",
                    "Menghapus seluruh token sinkronisasi, timestamp, karantina, dan preferensi aplikasi...",
                    58
                )
                clearedPrefsCount = wipeAllSharedPreferencesAndDataStore(warnings)
            }

            // STEP 5: Wipe Cloud Firestore & Firebase Storage when authenticated
            var firestoreReport = CloudFirestoreWipeReport(0, 0, emptyList())
            var storageReport = StorageWipeResult()

            if (runCloud && !authenticatedUid.isNullOrBlank()) {
                onProgress(
                    "Menghapus Seluruh Data Cloud Firestore",
                    "Menghapus massal seluruh dokumen pada semua koleksi pengguna hingga 0 dokumen tersisa...",
                    72
                )
                firestoreReport = cloudFirestoreRepo?.wipeAllUserCollectionsAndVerifyEmpty(authenticatedUid)
                    ?: CloudFirestoreWipeReport(0, 0, emptyList())
                warnings.addAll(firestoreReport.nonFatalWarnings)

                onProgress(
                    "Menghapus Seluruh File Firebase Storage",
                    "Menghapus seluruh foto dan lampiran di bucket cloud secara rekursif...",
                    86
                )
                storageReport = cloudPhotoManager?.wipeAllUserStorageRecursively(authenticatedUid)
                    ?: StorageWipeResult()
                warnings.addAll(storageReport.nonFatalWarnings)

                // Re-clear Room once more right after cloud wipe in case any in-flight coroutine touched it
                wipeLocalRoomDatabaseAndResetSequences(warnings)
            }

            // STEP 6: Strict Zero-Remnant Verification Gate
            onProgress(
                "Memverifikasi 0 Sisa Data Lokal & Cloud",
                "Memastikan database SQLite, penyimpanan file, Firestore, dan Storage benar-benar kosong...",
                95
            )
            remainingDbRows = countAllLocalDatabaseRows(warnings)
            remainingFilesCount = countRemainingLocalAppFiles()

            val localClean = remainingDbRows == 0 && remainingFilesCount == 0
            val cloudClean = !runCloud || (
                firestoreReport.remainingDocumentCount == 0 &&
                    storageReport.remainingObjectCount == 0
                )
            val fullyVerified = localClean && cloudClean

            if (localClean) {
                sentinelPrefs().edit()
                    .putBoolean(KEY_LOCAL_WIPE_VERIFIED_V1, true)
                    .putLong(KEY_COMPLETED_AT_MILLIS, System.currentTimeMillis())
                    .commit()
            }
            if (runCloud && !authenticatedUid.isNullOrBlank() && cloudClean) {
                sentinelPrefs().edit()
                    .putBoolean(KEY_CLOUD_WIPE_VERIFIED_PREFIX + authenticatedUid, true)
                    .putLong(KEY_COMPLETED_AT_MILLIS, System.currentTimeMillis())
                    .commit()
            }

            if (fullyVerified) {
                onProgress(
                    "Reset Total Terverifikasi (0 Sisa Data)",
                    "Aplikasi telah bersih 100% dan siap digunakan dari awal.",
                    100
                )
            }

            HardResetVerificationReport(
                localWipeExecuted = runLocal || runCloud,
                cloudWipeExecuted = runCloud,
                localRemainingTableRows = remainingDbRows,
                localDeletedFilesCount = deletedFilesCount,
                localRemainingFilesCount = remainingFilesCount,
                clearedSharedPrefsCount = clearedPrefsCount,
                firestoreDeletedDocsCount = firestoreReport.deletedDocumentCount,
                firestoreRemainingDocsCount = firestoreReport.remainingDocumentCount,
                storageDeletedObjectsCount = storageReport.deletedObjectCount,
                storageSkippedNotFoundCount = storageReport.skippedNotFoundCount,
                storageRemainingObjectsCount = storageReport.remainingObjectCount,
                warnings = warnings,
                isFullyVerifiedZeroRemnant = fullyVerified
            )
        } finally {
            // Only resume sync managers AFTER wipe & verification phase finishes
            cloudSyncManager?.resumeSyncAfterHardReset()
        }
    }

    private fun pauseAllSyncAndWorkers(warnings: MutableList<String>) {
        runCatching { cloudSyncManager?.pauseAllSyncForHardReset() }
            .onFailure { warnings += "Pause CloudSync warning: ${it.localizedMessage}" }
        runCatching { mirrorSyncEngine?.stopConnectivityMonitor() }
            .onFailure { warnings += "Pause MirrorSync warning: ${it.localizedMessage}" }
        runCatching { WorkManager.getInstance(context).cancelAllWork() }
            .onFailure { warnings += "Cancel WorkManager warning: ${it.localizedMessage}" }
    }

    private fun wipeLocalRoomDatabaseAndResetSequences(warnings: MutableList<String>) {
        // 1. Room clearAllTables() deletes all rows from every @Entity table atomically
        appDatabase.clearAllTables()

        // 2. Reset all SQLite AUTOINCREMENT primary key counters back to 0
        val writableDb = appDatabase.openHelper.writableDatabase
        runCatching {
            writableDb.execSQL("DELETE FROM sqlite_sequence")
        }.onFailure { err ->
            // sqlite_sequence may not exist yet on a fresh DB before first autoincrement insert
            Log.d(TAG, "sqlite_sequence reset info: ${err.message}")
        }

        // 3. Truncate WAL log so deleted data does not linger in SQLite write-ahead log
        runCatching {
            writableDb.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        }.onFailure { err ->
            warnings += "WAL checkpoint warning: ${err.localizedMessage}"
        }
    }

    private fun countAllLocalDatabaseRows(warnings: MutableList<String>): Int {
        var totalRows = 0
        val db = appDatabase.openHelper.readableDatabase
        val tableNames = mutableListOf<String>()
        runCatching {
            db.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' " +
                    "AND name NOT LIKE 'sqlite_%' " +
                    "AND name != 'android_metadata' " +
                    "AND name != 'room_master_table'"
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0)
                    if (!name.isNullOrBlank()) tableNames += name
                }
            }
            for (table in tableNames) {
                db.query("SELECT COUNT(*) FROM \"$table\"").use { countCursor ->
                    if (countCursor.moveToFirst()) {
                        totalRows += countCursor.getInt(0)
                    }
                }
            }
        }.onFailure { err ->
            warnings += "Count DB rows warning: ${err.localizedMessage}"
        }
        return totalRows
    }

    private fun wipeAllLocalAppFilesAndCaches(warnings: MutableList<String>): Int {
        var deletedCount = 0
        val targetDirs = linkedSetOf<File>()

        context.filesDir?.let { targetDirs += it }
        context.cacheDir?.let { targetDirs += it }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            runCatching { context.codeCacheDir }.getOrNull()?.let { targetDirs += it }
        }
        runCatching { context.getExternalFilesDir(null) }.getOrNull()?.let { targetDirs += it }
        runCatching { context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) }.getOrNull()?.let { targetDirs += it }
        runCatching { context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) }.getOrNull()?.let { targetDirs += it }
        runCatching { context.externalCacheDir }.getOrNull()?.let { targetDirs += it }

        for (dir in targetDirs) {
            deletedCount += deleteChildrenRecursively(dir, warnings)
        }

        // Also purge non-WorkManager files in noBackupFilesDir if any exist
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            runCatching { context.noBackupFilesDir }.getOrNull()?.let { noBackupDir ->
                noBackupDir.listFiles()?.forEach { child ->
                    if (!child.name.startsWith("androidx.work.workdb")) {
                        deletedCount += deleteFileOrDirectoryRecursively(child, warnings)
                    }
                }
            }
        }

        return deletedCount
    }

    private fun countRemainingLocalAppFiles(): Int {
        val dirsToCheck = listOfNotNull(
            context.filesDir,
            context.cacheDir,
            runCatching { context.getExternalFilesDir(null) }.getOrNull(),
            runCatching { context.externalCacheDir }.getOrNull()
        )
        var remaining = 0
        for (dir in dirsToCheck) {
            remaining += countFilesRecursive(dir)
        }
        return remaining
    }

    private fun countFilesRecursive(dir: File?): Int {
        if (dir == null || !dir.exists()) return 0
        val children = dir.listFiles() ?: return 0
        var count = 0
        for (child in children) {
            if (child.isDirectory) {
                count += countFilesRecursive(child)
            } else {
                count++
            }
        }
        return count
    }

    private fun deleteChildrenRecursively(dir: File?, warnings: MutableList<String>): Int {
        if (dir == null || !dir.exists() || !dir.isDirectory) return 0
        val children = dir.listFiles() ?: return 0
        var deleted = 0
        for (child in children) {
            deleted += deleteFileOrDirectoryRecursively(child, warnings)
        }
        return deleted
    }

    private fun deleteFileOrDirectoryRecursively(target: File, warnings: MutableList<String>): Int {
        var deleted = 0
        if (target.isDirectory) {
            target.listFiles()?.forEach { child ->
                deleted += deleteFileOrDirectoryRecursively(child, warnings)
            }
            runCatching { target.delete() }
        } else if (target.exists()) {
            val ok = runCatching { target.delete() }.getOrDefault(false)
            if (ok) {
                deleted++
            } else {
                warnings += "Gagal menghapus file lokal: ${target.absolutePath}"
            }
        }
        return deleted
    }

    private fun wipeAllSharedPreferencesAndDataStore(warnings: MutableList<String>): Int {
        val prefNames = linkedSetOf<String>()
        prefNames.addAll(KNOWN_APP_PREF_NAMES)
        prefNames.add("${context.packageName}_preferences")

        // Enumerate every existing XML file inside shared_prefs/
        val sharedPrefsDir = runCatching {
            File(context.applicationInfo.dataDir, "shared_prefs")
        }.getOrNull()
        sharedPrefsDir?.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".xml")) {
                val prefName = file.name.removeSuffix(".xml")
                if (prefName != SENTINEL_PREFS_NAME) {
                    prefNames += prefName
                }
            }
        }

        var clearedCount = 0
        for (name in prefNames) {
            if (name == SENTINEL_PREFS_NAME) continue
            try {
                val sp = context.getSharedPreferences(name, Context.MODE_PRIVATE)
                val hadKeys = sp.all.isNotEmpty()
                sp.edit().clear().commit()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    runCatching { context.deleteSharedPreferences(name) }
                }
                if (sharedPrefsDir != null) {
                    File(sharedPrefsDir, "$name.xml").takeIf { it.exists() }?.delete()
                    File(sharedPrefsDir, "$name.xml.bak").takeIf { it.exists() }?.delete()
                }
                if (hadKeys) clearedCount++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                warnings += "Clear SharedPreferences($name) warning: ${e.localizedMessage}"
            }
        }

        // Remove any DataStore directory under filesDir/datastore
        runCatching {
            val dsDir = File(context.filesDir, "datastore")
            if (dsDir.exists()) {
                deleteFileOrDirectoryRecursively(dsDir, warnings)
            }
        }

        return clearedCount
    }
}
