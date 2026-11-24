package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.local.CustomerEntity
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.RentalTransactionEntity
import com.example.data.local.TransactionUnitItemEntity
import com.example.ui.components.CustomizeAppearanceSettingsCard
import com.example.ui.components.DetailedRenewalTransactionDialog
import com.example.ui.components.GlobalOperationLoadingBanner
import com.example.ui.components.GlobalOperationNotificationBanner
import com.example.ui.components.LocationDocPhotoDisplayItem
import com.example.ui.components.LocationDocSlots
import com.example.ui.components.LocationFreezerDocumentationCard
import com.example.ui.theme.AppAppearanceConfig
import com.example.ui.theme.SEWAinAjaFreezerTheme
import com.example.viewmodel.AppLoadingState
import com.example.viewmodel.AppNotificationEvent
import com.example.viewmodel.OperationNotificationType
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric Compose UI verification for:
 * - Restored Documentation Upload Card (Dokumentasi Freezer di Lokasi + Dokumentasi Tampak Depan Lokasi + Upload Statuses)
 * - Detailed Multi-Unit Rental Renewal Dialog (Hak Milik Ke-6/12 + strictly Tersedia-only Tambah Unit Baru)
 * - Customize Appearance Settings Card (Theme Presets, Fonts, Preview, Simpan Tampilan, Reset Default)
 * - Centralized Global Loading & Notification Banner (+ Coba Lagi retry action)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ComprehensiveUpgradeUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun uiTest1_locationDocumentationCardDisplaysBothSectionsAndRealUploadStatuses() {
        composeRule.setContent {
            SEWAinAjaFreezerTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    LocationFreezerDocumentationCard(
                        freezerLocationPhotos = listOf(
                            LocationDocPhotoDisplayItem(
                                id = "p1",
                                docSlot = LocationDocSlots.SLOT_FREEZER_DI_LOKASI,
                                localPath = "/tmp/f1.jpg",
                                capturedAtIso = "2026-11-20T09:00:00",
                                latitude = -7.5755,
                                longitude = 110.8243,
                                uploadStatus = "UPLOADED"
                            )
                        ),
                        frontLocationPhotos = listOf(
                            LocationDocPhotoDisplayItem(
                                id = "p2",
                                docSlot = LocationDocSlots.SLOT_TAMPAK_DEPAN_LOKASI,
                                localPath = "/tmp/d1.jpg",
                                capturedAtIso = "2026-11-20T09:05:00",
                                latitude = -7.5755,
                                longitude = 110.8243,
                                uploadStatus = "FAILED"
                            )
                        ),
                        customerLabel = "Budi Frozen Solo",
                        unitLabel = "FRZ-001",
                        currentLatitude = -7.5755,
                        currentLongitude = 110.8243,
                        onAddPhotoFromSource = { _, _ -> },
                        onDeletePhoto = {},
                        onRetryUpload = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("A. Dokumentasi Freezer di Lokasi", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("B. Dokumentasi Tampak Depan Lokasi", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Berhasil", substring = true).assertExists()
        composeRule.onNodeWithText("Gagal", substring = true).assertExists()
    }

    @Test
    fun uiTest2_detailedRenewalDialogShowsHakMilikContinuationAndOnlyAvailableNewUnits() {
        val cust = CustomerEntity(
            id = 1L,
            kodePelanggan = "PLG-01",
            namaLengkap = "Warung Es Bu Sari",
            nik = "3372011111110001",
            nomorHp = "081234567890",
            alamatLengkap = "Jl. Slamet Riyadi Solo",
            jenisKelamin = "Perempuan",
            tanggalRegistrasi = "2026-06-01"
        )
        val hmUnit = FreezerUnitEntity(
            id = 5L, kodeUnit = "FRZ-HM-01", namaUnit = "GEA Hak Milik 400L",
            jenisUnit = "Chest Freezer", owner = "SOC", hargaPembelian = 5_500_000L,
            kapasitasLiter = 400, cicilanPerBulan = 420_000L, jumlahCicilanTotal = 12,
            merek = "GEA", model = "AB-418", nomorSeri = "SN-HM-01",
            statusUnit = "Disewa", lokasiUnit = "Lokasi Pelanggan", tanggalPembelian = "2026-01-01"
        )
        val availUnit = hmUnit.copy(id = 10L, kodeUnit = "FRZ-READY", namaUnit = "GEA Siap Sewa 300L", statusUnit = "Tersedia")
        val lostUnit = hmUnit.copy(id = 11L, kodeUnit = "FRZ-HILANG", namaUnit = "Unit Hilang", statusUnit = "Hilang")
        val maintUnit = hmUnit.copy(id = 12L, kodeUnit = "FRZ-SERVIS", namaUnit = "Unit Servis", statusUnit = "Dalam Perbaikan")

        val tx = RentalTransactionEntity(
            id = 100L, nomorInvoice = "INV-HM-100", customerId = 1L, unitId = 5L,
            namaPelangganSnapshot = cust.namaLengkap, namaUnitSnapshot = hmUnit.namaUnit,
            kodeUnitSnapshot = hmUnit.kodeUnit, tanggalPemesanan = "2026-06-01",
            hargaSewa = 420_000L, durasiSewaBulan = 1, durasiHari = 30,
            tanggalMulaiSewa = "2026-06-01", tanggalAkhirSewa = "2026-11-01", tanggalJatuhTempo = "2026-11-01",
            metodePembayaran = "Transfer Bank",
            totalTagihan = 420_000L, nominalPembayaran = 420_000L, statusPembayaran = "Lunas",
            statusPenyewaan = "Dalam Penyewaan", jenisProgram = "Sewa Hak Milik",
            totalPeriodeHakMilik = 12, pembayaranKeHakMilik = 5
        )
        val unitItem = TransactionUnitItemEntity(
            id = 200L, transactionId = 100L, customerId = 1L, unitId = 5L, unitIndexNumber = 1,
            kodeUnitSnapshot = hmUnit.kodeUnit, namaUnitSnapshot = hmUnit.namaUnit,
            jenisProgram = "Sewa Hak Milik", totalPeriodeHakMilik = 12, pembayaranKeHakMilik = 5,
            statusProgramHakMilik = "Aktif", hargaSewa = 420_000L, durasiHari = 30,
            tanggalMulaiSewa = "2026-06-01", tanggalAkhirSewa = "2026-11-01", tanggalJatuhTempo = "2026-11-01",
            nominalPembayaran = 420_000L, statusPembayaran = "Lunas", statusPenyewaan = "Dalam Penyewaan"
        )

        composeRule.setContent {
            SEWAinAjaFreezerTheme {
                DetailedRenewalTransactionDialog(
                    initialCustomerId = cust.id,
                    initialUnitId = hmUnit.id,
                    customers = listOf(cust),
                    units = listOf(hmUnit, availUnit, lostUnit, maintUnit),
                    unitRentalPrices = emptyList(),
                    transactions = listOf(tx),
                    transactionUnitItems = listOf(unitItem),
                    unitPaymentAllocations = emptyList(),
                    extensions = emptyList(),
                    paymentMethods = emptyList(),
                    loggedInOfficerName = "Admin SOC",
                    loggedInOfficerAccountId = "admin@soc.id",
                    isProcessing = false,
                    onDismiss = {},
                    onSubmitDetailedRenewal = { _, done -> done() }
                )
            }
        }
        composeRule.waitForIdle()

        // Verify Hak Milik continuation info (5 paid -> next ke-6 dari 12)
        assertTrue(
            "Expected FRZ-HM-01 to appear in active unit card and summary",
            composeRule.onAllNodesWithText("FRZ-HM-01", substring = true).fetchSemanticsNodes().isNotEmpty()
        )
        composeRule.onNodeWithTag("txt_hm_next_payment_${hmUnit.id}").assertExists()
        assertTrue(
            "Expected Hak Milik continuation text 'ke-6 dari 12' to be displayed",
            composeRule.onAllNodesWithText("ke-6 dari 12", substring = true, ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        )

        // Click [+ TAMBAH UNIT SEWA BARU] -> MUST show FRZ-READY and MUST NOT show FRZ-HILANG or FRZ-SERVIS!
        composeRule.onNodeWithTag("btn_renewal_add_new_unit").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("FRZ-READY", substring = true).assertExists()
        assertTrue(composeRule.onAllNodesWithText("FRZ-HILANG", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("FRZ-SERVIS", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun uiTest3_customizeAppearanceCardAndGlobalFeedbackBannersRenderAndTriggerActions() {
        var saveClicked = false
        var resetClicked = false
        var retryClicked = false

        composeRule.setContent {
            SEWAinAjaFreezerTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    GlobalOperationLoadingBanner(
                        loadingState = AppLoadingState(
                            isLoading = true,
                            message = "Menyinkronkan data ke Google Drive...",
                            progress = 0.65f,
                            isBlocking = false
                        )
                    )
                    GlobalOperationNotificationBanner(
                        event = AppNotificationEvent(
                            type = OperationNotificationType.ERROR,
                            message = "Sinkronisasi gagal.",
                            detail = "Koneksi internet terputus.",
                            retryLabel = "Coba Lagi",
                            onRetry = { retryClicked = true }
                        ),
                        onDismiss = {}
                    )
                    CustomizeAppearanceSettingsCard(
                        savedConfig = AppAppearanceConfig.DEFAULT,
                        onPreviewConfig = {},
                        onSaveConfig = { saveClicked = true },
                        onResetDefault = { resetClicked = true },
                        onFeedbackMessage = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Menyinkronkan data ke Google Drive...", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("65%", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Coba Lagi").performClick()
        assertTrue("Retry button on error banner must invoke callback", retryClicked)

        composeRule.onNodeWithTag("btn_customize_save").performScrollTo().performClick()
        assertTrue("SIMPAN TAMPILAN must invoke save callback", saveClicked)

        composeRule.onNodeWithTag("btn_customize_reset").performScrollTo().performClick()
        assertTrue("RESET DEFAULT must invoke reset callback", resetClicked)
    }
}
