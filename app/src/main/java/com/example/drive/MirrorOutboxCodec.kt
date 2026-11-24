package com.example.drive

import com.example.data.local.CustomerEntity
import com.example.data.local.DriveCloudFileMapEntity
import com.example.data.local.DriveDeletionTombstoneEntity
import com.example.data.local.DriveLocalSyncQueueEntity
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.PaymentProofEntity
import com.example.data.local.RentalTransactionEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

data class TransactionalOutboxPlan(
    val localRecordKey: String,
    val recordCategory: String,
    val recordId: Long,
    val recordUuid: String,
    val fileMap: DriveCloudFileMapEntity,
    val queueItem: DriveLocalSyncQueueEntity
)

data class TransactionalDeleteOutboxPlan(
    val localRecordKey: String,
    val recordUuid: String,
    val tombstone: DriveDeletionTombstoneEntity,
    val updatedFileMap: DriveCloudFileMapEntity?,
    val queueItem: DriveLocalSyncQueueEntity
)

/**
 * Canonical codec and Change Data Tracker shared by Room @Transaction DAO methods,
 * FreezerRepository, and GoogleDriveMirrorSyncEngine.
 * Guarantees Transactional Outbox consistency even if MirrorSyncEngine is detached/null.
 */
object MirrorOutboxCodec {

    const val APPLICATION_ID = "com.aistudio.sewainajafreezer.soc"
    const val APP_ID_KEY = "appId"
    const val APP_ID_VALUE = "sewain_aja_freezer_soc"
    const val CATEGORY_CUSTOMER = "CUSTOMER"
    const val CATEGORY_UNIT = "UNIT"
    const val CATEGORY_TRANSACTION = "TRANSACTION"
    const val CATEGORY_PAYMENT = "PAYMENT"

    const val OP_CREATE = "CREATE"
    const val OP_UPDATE = "UPDATE"
    const val OP_DELETE = "DELETE"

    const val STATUS_PENDING = "PENDING"
    const val STATUS_PENDING_CREATE = "PENDING_CREATE"
    const val STATUS_PENDING_UPDATE = "PENDING_UPDATE"
    const val STATUS_PENDING_DELETE = "PENDING_DELETE"
    const val STATUS_PENDING_SYNC = "PENDING_SYNC"
    const val STATUS_SYNCED = "SYNCED"
    const val STATUS_DELETE_PENDING = "DELETE_PENDING"

    const val CURRENT_SCHEMA_VERSION = 25

