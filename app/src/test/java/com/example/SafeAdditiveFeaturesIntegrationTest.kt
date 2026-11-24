package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.CustomerEntity
import com.example.data.local.FreezerDao
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.RentalTransactionEntity
import com.example.data.local.TransactionSignatureEntity
import com.example.data.local.TransactionUnitItemEntity
import com.example.data.repository.FreezerRepository
import com.example.drive.MirrorOutboxCodec
import com.example.ui.components.FormDraftCodec
import com.example.ui.components.FormDraftTypes
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SafeAdditiveFeaturesIntegrationTest {

    private lateinit var context: Context
    private lateinit var dbName: String
    private lateinit var database: AppDatabase
    private lateinit var dao: FreezerDao
    private lateinit var repository: FreezerRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbName = "safe_additive_v23_test_${System.nanoTime()}.db"
        openDb()
    }

    private fun openDb() {
        database = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_21_22, AppDatabase.MIGRATION_22_23, AppDatabase.MIGRATION_23_24, AppDatabase.MIGRATION_24_25)
            .allowMainThreadQueries()
            .build()
        dao = database.freezerDao()
        repository = FreezerRepository(dao)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
        context.deleteDatabase(dbName)
    }

    // =========================================================================
    // TEST 1: AUTO-DRAFT ISOLATION, PERSISTENCE ACROSS RESTART & CLEANUP
    // =========================================================================
    @Test
    fun test1_autoDraftPersistsAcrossRestartAndNeverPollutesActiveTablesOrSyncQueue(): Unit = runBlocking {
        val unitPayloadV1 = FormDraftCodec.encodeMap(
            mapOf("kodeUnit" to "FRZ-DRAFT-01", "namaUnit" to "Freezer Draft 250L")
        )
        val savedUnitDraft = repository.saveFormDraft(
            formType = FormDraftTypes.UNIT,
            targetEntityId = 0L,
            ownerAccount = "officer.a@saf.co.id",
            title = "Unit: Freezer Draft 250L",
            subtitle = "FRZ-DRAFT-01",
            payloadJson = unitPayloadV1
        )
        assertTrue(MirrorOutboxCodec.isValidUuidV4(savedUnitDraft.uuid))
        assertEquals(1, savedUnitDraft.version)

        // Update same draft -> keeps UUID, increments version
        val unitPayloadV2 = FormDraftCodec.encodeMap(
            mapOf("kodeUnit" to "FRZ-DRAFT-01", "namaUnit" to "Freezer Draft 250L Updated")
        )
        val updatedUnitDraft = repository.saveFormDraft(
            formType = FormDraftTypes.UNIT,
            targetEntityId = 0L,
            ownerAccount = "officer.a@saf.co.id",
            title = "Unit: Freezer Draft 250L Updated",
            subtitle = "FRZ-DRAFT-01",
            payloadJson = unitPayloadV2,
            existingUuid = savedUnitDraft.uuid
        )
        assertEquals(savedUnitDraft.uuid, updatedUnitDraft.uuid)
        assertEquals(2, updatedUnitDraft.version)

        // Save customer, transaction, and return drafts as well
        repository.saveFormDraft(
            formType = FormDraftTypes.CUSTOMER,
            targetEntityId = 0L,
            ownerAccount = "officer.a@saf.co.id",
            title = "Pelanggan: Draft Customer",
            payloadJson = """{"namaLengkap":"BUDI DRAFT"}"""
        )
        repository.saveFormDraft(
            formType = FormDraftTypes.TRANSACTION,
            targetEntityId = 0L,
            ownerAccount = "officer.a@saf.co.id",
            title = "Transaksi: Draft Tx",
            payloadJson = """{"durasiHari":"30"}"""
        )
        repository.saveFormDraft(
            formType = FormDraftTypes.RETURN,
            targetEntityId = 0L,
            ownerAccount = "officer.a@saf.co.id",
            title = "Pengembalian: Draft Return",
            payloadJson = """{"catatan":"Cek karet pintu"}"""
        )

        // Drafts MUST NEVER enter active operational tables or Drive sync queue
        assertEquals(0, dao.getAllUnitsOnce().size)
        assertEquals(0, dao.getAllCustomersOnce().size)
        assertEquals(0, dao.getAllTransactionsOnce().size)
        assertEquals(0, dao.getAllReturnsOnce().size)
        assertEquals(0, dao.getAllDriveLocalSyncQueueOnce().size)

        // Simulate app restart (close & reopen persistent SQLite DB)
        database.close()
        openDb()

        val allDraftsAfterRestart = repository.getAllFormDraftsOnce()
        assertEquals(4, allDraftsAfterRestart.size)
        val restoredUnitDraft = repository.getFormDraftByUuid(savedUnitDraft.uuid)
        assertNotNull(restoredUnitDraft)
        assertEquals(2, restoredUnitDraft!!.version)
        assertTrue(restoredUnitDraft.payloadJson.contains("Freezer Draft 250L Updated"))

        // Explicit final save + delete draft -> final record enters active table & sync queue; draft removed
        repository.saveUnit(
            FreezerUnitEntity(
                kodeUnit = "FRZ-DRAFT-01",
                namaUnit = "Freezer Draft 250L Updated",
                jenisUnit = "Chest Freezer",
                owner = "SOC",
                hargaPembelian = 4_000_000L,
                kapasitasLiter = 250,
                cicilanPerBulan = 0L,
                merek = "Gea",
                model = "AB-250",
                nomorSeri = "SN-DRAFT-01",
                statusUnit = "Tersedia",
                lokasiUnit = "Solo",
                tanggalPembelian = "2026-11-01"
            )
        )
        repository.deleteFormDraftByUuid(savedUnitDraft.uuid)

        assertNull(repository.getFormDraftByUuid(savedUnitDraft.uuid))
        assertEquals(3, repository.getAllFormDraftsOnce().size)
        assertEquals(1, dao.getAllUnitsOnce().size)
        assertEquals(1, dao.getAllDriveLocalSyncQueueOnce().size)
    }

    // =========================================================================
    // TEST 2: MULTI-UNIT INDEPENDENT SEWA HAK MILIK PROGRESS PER FREEZER UNIT
    // =========================================================================
    @Test
    fun test2_multiUnitSewaHakMilikProgressIsStrictlyIndependentPerFreezerUnit(): Unit = runBlocking {
        val custId = repository.saveCustomer(
            CustomerEntity(
                namaLengkap = "TOKO FROZEN MAKMUR",
                nik = "3372010101900001",
                nomorHp = "081234567890",
                alamatLengkap = "Jl. Slamet Riyadi No. 10 Solo",
                jenisKelamin = "Laki-laki",
                tanggalRegistrasi = "2026-11-01"
            )
        )
        val unitAId = repository.saveUnit(makeUnit("FRZ-HM-A", "Freezer A 300L"))
        val unitBId = repository.saveUnit(makeUnit("FRZ-HM-B", "Freezer B 400L"))
        val unitCId = repository.saveUnit(makeUnit("FRZ-HM-C", "Freezer C 500L"))

        val parentTx = RentalTransactionEntity(
            nomorInvoice = "INV/SAF/HM-MULTI-01",
            tanggalPemesanan = "2026-11-01",
            customerId = custId,
            namaPelangganSnapshot = "TOKO FROZEN MAKMUR",
            unitId = unitAId,
            namaUnitSnapshot = "Freezer A 300L",
            kodeUnitSnapshot = "FRZ-HM-A",
            jenisProgram = "Sewa Hak Milik",
            totalPeriodeHakMilik = 10,
            pembayaranKeHakMilik = 2,
            statusProgramHakMilik = "Aktif",
            hargaSewa = 1_500_000L,
            durasiHari = 30,
            tanggalMulaiSewa = "2026-11-01",
            tanggalAkhirSewa = "2026-12-01",
            tanggalJatuhTempo = "2026-12-01",
            nominalPembayaran = 0L,
            totalTagihan = 1_500_000L,
            statusPembayaran = "Belum Bayar",
            statusPenyewaan = "Dalam Penyewaan",
            metodePembayaran = "Transfer BCA",
            namaPetugas = "ANDI PRATAMA"
        )

        val itemA = TransactionUnitItemEntity(
            transactionId = 0L,
            customerId = custId,
            unitId = unitAId,
            unitIndexNumber = 1,
            kodeUnitSnapshot = "FRZ-HM-A",
            namaUnitSnapshot = "Freezer A 300L",
            jenisProgram = "Sewa Hak Milik",
            totalPeriodeHakMilik = 10,
            pembayaranKeHakMilik = 2,
            statusProgramHakMilik = "Aktif",
            hargaSewa = 450_000L,
            durasiHari = 30,
            tanggalMulaiSewa = "2026-11-01",
            tanggalAkhirSewa = "2026-12-01",
            tanggalJatuhTempo = "2026-12-01",
            nominalPembayaran = 0L,
            statusPembayaran = "Belum Bayar"
        )
        val itemB = TransactionUnitItemEntity(
            transactionId = 0L,
            customerId = custId,
            unitId = unitBId,
            unitIndexNumber = 2,
            kodeUnitSnapshot = "FRZ-HM-B",
            namaUnitSnapshot = "Freezer B 400L",
            jenisProgram = "Sewa Hak Milik",
            totalPeriodeHakMilik = 12,
            pembayaranKeHakMilik = 5,
            statusProgramHakMilik = "Aktif",
            hargaSewa = 550_000L,
            durasiHari = 30,
            tanggalMulaiSewa = "2026-11-01",
            tanggalAkhirSewa = "2026-12-01",
            tanggalJatuhTempo = "2026-12-05",
            nominalPembayaran = 0L,
            statusPembayaran = "Belum Bayar"
        )
        val itemC = TransactionUnitItemEntity(
            transactionId = 0L,
            customerId = custId,
            unitId = unitCId,
            unitIndexNumber = 3,
            kodeUnitSnapshot = "FRZ-HM-C",
            namaUnitSnapshot = "Freezer C 500L",
            jenisProgram = "Sewa Hak Milik",
            totalPeriodeHakMilik = 8,
            pembayaranKeHakMilik = 1,
            statusProgramHakMilik = "Aktif",
            hargaSewa = 500_000L,
            durasiHari = 30,
            tanggalMulaiSewa = "2026-11-01",
            tanggalAkhirSewa = "2026-12-01",
            tanggalJatuhTempo = "2026-12-10",
            nominalPembayaran = 0L,
            statusPembayaran = "Belum Bayar"
        )

        val savedTx = repository.saveMultiUnitTransaction(parentTx, listOf(itemA, itemB, itemC))
        val txId = savedTx.id
        val savedItems = dao.getTransactionUnitItemsByTxOnce(txId)
        assertEquals(3, savedItems.size)
        val savedA = savedItems.first { it.unitId == unitAId }
        val savedB = savedItems.first { it.unitId == unitBId }
        val savedC = savedItems.first { it.unitId == unitCId }

        assertEquals(2, savedA.pembayaranKeHakMilik)
        assertEquals(10, savedA.totalPeriodeHakMilik)
        assertEquals(5, savedB.pembayaranKeHakMilik)
        assertEquals(12, savedB.totalPeriodeHakMilik)
        assertEquals(1, savedC.pembayaranKeHakMilik)
        assertEquals(8, savedC.totalPeriodeHakMilik)

        // Pay installment ONLY for Freezer A
        val updatedA = repository.recordUnitHakMilikPayment(
            transactionId = txId,
            unitItemId = savedA.id,
            nominalPembayaran = 450_000L,
            metodePembayaran = "Transfer BCA",
            tanggalPembayaran = "2026-11-15",
            namaPengirim = "TOKO FROZEN MAKMUR",
            catatanPembayaran = "Cicilan ke-3 Freezer A",
            incrementPaymentSequence = true
        )

        assertEquals(3, updatedA.pembayaranKeHakMilik)
        assertEquals(10, updatedA.totalPeriodeHakMilik)
        assertEquals(7, updatedA.hakMilikRemainingCount)
        assertEquals(450_000L, updatedA.nominalPembayaran)

        // Verify Freezer B and Freezer C were NOT touched at all!
        val afterItems = dao.getTransactionUnitItemsByTxOnce(txId)
        val afterB = afterItems.first { it.unitId == unitBId }
        val afterC = afterItems.first { it.unitId == unitCId }
        assertEquals(5, afterB.pembayaranKeHakMilik)
        assertEquals(12, afterB.totalPeriodeHakMilik)
        assertEquals(0L, afterB.nominalPembayaran)
        assertEquals(1, afterC.pembayaranKeHakMilik)
        assertEquals(8, afterC.totalPeriodeHakMilik)
        assertEquals(0L, afterC.nominalPembayaran)

        // Verify payment proof & allocation tied strictly to Freezer A
        val allocations = dao.getUnitPaymentAllocationsByTxOnce(txId)
        assertEquals(1, allocations.size)
        assertEquals(savedA.id, allocations.first().unitItemId)
        assertEquals(unitAId, allocations.first().unitId)
        assertEquals(450_000L, allocations.first().allocatedAmount)

        // Parent transaction total paid reflects Freezer A's payment
        val refreshedParent = dao.getTransactionById(txId)!!
        assertEquals(450_000L, refreshedParent.nominalPembayaran)
        assertEquals("Dibayar Sebagian", refreshedParent.statusPembayaran)
    }

    // =========================================================================
    // TEST 3: PER-UNIT RENTAL PRICING (3/7/14/30/60/90 HARI) & SNAPSHOT IMMUTABILITY
    // =========================================================================
    @Test
    fun test3_perUnitRentalPricingIsIsolatedAndTransactionSnapshotsAreImmutable(): Unit = runBlocking {
        val unitAId = repository.saveUnitWithRentalPrices(
            unit = makeUnit("FRZ-PRC-A", "Freezer 200L"),
            pricesByPeriod = mapOf(3 to 90_000L, 7 to 175_000L, 30 to 350_000L)
        )
        val unitBId = repository.saveUnitWithRentalPrices(
            unit = makeUnit("FRZ-PRC-B", "Freezer 500L"),
            pricesByPeriod = mapOf(7 to 240_000L, 30 to 480_000L, 90 to 1_300_000L)
        )

        // Unit A only exposes 3, 7, 30 days
        val unitAPrices = repository.getUnitRentalPricesByUnitOnce(unitAId)
        assertEquals(listOf(3, 7, 30), unitAPrices.map { it.periodDays })
        assertEquals(350_000L, repository.resolveRentalPriceForUnitAndPeriod(unitAId, 30))
        assertNull(repository.resolveRentalPriceForUnitAndPeriod(unitAId, 90))

        // Unit B only exposes 7, 30, 90 days (never leaks Unit A's prices)
        val unitBPrices = repository.getUnitRentalPricesByUnitOnce(unitBId)
        assertEquals(listOf(7, 30, 90), unitBPrices.map { it.periodDays })
        assertEquals(480_000L, repository.resolveRentalPriceForUnitAndPeriod(unitBId, 30))
        assertNull(repository.resolveRentalPriceForUnitAndPeriod(unitBId, 3))

        val custId = repository.saveCustomer(
            CustomerEntity(
                namaLengkap = "KEDAI ES KRIM SOLO",
                nik = "3372010202900002",
                nomorHp = "081399998888",
                alamatLengkap = "Jl. Adi Sucipto Solo",
                jenisKelamin = "Perempuan",
                tanggalRegistrasi = "2026-11-01"
            )
        )

        // Create Sewa Reguler transaction using Unit A's 30-day configured price (350_000L)
        val snapPriceA = repository.resolveRentalPriceForUnitAndPeriod(unitAId, 30)!!
        val savedTx = repository.saveMultiUnitTransaction(
            parentTransaction = RentalTransactionEntity(
                nomorInvoice = "INV/SAF/REG-SNAP-01",
                tanggalPemesanan = "2026-11-01",
                customerId = custId,
                namaPelangganSnapshot = "KEDAI ES KRIM SOLO",
                unitId = unitAId,
                namaUnitSnapshot = "Freezer 200L",
                kodeUnitSnapshot = "FRZ-PRC-A",
                jenisProgram = "Sewa Reguler",
                hargaSewa = snapPriceA,
                durasiHari = 30,
                tanggalMulaiSewa = "2026-11-01",
                tanggalAkhirSewa = "2026-12-01",
                tanggalJatuhTempo = "2026-12-01",
                nominalPembayaran = snapPriceA,
                totalTagihan = snapPriceA,
                statusPembayaran = "Lunas",
                statusPenyewaan = "Dalam Penyewaan",
                metodePembayaran = "Transfer BCA",
                namaPetugas = "ANDI PRATAMA"
            ),
            unitItems = listOf(
                TransactionUnitItemEntity(
                    transactionId = 0L,
                    customerId = custId,
                    unitId = unitAId,
                    unitIndexNumber = 1,
                    kodeUnitSnapshot = "FRZ-PRC-A",
                    namaUnitSnapshot = "Freezer 200L",
                    jenisProgram = "Sewa Reguler",
                    hargaSewa = snapPriceA,
                    durasiHari = 30,
                    tanggalMulaiSewa = "2026-11-01",
                    tanggalAkhirSewa = "2026-12-01",
                    tanggalJatuhTempo = "2026-12-01",
                    nominalPembayaran = snapPriceA,
                    statusPembayaran = "Lunas"
                )
            )
        )
        val txId = savedTx.id

        // Now edit Unit A's 30-day price to 525_000L
        val unitAEntity = dao.getUnitById(unitAId)!!
        repository.saveUnitWithRentalPrices(
            unit = unitAEntity,
            pricesByPeriod = mapOf(3 to 110_000L, 7 to 200_000L, 30 to 525_000L)
        )

        // New lookups get updated price 525_000L
        assertEquals(525_000L, repository.resolveRentalPriceForUnitAndPeriod(unitAId, 30))

        // Existing saved transaction & unit item snapshot MUST remain 350_000L!
        val existingTx = dao.getTransactionById(txId)!!
        val existingItem = dao.getTransactionUnitItemsByTxOnce(txId).first()
        assertEquals(350_000L, existingTx.hargaSewa)
        assertEquals(350_000L, existingTx.totalTagihan)
        assertEquals(350_000L, existingItem.hargaSewa)
    }

    // =========================================================================
    // TEST 4: SIMPLIFIED PHOTO FORMS & NON-DESTRUCTIVE LEGACY DATA PRESERVATION
    // =========================================================================
    @Test
    fun test4_simplifiedPhotoFormsAndRemovedCicilanUiPreserveLegacyDatabaseValues(): Unit = runBlocking {
        // Legacy Unit with installment columns & old secondary photos populated
        val legacyUnitId = repository.saveUnit(
            makeUnit("FRZ-LEG-01", "Freezer Legacy").copy(
                cicilanPerBulan = 325_000L,
                jumlahCicilanTotal = 12,
                totalPembayaranCicilan = 975_000L,
                fotoKondisiFisikUri = "/photos/fisik_v1.jpg",
                fotoBagianDalamUri = "/photos/legacy_dalam.jpg",
                fotoMerekSeriUri = "/photos/legacy_sn.jpg"
            )
        )
        val loadedUnit = dao.getUnitById(legacyUnitId)!!

        // Simulate saving from simplified Unit Form (updates only fotoKondisiFisikUri + prices; preserves legacy cicilan & photos)
        repository.saveUnitWithRentalPrices(
            unit = loadedUnit.copy(fotoKondisiFisikUri = "/photos/fisik_v2.jpg"),
            pricesByPeriod = mapOf(30 to 360_000L)
        )
        val afterUnit = dao.getUnitById(legacyUnitId)!!
        assertEquals("/photos/fisik_v2.jpg", afterUnit.fotoKondisiFisikUri)
        assertEquals("/photos/legacy_dalam.jpg", afterUnit.fotoBagianDalamUri)
        assertEquals("/photos/legacy_sn.jpg", afterUnit.fotoMerekSeriUri)
        assertEquals(325_000L, afterUnit.cicilanPerBulan)
        assertEquals(12, afterUnit.jumlahCicilanTotal)
        assertEquals(975_000L, afterUnit.totalPembayaranCicilan)

        // Legacy Customer with back KTP & profile photo preserved when editing front KTP only
        val legacyCustId = repository.saveCustomer(
            CustomerEntity(
                namaLengkap = "PELANGGAN LEGACY",
                nik = "3372010303900003",
                nomorHp = "081511112222",
                alamatLengkap = "Solo",
                jenisKelamin = "Laki-laki",
                tanggalRegistrasi = "2026-10-01",
                fotoKtpDepanUri = "/photos/ktp_front_v1.jpg",
                fotoKtpBelakangUri = "/photos/ktp_back_legacy.jpg",
                fotoPelangganUri = "/photos/profile_legacy.jpg"
            )
        )
        val loadedCust = dao.getCustomerById(legacyCustId)!!
        repository.saveCustomer(loadedCust.copy(fotoKtpDepanUri = "/photos/ktp_front_v2.jpg"))
        val afterCust = dao.getCustomerById(legacyCustId)!!
        assertEquals("/photos/ktp_front_v2.jpg", afterCust.fotoKtpDepanUri)
        assertEquals("/photos/ktp_back_legacy.jpg", afterCust.fotoKtpBelakangUri)
        assertEquals("/photos/profile_legacy.jpg", afterCust.fotoPelangganUri)

        // Simplified Transaction Unit documentation completeness strictly checks the 2 main slots
        val incompleteUnitDoc = TransactionUnitItemEntity(
            transactionId = 1L,
            customerId = legacyCustId,
            unitId = legacyUnitId,
            fotoKondisiDepanUri = "/photos/front.jpg",
            fotoSerahTerimaPelangganUri = ""
        )
        assertFalse(incompleteUnitDoc.isDocumentationComplete)

        val completeUnitDoc = incompleteUnitDoc.copy(
            fotoSerahTerimaPelangganUri = "/photos/handover.jpg"
        )
        assertTrue(completeUnitDoc.isDocumentationComplete)
    }

    // =========================================================================
    // TEST 5: PERMANENT OFFICER IDENTITY & SIGNATURE SNAPSHOT + AUDITED CORRECTION
    // =========================================================================
    @Test
    fun test5_officerIdentityAndSignatureSnapshotIsLockedAndRequiresAuditLogToCorrect(): Unit = runBlocking {
        val custId = repository.saveCustomer(
            CustomerEntity(
                namaLengkap = "WARUNG BAKSO SOLO",
                nik = "3372010404900004",
                nomorHp = "081777776666",
                alamatLengkap = "Laweyan Solo",
                jenisKelamin = "Laki-laki",
                tanggalRegistrasi = "2026-11-01"
            )
        )
        val unitId = repository.saveUnit(makeUnit("FRZ-OFF-01", "Freezer Officer Test"))

        // Officer A creates transaction
        val txId = repository.saveTransaction(
            RentalTransactionEntity(
                nomorInvoice = "INV/SAF/OFF-01",
                tanggalPemesanan = "2026-11-01",
                customerId = custId,
                namaPelangganSnapshot = "WARUNG BAKSO SOLO",
                unitId = unitId,
                namaUnitSnapshot = "Freezer Officer Test",
                kodeUnitSnapshot = "FRZ-OFF-01",
                hargaSewa = 350_000L,
                durasiHari = 30,
                tanggalMulaiSewa = "2026-11-01",
                tanggalAkhirSewa = "2026-12-01",
                tanggalJatuhTempo = "2026-12-01",
                nominalPembayaran = 350_000L,
                totalTagihan = 350_000L,
                statusPembayaran = "Lunas",
                statusPenyewaan = "Dalam Penyewaan",
                metodePembayaran = "Transfer BCA",
                namaPetugas = "ANDI PRATAMA",
                officerAccountId = "andi@saf.co.id",
                tandaTanganPetugasUri = "/sigs/tx_andi.png"
            )
        )

        val createdTx = dao.getTransactionById(txId)!!
        assertTrue(createdTx.officerSnapshotLocked)
        assertEquals("ANDI PRATAMA", createdTx.namaPetugas)
        assertEquals("/sigs/tx_andi.png", createdTx.tandaTanganPetugasUri)
        assertEquals("andi@saf.co.id", createdTx.officerAccountId)

        // Officer B opens & updates transaction (e.g., updates catatan) and tries to overwrite officer fields
        repository.saveTransaction(
            createdTx.copy(
                catatan = "Diedit oleh Officer B",
                namaPetugas = "BUDI SANTOSO",
                officerAccountId = "budi@saf.co.id",
                tandaTanganPetugasUri = "/sigs/tx_budi.png"
            )
        )
        val afterOfficerBEdit = dao.getTransactionById(txId)!!
        assertEquals("Diedit oleh Officer B", afterOfficerBEdit.catatan)
        assertEquals("ANDI PRATAMA", afterOfficerBEdit.namaPetugas)
        assertEquals("/sigs/tx_andi.png", afterOfficerBEdit.tandaTanganPetugasUri)
        assertEquals("andi@saf.co.id", afterOfficerBEdit.officerAccountId)

        // Multi-unit edit & signature helper also MUST NOT overwrite locked Officer A snapshot
        repository.saveMultiUnitTransaction(
            parentTransaction = afterOfficerBEdit.copy(
                namaPetugas = "CITRA DEWI",
                tandaTanganPetugasUri = "/sigs/tx_citra.png"
            ),
            unitItems = emptyList()
        )
        repository.saveTransactionSignatureRecord(
            TransactionSignatureEntity(
                transactionId = txId,
                customerId = custId,
                signerRole = "PETUGAS_SAF",
                signerName = "CITRA DEWI",
                filePath = "/sigs/tx_citra.png"
            )
        )
        val afterMultiEdit = dao.getTransactionById(txId)!!
        assertEquals("ANDI PRATAMA", afterMultiEdit.namaPetugas)
        assertEquals("/sigs/tx_andi.png", afterMultiEdit.tandaTanganPetugasUri)

        // Explicit correction WITHOUT reason must be rejected
        var rejectedBlankReason = false
        try {
            repository.correctTransactionOfficerWithAuditLog(
                transactionId = txId,
                newOfficerName = "BUDI SANTOSO",
                newSignatureUri = "/sigs/tx_budi_corrected.png",
                correctedBy = "ADMIN SUPERVISOR",
                correctionReason = "   "
            )
        } catch (e: IllegalArgumentException) {
            rejectedBlankReason = true
        }
        assertTrue(rejectedBlankReason)
        assertEquals(0, repository.getOfficerAuditLogsByTransactionOnce(txId).size)

        // Explicit correction WITH valid reason succeeds and records permanent audit log
        val correctedTx = repository.correctTransactionOfficerWithAuditLog(
            transactionId = txId,
            newOfficerName = "BUDI SANTOSO",
            newSignatureUri = "/sigs/tx_budi_corrected.png",
            correctedBy = "ADMIN SUPERVISOR",
            correctionReason = "Salah input nama petugas serah terima di lapangan"
        )
        assertEquals("BUDI SANTOSO", correctedTx.namaPetugas)
        assertEquals("/sigs/tx_budi_corrected.png", correctedTx.tandaTanganPetugasUri)
        assertTrue(correctedTx.officerSnapshotLocked)

        val auditLogs = repository.getOfficerAuditLogsByTransactionOnce(txId)
        assertEquals(1, auditLogs.size)
        val log = auditLogs.first()
        assertEquals("ANDI PRATAMA", log.previousOfficerName)
        assertEquals("BUDI SANTOSO", log.newOfficerName)
        assertEquals("/sigs/tx_andi.png", log.previousSignatureUri)
        assertEquals("/sigs/tx_budi_corrected.png", log.newSignatureUri)
        assertEquals("ADMIN SUPERVISOR", log.correctedBy)
        assertEquals("Salah input nama petugas serah terima di lapangan", log.correctionReason)
    }

    // =========================================================================
    // TEST 6: NON-DESTRUCTIVE MIGRATION 22 -> 23 PRESERVES EXISTING DATA
    // =========================================================================
    @Test
    fun test6_migration22To23CreatesNewTablesAndPreservesExistingRecordsNonDestructively() {
        val migDbFile = File(context.cacheDir, "mig_22_23_test_${System.nanoTime()}.db")
        val rawSqlDb = SQLiteDatabase.openOrCreateDatabase(migDbFile, null)
        rawSqlDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `freezer_units` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `recordUuid` TEXT NOT NULL,
                `kodeUnit` TEXT NOT NULL,
                `namaUnit` TEXT NOT NULL
            )
            """.trimIndent()
        )
        rawSqlDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `rental_transactions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `recordUuid` TEXT NOT NULL,
                `nomorInvoice` TEXT NOT NULL,
                `namaPetugas` TEXT NOT NULL,
                `tandaTanganPetugasUri` TEXT NOT NULL
            )
            """.trimIndent()
        )
        val existingUnitUuid = "11111111-1111-4111-8111-111111111111"
        val existingTxUuid = "22222222-2222-4222-8222-222222222222"
        rawSqlDb.execSQL(
            "INSERT INTO freezer_units (recordUuid, kodeUnit, namaUnit) VALUES ('$existingUnitUuid', 'FRZ-V22', 'Freezer Existing V22')"
        )
        rawSqlDb.execSQL(
            "INSERT INTO rental_transactions (recordUuid, nomorInvoice, namaPetugas, tandaTanganPetugasUri) VALUES ('$existingTxUuid', 'INV/V22/001', 'PETUGAS LAMA', '/sigs/lama.png')"
        )
        rawSqlDb.version = 22
        rawSqlDb.close()

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(migDbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(23) {
                    override fun onCreate(db: SupportSQLiteDatabase) {}
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        if (oldVersion == 22 && newVersion == 23) {
                            AppDatabase.MIGRATION_22_23.migrate(db)
                        }
                    }
                })
                .build()
        )
        val migratedDb = helper.writableDatabase

        // Verify all 3 new tables exist
        val tables = mutableSetOf<String>()
        migratedDb.query("SELECT name FROM sqlite_master WHERE type='table'").use { c ->
            while (c.moveToNext()) tables.add(c.getString(0))
        }
        assertTrue(tables.contains("form_drafts"))
        assertTrue(tables.contains("unit_rental_prices"))
        assertTrue(tables.contains("transaction_officer_audit_logs"))

        // Verify existing unit & transaction data survived untouched + officer snapshot auto-locked!
        migratedDb.query("SELECT recordUuid, kodeUnit FROM freezer_units").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(existingUnitUuid, c.getString(0))
            assertEquals("FRZ-V22", c.getString(1))
        }
        migratedDb.query("SELECT recordUuid, namaPetugas, tandaTanganPetugasUri, officer_snapshot_locked FROM rental_transactions").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(existingTxUuid, c.getString(0))
            assertEquals("PETUGAS LAMA", c.getString(1))
            assertEquals("/sigs/lama.png", c.getString(2))
            assertEquals(1, c.getInt(3))
        }

        helper.close()
        migDbFile.delete()
    }

    private fun makeUnit(kode: String, nama: String): FreezerUnitEntity = FreezerUnitEntity(
        kodeUnit = kode,
        namaUnit = nama,
        jenisUnit = "Chest Freezer",
        owner = "SOC",
        hargaPembelian = 4_500_000L,
        kapasitasLiter = 300,
        cicilanPerBulan = 350_000L,
        merek = "Gea",
        model = "AB-300",
        nomorSeri = "SN-$kode",
        statusUnit = "Tersedia",
        lokasiUnit = "Solo",
        tanggalPembelian = "2026-11-01"
    )
}
