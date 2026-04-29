package com.slumdog88.dictationkeyboardai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class KeyboardColors(
    val keyboardBackground: Color,
    val keyBackground: Color,
    val keyPressedBackground: Color,
    val keyTextPrimary: Color,
    val keyTextSecondary: Color,
    val functionalKeyBackground: Color,
    val functionalKeyPressedBackground: Color,
    val functionalKeyText: Color,
    val accent: Color,
    val onAccent: Color,
    val micIdleBackground: Color,
    val micActiveBackground: Color,
    val micIconColor: Color,
    val keyPreviewBackground: Color
)

private val LocalKeyboardColors = staticCompositionLocalOf {
    KeyboardColors(
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
}

object KeyboardPalette {
    val colors: KeyboardColors
        @Composable
        get() = LocalKeyboardColors.current
}

private val DarkColorScheme = darkColorScheme(
    primary = LxxDarkAccent,
    secondary = LxxDarkAccent,
    background = LxxDarkKeyboardBackground,
    surface = LxxDarkKeyBackground,
    surfaceVariant = LxxDarkFunctionalKeyBackground,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = LxxDarkKeyTextPrimary,
    onSurface = LxxDarkKeyTextPrimary,
    onSurfaceVariant = LxxDarkKeyTextSecondary,
    outline = LxxDarkKeyTextSecondary,
    error = AccentError
)

private val LightColorScheme = lightColorScheme(
    primary = LxxLightAccent,
    secondary = LxxLightAccent,
    background = LxxLightKeyboardBackground,
    surface = LxxLightKeyBackground,
    surfaceVariant = LxxLightFunctionalKeyBackground,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = LxxLightKeyTextPrimary,
    onSurface = LxxLightKeyTextPrimary,
    onSurfaceVariant = LxxLightKeyTextSecondary,
    outline = LxxLightKeyTextSecondary,
    error = AccentError
)

@Composable
fun KeyboardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Ensure theme is loaded (idempotent if already loaded or handled in Service)
    // But for preview/composable context, we might want to trigger a load or assume it's done.
    // Since ThemeManager is a singleton, it holds state. 
    // We rely on Service or Activity calling ThemeManager.load().
    
    val palette = ThemeManager.getColors(darkTheme)
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalKeyboardColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
