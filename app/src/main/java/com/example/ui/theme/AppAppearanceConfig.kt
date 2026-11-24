package com.example.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

enum class AppColorThemePreset(
    val id: String,
    val displayName: String,
    val primaryHex: String,
    val secondaryHex: String,
    val backgroundHex: String,
    val cardHex: String,
    val textHex: String,
    val navBarHex: String,
    val buttonHex: String,
    val accentHex: String
) {
    BLUE_OCEAN(
        id = "BLUE_OCEAN",
        displayName = "Blue Ocean",
        primaryHex = "#0284C7",
        secondaryHex = "#0A2540",
        backgroundHex = "#F8FAFC",
        cardHex = "#FFFFFF",
        textHex = "#172B4D",
        navBarHex = "#0A2540",
        buttonHex = "#0284C7",
        accentHex = "#38BDF8"
    ),
    NAVY_BLUE(
        id = "NAVY_BLUE",
        displayName = "Navy Blue",
        primaryHex = "#1E3A8A",
        secondaryHex = "#0F172A",
        backgroundHex = "#F1F5F9",
        cardHex = "#FFFFFF",
        textHex = "#0F172A",
        navBarHex = "#0F172A",
        buttonHex = "#1E3A8A",
        accentHex = "#3B82F6"
    ),
    LIGHT_BLUE(
        id = "LIGHT_BLUE",
        displayName = "Light Blue",
        primaryHex = "#0EA5E9",
        secondaryHex = "#0369A1",
        backgroundHex = "#F0F9FF",
        cardHex = "#FFFFFF",
        textHex = "#0C4A6E",
        navBarHex = "#0369A1",
        buttonHex = "#0284C7",
        accentHex = "#38BDF8"
    ),
    DARK_BLUE(
        id = "DARK_BLUE",
        displayName = "Dark Blue",
        primaryHex = "#1D4ED8",
        secondaryHex = "#172554",
        backgroundHex = "#EFF6FF",
        cardHex = "#FFFFFF",
        textHex = "#1E293B",
        navBarHex = "#172554",
        buttonHex = "#1D4ED8",
        accentHex = "#60A5FA"
    ),
    GREEN(
        id = "GREEN",
        displayName = "Green",
        primaryHex = "#059669",
        secondaryHex = "#064E3B",
        backgroundHex = "#F0FDF4",
        cardHex = "#FFFFFF",
        textHex = "#14532D",
        navBarHex = "#064E3B",
        buttonHex = "#059669",
        accentHex = "#34D399"
    ),
    PURPLE(
        id = "PURPLE",
        displayName = "Purple",
        primaryHex = "#7C3AED",
        secondaryHex = "#3B0764",
        backgroundHex = "#FAF5FF",
        cardHex = "#FFFFFF",
        textHex = "#2E1065",
        navBarHex = "#3B0764",
        buttonHex = "#7C3AED",
        accentHex = "#A78BFA"
    ),
    RED(
        id = "RED",
        displayName = "Red",
        primaryHex = "#DC2626",
        secondaryHex = "#7F1D1D",
        backgroundHex = "#FEF2F2",
        cardHex = "#FFFFFF",
        textHex = "#450A0A",
        navBarHex = "#7F1D1D",
        buttonHex = "#DC2626",
        accentHex = "#F87171"
    ),
    ORANGE(
        id = "ORANGE",
        displayName = "Orange",
        primaryHex = "#EA580C",
        secondaryHex = "#7C2D12",
        backgroundHex = "#FFF7ED",
        cardHex = "#FFFFFF",
        textHex = "#431407",
        navBarHex = "#7C2D12",
        buttonHex = "#EA580C",
        accentHex = "#FB923C"
    ),
    BLACK(
        id = "BLACK",
        displayName = "Black",
        primaryHex = "#18181B",
        secondaryHex = "#09090B",
        backgroundHex = "#F4F4F5",
        cardHex = "#FFFFFF",
        textHex = "#18181B",
        navBarHex = "#09090B",
        buttonHex = "#27272A",
        accentHex = "#52525B"
    ),
    WHITE(
        id = "WHITE",
        displayName = "White",
        primaryHex = "#0284C7",
        secondaryHex = "#334155",
        backgroundHex = "#FFFFFF",
        cardHex = "#F8FAFC",
        textHex = "#172B4D",
        navBarHex = "#1E293B",
        buttonHex = "#0284C7",
        accentHex = "#087EA4"
    ),
    CUSTOM(
        id = "CUSTOM",
        displayName = "Custom Color",
        primaryHex = "#0284C7",
        secondaryHex = "#0A2540",
        backgroundHex = "#F8FAFC",
        cardHex = "#FFFFFF",
        textHex = "#172B4D",
        navBarHex = "#0A2540",
        buttonHex = "#0284C7",
        accentHex = "#38BDF8"
    );

    companion object {
        fun fromId(id: String): AppColorThemePreset =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: BLUE_OCEAN
    }
}

