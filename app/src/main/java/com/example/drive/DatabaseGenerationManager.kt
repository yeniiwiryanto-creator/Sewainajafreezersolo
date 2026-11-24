package com.example.drive

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.DriveCloudManifestState
import com.example.data.local.DriveManifestRecordEntry
import com.example.data.local.DriveManifestTombstoneEntry
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

data class GenerationRotationResult(
    val previousGenerationId: String,
    val newGenerationId: String,
    val newDatasetId: String,
    val resetTimestampMillis: Long
)

/**
 * 7. DatabaseGenerationManager
 * - Tracks dataset generation via generation_id (`GENERATION_001`, `GENERATION_002`, ...).
 * - Rotates generation_id on Factory Reset & explicitly invalidates previous generations.
 * - Blocks restore/import of data belonging to invalidated generations or pre-reset manifests.
 */
class DatabaseGenerationManager(
    private val context: Context,
    private val prefsName: String = "sewain_drive_cloud_mirror_prefs",
    private val appPrefsName: String = "saf_soc_prefs"
) {
    companion object {
        const val KEY_GENERATION_SEQ = "dataset_generation_seq"
        const val KEY_DATASET_ID = "dataset_id"
        const val KEY_LAST_RESET_TS = "last_reset_timestamp_ms"
        const val KEY_LAST_SYNC_TS = "last_sync_timestamp_ms"
        const val KEY_LAST_SERVER_TS = "last_server_timestamp_ms"
        const val KEY_SYNC_VERSION = "sync_version"
        const val KEY_DATASET_STATUS = "dataset_status"
        const val KEY_OVERALL_STATUS = "overall_status"
        const val KEY_LAST_AUDITED_CLOUD_COUNT = "last_audited_cloud_count"
        const val KEY_INVALIDATED_GENERATIONS = "invalidated_generations_json"
        const val KEY_INVOICE_SEQ_PREFIX = "max_invoice_seq_"
    }

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private val appPrefs: SharedPreferences
        get() = context.getSharedPreferences(appPrefsName, Context.MODE_PRIVATE)

    fun getGenerationSequenceNumber(): Int {
        val existing = prefs.getInt(KEY_GENERATION_SEQ, 0)
        if (existing >= 1) return existing
        prefs.edit().putInt(KEY_GENERATION_SEQ, 1).commit()
        return 1
    }

    fun getCurrentGenerationId(): String {
        val seq = getGenerationSequenceNumber()
        return String.format(Locale.US, "GENERATION_%03d", seq)
    }

    fun getCurrentDatasetId(): String {
        val existing = prefs.getString(KEY_DATASET_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val generated = "dataset_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
        prefs.edit().putString(KEY_DATASET_ID, generated).commit()
        return generated
    }

    fun getLastResetTimestampMillis(): Long = prefs.getLong(KEY_LAST_RESET_TS, 0L)

    fun getLastSyncTimestampMillis(): Long = prefs.getLong(KEY_LAST_SYNC_TS, 0L)

    fun getLastServerTimestampMillis(): Long = prefs.getLong(KEY_LAST_SERVER_TS, 0L)

    fun getDatasetStatus(): String = prefs.getString(KEY_DATASET_STATUS, "ACTIVE") ?: "ACTIVE"

    fun getSyncVersion(): Long = prefs.getLong(KEY_SYNC_VERSION, 1L)

    fun nextSyncVersion(): Long {
        val next = prefs.getLong(KEY_SYNC_VERSION, 0L) + 1L
        prefs.edit().putLong(KEY_SYNC_VERSION, next).apply()
        return next
    }

    fun getInvalidatedGenerations(): Set<String> {
        val raw = prefs.getString(KEY_INVALIDATED_GENERATIONS, "").orEmpty()
        if (raw.isBlank()) return emptySet()
        return runCatching {
            val arr = JSONArray(raw)
            buildSet {
                for (i in 0 until arr.length()) {
                    val g = arr.optString(i, "").trim()
                    if (g.isNotBlank()) add(g)
                }
            }
        }.getOrDefault(emptySet())
    }

    fun isGenerationInvalidated(generationId: String): Boolean {
        val clean = generationId.trim()
        if (clean.isBlank()) return false
        return clean in getInvalidatedGenerations()
    }

    fun isGenerationValid(generationId: String): Boolean {
        val clean = generationId.trim()
        if (clean.isBlank()) return true
        return !isGenerationInvalidated(clean)
    }

    fun rotateGenerationOnFactoryReset(
        isCloudAlsoReset: Boolean,
        resetTimestampMillis: Long = System.currentTimeMillis()
    ): GenerationRotationResult {
        val previousGen = getCurrentGenerationId()
        val prevSeq = getGenerationSequenceNumber()
        val nextSeq = prevSeq + 1
        val newGen = String.format(Locale.US, "GENERATION_%03d", nextSeq)
        val newDatasetId = if (isCloudAlsoReset) {
            "dataset_reset_${resetTimestampMillis}_${UUID.randomUUID().toString().take(6)}"
        } else {
            "dataset_local_reset_${resetTimestampMillis}_${UUID.randomUUID().toString().take(6)}"
        }

        val updatedInvalidated = (getInvalidatedGenerations() + previousGen).toMutableSet()
        // Invalidate all prior numeric sequence IDs too so ancient backups can never slip through
        for (i in 1 until nextSeq) {
            updatedInvalidated.add(String.format(Locale.US, "GENERATION_%03d", i))
        }
        val invalidatedJson = JSONArray(updatedInvalidated.toList()).toString()

        val editor = prefs.edit()
            .putInt(KEY_GENERATION_SEQ, nextSeq)
            .putString(KEY_DATASET_ID, newDatasetId)
            .putLong(KEY_LAST_RESET_TS, resetTimestampMillis)
            .putLong(KEY_SYNC_VERSION, 1L)
            .putString(KEY_DATASET_STATUS, "EMPTY_AFTER_RESET")
            .putString(KEY_OVERALL_STATUS, MirrorOutboxCodec.STATUS_SYNCED)
            .putString(KEY_INVALIDATED_GENERATIONS, invalidatedJson)

        if (isCloudAlsoReset) {
            editor
                .putLong(KEY_LAST_SYNC_TS, resetTimestampMillis)
                .putInt(KEY_LAST_AUDITED_CLOUD_COUNT, 0)
        } else {
            editor.putInt(KEY_LAST_AUDITED_CLOUD_COUNT, -1)
        }
        editor.commit()

        appPrefs.edit()
            .putBoolean("intentionally_cleared_db", true)
            .putBoolean("initial_seed_already_ran", true)
            .commit()

        return GenerationRotationResult(
            previousGenerationId = previousGen,
            newGenerationId = newGen,
            newDatasetId = newDatasetId,
            resetTimestampMillis = resetTimestampMillis
        )
    }

    fun rotateGenerationOnFullRestore(
        restoreTimestampMillis: Long = System.currentTimeMillis()
    ): GenerationRotationResult {
        val previousGen = getCurrentGenerationId()
        val prevSeq = getGenerationSequenceNumber()
        val nextSeq = prevSeq + 1
        val newGen = String.format(Locale.US, "GENERATION_%03d", nextSeq)
        val newDatasetId = "dataset_restore_${restoreTimestampMillis}_${UUID.randomUUID().toString().take(6)}"

        // Advance generation & dataset identity without adding to Factory-Reset invalidated set,
        // because user-initiated Full Restore reconciles pre-restore cloud records via explicit
        // outbox tombstones while still allowing subsequent user-initiated APPEND_MERGE imports.
        prefs.edit()
            .putInt(KEY_GENERATION_SEQ, nextSeq)
            .putString(KEY_DATASET_ID, newDatasetId)
            .putLong(KEY_SYNC_VERSION, 1L)
            .putString(KEY_DATASET_STATUS, "ACTIVE")
            .putString(KEY_OVERALL_STATUS, MirrorOutboxCodec.STATUS_PENDING_SYNC)
            .putInt(KEY_LAST_AUDITED_CLOUD_COUNT, -1)
            .commit()

        appPrefs.edit()
            .putBoolean("intentionally_cleared_db", false)
            .putBoolean("initial_seed_already_ran", true)
            .commit()

        return GenerationRotationResult(
            previousGenerationId = previousGen,
            newGenerationId = newGen,
            newDatasetId = newDatasetId,
            resetTimestampMillis = restoreTimestampMillis
        )
    }

    fun markDatasetActive() {
        prefs.edit()
            .putString(KEY_DATASET_STATUS, "ACTIVE")
            .remove(KEY_LAST_AUDITED_CLOUD_COUNT)
            .apply()
    }

    fun recordSyncBatchMetadata(
        syncFinishedMillis: Long,
        serverRefMillis: Long,
        overallStatus: String
    ) {
        val edit = prefs.edit()
            .putLong(KEY_LAST_SERVER_TS, serverRefMillis)
            .putInt(KEY_LAST_AUDITED_CLOUD_COUNT, -1)
        if (overallStatus == MirrorOutboxCodec.STATUS_SYNCED) {
            edit
                .putLong(KEY_LAST_SYNC_TS, syncFinishedMillis)
                .putString(KEY_OVERALL_STATUS, MirrorOutboxCodec.STATUS_SYNCED)
        }
        edit.apply()
    }

    fun recordManifestSyncSuccess(syncMillis: Long, status: String) {
        prefs.edit()
            .putLong(KEY_LAST_SYNC_TS, syncMillis)
            .putString(KEY_DATASET_STATUS, status)
            .apply()
    }

    fun recordAuditedCloudState(cloudOpsCount: Int, serverRefMillis: Long) {
        prefs.edit()
            .putInt(KEY_LAST_AUDITED_CLOUD_COUNT, cloudOpsCount)
            .putLong(KEY_LAST_SERVER_TS, serverRefMillis)
            .apply()
    }

    fun getLastAuditedCloudCount(): Int = prefs.getInt(KEY_LAST_AUDITED_CLOUD_COUNT, -1)

    /**
     * Parses `INV/SAF/YYYY/XXXX` (or `INV/SAF/YYYYMMDD/XXXX`) and advances the monotonic
     * per-year high-water sequence floor (local + cloud metadata combined) so invoice numbers
     * never regress back to `0001` after a failed local transaction or partial sync.
     */
    fun recordObservedInvoiceNumber(invoiceNumber: String) {
        val parsed = parseInvoiceYearAndSequence(invoiceNumber) ?: return
        val (year, seq) = parsed
        if (seq <= 0) return
        val key = "$KEY_INVOICE_SEQ_PREFIX$year"
        val current = prefs.getInt(key, 0)
        if (seq > current) {
            prefs.edit().putInt(key, seq).commit()
        }
    }

    fun getObservedMaxInvoiceSequenceForYear(year: Int): Int =
        prefs.getInt("$KEY_INVOICE_SEQ_PREFIX$year", 0).coerceAtLeast(0)

    fun parseInvoiceYearAndSequence(invoiceNumber: String): Pair<Int, Int>? {
        val m = Regex("""INV/SAF/(\d{4})\d*/(\d+)""", RegexOption.IGNORE_CASE)
            .find(invoiceNumber.trim()) ?: return null
        val year = m.groupValues[1].toIntOrNull() ?: return null
        val seq = m.groupValues[2].toIntOrNull() ?: return null
        return year to seq
    }

    fun validateCloudManifestBeforeRestore(manifest: DriveCloudManifestState): Result<Unit> {
        if (manifest.applicationId.isNotBlank() &&
            manifest.applicationId != MirrorOutboxCodec.APPLICATION_ID
        ) {
            return Result.failure(
                IllegalStateException("Manifest aplikasi tidak cocok (${manifest.applicationId}).")
            )
        }
        if (manifest.resetCompleted || manifest.datasetStatus == "EMPTY_AFTER_RESET") {
            return Result.failure(
                IllegalStateException("Manifest Google Drive berstatus EMPTY_AFTER_RESET (dataset kosong setelah Factory Reset).")
            )
        }
        if (isGenerationInvalidated(manifest.datasetGeneration)) {
            return Result.failure(
                IllegalStateException("Generasi manifest (${manifest.datasetGeneration}) sudah di-invalidate oleh Factory Reset dan ditolak.")
            )
        }
        val lastResetMs = getLastResetTimestampMillis()
        if (lastResetMs > 0L && manifest.lastSyncTimestamp in 1L until lastResetMs) {
            return Result.failure(
                IllegalStateException("Timestamp manifest cloud lebih lama daripada waktu Factory Reset terakhir.")
            )
        }
        return Result.success(Unit)
    }

    fun parseAndValidateManifestJson(manifestJsonText: String): Result<DriveCloudManifestState> {
        return try {
            val obj = JSONObject(manifestJsonText)
            val activeArr = obj.optJSONArray("activeRecords") ?: JSONArray()
            val activeRecords = buildList {
                for (i in 0 until activeArr.length()) {
                    val r = activeArr.getJSONObject(i)
                    add(
                        DriveManifestRecordEntry(
                            localRecordKey = r.optString("localRecordKey", ""),
                            recordCategory = r.optString("recordCategory", ""),
                            recordId = r.optLong("recordId", 0L),
                            recordUuid = r.optString("recordUuid", ""),
                            revision = r.optLong("revision", 1L),
                            driveFileId = r.optString("driveFileId", ""),
                            contentHash = r.optString("contentHash", ""),
                            updatedAtMillis = r.optLong("updatedAtMillis", 0L),
                            fileName = r.optString("fileName", "")
                        )
                    )
                }
            }
            val tombArr = obj.optJSONArray("tombstones") ?: JSONArray()
            val tombstones = buildList {
                for (i in 0 until tombArr.length()) {
                    val t = tombArr.getJSONObject(i)
                    add(
                        DriveManifestTombstoneEntry(
                            localRecordKey = t.optString("localRecordKey", ""),
                            recordCategory = t.optString("recordCategory", ""),
                            recordId = t.optLong("recordId", 0L),
                            recordUuid = t.optString("recordUuid", ""),
                            deletedVersion = t.optLong("deletedVersion", 1L),
                            deletedAtMillis = t.optLong("deletedAtMillis", 0L),
                            uuidVerifiedGone = t.optBoolean("uuidVerifiedGone", false)
                        )
                    )
                }
            }
            val manifest = DriveCloudManifestState(
                applicationId = obj.optString("applicationId", MirrorOutboxCodec.APPLICATION_ID),
                userId = obj.optString("userId", "owner_default"),
                datasetId = obj.optString("datasetId", ""),
                datasetGeneration = obj.optString("datasetGeneration", obj.optString("generation_id", "")),
                databaseVersion = obj.optInt("databaseVersion", MirrorOutboxCodec.CURRENT_SCHEMA_VERSION),
                lastSyncTimestamp = obj.optLong("lastSyncTimestamp", 0L),
                lastResetTimestamp = obj.optLong("lastResetTimestamp", 0L),
                resetCompleted = obj.optBoolean("resetCompleted", false),
                totalCustomers = obj.optInt("totalCustomers", 0),
                totalUnits = obj.optInt("totalUnits", 0),
                totalTransactions = obj.optInt("totalTransactions", 0),
                totalPayments = obj.optInt("totalPayments", 0),
                totalPhotos = obj.optInt("totalPhotos", 0),
                syncVersion = obj.optLong("syncVersion", 1L),
                datasetStatus = obj.optString("datasetStatus", "ACTIVE"),
                activeRecords = activeRecords,
                tombstones = tombstones
            )
            validateCloudManifestBeforeRestore(manifest).getOrElse { return Result.failure(it) }
            Result.success(manifest)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
