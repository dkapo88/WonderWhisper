package com.slumdog88.dictationkeyboardai.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

enum class AppThemeMode {
    System,
    Light,
    Dark,
    HighContrast,
    Neon,
    MidnightBlue,
    ForestGreen,
    Lavender,
    RetroTerminal
}

object ThemeManager {
    private const val PREFS_NAME = "keyboard_prefs"
    private const val KEY_THEME_MODE = "theme_mode"

    var themeMode by mutableStateOf(AppThemeMode.System)
        private set

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val modeName = prefs.getString(KEY_THEME_MODE, AppThemeMode.System.name)
        themeMode = try {
            AppThemeMode.valueOf(modeName ?: AppThemeMode.System.name)
        } catch (e: Exception) {
            AppThemeMode.System
        }
    }

    fun setMode(context: Context, mode: AppThemeMode) {
        themeMode = mode
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun getColors(isSystemDark: Boolean): KeyboardColors {
        return when (themeMode) {
            AppThemeMode.System -> if (isSystemDark) DarkColors else LightColors
            AppThemeMode.Light -> LightColors
            AppThemeMode.Dark -> DarkColors
            AppThemeMode.HighContrast -> HighContrastColors
            AppThemeMode.Neon -> NeonColors
            AppThemeMode.MidnightBlue -> MidnightBlueColors
            AppThemeMode.ForestGreen -> ForestGreenColors
            AppThemeMode.Lavender -> LavenderColors
            AppThemeMode.RetroTerminal -> RetroTerminalColors
        }
    }

    // --- Theme Definitions ---

    private val LightColors = KeyboardColors(
        keyboardBackground = LxxLightKeyboardBackground,
        keyBackground = LxxLightKeyBackground,
        keyPressedBackground = LxxLightKeyPressedBackground,
        keyTextPrimary = LxxLightKeyTextPrimary,
        keyTextSecondary = LxxLightKeyTextSecondary,
        functionalKeyBackground = LxxLightFunctionalKeyBackground,
        functionalKeyPressedBackground = LxxLightFunctionalKeyPressedBackground,
        functionalKeyText = LxxLightFunctionalKeyText,
        accent = LxxLightAccent,
        onAccent = Color.White,
        micIdleBackground = LxxLightMicIdle,
        micActiveBackground = LxxLightMicActive,
        micIconColor = Color.White,
        keyPreviewBackground = Color(0xFFD1D6D9)
    )

    private val DarkColors = KeyboardColors(
        keyboardBackground = LxxDarkKeyboardBackground,
        keyBackground = LxxDarkKeyBackground,
        keyPressedBackground = LxxDarkKeyPressedBackground,
        keyTextPrimary = LxxDarkKeyTextPrimary,
        keyTextSecondary = LxxDarkKeyTextSecondary,
        functionalKeyBackground = LxxDarkFunctionalKeyBackground,
        functionalKeyPressedBackground = LxxDarkFunctionalKeyPressedBackground,
        functionalKeyText = LxxDarkFunctionalKeyText,
        accent = LxxDarkAccent,
        onAccent = Color.Black,
        micIdleBackground = LxxDarkMicIdle,
        micActiveBackground = LxxDarkMicActive,
        micIconColor = Color.White,
        keyPreviewBackground = Color(0xFF3D484D)
    )

    private val HighContrastColors = KeyboardColors(
        keyboardBackground = Color.Black,
        keyBackground = Color.Black,
        keyPressedBackground = Color.DarkGray,
        keyTextPrimary = Color.White,
        keyTextSecondary = Color.Yellow,
        functionalKeyBackground = Color(0xFF1A1A1A),
        functionalKeyPressedBackground = Color.Gray,
        functionalKeyText = Color.Yellow,
        accent = Color.Yellow,
        onAccent = Color.Black,
        micIdleBackground = Color.DarkGray,
        micActiveBackground = Color.Red,
        micIconColor = Color.White,
        keyPreviewBackground = Color.DarkGray
    )

    private val NeonColors = KeyboardColors(
        keyboardBackground = Color(0xFF0F0F1A),
        keyBackground = Color(0xFF1F1F33),
        keyPressedBackground = Color(0xFF3D3D5C),
        keyTextPrimary = Color(0xFF00FF9D), // Neon Green
        keyTextSecondary = Color(0xFFB300FF), // Neon Purple
        functionalKeyBackground = Color(0xFF2A2A40),
        functionalKeyPressedBackground = Color(0xFF3D3D5C),
        functionalKeyText = Color(0xFFFF00FF), // Magenta
        accent = Color(0xFF00E5FF), // Cyan
        onAccent = Color.Black,
        micIdleBackground = Color(0xFF2A2A40),
        micActiveBackground = Color(0xFFFF0055),
        micIconColor = Color.White,
        keyPreviewBackground = Color(0xFF2A2A40)
    )

    private val MidnightBlueColors = KeyboardColors(
        keyboardBackground = Color(0xFF1A237E), // Deep Blue
        keyBackground = Color(0xFF283593),
        keyPressedBackground = Color(0xFF3949AB),
        keyTextPrimary = Color(0xFFE8EAF6),
        keyTextSecondary = Color(0xFF9FA8DA),
        functionalKeyBackground = Color(0xFF303F9F),
        functionalKeyPressedBackground = Color(0xFF3949AB),
        functionalKeyText = Color(0xFFC5CAE9),
        accent = Color(0xFF5C6BC0), // Lighter Indigo
        onAccent = Color.White,
        micIdleBackground = Color(0xFF303F9F),
        micActiveBackground = Color(0xFFEF5350),
        micIconColor = Color.White,
        keyPreviewBackground = Color(0xFF283593)
    )

    private val ForestGreenColors = KeyboardColors(
        keyboardBackground = Color(0xFF1B5E20), // Dark Green
        keyBackground = Color(0xFF2E7D32),
        keyPressedBackground = Color(0xFF388E3C),
        keyTextPrimary = Color(0xFFE8F5E9),
        keyTextSecondary = Color(0xFFA5D6A7),
        functionalKeyBackground = Color(0xFF388E3C),
        functionalKeyPressedBackground = Color(0xFF4CAF50),
        functionalKeyText = Color(0xFFC8E6C9),
        accent = Color(0xFF66BB6A),
        onAccent = Color.White,
        micIdleBackground = Color(0xFF388E3C),
        micActiveBackground = Color(0xFFFFCA28),
        micIconColor = Color.White,
        keyPreviewBackground = Color(0xFF2E7D32)
    )

    private val LavenderColors = KeyboardColors(
        keyboardBackground = Color(0xFFF3E5F5), // Very Light Purple
        keyBackground = Color.White,
        keyPressedBackground = Color(0xFFE1BEE7),
        keyTextPrimary = Color(0xFF4A148C), // Dark Purple Text
        keyTextSecondary = Color(0xFF8E24AA),
        functionalKeyBackground = Color(0xFFE1BEE7),
        functionalKeyPressedBackground = Color(0xFFCE93D8),
        functionalKeyText = Color(0xFF6A1B9A),
        accent = Color(0xFFAB47BC),
        onAccent = Color.White,
        micIdleBackground = Color(0xFFE1BEE7),
        micActiveBackground = Color(0xFFFF7043),
        micIconColor = Color.White,
        keyPreviewBackground = Color(0xFFE1BEE7)
    )

    private val RetroTerminalColors = KeyboardColors(
        keyboardBackground = Color(0xFF000000),
        keyBackground = Color(0xFF000000),
        keyPressedBackground = Color(0xFF003300),
        keyTextPrimary = Color(0xFF00FF00), // Bright Green
        keyTextSecondary = Color(0xFF008000),
        functionalKeyBackground = Color(0xFF001100),
        functionalKeyPressedBackground = Color(0xFF003300),
        functionalKeyText = Color(0xFF00FF00),
        accent = Color(0xFF00FF00),
        onAccent = Color.Black,
        micIdleBackground = Color(0xFF001100),
        micActiveBackground = Color(0xFF003300),
        micIconColor = Color(0xFF00FF00),
        keyPreviewBackground = Color(0xFF001100)
    )
}
