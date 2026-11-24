package com.example.util

import android.database.sqlite.SQLiteException
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 5-State Standardized Local Storage & Google Drive Sync Status Indicator.
 * Local SQLite is always primary; cloud sync reflects real queue/mirror progress.
 */
enum class StorageSyncStatus(val label: String) {
    SAVED_LOCAL("Tersimpan di perangkat"),
    PENDING_SYNC("Menunggu sinkronisasi"),
    SYNCING("Sedang disinkronkan"),
    SYNCED("Berhasil tersinkronisasi"),
    SYNC_FAILED("Gagal disinkronisasi");

    companion object {
        fun fromEntitySyncStatus(
            rawSyncStatus: String?,
            isCurrentlySyncing: Boolean = false,
            isOffline: Boolean = false
        ): StorageSyncStatus {
            if (isCurrentlySyncing) return SYNCING
            val normalized = rawSyncStatus?.trim()?.uppercase().orEmpty()
            return when {
                normalized == "SYNCED" || normalized == "UPLOADED" -> SYNCED
                normalized == "FAILED" || normalized == "SYNC_FAILED" || normalized == "ERROR" -> SYNC_FAILED
                normalized.startsWith("PENDING") || normalized == "UPLOADING" || normalized == "QUEUED" -> {
                    if (isOffline) SAVED_LOCAL else PENDING_SYNC
                }
                normalized.isBlank() -> SAVED_LOCAL
                else -> PENDING_SYNC
            }
        }
    }
}

/**
 * Structured result of form validation so every form can:
 * 1. Show a clear Indonesian banner: "DATA BELUM LENGKAP! Silakan lengkapi ..."
 * 2. Highlight every missing/invalid field inline (via fieldErrors[key])
 * 3. Scroll/focus directly to firstErrorFieldKey without clearing already-filled inputs.
 */
data class FormValidationResult(
    val isValid: Boolean,
    val missingFieldLabels: List<String> = emptyList(),
    val fieldErrors: Map<String, String> = emptyMap(),
    val firstErrorFieldKey: String? = null,
    val bannerMessage: String = ""
) {
    companion object {
        fun valid(): FormValidationResult = FormValidationResult(isValid = true)

        fun invalid(errors: LinkedHashMap<String, Pair<String, String>>): FormValidationResult {
            val labels = errors.values.map { it.first }
            val fieldMap = errors.mapValues { it.value.second }
            val firstKey = errors.keys.firstOrNull()
            val joinedLabels = when (labels.size) {
                0 -> "kolom wajib"
                1 -> labels.first()
                2 -> "${labels[0]} dan ${labels[1]}"
                else -> "${labels.dropLast(1).joinToString(", ")}, dan ${labels.last()}"
            }
            return FormValidationResult(
                isValid = false,
                missingFieldLabels = labels,
                fieldErrors = fieldMap,
                firstErrorFieldKey = firstKey,
                bannerMessage = "DATA BELUM LENGKAP! Silakan lengkapi $joinedLabels sebelum menyimpan."
            )
        }
    }
}

/**
 * Centralized validation & error classification engine for ALL forms in SEWAin Aja Freezer SOC.
 */
object FormValidationEngine {

    const val TITLE_SUCCESS = "DATA BERHASIL DISIMPAN!"
    const val TITLE_FAILURE = "DATA GAGAL DISIMPAN!"
    const val TITLE_INCOMPLETE = "DATA BELUM LENGKAP!"

    // 1. Form Pelanggan
    fun validateCustomerForm(
        namaLengkap: String,
        nik: String,
        nomorHp: String,
        alamatLengkap: String,
        requireOcrVerification: Boolean = false,
        isOcrVerifiedByAdmin: Boolean = true
    ): FormValidationResult {
        val errors = linkedMapOf<String, Pair<String, String>>()
        if (namaLengkap.trim().isEmpty()) {
            errors["namaLengkap"] = "Nama Lengkap Pelanggan" to "Nama lengkap wajib diisi."
        }
        val cleanNik = nik.filter { it.isDigit() }
        if (cleanNik.isEmpty()) {
            errors["nik"] = "NIK (16 Digit)" to "NIK KTP wajib diisi (16 digit angka)."
        } else if (cleanNik.length != 16) {
            errors["nik"] = "NIK (16 Digit)" to "NIK harus tepat 16 digit angka (saat ini ${cleanNik.length} digit)."
        }
        val cleanHp = nomorHp.filter { it.isDigit() }
        if (cleanHp.length < 8) {
            errors["nomorHp"] = "Nomor HP / WhatsApp" to "Nomor HP/WhatsApp wajib diisi (minimal 8 digit)."
        }
        if (alamatLengkap.trim().isEmpty()) {
            errors["alamatLengkap"] = "Alamat Lengkap" to "Alamat lengkap pelanggan wajib diisi."
        }
        if (requireOcrVerification && !isOcrVerifiedByAdmin) {
            errors["ocrVerification"] = "Konfirmasi Verifikasi Admin OCR" to "Centang verifikasi hasil OCR sebelum menyimpan."
        }
        return if (errors.isEmpty()) FormValidationResult.valid() else FormValidationResult.invalid(errors)
    }

