package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.ui.components.FormValidationAlertBanner
import com.example.ui.components.StorageSyncStatusBadge
import com.example.ui.screens.PaymentProofUploadDialog
import com.example.ui.theme.SEWAinAjaFreezerTheme
import com.example.util.FormValidationEngine
import com.example.util.StorageSyncStatus
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class InvoicePaymentProofAndValidationUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun uiTest1_paymentProofDialog_invalidSubmitShowsBannerAndPreservesFilledFields() {
        var savedCalled = false
        composeRule.setContent {
            SEWAinAjaFreezerTheme {
                PaymentProofUploadDialog(
                    transactionId = 55L,
                    invoiceNumber = "INV/SAF/2026/0055",
                    defaultAmount = 0L,
                    defaultMethod = "Transfer BCA",
                    defaultSender = "BUDI SANTOSO",
                    onDismiss = {},
                    onSave = { savedCalled = true }
                )
            }
        }

        // Verify Kamera, Galeri/QRIS, and Dokumen/PDF attachment options exist
        composeRule.onNodeWithTag("btn_camera_payment_proof").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_gallery_payment_proof").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_doc_payment_proof").assertIsDisplayed()

        // Fill notes while keeping nominal invalid (0)
        composeRule.onNodeWithTag("input_payment_proof_notes")
            .performScrollTo()
            .performTextInput("CICILAN TAHAP 1")

        composeRule.onNodeWithTag("input_payment_proof_amount")
            .performScrollTo()
            .performTextClearance()

        // Tap SIMPAN -> must show DATA BELUM LENGKAP! banner and NOT call onSave or wipe filled notes
        composeRule.onNodeWithTag("btn_save_payment_proof")
            .performScrollTo()
            .performClick()

        assertFalse("onSave must NOT fire when required fields are incomplete", savedCalled)
        composeRule.onNodeWithTag("payment_proof_validation_banner").assertIsDisplayed()
        composeRule.onNodeWithText("DATA BELUM LENGKAP!", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("input_payment_proof_notes")
            .performScrollTo()
            .assertTextContains("CICILAN TAHAP 1", substring = true)
    }

    @Test
    fun uiTest2_allFiveStorageSyncBadgesAndValidationBannerRenderClearly() {
        val sampleInvalid = FormValidationEngine.validateCustomerForm("", "123", "", "")
        composeRule.setContent {
            SEWAinAjaFreezerTheme {
                Column {
                    FormValidationAlertBanner(validationResult = sampleInvalid)
                    StorageSyncStatus.entries.forEach { st ->
                        StorageSyncStatusBadge(status = st)
                    }
                }
            }
        }

        composeRule.onNodeWithTag("form_validation_alert_banner").assertIsDisplayed()
        composeRule.onNodeWithText("DATA BELUM LENGKAP!", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Tersimpan di perangkat").assertIsDisplayed()
        composeRule.onNodeWithText("Menunggu sinkronisasi").assertIsDisplayed()
        composeRule.onNodeWithText("Sedang disinkronkan").assertIsDisplayed()
        composeRule.onNodeWithText("Berhasil tersinkronisasi").assertIsDisplayed()
        composeRule.onNodeWithText("Gagal disinkronisasi").assertIsDisplayed()
    }
}
