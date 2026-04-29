package com.slumdog88.dictationkeyboardai.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ========== Design Tokens ==========

// Base palette (pastel accents)
val PastelBlue   = Color(0xFF74C0FC)
val PastelPurple = Color(0xFFB197FC)
val PastelPink   = Color(0xFFFFAFD2)
val PastelGreen  = Color(0xFF8CE99A)
val PastelOrange = Color(0xFFFFD8A8)

// Surfaces and text
val Bg       = Color(0xFF0E0F13)
val Surface1 = Color(0xFF151821)
val Surface2 = Color(0xFF1B1F2B)
val Surface3 = Color(0xFF232838)
val TextHi   = Color(0xFFE8EAF0)
val TextDim  = Color(0xFFB7B9C4)
val Border   = Color(0xFF2B3145)

// Radii
val RadiusSm = 10.dp
val RadiusMd = 16.dp
val RadiusLg = 22.dp

// Elevations (if needed in components)
object Elevations {
    val Level1 = 1.dp
    val Level2 = 3.dp
    val Level3 = 6.dp
    val Level4 = 12.dp
}

// ========== Theme ==========

val DarkMaterialColors = darkColorScheme(
    primary = PastelBlue,
    secondary = PastelPurple,
    tertiary = PastelPink,
    background = Bg,
    surface = Surface1,
    surfaceVariant = Surface2,
    onBackground = TextHi,
    onSurface = TextHi,
    onSurfaceVariant = TextDim,
    outline = Border
)

val Radii = Shapes(
    small = RoundedCornerShape(RadiusSm),
    medium = RoundedCornerShape(RadiusMd),
    large = RoundedCornerShape(RadiusLg)
)

val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 16.sp),
    bodySmall = TextStyle(fontSize = 12.sp)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkMaterialColors,
        typography = AppTypography,
        shapes = Radii,
        content = content
    )
}