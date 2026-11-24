package com.example.backup

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.cloud.CloudFirestoreRepository
import com.example.data.local.AppDatabase
import com.example.data.local.BackupScheduleConfig
import com.example.data.local.LocalBackupEntity
import com.example.data.repository.FreezerRepository
import com.example.drive.GoogleDriveAuthManager
import com.example.drive.GoogleDriveBackupService
import com.example.drive.GoogleDriveRestClient
import com.example.util.DateCurrencyUtils
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

object AutoBackupPreferences {
    private const val PREFS_NAME = "saf_soc_auto_backup_prefs"
    private const val KEY_FREQ = "freq"
    private const val KEY_HOUR = "hour"
    private const val KEY_MINUTE = "minute"
    private const val KEY_WIFI_ONLY = "wifi_only"
    private const val KEY_BATTERY_NOT_LOW = "battery_not_low"
    private const val KEY_MAX_VERSIONS = "max_versions"
    private const val KEY_ENCRYPT = "encrypt"
    private const val KEY_PHOTOS = "include_photos"
    private const val KEY_AUTO_UPLOAD = "auto_upload_drive"

    fun loadConfig(context: Context): BackupScheduleConfig {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return BackupScheduleConfig(
            frequency = p.getString(KEY_FREQ, "DAILY") ?: "DAILY",
            scheduledHour = p.getInt(KEY_HOUR, 1).coerceIn(0, 23),
            scheduledMinute = p.getInt(KEY_MINUTE, 0).coerceIn(0, 59),
            requireWifiOnly = p.getBoolean(KEY_WIFI_ONLY, false),
            requireBatteryNotLow = p.getBoolean(KEY_BATTERY_NOT_LOW, true),
            maxVersionsToKeep = p.getInt(KEY_MAX_VERSIONS, 30).coerceIn(5, 100),
            encryptSensitivePayload = p.getBoolean(KEY_ENCRYPT, true),
            includePhotos = p.getBoolean(KEY_PHOTOS, true),
            autoUploadToDrive = p.getBoolean(KEY_AUTO_UPLOAD, true)
        )
    }

    fun saveConfig(context: Context, config: BackupScheduleConfig) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_FREQ, config.frequency)
            .putInt(KEY_HOUR, config.scheduledHour)
            .putInt(KEY_MINUTE, config.scheduledMinute)
            .putBoolean(KEY_WIFI_ONLY, config.requireWifiOnly)
            .putBoolean(KEY_BATTERY_NOT_LOW, config.requireBatteryNotLow)
            .putInt(KEY_MAX_VERSIONS, config.maxVersionsToKeep)
            .putBoolean(KEY_ENCRYPT, config.encryptSensitivePayload)
            .putBoolean(KEY_PHOTOS, config.includePhotos)
            .putBoolean(KEY_AUTO_UPLOAD, config.autoUploadToDrive)
            .apply()
    }

    fun isNetworkConnected(context: Context, wifiOnly: Boolean): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (!hasInternet) return false
        return if (wifiOnly) caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) else true
    }
}

data class BackupExecutionOutcome(
    val success: Boolean,
    val message: String,
    val backupEntity: LocalBackupEntity? = null,
    val queuedOffline: Boolean = false
)

/**
 * Coordinates structured ZIP creation + Google Drive upload + offline queuing + audit logging.
 */
object DriveBackupCoordinator {