    // 2. Form Unit Freezer
    fun validateUnitForm(
        namaUnit: String,
        merek: String,
        model: String,
        nomorSeri: String,
        kapasitasLiter: Int,
        hargaPembelian: Long
    ): FormValidationResult {
        val errors = linkedMapOf<String, Pair<String, String>>()
        if (namaUnit.trim().isEmpty()) {
            errors["namaUnit"] = "Nama Unit Freezer" to "Nama unit freezer wajib diisi."
        }
        if (merek.trim().isEmpty()) {
            errors["merek"] = "Merek Freezer" to "Merek freezer wajib diisi."
        }
        if (model.trim().isEmpty()) {
            errors["model"] = "Model / Tipe Freezer" to "Model atau tipe unit wajib diisi."
        }
        if (nomorSeri.trim().isEmpty()) {
            errors["nomorSeri"] = "Nomor Seri Freezer" to "Nomor seri freezer wajib diisi."
        }
        if (kapasitasLiter <= 0) {
            errors["kapasitasLiter"] = "Kapasitas (Liter)" to "Kapasitas freezer harus lebih dari 0 liter."
        }
        if (hargaPembelian < 0L) {
            errors["hargaPembelian"] = "Harga Pembelian" to "Harga pembelian tidak boleh negatif."
        }
        return if (errors.isEmpty()) FormValidationResult.valid() else FormValidationResult.invalid(errors)
    }

    // 3. Form Investasi Unit / Update Cicilan
    fun validateInvestmentForm(
        unitName: String,
        hargaPembelian: Long,
        cicilanPerBulan: Long,
        jumlahCicilanTotal: Int,
        cicilanSudahDibayar: Int
    ): FormValidationResult {
        val errors = linkedMapOf<String, Pair<String, String>>()
        if (unitName.trim().isEmpty()) {
            errors["unitName"] = "Unit Freezer" to "Pilih unit freezer terlebih dahulu."
        }
        if (hargaPembelian < 0L) {
            errors["hargaPembelian"] = "Harga Pembelian" to "Harga pembelian tidak boleh negatif."
        }
        if (cicilanPerBulan < 0L) {
            errors["cicilanPerBulan"] = "Cicilan Per Bulan" to "Nominal cicilan per bulan tidak boleh negatif."
        }
        if (jumlahCicilanTotal < 0) {
            errors["jumlahCicilanTotal"] = "Total Tenor Cicilan" to "Total tenor cicilan tidak valid."
        }
        if (cicilanSudahDibayar < 0) {
            errors["cicilanSudahDibayar"] = "Jumlah Cicilan Dibayar" to "Jumlah cicilan dibayar tidak boleh negatif."
        } else if (jumlahCicilanTotal > 0 && cicilanSudahDibayar > jumlahCicilanTotal) {
            errors["cicilanSudahDibayar"] = "Jumlah Cicilan Dibayar" to "Cicilan dibayar ($cicilanSudahDibayar) tidak boleh melebihi total tenor ($jumlahCicilanTotal)."
        }
        return if (errors.isEmpty()) FormValidationResult.valid() else FormValidationResult.invalid(errors)
    }

