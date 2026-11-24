package com.example.ui.theme

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * Skema warna resmi SEWAin Aja Freezer SOC dengan kontras tinggi yang konsisten.
 *
 * Seluruh surface aplikasi menggunakan latar terang (IceWhite / PureWhite) dengan aksen
 * identitas biru laut (SeaBluePrimary) & biru gelap (NavyDark).
 * Tema dikunci ke skema terang ber-kontras tinggi agar pengaturan Dark Mode sistem Android
 * tidak pernah membalik warna font menjadi putih di atas input/kartu berwarna putih.
 */
val AppHighContrastLightColorScheme = lightColorScheme(
    primary = SeaBluePrimary,
    onPrimary = TextOnBlueButton,
    primaryContainer = SkyBlueSurface,
    onPrimaryContainer = TextPageTitle,
    secondary = NavyDark,
    onSecondary = PureWhite,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = TextPageTitle,
    tertiary = TextDateAccent,
    onTertiary = PureWhite,
    tertiaryContainer = SkyBlueSurface,
    onTertiaryContainer = TextPageTitle,
    background = IceWhite,
    onBackground = TextPageTitle,
    surface = PureWhite,
    onSurface = TextInputPrimary,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = TextDescription,
    surfaceContainerLowest = PureWhite,
    surfaceContainerLow = IceWhite,
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerHigh = Color(0xFFE2E8F0),
    surfaceContainerHighest = Color(0xFFE2E8F0),
    outline = AppColorTokens.BorderStrong,
    outlineVariant = InputBorderDefault,
    error = AppColorTokens.ErrorText,
    onError = PureWhite,
    errorContainer = AppColorTokens.ErrorSurface,
    onErrorContainer = Color(0xFF991B1B),
    inverseSurface = NavyDark,
    inverseOnSurface = PureWhite,
    inversePrimary = LightBlueAccent
)

@Composable
fun SEWAinAjaFreezerTheme(
    @Suppress("UNUSED_PARAMETER")
    darkTheme: Boolean = false,
    appearanceConfig: AppAppearanceConfig = AppAppearanceConfig.DEFAULT,
    content: @Composable () -> Unit
) {
    val isDefaultColors = appearanceConfig.themePresetId == AppColorThemePreset.BLUE_OCEAN.id &&
        appearanceConfig.primaryColorHex.equals(AppColorThemePreset.BLUE_OCEAN.primaryHex, ignoreCase = true) &&
        appearanceConfig.secondaryColorHex.equals(AppColorThemePreset.BLUE_OCEAN.secondaryHex, ignoreCase = true) &&
        appearanceConfig.backgroundColorHex.equals(AppColorThemePreset.BLUE_OCEAN.backgroundHex, ignoreCase = true) &&
        appearanceConfig.cardColorHex.equals(AppColorThemePreset.BLUE_OCEAN.cardHex, ignoreCase = true) &&
        appearanceConfig.textColorHex.equals(AppColorThemePreset.BLUE_OCEAN.textHex, ignoreCase = true)

    val resolvedColorScheme = if (isDefaultColors) {
        AppHighContrastLightColorScheme
    } else {
        val primary = appearanceConfig.primaryColor
        val secondary = appearanceConfig.secondaryColor
        val bg = appearanceConfig.backgroundColor
        val card = appearanceConfig.cardColor
        val safeOnBg = AppAppearanceConfig.ensureReadableTextOn(appearanceConfig.textColor, bg)
        val safeOnCard = AppAppearanceConfig.ensureReadableTextOn(appearanceConfig.textColor, card)
        val safeOnPrimary = AppAppearanceConfig.ensureReadableTextOn(PureWhite, primary)
        val safeOnSecondary = AppAppearanceConfig.ensureReadableTextOn(PureWhite, secondary)
        AppHighContrastLightColorScheme.copy(
            primary = primary,
            onPrimary = safeOnPrimary,
            primaryContainer = primary.copy(alpha = 0.14f),
            onPrimaryContainer = safeOnCard,
            secondary = secondary,
            onSecondary = safeOnSecondary,
            tertiary = appearanceConfig.accentColor,
            background = bg,
            onBackground = safeOnBg,
            surface = card,
            onSurface = safeOnCard,
            surfaceContainerLowest = card,
            surfaceContainerLow = bg
        )
    }

    val resolvedTypography = if (appearanceConfig.fontChoice == AppFontChoice.DEFAULT_PLUS_JAKARTA) {
        Typography
    } else {
        buildAppTypography(AppAppearanceConfig.resolveFontFamily(appearanceConfig))
    }

    MaterialTheme(
        colorScheme = resolvedColorScheme,
        typography = resolvedTypography
    ) {
        CompositionLocalProvider(
            LocalContentColor provides resolvedColorScheme.onSurface,
            LocalAppAppearance provides appearanceConfig
        ) {
            content()
        }
    }
}

/**
 * Standar warna terpusat untuk seluruh komponen OutlinedTextField / Input / Dropdown / TextArea / Date Input.
 * Menjamin teks input gelap (#172B4D), label jelas (#263445), placeholder terlihat (#64748B),
 * helper text terbaca (#475569), error tegas (#DC2626), dan teks disabled tetap terbaca.
 */