    suspend fun runBackupAndUploadToDrive(
        context: Context,
        repository: FreezerRepository,
        config: BackupScheduleConfig = AutoBackupPreferences.loadConfig(context),
        driveClient: GoogleDriveRestClient = GoogleDriveRestClient(),
        explicitAccessToken: String? = null,
        isNetworkConnectedOverride: Boolean? = null
    ): BackupExecutionOutcome = withContext(Dispatchers.IO) {
        val backupResult = StructuredBackupEngine.createStructuredBackupZip(
            context = context,
            repository = repository,
            encryptSensitivePayload = config.encryptSensitivePayload,
            includePhotos = config.includePhotos,
            backupType = "FULL_BACKUP",
            saveToHistory = true,
            maxVersionsToKeep = config.maxVersionsToKeep
        )

        val (savedEntity, zipFile) = backupResult.getOrElse { err ->
            repository.logBackupAuditEvent(
                actionType = "BACKUP_LOCAL",
                status = "GAGAL",
                detailMessage = err.localizedMessage ?: "Gagal membuat arsip backup"
            )
            return@withContext BackupExecutionOutcome(
                success = false,
                message = "Gagal membuat backup: ${err.localizedMessage}"
            )
        }

        val currentUser = runCatching { Firebase.auth.currentUser }.getOrNull()
        val actorUid = currentUser?.uid.orEmpty()
        val actorEmail = currentUser?.email ?: GoogleDriveAuthManager.getConnectionStatus(context).accountEmail

        val online = isNetworkConnectedOverride ?: AutoBackupPreferences.isNetworkConnected(context, config.requireWifiOnly)
        if (explicitAccessToken.isNullOrBlank()) {
            GoogleDriveAuthManager.attachAuthRefresh(context, driveClient, actorEmail)
        }
        val token = explicitAccessToken ?: GoogleDriveAuthManager.getValidAccessTokenOrNull(context, actorEmail)

        if (!config.autoUploadToDrive || token.isNullOrBlank()) {
            repository.logBackupAuditEvent(
                actionType = "BACKUP_LOCAL",
                status = "SUKSES",
                fileName = savedEntity.backupName,
                checksum = savedEntity.checksumSha256,
                recordCount = savedEntity.customerCount + savedEntity.unitCount + savedEntity.transactionCount,
                actorUid = actorUid,
                actorEmail = actorEmail,
                detailMessage = "Backup lokal terenkripsi dibuat. Hubungkan Google Drive untuk upload otomatis."
            )
            return@withContext BackupExecutionOutcome(
                success = true,
                message = "Backup lokal tersimpan (${savedEntity.backupName}). Hubungkan Google Drive untuk sinkronisasi cloud.",
                backupEntity = savedEntity,
                queuedOffline = false
            )
        }

        if (!online) {
            val queuedEntity = savedEntity.copy(
                backupStatus = "QUEUED_OFFLINE",
                locationType = "LOKAL",
                verificationStatus = "TERVERIFIKASI"
            )
            repository.saveLocalBackup(queuedEntity)
            GoogleDriveAuthManager.setPendingOfflineUpload(context, true)
            repository.logBackupAuditEvent(
                actionType = "UPLOAD_DRIVE",
                status = "QUEUED",
                fileName = queuedEntity.backupName,
                checksum = queuedEntity.checksumSha256,
                recordCount = queuedEntity.customerCount + queuedEntity.unitCount + queuedEntity.transactionCount,
                actorUid = actorUid,
                actorEmail = actorEmail,
                detailMessage = "Perangkat offline. Backup disimpan aman secara lokal dan dijadwalkan upload otomatis saat internet tersambung."
            )
            return@withContext BackupExecutionOutcome(
                success = true,
                message = "Offline: Backup tersimpan aman di perangkat dan masuk antrian upload otomatis Google Drive.",
                backupEntity = queuedEntity,
                queuedOffline = true
            )
        }

        val uploadRes = driveClient.uploadBackupZip(
            accessToken = token,
            zipFile = zipFile,
            backupId = savedEntity.backupId,
            backupType = savedEntity.backupType,
            checksumSha256 = savedEntity.checksumSha256,
            customerCount = savedEntity.customerCount,
            unitCount = savedEntity.unitCount,
            transactionCount = savedEntity.transactionCount,
            paymentCount = savedEntity.paymentCount,
            photoCount = savedEntity.photoCount
        )

        return@withContext uploadRes.fold(
            onSuccess = { driveFile ->
                val updated = savedEntity.copy(
                    locationType = "LOKAL_DAN_DRIVE",
                    driveFileId = driveFile.id,
                    backupStatus = "SUCCESS",
                    verificationStatus = "TERVERIFIKASI"
                )
                repository.saveLocalBackup(updated)
                GoogleDriveAuthManager.updateLastDriveSyncAt(context, DateCurrencyUtils.nowDateTimeDisplay())

                // Enforce max 30 versions AFTER new upload is verified!
                driveClient.pruneOldDriveBackupsAfterVerifiedUpload(token, config.maxVersionsToKeep)

                repository.logBackupAuditEvent(
                    actionType = "BACKUP_DRIVE",
                    status = "SUKSES",
                    fileName = updated.backupName,
                    checksum = updated.checksumSha256,
                    recordCount = updated.customerCount + updated.unitCount + updated.transactionCount,
                    actorUid = actorUid,
                    actorEmail = actorEmail,
                    detailMessage = "Backup terenkripsi berhasil diupload & diverifikasi ke Google Drive (ID: ${driveFile.id})."
                )

                // Mirror metadata & audit log to Firestore when Firebase user is authenticated
                if (actorUid.isNotBlank()) {
                    runCatching {
                        val cloudRepo = CloudFirestoreRepository(context)
                        cloudRepo.upsertBackupMetadata(
                            backupId = updated.backupId,
                            fileName = updated.backupName,
                            backupType = updated.backupType,
                            locationType = updated.locationType,
                            driveFileId = updated.driveFileId,
                            verificationStatus = updated.verificationStatus,
                            checksumSha256 = updated.checksumSha256,
                            fileSizeBytes = updated.backupSize,
                            isEncrypted = updated.isEncrypted,
                            customerCount = updated.customerCount,
                            unitCount = updated.unitCount,
                            transactionCount = updated.transactionCount,
                            paymentCount = updated.paymentCount,
                            photoCount = updated.photoCount,
                            createdAtDisplay = updated.createdAt
                        )
                        cloudRepo.appendBackupAuditLog(
                            actionType = "BACKUP_DRIVE",
                            status = "SUKSES",
                            fileName = updated.backupName,
                            checksum = updated.checksumSha256,
                            recordCount = updated.customerCount + updated.unitCount + updated.transactionCount,
                            actorEmail = actorEmail,
                            detailMessage = "Backup otomatis & terenkripsi berhasil dikirim ke Google Drive.",
                            timestampDisplay = updated.createdAt
                        )
                    }
                }

                BackupExecutionOutcome(
                    success = true,
                    message = "Backup terenkripsi berhasil diupload & diverifikasi di Google Drive (${updated.backupName}).",
                    backupEntity = updated,
                    queuedOffline = false
                )
            },
            onFailure = { err ->
                GoogleDriveAuthManager.setPendingOfflineUpload(context, true)
                repository.saveLocalBackup(
                    savedEntity.copy(
                        backupStatus = "QUEUED_OFFLINE",
                        locationType = "LOKAL"
                    )
                )
                repository.logBackupAuditEvent(
                    actionType = "UPLOAD_DRIVE",
                    status = "GAGAL",
                    fileName = savedEntity.backupName,
                    checksum = savedEntity.checksumSha256,
                    actorUid = actorUid,
                    actorEmail = actorEmail,
                    detailMessage = "Upload Google Drive tertunda (${err.localizedMessage}). Data lokal tetap aman."
                )
                BackupExecutionOutcome(
                    success = false,
                    message = "Backup lokal aman, namun upload Drive tertunda: ${err.localizedMessage}",
                    backupEntity = savedEntity,
                    queuedOffline = true
                )
            }
        )
    }

