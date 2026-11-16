package com.kelaslive.admin

import android.content.Context

/** Local theme preferences. Built-in palettes and the user's custom palette are stored separately. */
data class AppTheme(
    val id: String,
    val background: Int,
    val primary: Int,
    val button: Int,
    val text: Int,
    val card: Int,
    val navigation: Int,
    val cornerRadius: Float = 16f,
    val textScale: Float = 1f,
    val wallpaperUri: String = "",
    val logoUri: String = ""
)

class ThemeManager(context: Context) {
    private val prefs = context.getSharedPreferences("kelaslive_admin_theme", Context.MODE_PRIVATE)

    fun builtInThemes(): List<AppTheme> = listOf(
        AppTheme("dark", 0xFF12151C.toInt(), 0xFF8B9CFF.toInt(), 0xFF536DFE.toInt(),
            0xFFF3F4F8.toInt(), 0xFF202631.toInt(), 0xFF171C25.toInt()),
        AppTheme("light", 0xFFF4F6FA.toInt(), 0xFF234B70.toInt(), 0xFF28679B.toInt(),
            0xFF172B3D.toInt(), 0xFFFFFFFF.toInt(), 0xFFE6ECF3.toInt()),
        AppTheme("purple", 0xFFF5F0FF.toInt(), 0xFF7138B8.toInt(), 0xFF8146C7.toInt(),
            0xFF302044.toInt(), 0xFFFFFFFF.toInt(), 0xFFE9DDF8.toInt()),
        AppTheme("premium", 0xFF101010.toInt(), 0xFFE2BD63.toInt(), 0xFFB58A35.toInt(),
            0xFFFFF4D6.toInt(), 0xFF24211A.toInt(), 0xFF18150F.toInt())
    )

    fun load(): AppTheme {
        val id = prefs.getString("theme_id", "dark") ?: "dark"
        val base = if (id == "custom") loadCustom() else
            builtInThemes().firstOrNull { it.id == id } ?: builtInThemes().first()
        return base.copy(
            wallpaperUri = prefs.getString("wallpaper_uri", "") ?: "",
            logoUri = prefs.getString("logo_uri", "") ?: ""
        )
    }

    /** Returns the saved custom palette even while a built-in theme is selected. */
    fun loadCustom(): AppTheme {
        val defaults = customDefaults()
        // Read legacy keys only when the previous app version had Custom Theme selected.
        val legacyCustom = prefs.getString("theme_id", "dark") == "custom"
        return defaults.copy(
            background = prefs.getInt("custom_background", if (legacyCustom) prefs.getInt("background", defaults.background) else defaults.background),
            primary = prefs.getInt("custom_primary", if (legacyCustom) prefs.getInt("primary", defaults.primary) else defaults.primary),
            button = prefs.getInt("custom_button", if (legacyCustom) prefs.getInt("button", defaults.button) else defaults.button),
            text = prefs.getInt("custom_text", if (legacyCustom) prefs.getInt("text", defaults.text) else defaults.text),
            card = prefs.getInt("custom_card", if (legacyCustom) prefs.getInt("card", defaults.card) else defaults.card),
            navigation = prefs.getInt("custom_navigation", if (legacyCustom) prefs.getInt("navigation", defaults.navigation) else defaults.navigation),
            cornerRadius = prefs.getFloat("custom_corner_radius", if (legacyCustom) prefs.getFloat("corner_radius", defaults.cornerRadius) else defaults.cornerRadius),
            textScale = prefs.getFloat("custom_text_scale", if (legacyCustom) prefs.getFloat("text_scale", defaults.textScale) else defaults.textScale),
            wallpaperUri = prefs.getString("wallpaper_uri", "") ?: "",
            logoUri = prefs.getString("logo_uri", "") ?: ""
        )
    }

    fun save(theme: AppTheme) {
        val editor = prefs.edit().putString("theme_id", theme.id)
            .putString("wallpaper_uri", theme.wallpaperUri)
            .putString("logo_uri", theme.logoUri)
        if (theme.id == "custom") {
            editor.putInt("custom_background", theme.background)
                .putInt("custom_primary", theme.primary)
                .putInt("custom_button", theme.button)
                .putInt("custom_text", theme.text)
                .putInt("custom_card", theme.card)
                .putInt("custom_navigation", theme.navigation)
                .putFloat("custom_corner_radius", theme.cornerRadius)
                .putFloat("custom_text_scale", theme.textScale)
        }
        editor.apply()
    }

    fun saveImageUris(wallpaperUri: String? = null, logoUri: String? = null) {
        val editor = prefs.edit()
        if (wallpaperUri != null) editor.putString("wallpaper_uri", wallpaperUri)
        if (logoUri != null) editor.putString("logo_uri", logoUri)
        editor.apply()
    }

    fun customDefaults(): AppTheme = AppTheme(
        "custom", 0xFFF4F6FA.toInt(), 0xFF7138B8.toInt(), 0xFF8146C7.toInt(),
        0xFF172B3D.toInt(), 0xFFFFFFFF.toInt(), 0xFFE9DDF8.toInt()
    )
}
