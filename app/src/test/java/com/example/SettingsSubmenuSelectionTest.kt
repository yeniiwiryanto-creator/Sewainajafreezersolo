package com.example

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.local.CompanyProfile
import com.example.ui.screens.SettingSubmenuTab
import com.example.ui.screens.SettingsAndSyncScreen
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class SettingsSubmenuSelectionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `settings opens neatly with no submenu content and displays only selected submenu without tampilkan semua`() {
        // Pastikan enum SettingSubmenuTab tidak lagi memiliki opsi ALL / Semua
        assertFalse(
            "SettingSubmenuTab must not contain ALL",
            SettingSubmenuTab.entries.any { it.name == "ALL" || it.numberLabel.equals("Semua", ignoreCase = true) }
        )

        composeTestRule.setContent {
            MaterialTheme {
                SettingsAndSyncScreen(
                    companyProfile = CompanyProfile(),
                    googleSheetUrl = "",
                    webhookScriptUrl = "",
                    autoSyncEnabled = false,
                    maskNikInPdf = false,
                    lastSyncTime = "-",
                    syncStatusIndicator = "Siap",
                    importPreview = null,
                    lastImportReport = null,
                    syncLogs = emptyList(),
                    onLoadFileForPreview = { _, _, _ -> },
                    onLoadBuiltInTimelinePreview = {},
                    onSelectSheetInPreview = {},
                    onConfirmImport = {},
                    onDismissPreview = {},
                    onImportFromGoogleSheets = {},
                    onManualSyncNow = {},
                    onExportFullXlsx = {},
                    onExportAndShareApk = {},
                    onSaveApkToUri = {},
                    onSaveCompanyProfile = {},
                    onSaveSyncSettings = { _, _, _, _ -> },
                    onResetTransactionsOnly = {},
                    onResetToDefaultSoloRayaData = {},
                    onResetAllDataEmpty = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Awal dibuka: Menu Setting tampil rapi hanya daftar submenu & prompt pilih salah satu; tidak ada "Tampilkan Semua"
        composeTestRule.onNodeWithTag("card_setting_menu_hub").assertIsDisplayed()
        composeTestRule.onNodeWithTag("card_setting_select_prompt").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tampilkan Semua", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("card_company_profile").assertDoesNotExist()
        composeTestRule.onNodeWithTag("card_payment_methods").assertDoesNotExist()
        composeTestRule.onNodeWithTag("card_customize_perjanjian_sewa").assertDoesNotExist()
        composeTestRule.onNodeWithTag("card_reset_data").assertDoesNotExist()

        // 2. Pilih submenu 1 (Profil Perusahaan): hanya konten Profil Perusahaan yang muncul, tanpa "Tampilkan Semua"
        composeTestRule.onNodeWithTag("submenu_tab_1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("card_company_profile").assertIsDisplayed()
        composeTestRule.onNodeWithTag("card_setting_select_prompt").assertDoesNotExist()
        composeTestRule.onNodeWithTag("card_payment_methods").assertDoesNotExist()
        composeTestRule.onNodeWithText("Tampilkan Semua", substring = true).assertDoesNotExist()

        // 3. Kembali ke daftar menu lewat tombol Menu, lalu pilih submenu 4 (Metode Pembayaran)
        composeTestRule.onNodeWithTag("btn_close_submenu").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("card_setting_select_prompt").assertIsDisplayed()
        composeTestRule.onNodeWithTag("card_company_profile").assertDoesNotExist()

        composeTestRule.onNodeWithTag("submenu_tab_4").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("card_payment_methods").assertIsDisplayed()
        composeTestRule.onNodeWithTag("card_company_profile").assertDoesNotExist()

        // 4. Tekan tombol Menu untuk menutup kembali ke daftar submenu
        composeTestRule.onNodeWithTag("btn_close_submenu").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("card_setting_select_prompt").assertIsDisplayed()
        composeTestRule.onNodeWithTag("card_payment_methods").assertDoesNotExist()
    }
}
