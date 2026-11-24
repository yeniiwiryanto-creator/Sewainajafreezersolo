package com.example.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import com.example.ui.theme.TextInputPrimary
import com.example.ui.theme.appOutlinedTextFieldColors
import java.util.Locale

/**
 * Aturan transformasi huruf otomatis untuk komponen input teks SEWAin Aja Freezer SOC:
 * - UPPERCASE: Default untuk seluruh input teks form (Nama, Alamat, Catatan, Unit, Pencarian, dll.)
 * - LOWERCASE: Khusus untuk field email (agar selalu huruf kecil standar)
 * - PRESERVE: Pengecualian untuk Nomor HP/WhatsApp, Password/PIN, URL/Link, OTP, Angka, Kode ID teknis, atau Nama File
 */
enum class InputTextCaseMode {
    UPPERCASE,
    LOWERCASE,
    PRESERVE
}

/**
 * Mengonversi teks sesuai mode case yang ditentukan.
 */
fun transformTextCase(
    input: String,
    mode: InputTextCaseMode,
    locale: Locale = Locale.getDefault()
): String {
    return when (mode) {
        InputTextCaseMode.UPPERCASE -> input.uppercase(locale)
        InputTextCaseMode.LOWERCASE -> input.lowercase(locale)
        InputTextCaseMode.PRESERVE -> input
    }
}

/**
 * Mengonversi `TextFieldValue` tanpa melompatkan posisi kursor (cursor-safe)
 * ketika pengguna mengetik di awal, tengah, maupun akhir teks.
 */
fun applyCaseToTextFieldValue(
    incoming: TextFieldValue,
    mode: InputTextCaseMode,
    locale: Locale = Locale.getDefault()
): TextFieldValue {
    if (mode == InputTextCaseMode.PRESERVE) return incoming

    val transformedText = transformTextCase(incoming.text, mode, locale)
    val safeStart = incoming.selection.start.coerceIn(0, transformedText.length)
    val safeEnd = incoming.selection.end.coerceIn(0, transformedText.length)
    val safeSelection = TextRange(safeStart, safeEnd)

    val safeComposition = incoming.composition?.let { comp ->
        if (transformedText.length == incoming.text.length) {
            comp
        } else {
            TextRange(
                comp.start.coerceIn(0, transformedText.length),
                comp.end.coerceIn(0, transformedText.length)
            )
        }
    }

    return incoming.copy(
        text = transformedText,
        selection = safeSelection,
        composition = safeComposition
    )
}

/**
 * Komponen reusable `SmartCaseOutlinedTextField` dengan Auto CapsLock (UPPERCASE default)
 * yang mempertahankan posisi kursor & mencegah keyboard Android berkedip/menutup.
 */
@Composable
fun SmartCaseOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    caseMode: InputTextCaseMode = InputTextCaseMode.UPPERCASE,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current.copy(color = TextInputPrimary),
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = appOutlinedTextFieldColors()
) {
    val normalizedExternalValue = remember(value, caseMode) {
        transformTextCase(value, caseMode)
    }

    var textFieldValueState by remember(caseMode) {
        mutableStateOf(
            TextFieldValue(
                text = normalizedExternalValue,
                selection = TextRange(normalizedExternalValue.length)
            )
        )
    }

    // Sinkronkan apabila `value` berubah secara eksternal (misal dari OCR, Contact Picker, GPS, atau Clear)
    if (textFieldValueState.text != normalizedExternalValue) {
        val prevSel = textFieldValueState.selection
        val newCursorStart = if (textFieldValueState.text.isEmpty() && normalizedExternalValue.isNotEmpty()) {
            normalizedExternalValue.length
        } else {
            prevSel.start.coerceIn(0, normalizedExternalValue.length)
        }
        val newCursorEnd = if (textFieldValueState.text.isEmpty() && normalizedExternalValue.isNotEmpty()) {
            normalizedExternalValue.length
        } else {
            prevSel.end.coerceIn(0, normalizedExternalValue.length)
        }
        textFieldValueState = textFieldValueState.copy(
            text = normalizedExternalValue,
            selection = TextRange(newCursorStart, newCursorEnd),
            composition = null
        )
    }

    val effectiveKeyboardOptions = remember(keyboardOptions, caseMode) {
        when (caseMode) {
            InputTextCaseMode.UPPERCASE -> {
                if (keyboardOptions.capitalization == KeyboardCapitalization.Unspecified ||
                    keyboardOptions.capitalization == KeyboardCapitalization.None ||
                    keyboardOptions.capitalization == KeyboardCapitalization.Sentences ||
                    keyboardOptions.capitalization == KeyboardCapitalization.Words
                ) {
                    keyboardOptions.copy(capitalization = KeyboardCapitalization.Characters)
                } else {
                    keyboardOptions
                }
            }
            InputTextCaseMode.LOWERCASE -> {
                keyboardOptions.copy(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = if (keyboardOptions.keyboardType == KeyboardType.Text ||
                        keyboardOptions.keyboardType == KeyboardType.Unspecified
                    ) {
                        KeyboardType.Email
                    } else {
                        keyboardOptions.keyboardType
                    }
                )
            }
            InputTextCaseMode.PRESERVE -> keyboardOptions
        }
    }

    OutlinedTextField(
        value = textFieldValueState,
        onValueChange = { incomingTfv ->
            val updatedTfv = applyCaseToTextFieldValue(incomingTfv, caseMode)
            textFieldValueState = updatedTfv
            onValueChange(updatedTfv.text)
        },
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = effectiveKeyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        interactionSource = interactionSource,
        shape = shape,
        colors = colors
    )
}
