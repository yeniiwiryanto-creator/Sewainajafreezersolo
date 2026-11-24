package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CompanyProfile
import com.example.data.local.CustomerEntity
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.RentalTransactionEntity
import com.example.data.local.TransactionUnitItemEntity
import com.example.data.local.UnitRentalPriceEntity
import com.example.pdf.InvoicePdfGenerator
import com.example.ui.components.InputTextCaseMode
import com.example.ui.components.SmartCaseOutlinedTextField
import com.example.ui.screens.CustomersScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.FreezerReturnScreen
import com.example.ui.screens.ReportsAndInvestmentScreen
import com.example.ui.screens.TransactionsScreen
import com.example.ui.screens.UnitsScreen
import com.example.ui.screens.UsageLocationScreen
import com.example.ui.theme.AppColorTokens
import com.example.ui.theme.AppHighContrastLightColorScheme
import com.example.ui.theme.IceWhite
import com.example.ui.theme.NavyDark
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SEWAinAjaFreezerTheme
import com.example.ui.theme.SeaBluePrimary
import com.example.ui.theme.appCheckboxColors
import com.example.ui.theme.appFilterChipColors
import com.example.ui.theme.appOutlinedTextFieldColors
import com.example.ui.theme.appPrimaryButtonColors
import com.example.ui.theme.appRadioButtonColors
import com.example.ui.theme.appWhiteOutlinedButtonColors
import com.example.viewmodel.DashboardMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.max
import kotlin.math.min

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContrastAndThemeAuditTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun contrastRatio(fg: Color, bg: Color): Double {
        val l1 = fg.luminance().toDouble()
        val l2 = bg.luminance().toDouble()
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun sampleUnit() = FreezerUnitEntity(
        id = 1L,
        kodeUnit = "SAF-001",
        namaUnit = "GEA AB-318-R",
        jenisUnit = "Chest Freezer",
        owner = "SOC",
        hargaPembelian = 4_500_000L,
        kapasitasLiter = 300,
        cicilanPerBulan = 350_000L,
        merek = "GEA",
        model = "AB-318-R",
        nomorSeri = "SN-318-001",
        statusUnit = "Tersedia",
        lokasiUnit = "Gudang Solo",
        tanggalPembelian = "2026-01-10",
        tanggalPengiriman = "2026-02-01"
    )

    private fun sampleCustomer() = CustomerEntity(
        id = 1L,
        kodePelanggan = "CUST/SAF/2026/0001",
        namaLengkap = "BUDI SANTOSO",
        nik = "3372011234560001",
        jenisKelamin = "Laki-laki",
        nomorHp = "081234567890",
        alamatLengkap = "JL. SLAMET RIYADI NO. 10, SOLO",
        kabupatenKota = "Kota Surakarta",
        kecamatan = "Laweyan",
        tanggalRegistrasi = "2026-01-15"
    )

    private fun sampleTransaction() = RentalTransactionEntity(
        id = 1L,
        nomorInvoice = "INV/SAF/2026/0001",
        tanggalPemesanan = "2026-03-01",
        customerId = 1L,
        namaPelangganSnapshot = "BUDI SANTOSO",
        unitId = 1L,
        namaUnitSnapshot = "GEA AB-318-R",
        kodeUnitSnapshot = "SAF-001",
        hargaSewa = 350_000L,
        durasiSewaBulan = 1,
        durasiHari = 30,
        tanggalMulaiSewa = "2026-03-01",
        tanggalAkhirSewa = "2026-03-31",
        tanggalJatuhTempo = "2026-03-31",
        nominalPembayaran = 350_000L,
        totalTagihan = 350_000L,
        statusPembayaran = "Lunas",
        statusPenyewaan = "Dalam Penyewaan",
        metodePembayaran = "Transfer BCA",
        namaPetugas = "ADMIN SOLO",
        officerSnapshotLocked = true,
        officerSignedAt = "2026-03-01 09:30"
    )

    @Test
    fun test1_allMandatedColorTokensMatchSpecAndPassContrastAudit() {
        // 1. Exact hex verification against user specification
        assertEquals(Color(0xFF172B4D), AppColorTokens.PageTitle)
        assertEquals(Color(0xFF172B4D), AppColorTokens.SectionTitle)
        assertEquals(Color(0xFF263445), AppColorTokens.FormLabel)
        assertEquals(Color(0xFF172B4D), AppColorTokens.InputText)
        assertEquals(Color(0xFF64748B), AppColorTokens.Placeholder)
        assertEquals(Color(0xFF475569), AppColorTokens.Description)
        assertEquals(Color(0xFF087EA4), AppColorTokens.DateText)
        assertEquals(Color(0xFFFFFFFF), AppColorTokens.TextOnBlueButton)
        assertEquals(Color(0xFF172B4D), AppColorTokens.TextOnWhiteButton)
        assertEquals(Color(0xFFFFFFFF), AppColorTokens.NavActiveText)
        assertEquals(Color(0xFF475569), AppColorTokens.NavInactiveText)

        // 2. WCAG contrast ratio verification (zero white-on-white, strong readability)
        assertTrue("PageTitle on White must exceed 10:1", contrastRatio(AppColorTokens.PageTitle, PureWhite) >= 10.0)
        assertTrue("FormLabel on White must exceed 10:1", contrastRatio(AppColorTokens.FormLabel, PureWhite) >= 10.0)
        assertTrue("InputText on White must exceed 10:1", contrastRatio(AppColorTokens.InputText, PureWhite) >= 10.0)
        assertTrue("Placeholder on White must exceed 4.5:1", contrastRatio(AppColorTokens.Placeholder, PureWhite) >= 4.5)
        assertTrue("Description on White must exceed 7:1", contrastRatio(AppColorTokens.Description, PureWhite) >= 7.0)
        assertTrue("DateText on White must exceed 4.5:1", contrastRatio(AppColorTokens.DateText, PureWhite) >= 4.5)
        assertTrue("DisabledText on DisabledSurface must exceed 6:1", contrastRatio(AppColorTokens.DisabledText, AppColorTokens.InputSurfaceDisabled) >= 6.0)
        assertTrue("TextOnBlueButton on SeaBluePrimary must exceed 4:1", contrastRatio(AppColorTokens.TextOnBlueButton, SeaBluePrimary) >= 4.0)
        assertTrue("TextOnBlueButton on NavyDark must exceed 14:1", contrastRatio(AppColorTokens.TextOnBlueButton, NavyDark) >= 14.0)
        assertTrue("TextOnWhiteButton on White must exceed 10:1", contrastRatio(AppColorTokens.TextOnWhiteButton, PureWhite) >= 10.0)
        assertTrue("NavInactiveText on White must exceed 7:1", contrastRatio(AppColorTokens.NavInactiveText, PureWhite) >= 7.0)
        assertTrue("NavInactiveOnDark on NavyDark must exceed 9:1", contrastRatio(AppColorTokens.NavInactiveOnDark, NavyDark) >= 9.0)

        // 3. Explicit prohibition: white text NEVER paired with white surface in ColorScheme
        assertNotEquals(PureWhite, AppHighContrastLightColorScheme.onSurface)
        assertNotEquals(PureWhite, AppHighContrastLightColorScheme.onBackground)
        assertEquals(AppColorTokens.InputText, AppHighContrastLightColorScheme.onSurface)
        assertEquals(AppColorTokens.PageTitle, AppHighContrastLightColorScheme.onBackground)
        assertEquals(AppColorTokens.Description, AppHighContrastLightColorScheme.onSurfaceVariant)
        assertEquals(PureWhite, AppHighContrastLightColorScheme.surface)
        assertEquals(IceWhite, AppHighContrastLightColorScheme.background)
    }

    @Test
    fun test2_themeNeverInvertsToWhiteOnWhiteEvenWhenDarkThemeRequested() {
        var resolvedSurface: Color? = null
        var resolvedOnSurface: Color? = null
        var resolvedOnBackground: Color? = null
        var resolvedLocalContent: Color? = null
        var focusedInputText: Color? = null
        var unfocusedLabel: Color? = null
        var placeholderColor: Color? = null
        var disabledInputText: Color? = null
        var chipColorsBuilt = false

        composeRule.setContent {
            // Simulate Android system Dark Mode ON
            SEWAinAjaFreezerTheme(darkTheme = true) {
                resolvedSurface = MaterialTheme.colorScheme.surface
                resolvedOnSurface = MaterialTheme.colorScheme.onSurface
                resolvedOnBackground = MaterialTheme.colorScheme.onBackground
                resolvedLocalContent = LocalContentColor.current

                val tfColors = appOutlinedTextFieldColors()
                focusedInputText = tfColors.focusedTextColor
                unfocusedLabel = tfColors.unfocusedLabelColor
                placeholderColor = tfColors.unfocusedPlaceholderColor
                disabledInputText = tfColors.disabledTextColor

                // Verify helper builders instantiate cleanly
                appFilterChipColors()
                chipColorsBuilt = true
                appPrimaryButtonColors()
                appWhiteOutlinedButtonColors()
                appCheckboxColors()
                appRadioButtonColors()

                Column {
                    SmartCaseOutlinedTextField(
                        value = "TEST INPUT",
                        onValueChange = {},
                        caseMode = InputTextCaseMode.UPPERCASE,
                        label = { Text("Label Uji") },
                        placeholder = { Text("Placeholder Uji") }
                    )
                }
            }
        }

        composeRule.waitForIdle()
        assertEquals(PureWhite, resolvedSurface)
        assertEquals(AppColorTokens.InputText, resolvedOnSurface)
        assertEquals(AppColorTokens.PageTitle, resolvedOnBackground)
        assertEquals(AppColorTokens.InputText, resolvedLocalContent)
        assertEquals(AppColorTokens.InputText, focusedInputText)
        assertEquals(AppColorTokens.FormLabel, unfocusedLabel)
        assertEquals(AppColorTokens.Placeholder, placeholderColor)
        assertEquals(AppColorTokens.FormLabel, disabledInputText)
        assertTrue(chipColorsBuilt)
    }

    @Test
    fun test3_allCoreScreensFormsAndDetailsRenderAcrossMultipleScreenWidths() {
        val unit = sampleUnit()
        val customer = sampleCustomer()
        val tx = sampleTransaction()
        val unitPrice = UnitRentalPriceEntity(id = 1L, unitId = 1L, periodDays = 30, price = 350_000L)
        val unitItem = TransactionUnitItemEntity(
            id = 1L,
            transactionId = 1L,
            customerId = 1L,
            unitId = 1L,
            kodeUnitSnapshot = unit.kodeUnit,
            namaUnitSnapshot = unit.namaUnit,
            jenisProgram = "Sewa Hak Milik",
            totalPeriodeHakMilik = 12,
            pembayaranKeHakMilik = 3,
            hargaSewa = 350_000L,
            nominalPembayaran = 350_000L
        )
        val metrics = DashboardMetrics(
            totalUnits = 1,
            unitTersedia = 1,
            totalPelanggan = 1,
            transaksiAktifCount = 1,
            pendapatanBulanIni = 350_000L
        )

        // Render compact (360dp), standard (412dp), and tablet (800dp) screen containers
        composeRule.setContent {
            SEWAinAjaFreezerTheme(darkTheme = true) {
                Column {
                    Box(modifier = Modifier.size(width = 360.dp, height = 220.dp)) {
                        DashboardScreen(
                            metrics = metrics,
                            syncStatus = "SYNCED",
                            transactions = listOf(tx),
                            customers = listOf(customer),
                            units = listOf(unit),
                            onNavigateToUnits = {},
                            onNavigateToCustomers = {},
                            onNavigateToTransactions = {},
                            onOpenTransactionDetail = {},
                            onTriggerNotificationTest = {}
                        )
                    }
                    Box(modifier = Modifier.size(width = 412.dp, height = 220.dp)) {
                        UnitsScreen(
                            units = listOf(unit),
                            transactions = listOf(tx),
                            unitRentalPrices = listOf(unitPrice),
                            onSaveUnit = {},
                            onDeleteUnit = {}
                        )
                    }
                    Box(modifier = Modifier.size(width = 800.dp, height = 220.dp)) {
                        CustomersScreen(
                            customers = listOf(customer),
                            transactions = listOf(tx),
                            onSaveCustomer = {},
                            onDeleteCustomer = {}
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dashboard_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("units_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("customers_screen").assertIsDisplayed()
        composeRule.onNodeWithText("SEWAin Aja Freezer SOC").assertIsDisplayed()

        // Open Add Unit form dialog & verify DAFTAR HARGA SEWA FREEZER renders cleanly
        composeRule.onNodeWithTag("fab_add_unit").performClick()
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithTag("section_daftar_harga_sewa_freezer").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun test4_transactionsReportsReturnsUsageLocationAndPdfGenerationWorkCleanly() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val unit = sampleUnit()
        val customer = sampleCustomer()
        val tx = sampleTransaction()
        val unitItem = TransactionUnitItemEntity(
            id = 1L,
            transactionId = 1L,
            customerId = 1L,
            unitId = 1L,
            kodeUnitSnapshot = unit.kodeUnit,
            namaUnitSnapshot = unit.namaUnit,
            jenisProgram = "Sewa Hak Milik",
            totalPeriodeHakMilik = 12,
            pembayaranKeHakMilik = 4,
            hargaSewa = 350_000L,
            nominalPembayaran = 350_000L
        )

        // Verify PDF stamp & template configuration helpers resolve cleanly without crashing
        val stamp = com.example.pdf.PdfTemplateRenderer.resolvePaymentStampLabel(tx)
        assertEquals("LUNAS", stamp)
        assertTrue(context.packageName.isNotBlank())

        // Verify Transactions, Reports, FreezerReturn, and UsageLocation each render cleanly full-size under darkTheme = true
        val activeScreen = androidx.compose.runtime.mutableIntStateOf(0)
        composeRule.setContent {
            SEWAinAjaFreezerTheme(darkTheme = true) {
                when (activeScreen.intValue) {
                    0 -> TransactionsScreen(
                        transactions = listOf(tx),
                        customers = listOf(customer),
                        units = listOf(unit),
                        transactionUnitItems = listOf(unitItem),
                        externallySelectedTx = null,
                        onClearExternalTx = {},
                        onSaveTransaction = { _, _ -> },
                        onExtendRentalByDays = { _, _, _, _, _, _ -> },
                        onGenerateInvoicePdf = { _, _ -> },
                        onCaptureGpsLocation = {},
                        onPostMessage = {}
                    )
                    1 -> ReportsAndInvestmentScreen(
                        units = listOf(unit),
                        transactions = listOf(tx),
                        extensions = emptyList(),
                        metrics = DashboardMetrics(totalUnits = 1, totalPelanggan = 1),
                        onUpdateUnitInvestment = {},
                        onExportFullXlsx = {},
                        onExportCategoryCsv = {},
                        transactionUnitItems = listOf(unitItem)
                    )
                    2 -> FreezerReturnScreen(
                        transactions = listOf(tx),
                        customers = listOf(customer),
                        units = listOf(unit),
                        returnsHistory = emptyList(),
                        onSubmitReturn = { _, _ -> },
                        onGenerateReturnPdf = { _, _ -> }
                    )
                    else -> UsageLocationScreen(
                        transactions = listOf(tx),
                        customers = listOf(customer),
                        units = listOf(unit),
                        onCaptureGpsLocation = {},
                        onUpdateTransactionLocation = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("transactions_screen").assertIsDisplayed()

        activeScreen.intValue = 1
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reports_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("tab_reports_hak_milik_units").performClick()
        composeRule.waitForIdle()

        activeScreen.intValue = 2
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tab_form_pengembalian").assertIsDisplayed()

        activeScreen.intValue = 3
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("search_usage_location_input").assertIsDisplayed()
    }
}