    suspend fun drainQueuedOfflineBackupsToDrive(
        context: Context,
        repository: FreezerRepository,
        config: BackupScheduleConfig = AutoBackupPreferences.loadConfig(context),
        driveClient: GoogleDriveRestClient = GoogleDriveRestClient(),
        explicitAccessToken: String? = null
    ): Int = withContext(Dispatchers.IO) {
        val service = GoogleDriveBackupService(context, driveClient)
        service.drainPendingOfflineBackups(
            repository = repository,
            explicitAccessToken = explicitAccessToken,
            maxVersionsToKeep = config.maxVersionsToKeep
        ).first
    }
}

class AutoBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val dao = AppDatabase.getInstance(applicationContext).freezerDao()
        val repository = FreezerRepository(dao)
        val config = AutoBackupPreferences.loadConfig(applicationContext)
        val outcome = DriveBackupCoordinator.runBackupAndUploadToDrive(
            context = applicationContext,
            repository = repository,
            config = config
        )
        return when {
            outcome.queuedOffline -> Result.retry()
            outcome.success -> Result.success()
            else -> if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}

object AutoBackupWorkScheduler {

    const val UNIQUE_PERIODIC_BACKUP_WORK = "saf_soc_auto_drive_backup"
    const val UNIQUE_IMMEDIATE_BACKUP_WORK = "saf_soc_immediate_drive_backup"

    fun computeInitialDelayToTimeMillis(hour: Int = 1, minute: Int = 0, nowMillis: Long = System.currentTimeMillis()): Long {
        val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val targetCal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= nowMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return (targetCal.timeInMillis - nowCal.timeInMillis).coerceAtLeast(1000L)
    }

    fun applyBackupSchedule(context: Context, config: BackupScheduleConfig) {
        AutoBackupPreferences.saveConfig(context, config)
        val wm = WorkManager.getInstance(context)
        if (config.frequency == "MANUAL") {
            wm.cancelUniqueWork(UNIQUE_PERIODIC_BACKUP_WORK)
            return
        }

        val intervalDays = when (config.frequency.uppercase()) {
            "WEEKLY" -> 7L
            "MONTHLY" -> 30L
            else -> 1L // DAILY default at 01:00
        }

        val initialDelayMs = computeInitialDelayToTimeMillis(config.scheduledHour, config.scheduledMinute)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (config.requireWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(config.requireBatteryNotLow)
            .build()

        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(intervalDays, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

        wm.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_BACKUP_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun enqueueImmediateDriveBackup(context: Context, wifiOnly: Boolean = false) {
        val wm = WorkManager.getInstance(context)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val req = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setConstraints(constraints)
            .build()
        wm.enqueueUniqueWork(UNIQUE_IMMEDIATE_BACKUP_WORK, ExistingWorkPolicy.REPLACE, req)
    }
}