enum class AppFontChoice(val id: String, val displayName: String) {
    DEFAULT_PLUS_JAKARTA("PLUS_JAKARTA_SANS", "Plus Jakarta Sans (Bawaan)"),
    INTER("INTER", "Inter (Modern Clean)"),
    POPPINS("POPPINS", "Poppins (Geometric)"),
    ROBOTO("ROBOTO", "Roboto (Android Classic)"),
    LORA("LORA", "Lora (Editorial Serif)"),
    JETBRAINS_MONO("JETBRAINS_MONO", "JetBrains Mono (Teknis)"),
    CUSTOM_FILE("CUSTOM_FILE", "Font Upload Sendiri (.TTF / .OTF)");

    companion object {
        fun fromId(id: String): AppFontChoice =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT_PLUS_JAKARTA
    }
}

enum class BackgroundTargetScope(val id: String, val label: String) {
    NONE("NONE", "Tanpa Background Gambar"),
    ALL_SCREENS("ALL_SCREENS", "Seluruh halaman aplikasi"),
    DASHBOARD_ONLY("DASHBOARD_ONLY", "Halaman dashboard saja"),
    LOGIN_ONLY("LOGIN_ONLY", "Halaman login"),
    SPECIFIC_SCREENS("SPECIFIC_SCREENS", "Halaman tertentu");

    companion object {
        fun fromId(id: String): BackgroundTargetScope =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: NONE
    }
}

enum class BackgroundScaleMode(val id: String, val label: String) {
    CROP("CROP", "Isi Penuh (Crop)"),
    FIT("FIT", "Sesuaikan Utuh (Fit)"),
    FILL_WIDTH("FILL_WIDTH", "Lebar Penuh");

    companion object {
        fun fromId(id: String): BackgroundScaleMode =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: CROP
    }
}

enum class BackgroundPositionMode(val id: String, val label: String) {
    CENTER("CENTER", "Tengah"),
    TOP("TOP", "Atas"),
    BOTTOM("BOTTOM", "Bawah");

    companion object {
        fun fromId(id: String): BackgroundPositionMode =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: CENTER
    }
}

