package com.example

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.local.CustomerEntity
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.RentalTransactionEntity
import com.example.data.local.TransactionUnitItemEntity
import com.example.data.repository.UnitReservationManager
import com.example.ui.screens.TransactionsScreen
import com.example.ui.theme.SEWAinAjaFreezerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransactionUiAuditTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        UnitReservationManager.clearAll()
    }

    @After
    fun tearDown() {
        UnitReservationManager.clearAll()
    }

    private fun makeCustomer(id: Long, code: String, name: String) = CustomerEntity(
        id = id,
        kodePelanggan = code,
        namaLengkap = name,
        nik = "3372011111110099",
        jenisKelamin = "Perempuan",
        nomorHp = "081234567890",
        alamatLengkap = "JL. SLAMET RIYADI NO. 10, SOLO",
        kabupatenKota = "Kota Surakarta",
        kecamatan = "Laweyan",
        tanggalRegistrasi = "2026-11-01"
    )

    private fun makeUnit(id: Long, code: String, name: String, status: String, price: Long = 350_000L) = FreezerUnitEntity(
        id = id,
        kodeUnit = code,
        namaUnit = name,
        jenisUnit = "Chest Freezer",
        owner = "SOC",
        hargaPembelian = 4_500_000L,
        kapasitasLiter = 300,
        cicilanPerBulan = price,
        merek = "GEA",
        model = "AB-318-R",
        nomorSeri = "SN-$code",
        statusUnit = status,
        lokasiUnit = "Gudang Solo",
        tanggalPembelian = "2026-01-10",
        tanggalPengiriman = "2026-02-01"
    )

    private fun makeTx(id: Long, custId: Long, custName: String, unit: FreezerUnitEntity, invoice: String, total: Long) =
        RentalTransactionEntity(
            id = id,
            nomorInvoice = invoice,
            tanggalPemesanan = "2026-11-01",
            customerId = custId,
            namaPelangganSnapshot = custName,
            unitId = unit.id,
            namaUnitSnapshot = unit.namaUnit,
            kodeUnitSnapshot = unit.kodeUnit,
            hargaSewa = total,
            durasiSewaBulan = 1,
            durasiHari = 30,
            tanggalMulaiSewa = "2026-11-01",
            tanggalAkhirSewa = "2026-12-01",
            tanggalJatuhTempo = "2026-12-01",
            nominalPembayaran = total,
            ongkir = 0L,
            deposit = 0L,
            diskon = 0L,
            totalTagihan = total,
            statusPembayaran = "Lunas",
            statusPenyewaan = "Dalam Penyewaan",
            metodePembayaran = "Transfer BCA",
            namaPetugas = "Admin Audit SAF"
        )

    private fun makeUnitItem(id: Long, txId: Long, custId: Long, unit: FreezerUnitEntity, idx: Int, price: Long) =
        TransactionUnitItemEntity(
            id = id,
            transactionId = txId,
            customerId = custId,
            unitId = unit.id,
            unitIndexNumber = idx,
            kodeUnitSnapshot = unit.kodeUnit,
            namaUnitSnapshot = unit.namaUnit,
            jenisUnitSnapshot = unit.jenisUnit,
            merekSnapshot = unit.merek,
            kapasitasLiterSnapshot = unit.kapasitasLiter,
            jenisProgram = "Sewa Reguler",
            hargaSewa = price,
            durasiHari = 30,
            tanggalMulaiSewa = "2026-11-01",
            tanggalAkhirSewa = "2026-12-01",
            tanggalJatuhTempo = "2026-12-01",
            nominalPembayaran = price,
            statusPembayaran = "Lunas",
            statusPenyewaan = "Dalam Penyewaan"
        )

    @Test
    fun uiAudit_deleteFromListShowsConfirmationAndNewFormEnforcesStrictAvailabilityAndBiayaSection() {
        val cust = makeCustomer(1L, "CUST-UI-01", "RINA KUSUMA")
        val uAvail1 = makeUnit(11L, "SAF-U1", "Freezer Ready 1", "Tersedia", 350_000L)
        val uAvail2 = makeUnit(12L, "SAF-U2", "Freezer Ready 2", "Tersedia", 400_000L)
        val uAvail3 = makeUnit(13L, "SAF-U3", "Freezer Ready 3", "Tersedia", 450_000L)
        val uLost = makeUnit(14L, "SAF-LOST", "Freezer Hilang X", "Hilang", 300_000L)
        val uRented = makeUnit(15L, "SAF-RENT", "Freezer Disewa X", "Disewa", 300_000L)
        val uRepair = makeUnit(16L, "SAF-REP", "Freezer Perbaikan X", "Dalam Perbaikan", 300_000L)

        val existingTx = makeTx(77L, 1L, "RINA KUSUMA", uRented, "INV/SAF/2026/7777", 700_000L)
        val existingTxItems = listOf(
            makeUnitItem(1L, 77L, 1L, uRented, 1, 350_000L),
            makeUnitItem(2L, 77L, 1L, uAvail1, 2, 350_000L)
        )

        var deletedTxCaptured: RentalTransactionEntity? = null

        composeRule.setContent {
            SEWAinAjaFreezerTheme {
                TransactionsScreen(
                    transactions = listOf(existingTx),
                    customers = listOf(cust),
                    units = listOf(uAvail1, uAvail2, uAvail3, uLost, uRented, uRepair),
                    transactionUnitItems = existingTxItems,
                    externallySelectedTx = null,
                    onClearExternalTx = {},
                    onSaveTransaction = { _, _ -> },
                    onExtendRentalByDays = { _, _, _, _, _, _ -> },
                    onGenerateInvoicePdf = { _, _ -> },
                    onCaptureGpsLocation = {},
                    onPostMessage = {},
                    onDeleteTransaction = { deletedTxCaptured = it }
                )
            }
        }

        // 1. Delete button on Transaction List card opens rich confirmation dialog
        composeRule.onNodeWithTag("btn_delete_tx_card_77").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("delete_tx_confirm_summary").assertIsDisplayed()
        composeRule.onNodeWithText("Nomor Transaksi: INV/SAF/2026/7777", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Nama Pelanggan: RINA KUSUMA", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Jumlah Unit: 2 Unit Freezer", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Total Transaksi:", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("btn_confirm_delete_tx").performClick()
        assertEquals(77L, deletedTxCaptured?.id)

        // 2. Open New Transaction Form via FAB
        composeRule.onNodeWithTag("fab_add_transaction").performClick()

        // Duplicate Section 3 Program cards MUST NOT exist
        composeRule.onAllNodesWithTag("program_card_sewa_reguler").assertCountEquals(0)
        composeRule.onAllNodesWithTag("program_card_sewa_hak_milik").assertCountEquals(0)

        // New Section 3: BIAYA & JATUH TEMPO MUST exist
        composeRule.onNodeWithTag("section_biaya_jatuh_tempo").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("card_biaya_jatuh_tempo_per_unit").performScrollTo().assertIsDisplayed()

        // Sequential Multi-Unit selection: Unit 1 -> add Unit 2 -> add Unit 3 -> disabled (no Hilang/Disewa/Perbaikan)
        composeRule.onNodeWithTag("card_unit_item_1").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("btn_add_unit_to_transaction").performScrollTo().performClick()
        composeRule.onNodeWithTag("card_unit_item_2").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("btn_add_unit_to_transaction").performScrollTo().performClick()
        composeRule.onNodeWithTag("card_unit_item_3").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("btn_add_unit_to_transaction").performScrollTo().assertIsNotEnabled()
        composeRule.onAllNodesWithText("Freezer Hilang X", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Freezer Perbaikan X", substring = true).assertCountEquals(0)
    }

    @Test
    fun uiAudit_editTransactionShowsTambahUnitButtonAndPreservesExistingUnit() {
        val cust = makeCustomer(1L, "CUST-UI-02", "BUDI EDIT")
        val uExisting = makeUnit(21L, "SAF-EX", "Freezer Lama", "Disewa", 350_000L)
        val uNewAvail = makeUnit(22L, "SAF-NEW", "Freezer Baru Tersedia", "Tersedia", 400_000L)
        val uHilang = makeUnit(23L, "SAF-LOST2", "Freezer Hilang Z", "Hilang", 300_000L)

        val existingTx = makeTx(88L, 1L, "BUDI EDIT", uExisting, "INV/SAF/2026/8888", 350_000L)
        val existingTxItems = listOf(
            makeUnitItem(10L, 88L, 1L, uExisting, 1, 350_000L)
        )

        composeRule.setContent {
            SEWAinAjaFreezerTheme {
                TransactionsScreen(
                    transactions = listOf(existingTx),
                    customers = listOf(cust),
                    units = listOf(uExisting, uNewAvail, uHilang),
                    transactionUnitItems = existingTxItems,
                    externallySelectedTx = null,
                    onClearExternalTx = {},
                    onSaveTransaction = { _, _ -> },
                    onExtendRentalByDays = { _, _, _, _, _, _ -> },
                    onGenerateInvoicePdf = { _, _ -> },
                    onCaptureGpsLocation = {},
                    onPostMessage = {},
                    onDeleteTransaction = {}
                )
            }
        }

        // Open Edit Transaction form from card
        composeRule.onNodeWithTag("btn_edit_tx_card_88").assertIsDisplayed().performClick()

        // Existing unit 1 is displayed
        composeRule.onNodeWithTag("card_unit_item_1").performScrollTo().assertIsDisplayed()

        // Click + TAMBAH UNIT KE TRANSAKSI -> adds uNewAvail as Unit 2, then disables (uHilang excluded!)
        composeRule.onNodeWithTag("btn_add_unit_to_transaction").performScrollTo().performClick()
        composeRule.onNodeWithTag("card_unit_item_2").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("btn_add_unit_to_transaction").performScrollTo().assertIsNotEnabled()
        composeRule.onAllNodesWithText("Freezer Hilang Z", substring = true).assertCountEquals(0)
    }
}
