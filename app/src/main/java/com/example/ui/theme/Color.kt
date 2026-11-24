package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// SEWAin Aja Freezer SOC - Modern Sea Blue, Dark Navy Blue, Light Blue, White Palette
val NavyDark = Color(0xFF0A2540)
val NavyDeep = Color(0xFF061626)
val SeaBluePrimary = Color(0xFF0284C7)
val SeaBlueDark = Color(0xFF0369A1)
val LightBlueAccent = Color(0xFF38BDF8)
val SkyBlueSurface = Color(0xFFE0F2FE)
val IceWhite = Color(0xFFF8FAFC)
val PureWhite = Color(0xFFFFFFFF)

// =========================================================================
// CENTRALIZED CONTRAST & TYPOGRAPHY DESIGN TOKENS (SEWAin Aja Freezer SOC)
// =========================================================================
object AppColorTokens {
    // 1. Primary Text & Heading Tokens
    val PageTitle = Color(0xFF172B4D)        // Judul halaman (#172B4D)
    val SectionTitle = Color(0xFF172B4D)     // Judul section (#172B4D)
    val FormLabel = Color(0xFF263445)        // Label form (#263445)
    val InputText = Color(0xFF172B4D)        // Teks input (#172B4D)
    val Placeholder = Color(0xFF64748B)      // Placeholder (#64748B)
    val Description = Color(0xFF475569)      // Keterangan / deskripsi (#475569)
    val DateText = Color(0xFF087EA4)         // Teks tanggal (#087EA4)

    // 2. Button Text Tokens
    val TextOnBlueButton = Color(0xFFFFFFFF) // Teks tombol biru (#FFFFFF)
    val TextOnWhiteButton = Color(0xFF172B4D)// Teks tombol putih (#172B4D)

    // 3. Navigation & Tab Tokens
    val NavActiveText = Color(0xFFFFFFFF)    // Teks navigasi aktif (#FFFFFF)
    val NavInactiveText = Color(0xFF475569)  // Teks navigasi nonaktif (#475569)
    val NavInactiveOnDark = Color(0xFFCBD5E1)// Teks navigasi nonaktif di atas background biru gelap

    // 4. Form Surface, Border & State Tokens
    val InputSurface = Color(0xFFFFFFFF)
    val InputSurfaceDisabled = Color(0xFFF1F5F9)
    val DisabledText = Color(0xFF475569)     // Teks disabled tetap terbaca jelas (tanpa opacity pudar)
    val BorderDefault = Color(0xFFCBD5E1)
    val BorderStrong = Color(0xFF94A3B8)
    val BorderFocused = Color(0xFF0284C7)
    val ErrorText = Color(0xFFDC2626)
    val ErrorSurface = Color(0xFFFEE2E2)
}

// Convenience top-level aliases referencing AppColorTokens (single source of truth)
val TextPageTitle = AppColorTokens.PageTitle
val TextSectionTitle = AppColorTokens.SectionTitle
val TextFormLabel = AppColorTokens.FormLabel
val TextInputPrimary = AppColorTokens.InputText
val TextPlaceholder = AppColorTokens.Placeholder
val TextDescription = AppColorTokens.Description
val TextDateAccent = AppColorTokens.DateText
val TextOnBlueButton = AppColorTokens.TextOnBlueButton
val TextOnWhiteButton = AppColorTokens.TextOnWhiteButton
val TextNavActive = AppColorTokens.NavActiveText
val TextNavInactive = AppColorTokens.NavInactiveText
val TextDisabledReadable = AppColorTokens.DisabledText
val InputSurfaceWhite = AppColorTokens.InputSurface
val InputSurfaceDisabled = AppColorTokens.InputSurfaceDisabled
val InputBorderDefault = AppColorTokens.BorderDefault

// Status Indicator Colors (Section 6)
val StatusPaidGreen = Color(0xFF10B981)
val StatusPaidGreenBg = Color(0xFFD1FAE5)
val StatusWarningYellow = Color(0xFFF59E0B)
val StatusWarningYellowBg = Color(0xFFFEF3C7)
val StatusOverdueRed = Color(0xFFEF4444)
val StatusOverdueRedBg = Color(0xFFFEE2E2)
val StatusActiveBlue = Color(0xFF0284C7)
val StatusActiveBlueBg = Color(0xFFE0F2FE)
val StatusNeutralGray = Color(0xFF64748B)
val StatusNeutralGrayBg = Color(0xFFF1F5F9)