data class AppAppearanceConfig(
    val themePresetId: String = AppColorThemePreset.BLUE_OCEAN.id,
    val primaryColorHex: String = AppColorThemePreset.BLUE_OCEAN.primaryHex,
    val secondaryColorHex: String = AppColorThemePreset.BLUE_OCEAN.secondaryHex,
    val backgroundColorHex: String = AppColorThemePreset.BLUE_OCEAN.backgroundHex,
    val cardColorHex: String = AppColorThemePreset.BLUE_OCEAN.cardHex,
    val textColorHex: String = AppColorThemePreset.BLUE_OCEAN.textHex,
    val navBarColorHex: String = AppColorThemePreset.BLUE_OCEAN.navBarHex,
    val buttonColorHex: String = AppColorThemePreset.BLUE_OCEAN.buttonHex,
    val accentColorHex: String = AppColorThemePreset.BLUE_OCEAN.accentHex,
    val fontChoiceId: String = AppFontChoice.DEFAULT_PLUS_JAKARTA.id,
    val customFontPath: String = "",
    val customFontDisplayName: String = "",
    val backgroundImagePath: String = "",
    val backgroundScaleModeId: String = BackgroundScaleMode.CROP.id,
    val backgroundPositionId: String = BackgroundPositionMode.CENTER.id,
    val backgroundOpacity: Float = 0.22f, // 0.05f..0.60f keeps text always readable
    val backgroundBlurDp: Float = 0f, // 0f..20f
    val backgroundScopeId: String = BackgroundTargetScope.NONE.id,
    val backgroundSpecificTabs: Set<Int> = setOf(0) // 0=Dashboard,1=Units,2=Customers,3=Tx,4=Reports,5=Settings
) {
    val preset: AppColorThemePreset get() = AppColorThemePreset.fromId(themePresetId)
    val fontChoice: AppFontChoice get() = AppFontChoice.fromId(fontChoiceId)
    val backgroundScope: BackgroundTargetScope get() = BackgroundTargetScope.fromId(backgroundScopeId)
    val backgroundScaleMode: BackgroundScaleMode get() = BackgroundScaleMode.fromId(backgroundScaleModeId)
    val backgroundPositionMode: BackgroundPositionMode get() = BackgroundPositionMode.fromId(backgroundPositionId)

    val primaryColor: Color get() = parseHexColorOrDefault(primaryColorHex, SeaBluePrimary)
    val secondaryColor: Color get() = parseHexColorOrDefault(secondaryColorHex, NavyDark)
    val backgroundColor: Color get() = parseHexColorOrDefault(backgroundColorHex, IceWhite)
    val cardColor: Color get() = parseHexColorOrDefault(cardColorHex, PureWhite)
    val textColor: Color get() = ensureReadableTextOn(parseHexColorOrDefault(textColorHex, TextInputPrimary), cardColor)
    val navBarColor: Color get() = parseHexColorOrDefault(navBarColorHex, NavyDark)
    val buttonColor: Color get() = parseHexColorOrDefault(buttonColorHex, SeaBluePrimary)
    val accentColor: Color get() = parseHexColorOrDefault(accentColorHex, LightBlueAccent)

    fun shouldShowBackgroundOnTab(tabIndex: Int, isLoginScreen: Boolean = false): Boolean {
        if (backgroundImagePath.isBlank()) return false
        return when (backgroundScope) {
            BackgroundTargetScope.NONE -> false
            BackgroundTargetScope.ALL_SCREENS -> true
            BackgroundTargetScope.DASHBOARD_ONLY -> !isLoginScreen && tabIndex == 0
            BackgroundTargetScope.LOGIN_ONLY -> isLoginScreen
            BackgroundTargetScope.SPECIFIC_SCREENS -> !isLoginScreen && tabIndex in backgroundSpecificTabs
        }
    }

    fun applyPreset(preset: AppColorThemePreset): AppAppearanceConfig = copy(
        themePresetId = preset.id,
        primaryColorHex = preset.primaryHex,
        secondaryColorHex = preset.secondaryHex,
        backgroundColorHex = preset.backgroundHex,
        cardColorHex = preset.cardHex,
        textColorHex = preset.textHex,
        navBarColorHex = preset.navBarHex,
        buttonColorHex = preset.buttonHex,
        accentColorHex = preset.accentHex
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("themePresetId", themePresetId)
        put("primaryColorHex", primaryColorHex)
        put("secondaryColorHex", secondaryColorHex)
        put("backgroundColorHex", backgroundColorHex)
        put("cardColorHex", cardColorHex)
        put("textColorHex", textColorHex)
        put("navBarColorHex", navBarColorHex)
        put("buttonColorHex", buttonColorHex)
        put("accentColorHex", accentColorHex)
        put("fontChoiceId", fontChoiceId)
        put("customFontPath", customFontPath)
        put("customFontDisplayName", customFontDisplayName)
        put("backgroundImagePath", backgroundImagePath)
        put("backgroundScaleModeId", backgroundScaleModeId)
        put("backgroundPositionId", backgroundPositionId)
        put("backgroundOpacity", backgroundOpacity.toDouble())
        put("backgroundBlurDp", backgroundBlurDp.toDouble())
        put("backgroundScopeId", backgroundScopeId)
        put("backgroundSpecificTabs", JSONArray(backgroundSpecificTabs.sorted()))
    }

    companion object {
        val DEFAULT = AppAppearanceConfig()

        fun fromJson(json: JSONObject?): AppAppearanceConfig {
            if (json == null) return DEFAULT
            val tabsArr = json.optJSONArray("backgroundSpecificTabs")
            val tabs = if (tabsArr != null) {
                buildSet {
                    for (i in 0 until tabsArr.length()) add(tabsArr.optInt(i, 0))
                }
            } else setOf(0)
            return AppAppearanceConfig(
                themePresetId = json.optString("themePresetId", DEFAULT.themePresetId),
                primaryColorHex = json.optString("primaryColorHex", DEFAULT.primaryColorHex),
                secondaryColorHex = json.optString("secondaryColorHex", DEFAULT.secondaryColorHex),
                backgroundColorHex = json.optString("backgroundColorHex", DEFAULT.backgroundColorHex),
                cardColorHex = json.optString("cardColorHex", DEFAULT.cardColorHex),
                textColorHex = json.optString("textColorHex", DEFAULT.textColorHex),
                navBarColorHex = json.optString("navBarColorHex", DEFAULT.navBarColorHex),
                buttonColorHex = json.optString("buttonColorHex", DEFAULT.buttonColorHex),
                accentColorHex = json.optString("accentColorHex", DEFAULT.accentColorHex),
                fontChoiceId = json.optString("fontChoiceId", DEFAULT.fontChoiceId),
                customFontPath = json.optString("customFontPath", ""),
                customFontDisplayName = json.optString("customFontDisplayName", ""),
                backgroundImagePath = json.optString("backgroundImagePath", ""),
                backgroundScaleModeId = json.optString("backgroundScaleModeId", DEFAULT.backgroundScaleModeId),
                backgroundPositionId = json.optString("backgroundPositionId", DEFAULT.backgroundPositionId),
                backgroundOpacity = json.optDouble("backgroundOpacity", DEFAULT.backgroundOpacity.toDouble()).toFloat().coerceIn(0.05f, 0.60f),
                backgroundBlurDp = json.optDouble("backgroundBlurDp", DEFAULT.backgroundBlurDp.toDouble()).toFloat().coerceIn(0f, 24f),
                backgroundScopeId = json.optString("backgroundScopeId", DEFAULT.backgroundScopeId),
                backgroundSpecificTabs = tabs
            )
        }

        fun parseHexColorOrDefault(hex: String, fallback: Color): Color {
            val clean = hex.trim()
            return runCatching {
                val normalized = if (clean.startsWith("#")) clean else "#$clean"
                Color(android.graphics.Color.parseColor(normalized))
            }.getOrDefault(fallback)
        }

        fun colorToHex(color: Color): String {
            val argb = color.toArgb()
            return String.format("#%06X", 0xFFFFFF and argb)
        }

        fun contrastRatio(fg: Color, bg: Color): Double {
            val l1 = fg.luminance().toDouble()
            val l2 = bg.luminance().toDouble()
            val lighter = maxOf(l1, l2)
            val darker = minOf(l1, l2)
            return (lighter + 0.05) / (darker + 0.05)
        }

        fun ensureReadableTextOn(preferredText: Color, background: Color): Color {
            if (contrastRatio(preferredText, background) >= 4.5) return preferredText
            return if (background.luminance() > 0.5f) TextPageTitle else PureWhite
        }

        fun resolveFontFamily(config: AppAppearanceConfig): FontFamily {
            return when (config.fontChoice) {
                AppFontChoice.DEFAULT_PLUS_JAKARTA -> PlusJakartaSans
                AppFontChoice.INTER -> InterFontFamily
                AppFontChoice.POPPINS -> PoppinsFontFamily
                AppFontChoice.ROBOTO -> RobotoFontFamily
                AppFontChoice.LORA -> LoraFontFamily
                AppFontChoice.JETBRAINS_MONO -> JetBrainsMono
                AppFontChoice.CUSTOM_FILE -> {
                    val file = File(config.customFontPath)
                    if (config.customFontPath.isNotBlank() && file.exists() && file.length() > 16L) {
                        runCatching {
                            val tf = Typeface.createFromFile(file)
                            if (tf != null) FontFamily(tf) else PlusJakartaSans
                        }.getOrDefault(PlusJakartaSans)
                    } else {
                        PlusJakartaSans
                    }
                }
            }
        }
    }
}