    // 4 & 5. Form Penyewaan & Transaksi Baru
    fun validateTransactionForm(
        customerId: Long,
        selectedUnitCount: Int,
        tanggalMulaiSewa: String,
        totalTagihan: Long,
        alamatLokasiGps: String,
        requireLocationDocumentation: Boolean = false,
        hasLocationDocumentation: Boolean = true,
        duplicateUnitIds: Boolean = false
    ): FormValidationResult {
        val errors = linkedMapOf<String, Pair<String, String>>()
        if (customerId <= 0L) {
            errors["customerId"] = "Data Pelanggan" to "Pilih atau tambahkan pelanggan terlebih dahulu."
        }
        if (selectedUnitCount <= 0) {
            errors["units"] = "Unit Freezer" to "Pilih minimal 1 unit freezer yang tersedia."
        } else if (duplicateUnitIds) {
            errors["units"] = "Unit Freezer (Duplikat)" to "Unit yang sama tidak boleh dipilih dua kali dalam 1 transaksi."
        }
        if (tanggalMulaiSewa.trim().isEmpty()) {
            errors["tanggalMulaiSewa"] = "Tanggal Mulai Sewa" to "Tanggal mulai sewa wajib diisi."
        }
        if (totalTagihan <= 0L) {
            errors["totalTagihan"] = "Biaya / Harga Sewa" to "Total tagihan sewa harus lebih dari Rp 0."
        }
        if (alamatLokasiGps.trim().isEmpty()) {
            errors["alamatLokasiGps"] = "Alamat Lokasi Penggunaan" to "Alamat lokasi penggunaan freezer wajib diisi."
        }
        if (requireLocationDocumentation && !hasLocationDocumentation) {
            errors["dokumentasiLokasi"] = "Dokumentasi Lokasi" to "Unggah minimal 1 foto dokumentasi freezer atau tampak depan lokasi."
        }
        return if (errors.isEmpty()) FormValidationResult.valid() else FormValidationResult.invalid(errors)
    }

    // 6. Form Perpanjangan Sewa (Detail Multi-Unit & Cepat)
    fun validateRenewalForm(
        customerId: Long,
        renewedUnitCount: Int,
        addedNewUnitCount: Int,
        metodePembayaran: String,
        anyInvalidDuration: Boolean = false,
        anyNegativeOrZeroPrice: Boolean = false
    ): FormValidationResult {
        val errors = linkedMapOf<String, Pair<String, String>>()
        if (customerId <= 0L) {
            errors["customerId"] = "Pelanggan" to "Pilih pelanggan yang akan memperpanjang sewa."
        }
        if (renewedUnitCount <= 0 && addedNewUnitCount <= 0) {
            errors["selectedUnits"] = "Unit yang Diperpanjang / Unit Baru" to "Centang minimal 1 unit aktif atau tambahkan unit sewa baru."
        }
        if (anyInvalidDuration) {
            errors["durasi"] = "Durasi Perpanjangan" to "Durasi perpanjangan harus minimal 1 hari."
        }
        if (anyNegativeOrZeroPrice) {
            errors["hargaSewa"] = "Tarif Sewa Unit" to "Harga sewa per unit harus lebih dari Rp 0."
        }
        if (metodePembayaran.trim().isEmpty()) {
            errors["metodePembayaran"] = "Metode Pembayaran" to "Pilih metode pembayaran terlebih dahulu."
        }
        return if (errors.isEmpty()) FormValidationResult.valid() else FormValidationResult.invalid(errors)
    }

    // 7. Form Pembayaran / Bukti Pembayaran
    fun validatePaymentProofForm(
        transactionId: Long,
        tanggalPembayaran: String,
        nominalPembayaran: Long,
        metodePembayaran: String,
        requireProofAttachment: Boolean = false,
        fotoBuktiUri: String = ""
    ): FormValidationResult {
        val errors = linkedMapOf<String, Pair<String, String>>()
        if (transactionId <= 0L) {
            errors["transactionId"] = "Transaksi Terkait" to "Transaksi belum tersimpan atau tidak valid."
        }
        if (tanggalPembayaran.trim().isEmpty()) {
            errors["tanggalPembayaran"] = "Tanggal Pembayaran" to "Tanggal pembayaran wajib diisi (YYYY-MM-DD)."
        }
        if (nominalPembayaran <= 0L) {
            errors["nominalPembayaran"] = "Nominal Pembayaran" to "Nominal pembayaran harus lebih dari Rp 0."
        }
        if (metodePembayaran.trim().isEmpty()) {
            errors["metodePembayaran"] = "Metode Pembayaran" to "Metode pembayaran wajib dipilih."
        }
        if (requireProofAttachment && fotoBuktiUri.trim().isEmpty()) {
            errors["fotoBuktiUri"] = "Lampiran Bukti Pembayaran" to "Lampirkan foto struk/screenshot/QRIS bukti pembayaran."
        }
        return if (errors.isEmpty()) FormValidationResult.valid() else FormValidationResult.invalid(errors)
    }