@Composable
fun appOutlinedTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextInputPrimary,
    unfocusedTextColor = TextInputPrimary,
    disabledTextColor = TextFormLabel,
    errorTextColor = TextInputPrimary,
    focusedContainerColor = InputSurfaceWhite,
    unfocusedContainerColor = InputSurfaceWhite,
    disabledContainerColor = InputSurfaceDisabled,
    errorContainerColor = InputSurfaceWhite,
    cursorColor = SeaBluePrimary,
    errorCursorColor = AppColorTokens.ErrorText,
    focusedBorderColor = SeaBluePrimary,
    unfocusedBorderColor = InputBorderDefault,
    disabledBorderColor = InputBorderDefault,
    errorBorderColor = AppColorTokens.ErrorText,
    focusedLeadingIconColor = SeaBluePrimary,
    unfocusedLeadingIconColor = TextDescription,
    disabledLeadingIconColor = TextDescription,
    errorLeadingIconColor = AppColorTokens.ErrorText,
    focusedTrailingIconColor = SeaBluePrimary,
    unfocusedTrailingIconColor = TextDescription,
    disabledTrailingIconColor = TextDescription,
    errorTrailingIconColor = AppColorTokens.ErrorText,
    focusedLabelColor = SeaBluePrimary,
    unfocusedLabelColor = TextFormLabel,
    disabledLabelColor = TextFormLabel,
    errorLabelColor = AppColorTokens.ErrorText,
    focusedPlaceholderColor = TextPlaceholder,
    unfocusedPlaceholderColor = TextPlaceholder,
    disabledPlaceholderColor = TextPlaceholder,
    errorPlaceholderColor = TextPlaceholder,
    focusedSupportingTextColor = TextDescription,
    unfocusedSupportingTextColor = TextDescription,
    disabledSupportingTextColor = TextDescription,
    errorSupportingTextColor = AppColorTokens.ErrorText,
    focusedPrefixColor = TextFormLabel,
    unfocusedPrefixColor = TextFormLabel,
    disabledPrefixColor = TextFormLabel,
    errorPrefixColor = TextFormLabel,
    focusedSuffixColor = TextFormLabel,
    unfocusedSuffixColor = TextFormLabel,
    disabledSuffixColor = TextFormLabel,
    errorSuffixColor = TextFormLabel
)

/**
 * Standar warna tombol biru / berwarna dengan teks putih (#FFFFFF) yang jelas termasuk saat disabled.
 */
@Composable
fun appPrimaryButtonColors(
    containerColor: Color = SeaBluePrimary
): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = containerColor,
    contentColor = TextOnBlueButton,
    disabledContainerColor = Color(0xFF94A3B8),
    disabledContentColor = PureWhite
)

/**
 * Standar warna tombol putih / outlined di atas latar terang dengan teks gelap (#172B4D).
 */
@Composable
fun appWhiteOutlinedButtonColors(): ButtonColors = ButtonDefaults.outlinedButtonColors(
    containerColor = PureWhite,
    contentColor = TextOnWhiteButton,
    disabledContainerColor = InputSurfaceDisabled,
    disabledContentColor = TextDisabledReadable
)

/**
 * Standar warna FilterChip & Tab Navigasi di atas latar terang:
 * - Aktif: background biru (#0284C7) dengan teks & ikon putih (#FFFFFF)
 * - Nonaktif: background putih (#FFFFFF) dengan teks & ikon abu gelap (#475569)
 */
@Composable
fun appFilterChipColors(
    selectedContainerColor: Color = SeaBluePrimary
): SelectableChipColors = FilterChipDefaults.filterChipColors(
    containerColor = PureWhite,
    labelColor = TextNavInactive,
    iconColor = TextNavInactive,
    disabledContainerColor = InputSurfaceDisabled,
    disabledLabelColor = TextDisabledReadable,
    disabledLeadingIconColor = TextDisabledReadable,
    selectedContainerColor = selectedContainerColor,
    selectedLabelColor = TextNavActive,
    selectedLeadingIconColor = TextNavActive,
    selectedTrailingIconColor = TextNavActive
)

/**
 * Standar warna Checkbox kontras tinggi untuk form di atas latar terang.
 */
@Composable
fun appCheckboxColors(): CheckboxColors = CheckboxDefaults.colors(
    checkedColor = SeaBluePrimary,
    uncheckedColor = TextDescription,
    checkmarkColor = PureWhite,
    disabledCheckedColor = TextDescription,
    disabledUncheckedColor = AppColorTokens.BorderStrong
)

/**
 * Standar warna RadioButton kontras tinggi untuk form di atas latar terang.
 */
@Composable
fun appRadioButtonColors(): RadioButtonColors = RadioButtonDefaults.colors(
    selectedColor = SeaBluePrimary,
    unselectedColor = TextDescription,
    disabledSelectedColor = TextDescription,
    disabledUnselectedColor = AppColorTokens.BorderStrong
)