val LocalAppAppearance = staticCompositionLocalOf { AppAppearanceConfig.DEFAULT }

/**
 * Local-only persistence and asset validator for Customize App settings.
 * Stored exclusively on-device in SharedPreferences + filesDir (never touches transaction DB
 * and never uploads fonts/backgrounds to Google Drive without explicit user consent).
 */
object AppAppearancePreferences {
    private const val PREFS_NAME = "saf_appearance_prefs"
    private const val KEY_CONFIG_JSON = "appearance_config_json"

    fun load(context: Context): AppAppearanceConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CONFIG_JSON, null) ?: return AppAppearanceConfig.DEFAULT
        return runCatching { AppAppearanceConfig.fromJson(JSONObject(raw)) }
            .getOrDefault(AppAppearanceConfig.DEFAULT)
    }

    fun save(context: Context, config: AppAppearanceConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CONFIG_JSON, config.toJson().toString()).commit()
    }

    fun reset(context: Context): AppAppearanceConfig {
        save(context, AppAppearanceConfig.DEFAULT)
        return AppAppearanceConfig.DEFAULT
    }

    /**
     * Validates and copies a user-selected `.ttf` or `.otf` font file into app-local storage.
     * Verifies TrueType / OpenType magic header AND Android Typeface loading so corrupted
     * files are rejected cleanly without ever crashing the app.
     */
    fun importAndValidateCustomFont(
        context: Context,
        sourceUriOrFile: Any,
        suggestedFileName: String = "custom_font.ttf"
    ): Result<Pair<String, String>> {
        return try {
            val cleanName = suggestedFileName.ifBlank { "custom_font.ttf" }
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val ext = cleanName.substringAfterLast('.', "").lowercase()
            if (ext !in setOf("ttf", "otf")) {
                return Result.failure(IllegalArgumentException("Format font tidak didukung (.$ext). Hanya file .TTF dan .OTF yang diizinkan."))
            }
            val dir = File(context.filesDir, "custom_fonts").apply { mkdirs() }
            val destFile = File(dir, "font_${System.currentTimeMillis()}_$cleanName")

            when (sourceUriOrFile) {
                is File -> sourceUriOrFile.copyTo(destFile, overwrite = true)
                is Uri -> {
                    context.contentResolver.openInputStream(sourceUriOrFile)?.use { input ->
                        FileOutputStream(destFile).use { output -> input.copyTo(output) }
                    } ?: return Result.failure(IllegalArgumentException("Tidak dapat membuka file font."))
                }
                else -> return Result.failure(IllegalArgumentException("Sumber font tidak dikenali."))
            }

            if (!destFile.exists() || destFile.length() < 64L || destFile.length() > 15L * 1024L * 1024L) {
                destFile.delete()
                return Result.failure(IllegalArgumentException("Ukuran file font tidak valid (kosong atau terlalu besar)."))
            }

            // Validate SFNT magic header (TTF: 00 01 00 00 / 'true' / 'ttcf'; OTF: 'OTTO')
            val header = ByteArray(4)
            destFile.inputStream().use { it.read(header) }
            val isTtf = (header[0] == 0x00.toByte() && header[1] == 0x01.toByte() && header[2] == 0x00.toByte() && header[3] == 0x00.toByte()) ||
                String(header, Charsets.US_ASCII) in setOf("true", "ttcf", "OTTO")
            if (!isTtf) {
                destFile.delete()
                return Result.failure(IllegalArgumentException("File bukan font TrueType (.TTF) atau OpenType (.OTF) yang valid."))
            }

            // Verify Android Typeface loader does not reject it
            val loaded = runCatching { Typeface.createFromFile(destFile) }.getOrNull()
            if (loaded == null) {
                destFile.delete()
                return Result.failure(IllegalArgumentException("Font gagal dimuat oleh sistem Android. Pilih file .TTF/.OTF lain."))
            }

            Result.success(destFile.absolutePath to cleanName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Copies a chosen background image (Camera Bitmap or Gallery Uri) into local app storage.
     */
    fun saveCustomBackgroundImage(
        context: Context,
        source: Any
    ): Result<String> {
        return try {
            val dir = File(context.filesDir, "custom_backgrounds").apply { mkdirs() }
            val destFile = File(dir, "app_bg_${System.currentTimeMillis()}.jpg")
            when (source) {
                is android.graphics.Bitmap -> {
                    FileOutputStream(destFile).use { fos ->
                        source.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, fos)
                    }
                }
                is File -> source.copyTo(destFile, overwrite = true)
                is Uri -> {
                    context.contentResolver.openInputStream(source)?.use { input ->
                        FileOutputStream(destFile).use { output -> input.copyTo(output) }
                    } ?: return Result.failure(IllegalArgumentException("Tidak dapat membuka gambar background."))
                }
                else -> return Result.failure(IllegalArgumentException("Format gambar tidak didukung."))
            }
            if (!destFile.exists() || destFile.length() == 0L) {
                return Result.failure(IllegalArgumentException("Gagal menyimpan gambar background."))
            }
            Result.success(destFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