    // 8. Form Pengembalian Unit
    fun validateReturnForm(
        transactionId: Long,
        tanggalPengembalian: String,
        namaPenerimaPetugas: String,
        namaPenyerahPelanggan: String,
        kondisiUnit: String
    ): FormValidationResult {
        val errors = linkedMapOf<String, Pair<String, String>>()
        if (transactionId <= 0L) {
            errors["transactionId"] = "Transaksi / Unit Sewa" to "Pilih transaksi sewa yang akan dikembalikan."
        }
        if (tanggalPengembalian.trim().isEmpty()) {
            errors["tanggalPengembalian"] = "Tanggal Pengembalian" to "Tanggal pengembalian unit wajib diisi."
        }
        if (namaPenyerahPelanggan.trim().isEmpty()) {
            errors["namaPenyerah"] = "Nama Penyerah (Pelanggan)" to "Nama pihak pelanggan yang menyerahkan unit wajib diisi."
        }
        if (namaPenerimaPetugas.trim().isEmpty()) {
            errors["namaPetugas"] = "Nama Petugas Penerima" to "Nama petugas penerima unit wajib diisi."
        }
        if (kondisiUnit.trim().isEmpty()) {
            errors["kondisiUnit"] = "Kondisi Fisik Unit" to "Pilih atau isi kondisi unit saat pengembalian."
        }
        return if (errors.isEmpty()) FormValidationResult.valid() else FormValidationResult.invalid(errors)
    }

    // 9. Form Perbaikan / Maintenance Unit
    fun validateMaintenanceForm(
        unitId: Long,
        tanggalPerbaikan: String,
        jenisPerbaikan: String,
        biayaPerbaikan: Long
    ): FormValidationResult {
        val errors = linkedMapOf<String, Pair<String, String>>()
        if (unitId <= 0L) {
            errors["unitId"] = "Unit Freezer" to "Unit freezer belum dipilih."
        }
        if (tanggalPerbaikan.trim().isEmpty()) {
            errors["tanggalPerbaikan"] = "Tanggal Perbaikan" to "Tanggal perbaikan wajib diisi."
        }
        if (jenisPerbaikan.trim().isEmpty()) {
            errors["jenisPerbaikan"] = "Jenis / Deskripsi Perbaikan" to "Deskripsi kerusakan atau tindakan perbaikan wajib diisi."
        }
        if (biayaPerbaikan < 0L) {
            errors["biayaPerbaikan"] = "Biaya Perbaikan" to "Biaya perbaikan tidak boleh bernilai negatif."
        }
        return if (errors.isEmpty()) FormValidationResult.valid() else FormValidationResult.invalid(errors)
    }

    // 10. Form Dokumentasi (Foto Pelanggan / Freezer / Lokasi)
    fun validateDocumentationForm(
        customerId: Long,
        category: String,
        documentationName: String,
        hasPhotoSelected: Boolean
    ): FormValidationResult {
        val errors = linkedMapOf<String, Pair<String, String>>()
        if (customerId <= 0L) {
            errors["customerId"] = "Data Pelanggan" to "Pilih pelanggan terlebih dahulu."
        }
        if (category.trim().isEmpty()) {
            errors["category"] = "Kategori Dokumentasi" to "Pilih kategori dokumentasi."
        }
        if (documentationName.trim().isEmpty()) {
            errors["documentationName"] = "Nama Dokumentasi" to "Judul/nama dokumentasi wajib diisi."
        }
        if (!hasPhotoSelected) {
            errors["photo"] = "Foto Dokumentasi" to "Ambil foto dari kamera atau pilih gambar dari galeri."
        }
        return if (errors.isEmpty()) FormValidationResult.valid() else FormValidationResult.invalid(errors)
    }