    private val UUID_V4_REGEX = Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$"
    )

    fun isValidUuidV4(candidate: String?): Boolean {
        val trimmed = candidate?.trim().orEmpty()
        return UUID_V4_REGEX.matches(trimmed)
    }

    fun generateUuidV4(): String = UUID.randomUUID().toString()

    fun ensureValidUuidV4(candidate: String?): String {
        val trimmed = candidate?.trim().orEmpty()
        return if (trimmed.isNotBlank()) trimmed else UUID.randomUUID().toString()
    }

    fun buildRecordAppProperties(
        recordUuid: String,
        entityType: String,
        generationId: String,
        revision: Long,
        checksum: String,
        syncTimestamp: Long,
        datasetId: String = "",
        localRecordKey: String = "",
        recordId: Long = 0L,
        schemaVersion: Int = CURRENT_SCHEMA_VERSION
    ): Map<String, String> = buildMap {
        put(APP_ID_KEY, APP_ID_VALUE)
        put("applicationId", APPLICATION_ID)
        // Standard snake_case appProperties (Requirement B)
        if (recordUuid.isNotBlank()) put("record_uuid", recordUuid)
        if (entityType.isNotBlank()) put("entity_type", entityType)
        if (generationId.isNotBlank()) put("generation_id", generationId)
        put("revision", revision.toString())
        put("schema_version", schemaVersion.toString())
        if (checksum.isNotBlank()) put("checksum", checksum)
        put("sync_timestamp", syncTimestamp.toString())
        // Backward-compatible camelCase appProperties
        if (recordUuid.isNotBlank()) put("recordUuid", recordUuid)
        if (entityType.isNotBlank()) put("recordCategory", entityType)
        if (generationId.isNotBlank()) put("datasetGeneration", generationId)
        put("version", revision.toString())
        if (checksum.isNotBlank()) put("checksumSha256", checksum)
        put("updatedAtMillis", syncTimestamp.toString())
        if (datasetId.isNotBlank()) put("datasetId", datasetId)
        if (localRecordKey.isNotBlank()) put("localRecordKey", localRecordKey)
        if (recordId > 0L) put("recordId", recordId.toString())
    }

    fun isPendingEntityStatus(status: String): Boolean {
        return status == STATUS_PENDING ||
            status == STATUS_PENDING_CREATE ||
            status == STATUS_PENDING_UPDATE ||
            status == STATUS_PENDING_DELETE ||
            status == STATUS_PENDING_SYNC ||
            status == STATUS_DELETE_PENDING
    }

    fun computeSha256(text: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun computeFileSha256(file: File): String {
        if (!file.exists() || !file.isFile) return ""
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buf = ByteArray(8192)
            var read: Int
            while (fis.read(buf).also { read = it } != -1) {
                md.update(buf, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun buildDeterministicRecordFileName(
        recordCategory: String,
        recordId: Long,
        recordUuid: String = ""
    ): String {
        return when (recordCategory) {
            "SETTINGS" -> "APP_SETTINGS.json"
            "CLOUD_MANIFEST" -> "manifest.json"
            else -> {
                val cleanUuid = recordUuid.trim()
                if (cleanUuid.isNotBlank()) "${cleanUuid}.json" else "${recordCategory}_${recordId}.json"
            }
        }
    }

    fun computeUnitChangedFieldsJson(existing: FreezerUnitEntity?, updated: FreezerUnitEntity): String {
        if (existing == null) return JSONArray(listOf("*CREATE*")).toString()
        val changed = mutableListOf<String>()
        if (existing.kodeUnit != updated.kodeUnit) changed.add("kodeUnit")
        if (existing.namaUnit != updated.namaUnit) changed.add("namaUnit")
        if (existing.jenisUnit != updated.jenisUnit) changed.add("jenisUnit")
        if (existing.owner != updated.owner) changed.add("owner")
        if (existing.hargaPembelian != updated.hargaPembelian) changed.add("hargaPembelian")
        if (existing.kapasitasLiter != updated.kapasitasLiter) changed.add("kapasitasLiter")
        if (existing.cicilanPerBulan != updated.cicilanPerBulan) changed.add("cicilanPerBulan")
        if (existing.jumlahCicilanTotal != updated.jumlahCicilanTotal) changed.add("jumlahCicilanTotal")
        if (existing.totalPembayaranCicilan != updated.totalPembayaranCicilan) changed.add("totalPembayaranCicilan")
        if (existing.statusInvestasi != updated.statusInvestasi) changed.add("statusInvestasi")
        if (existing.kategoriSheet != updated.kategoriSheet) changed.add("kategoriSheet")
        if (existing.merek != updated.merek) changed.add("merek")
        if (existing.model != updated.model) changed.add("model")
        if (existing.nomorSeri != updated.nomorSeri) changed.add("nomorSeri")
        if (existing.fotoUnitUri != updated.fotoUnitUri) changed.add("fotoUnitUri")
        if (existing.fotoKondisiFisikUri != updated.fotoKondisiFisikUri) changed.add("fotoKondisiFisikUri")
        if (existing.fotoBagianDalamUri != updated.fotoBagianDalamUri) changed.add("fotoBagianDalamUri")
        if (existing.fotoMerekSeriUri != updated.fotoMerekSeriUri) changed.add("fotoMerekSeriUri")
        if (existing.statusUnit != updated.statusUnit) changed.add("statusUnit")
        if (existing.lokasiUnit != updated.lokasiUnit) changed.add("lokasiUnit")
        if (existing.tanggalPembelian != updated.tanggalPembelian) changed.add("tanggalPembelian")
        if (existing.keterangan != updated.keterangan) changed.add("keterangan")
        if (existing.kondisiUnit != updated.kondisiUnit) changed.add("kondisiUnit")
        if (existing.maintenanceHistory != updated.maintenanceHistory) changed.add("maintenanceHistory")
        return JSONArray(changed).toString()
    }

    fun computeCustomerChangedFieldsJson(existing: CustomerEntity?, updated: CustomerEntity): String {
        if (existing == null) return JSONArray(listOf("*CREATE*")).toString()
        val changed = mutableListOf<String>()
        if (existing.kodePelanggan != updated.kodePelanggan) changed.add("kodePelanggan")
        if (existing.namaLengkap != updated.namaLengkap) changed.add("namaLengkap")
        if (existing.nik != updated.nik) changed.add("nik")
        if (existing.nomorHp != updated.nomorHp) changed.add("nomorHp")
        if (existing.alamatLengkap != updated.alamatLengkap) changed.add("alamatLengkap")
        if (existing.jenisKelamin != updated.jenisKelamin) changed.add("jenisKelamin")
        if (existing.fotoKtpDepanUri != updated.fotoKtpDepanUri) changed.add("fotoKtpDepanUri")
        if (existing.fotoKtpBelakangUri != updated.fotoKtpBelakangUri) changed.add("fotoKtpBelakangUri")
        if (existing.fotoPelangganUri != updated.fotoPelangganUri) changed.add("fotoPelangganUri")
        if (existing.catatan != updated.catatan) changed.add("catatan")
        if (existing.statusPelanggan != updated.statusPelanggan) changed.add("statusPelanggan")
        if (existing.lokasiPenggunaanDefault != updated.lokasiPenggunaanDefault) changed.add("lokasiPenggunaanDefault")
        if (existing.latitudeDefault != updated.latitudeDefault) changed.add("latitudeDefault")
        if (existing.longitudeDefault != updated.longitudeDefault) changed.add("longitudeDefault")
        return JSONArray(changed).toString()
    }

    fun computeTransactionChangedFieldsJson(existing: RentalTransactionEntity?, updated: RentalTransactionEntity): String {
        if (existing == null) return JSONArray(listOf("*CREATE*")).toString()
        val changed = mutableListOf<String>()
        if (existing.nomorInvoice != updated.nomorInvoice) changed.add("nomorInvoice")
        if (existing.customerId != updated.customerId) changed.add("customerId")
        if (existing.namaPelangganSnapshot != updated.namaPelangganSnapshot) changed.add("namaPelangganSnapshot")
        if (existing.unitId != updated.unitId) changed.add("unitId")
        if (existing.namaUnitSnapshot != updated.namaUnitSnapshot) changed.add("namaUnitSnapshot")
        if (existing.kodeUnitSnapshot != updated.kodeUnitSnapshot) changed.add("kodeUnitSnapshot")
        if (existing.jenisProgram != updated.jenisProgram) changed.add("jenisProgram")
        if (existing.hargaSewa != updated.hargaSewa) changed.add("hargaSewa")
        if (existing.durasiHari != updated.durasiHari) changed.add("durasiHari")
        if (existing.tanggalMulaiSewa != updated.tanggalMulaiSewa) changed.add("tanggalMulaiSewa")
        if (existing.tanggalAkhirSewa != updated.tanggalAkhirSewa) changed.add("tanggalAkhirSewa")
        if (existing.tanggalJatuhTempo != updated.tanggalJatuhTempo) changed.add("tanggalJatuhTempo")
        if (existing.nominalPembayaran != updated.nominalPembayaran) changed.add("nominalPembayaran")
        if (existing.totalTagihan != updated.totalTagihan) changed.add("totalTagihan")
        if (existing.statusPembayaran != updated.statusPembayaran) changed.add("statusPembayaran")
        if (existing.statusPenyewaan != updated.statusPenyewaan) changed.add("statusPenyewaan")
        if (existing.metodePembayaran != updated.metodePembayaran) changed.add("metodePembayaran")
        if (existing.catatan != updated.catatan) changed.add("catatan")
        if (existing.fotoSerahTerimaUri != updated.fotoSerahTerimaUri) changed.add("fotoSerahTerimaUri")
        if (existing.tandaTanganPelangganUri != updated.tandaTanganPelangganUri) changed.add("tandaTanganPelangganUri")
        if (existing.namaPetugas != updated.namaPetugas) changed.add("namaPetugas")
        if (existing.tandaTanganPetugasUri != updated.tandaTanganPetugasUri) changed.add("tandaTanganPetugasUri")
        if (existing.officerAccountId != updated.officerAccountId) changed.add("officerAccountId")
        if (existing.officerSnapshotLocked != updated.officerSnapshotLocked) changed.add("officerSnapshotLocked")
        if (existing.officerSignedAt != updated.officerSignedAt) changed.add("officerSignedAt")
        if (existing.isDeleted != updated.isDeleted) changed.add("isDeleted")
        if (existing.deletedAtMillis != updated.deletedAtMillis) changed.add("deletedAtMillis")
        if (existing.deletedBy != updated.deletedBy) changed.add("deletedBy")
        return JSONArray(changed).toString()
    }

    fun computePaymentProofChangedFieldsJson(existing: PaymentProofEntity?, updated: PaymentProofEntity): String {
        if (existing == null) return JSONArray(listOf("*CREATE*")).toString()
        val changed = mutableListOf<String>()
        if (existing.transactionId != updated.transactionId) changed.add("transactionId")
        if (existing.tanggalPembayaran != updated.tanggalPembayaran) changed.add("tanggalPembayaran")
        if (existing.nominalPembayaran != updated.nominalPembayaran) changed.add("nominalPembayaran")
        if (existing.metodePembayaran != updated.metodePembayaran) changed.add("metodePembayaran")
        if (existing.namaPengirim != updated.namaPengirim) changed.add("namaPengirim")
        if (existing.catatanPembayaran != updated.catatanPembayaran) changed.add("catatanPembayaran")
        if (existing.fotoBuktiUri != updated.fotoBuktiUri) changed.add("fotoBuktiUri")
        return JSONArray(changed).toString()
    }

    fun serializeCustomerJson(
        c: CustomerEntity,
        datasetId: String,
        datasetGenerationId: String,
        serverSyncTimeMs: Long = 0L
    ): String = JSONObject().apply {
        put("id", c.id)
        put("uuid", c.uuid)
        put("version", c.version)
        put("revision", c.version)
        put("created_at", c.createdAtMillis)
        put("updated_at", c.updatedAtMillis)
        put("sync_status", c.syncStatus)
        put("is_deleted", c.isDeleted)
        put("drive_id", c.driveId)
        if (c.changedFieldsJson.isNotBlank()) put("changed_fields", c.changedFieldsJson)
        put("server_sync_time_ms", serverSyncTimeMs)
        put("kodePelanggan", c.kodePelanggan)
        put("namaLengkap", c.namaLengkap)
        put("nik", c.nik)
        put("nomorHp", c.nomorHp)
        put("nomorWhatsapp", c.nomorHp)
        put("alamatLengkap", c.alamatLengkap)
        put("rt", c.rt)
        put("rw", c.rw)
        put("kelurahanDesa", c.kelurahanDesa)
        put("kecamatan", c.kecamatan)
        put("kabupatenKota", c.kabupatenKota)
        put("fotoKtpDepanUri", c.fotoKtpDepanUri)
        put("fotoKtpBelakangUri", c.fotoKtpBelakangUri)
        put("fotoPelangganUri", c.fotoPelangganUri)
        put("catatan", c.catatan)
        put("statusPelanggan", c.statusPelanggan)
        put("applicationId", APPLICATION_ID)
        put(APP_ID_KEY, APP_ID_VALUE)
        put("datasetId", datasetId)
        put("datasetGeneration", datasetGenerationId)
    }.toString()

    fun serializeUnitJson(
        u: FreezerUnitEntity,
        datasetId: String,
        datasetGenerationId: String,
        serverSyncTimeMs: Long = 0L
    ): String = JSONObject().apply {
        put("id", u.id)
        put("uuid", u.uuid)
        put("version", u.version)
        put("revision", u.version)
        put("created_at", u.createdAtMillis)
        put("updated_at", u.updatedAtMillis)
        put("sync_status", u.syncStatus)
        put("is_deleted", u.isDeleted)
        put("drive_id", u.driveId)
        if (u.changedFieldsJson.isNotBlank()) put("changed_fields", u.changedFieldsJson)
        put("server_sync_time_ms", serverSyncTimeMs)
        put("kodeUnit", u.kodeUnit)
        put("namaUnit", u.namaUnit)
        put("jenisUnit", u.jenisUnit)
        put("merek", u.merek)
        put("model", u.model)
        put("kapasitasLiter", u.kapasitasLiter)
        put("nomorSeri", u.nomorSeri)
        put("hargaPembelian", u.hargaPembelian)
        put("cicilanPerBulan", u.cicilanPerBulan)
        put("statusInvestasi", u.statusInvestasi)
        put("statusUnit", u.statusUnit)
        put("kondisiUnit", u.kondisiUnit)
        put("maintenanceHistory", u.maintenanceHistory)
        put("fotoUnitUri", u.fotoUnitUri)
        put("applicationId", APPLICATION_ID)
        put(APP_ID_KEY, APP_ID_VALUE)
        put("datasetId", datasetId)
        put("datasetGeneration", datasetGenerationId)
    }.toString()

    fun serializeTransactionJson(
        tx: RentalTransactionEntity,
        datasetId: String,
        datasetGenerationId: String,
        serverSyncTimeMs: Long = 0L
    ): String = JSONObject().apply {
        put("id", tx.id)
        put("uuid", tx.uuid)
        put("version", tx.version)
        put("revision", tx.version)
        put("created_at", tx.createdAtMillis)
        put("updated_at", tx.updatedAtMillis)
        put("sync_status", tx.syncStatus)
        put("is_deleted", tx.isDeleted)
        put("deleted_at", tx.deletedAtMillis)
        put("deleted_by", tx.deletedBy)
        put("drive_id", tx.driveId)
        if (tx.changedFieldsJson.isNotBlank()) put("changed_fields", tx.changedFieldsJson)
        put("server_sync_time_ms", serverSyncTimeMs)
        put("nomorInvoice", tx.nomorInvoice)
        put("customerId", tx.customerId)
        put("unitId", tx.unitId)
        put("namaUnitSnapshot", tx.namaUnitSnapshot)
        put("kodeUnitSnapshot", tx.kodeUnitSnapshot)
        put("jenisProgram", tx.jenisProgram)
        put("tanggalMulaiSewa", tx.tanggalMulaiSewa)
        put("tanggalAkhirSewa", tx.tanggalAkhirSewa)
        put("tanggalJatuhTempo", tx.tanggalJatuhTempo)
        put("totalTagihan", tx.totalTagihan)
        put("nominalPembayaran", tx.nominalPembayaran)
        put("statusPembayaran", tx.statusPembayaran)
        put("statusPenyewaan", tx.statusPenyewaan)
        put("namaPetugas", tx.namaPetugas)
        put("tandaTanganPetugasUri", tx.tandaTanganPetugasUri)
        put("officerAccountId", tx.officerAccountId)
        put("officerSnapshotLocked", tx.officerSnapshotLocked)
        put("officerSignedAt", tx.officerSignedAt)
        put("applicationId", APPLICATION_ID)
        put(APP_ID_KEY, APP_ID_VALUE)
        put("datasetId", datasetId)
        put("datasetGeneration", datasetGenerationId)
    }.toString()

    fun serializePaymentJson(
        p: PaymentProofEntity,
        datasetId: String,
        datasetGenerationId: String,
        serverSyncTimeMs: Long = 0L
    ): String = JSONObject().apply {
        put("id", p.id)
        put("uuid", p.uuid)
        put("version", p.version)
        put("revision", p.version)
        put("created_at", p.createdAtMillis)
        put("updated_at", p.updatedAtMillis)
        put("sync_status", p.syncStatus)
        put("is_deleted", p.isDeleted)
        put("drive_id", p.driveId)
        if (p.changedFieldsJson.isNotBlank()) put("changed_fields", p.changedFieldsJson)
        put("server_sync_time_ms", serverSyncTimeMs)
        put("transactionId", p.transactionId)
        put("tanggalPembayaran", p.tanggalPembayaran)
        put("nominalPembayaran", p.nominalPembayaran)
        put("metodePembayaran", p.metodePembayaran)
        put("namaPengirim", p.namaPengirim)
        put("catatanPembayaran", p.catatanPembayaran)
        put("fotoBuktiUri", p.fotoBuktiUri)
        put("applicationId", APPLICATION_ID)
        put(APP_ID_KEY, APP_ID_VALUE)
        put("datasetId", datasetId)
        put("datasetGeneration", datasetGenerationId)
    }.toString()

    fun buildUpsertOutboxPlan(
        localRecordKey: String,
        recordCategory: String,
        recordId: Long,
        recordUuid: String,
        entityVersion: Long,
        updatedAtMillis: Long,
        payloadJson: String,
        isNew: Boolean,
        datasetGenerationId: String,
        changedFieldsJson: String,
        existingMap: DriveCloudFileMapEntity?,
        latestQueue: DriveLocalSyncQueueEntity?,
        serverTimestampMillis: Long = 0L,
        explicitDriveFileId: String = ""
    ): TransactionalOutboxPlan? {
        val now = updatedAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
        val contentHash = computeSha256(payloadJson)
        val resolvedUuid = ensureValidUuidV4(recordUuid.ifBlank { existingMap?.recordUuid.orEmpty() })
        val resolvedDriveFileId = explicitDriveFileId.ifBlank { existingMap?.driveFileId.orEmpty() }

        if (existingMap != null &&
            existingMap.contentHash == contentHash &&
            existingMap.syncStatus == STATUS_SYNCED &&
            resolvedDriveFileId.isNotBlank() &&
            latestQueue?.syncStatus != STATUS_PENDING_SYNC
        ) {
            return null
        }
        if (latestQueue != null &&
            latestQueue.contentHash == contentHash &&
            latestQueue.syncStatus == STATUS_PENDING_SYNC &&
            latestQueue.operationType != OP_DELETE
        ) {
            return null
        }

        val currentKnownMaxVersion = maxOf(existingMap?.version ?: 0L, latestQueue?.version ?: 0L)
        val baseVersion = existingMap?.version ?: 0L
        val nextVersion = if (entityVersion > currentKnownMaxVersion) {
            entityVersion
        } else {
            currentKnownMaxVersion + 1L
        }
        val rawOp = if (isNew) OP_CREATE else OP_UPDATE
        val effectiveOp = when {
            resolvedDriveFileId.isNotBlank() && rawOp == OP_CREATE -> OP_UPDATE
            resolvedDriveFileId.isBlank() &&
                rawOp == OP_UPDATE &&
                latestQueue?.operationType == OP_CREATE &&
                latestQueue.syncStatus != STATUS_SYNCED -> OP_CREATE
            else -> rawOp
        }

        val fileName = buildDeterministicRecordFileName(recordCategory, recordId, resolvedUuid)
        val fileMap = DriveCloudFileMapEntity(
            localRecordKey = localRecordKey,
            recordCategory = recordCategory,
            localRecordId = recordId,
            driveFileId = resolvedDriveFileId,
            driveFolderId = existingMap?.driveFolderId.orEmpty(),
            fileName = fileName,
            contentHash = contentHash,
            version = nextVersion,
            lastModifiedMillis = now,
            syncStatus = STATUS_PENDING,
            datasetGenerationId = datasetGenerationId,
            recordUuid = resolvedUuid,
            serverTimestampMillis = existingMap?.serverTimestampMillis ?: 0L,
            conflictReason = ""
        )

        val opId = "${resolvedUuid}_${effectiveOp}_v${nextVersion}_${contentHash.take(12)}"
        val queueItem = DriveLocalSyncQueueEntity(
            operationId = opId,
            recordCategory = recordCategory,
            recordId = recordId,
            localRecordKey = localRecordKey,
            operationType = effectiveOp,
            version = nextVersion,
            timestampMillis = now,
            payloadJson = payloadJson,
            contentHash = contentHash,
            syncStatus = STATUS_PENDING_SYNC,
            datasetGenerationId = datasetGenerationId,
            recordUuid = resolvedUuid,
            baseVersion = baseVersion,
            serverTimestampMillis = serverTimestampMillis,
            changedFieldsJson = changedFieldsJson
        )
        return TransactionalOutboxPlan(
            localRecordKey = localRecordKey,
            recordCategory = recordCategory,
            recordId = recordId,
            recordUuid = resolvedUuid,
            fileMap = fileMap,
            queueItem = queueItem
        )
    }

    fun buildDeleteOutboxPlan(
        localRecordKey: String,
        recordCategory: String,
        recordId: Long,
        recordUuid: String,
        explicitDriveFileId: String = "",
        datasetGenerationId: String,
        existingMap: DriveCloudFileMapEntity?,
        latestQueue: DriveLocalSyncQueueEntity?,
        serverTimestampMillis: Long = 0L
    ): TransactionalDeleteOutboxPlan {
        val now = System.currentTimeMillis()
        val nextVersion = maxOf(existingMap?.version ?: 0L, latestQueue?.version ?: 0L) + 1L
        val targetDriveFileId = explicitDriveFileId.ifBlank { existingMap?.driveFileId.orEmpty() }
        val resolvedUuid = recordUuid.ifBlank { existingMap?.recordUuid.orEmpty() }

        val tombstone = DriveDeletionTombstoneEntity(
            localRecordKey = localRecordKey,
            recordCategory = recordCategory,
            recordId = recordId,
            deletedVersion = nextVersion,
            deletedAtMillis = now,
            driveFileIdAtDeletion = targetDriveFileId,
            syncedToCloud = false,
            datasetGenerationId = datasetGenerationId,
            recordUuid = resolvedUuid,
            uuidVerifiedGone = false,
            manifestUpdatedAtDeletion = false
        )

        val updatedMap = existingMap?.copy(
            version = nextVersion,
            lastModifiedMillis = now,
            syncStatus = STATUS_DELETE_PENDING,
            datasetGenerationId = datasetGenerationId,
            recordUuid = resolvedUuid
        )

        val payloadJson = JSONObject().apply {
            put("localRecordKey", localRecordKey)
            put("recordCategory", recordCategory)
            put("recordId", recordId)
            put("recordUuid", resolvedUuid)
            put("driveFileId", targetDriveFileId)
            put("deletedVersion", nextVersion)
            put("deletedAtMillis", now)
            put("datasetGeneration", datasetGenerationId)
        }.toString()
        val hash = computeSha256(payloadJson)
        val opId = "${datasetGenerationId}_${localRecordKey}_DELETE_v${nextVersion}_${hash.take(10)}"
        val queueItem = DriveLocalSyncQueueEntity(
            operationId = opId,
            recordCategory = recordCategory,
            recordId = recordId,
            localRecordKey = localRecordKey,
            operationType = OP_DELETE,
            version = nextVersion,
            timestampMillis = now,
            payloadJson = payloadJson,
            previousDriveFileId = targetDriveFileId,
            contentHash = hash,
            syncStatus = STATUS_PENDING_SYNC,
            datasetGenerationId = datasetGenerationId,
            recordUuid = resolvedUuid,
            baseVersion = existingMap?.version ?: 0L,
            serverTimestampMillis = serverTimestampMillis,
            changedFieldsJson = "[\"*DELETE*\"]"
        )
        return TransactionalDeleteOutboxPlan(
            localRecordKey = localRecordKey,
            recordUuid = resolvedUuid,
            tombstone = tombstone,
            updatedFileMap = updatedMap,
            queueItem = queueItem
        )
    }
}