    // 11. Form Lokasi Penggunaan GPS
    fun validateUsageLocationForm(
        customerId: Long,
        alamatLengkap: String,
        latitude: Double?,
        longitude: Double?
    ): FormValidationResult {
        val errors = linkedMapOf<String, Pair<String, String>>()
        if (customerId <= 0L) {
            errors["customerId"] = "Data Pelanggan" to "Pelanggan belum dipilih."
        }
        if (alamatLengkap.trim().isEmpty()) {
            errors["alamatLengkap"] = "Alamat Lokasi Penggunaan" to "Alamat lengkap lokasi freezer wajib diisi."
        }
        if (latitude == null || longitude == null || (latitude == 0.0 && longitude == 0.0)) {
            errors["koordinatGps"] = "Koordinat GPS Lokasi" to "Deteksi atau tentukan koordinat GPS lokasi terlebih dahulu."
        }
        return if (errors.isEmpty()) FormValidationResult.valid() else FormValidationResult.invalid(errors)
    }

    // 12. Form Metode Pembayaran (Setting)
    fun validatePaymentMethodForm(
        namaMetode: String,
        kategoriMetode: String,
        nomorRekening: String,
        namaPemilikRekening: String
    ): FormValidationResult {
        val errors = linkedMapOf<String, Pair<String, String>>()
        if (namaMetode.trim().isEmpty()) {
            errors["namaMetode"] = "Nama Metode Pembayaran" to "Nama metode pembayaran wajib diisi."
        }
        if (kategoriMetode.equals("TRANSFER_BANK", ignoreCase = true)) {
            if (nomorRekening.trim().isEmpty()) {
                errors["nomorRekening"] = "Nomor Rekening" to "Nomor rekening bank wajib diisi untuk Transfer Bank."
            }
            if (namaPemilikRekening.trim().isEmpty()) {
                errors["namaPemilikRekening"] = "Nama Pemilik Rekening" to "Nama pemilik rekening wajib diisi."
            }
        }
        return if (errors.isEmpty()) FormValidationResult.valid() else FormValidationResult.invalid(errors)
    }

    /**
     * Translates low-level technical exceptions into clear Indonesian root-cause descriptions
     * so users always know WHY "DATA GAGAL DISIMPAN!" happened.
     */
    fun classifySaveFailureReason(throwable: Throwable?): String {
        if (throwable == null) return "Terjadi kesalahan penyimpanan yang tidak diketahui."
        val msg = (throwable.message ?: throwable.localizedMessage ?: "").trim()
        val lower = msg.lowercase()
        return when {
            throwable is SQLiteException || lower.contains("sqlite") || lower.contains("database") ->
                "Database lokal mengalami error${if (msg.isNotBlank()) " ($msg)" else ""}."
            lower.contains("enospc") || lower.contains("no space") || lower.contains("storage") || lower.contains("read-only") ->
                "Penyimpanan perangkat tidak tersedia atau penuh."
            throwable is UnknownHostException || lower.contains("unable to resolve host") || lower.contains("offline") || lower.contains("no internet") || lower.contains("network") ->
                "Koneksi internet terputus."
            throwable is SocketTimeoutException || lower.contains("timeout") || lower.contains("timed out") ->
                "Server tidak memberikan respons (timeout)."
            lower.contains("drive") || lower.contains("google") || lower.contains("oauth") || lower.contains("401") || lower.contains("403") ->
                "Sinkronisasi Google Drive gagal${if (msg.isNotBlank()) " ($msg)" else ""}."
            lower.contains("foto") || lower.contains("upload") || lower.contains("dokumentasi") || lower.contains("gambar") ->
                "File dokumentasi gagal diunggah${if (msg.isNotBlank()) " ($msg)" else ""}."
            lower.contains("konflik") || lower.contains("conflict") || lower.contains("unique") || lower.contains("tersedia") || lower.contains("sudah disewa") ->
                "Terjadi konflik data${if (msg.isNotBlank()) " ($msg)" else ""}."
            throwable is IOException ->
                "Penyimpanan perangkat atau akses file bermasalah${if (msg.isNotBlank()) " ($msg)" else ""}."
            msg.isNotBlank() -> msg
            else -> "Terjadi kesalahan sistem saat menyimpan data."
        }
    }

    /**
     * Checks whether local storage directory is writable before persisting files/photos.
     */
    fun isDeviceStorageWritable(dir: File): Boolean {
        return try {
            if (!dir.exists()) dir.mkdirs()
            dir.exists() && dir.isDirectory && dir.canWrite()
        } catch (_: Exception) {
            false
        }
    }
}
