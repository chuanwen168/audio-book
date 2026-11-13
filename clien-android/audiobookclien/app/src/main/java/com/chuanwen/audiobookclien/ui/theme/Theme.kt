package com.chuanwen.audiobookclien.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// —— 品牌色（喜马拉雅橙 + 儿童应用的活泼配色）——
val XimalayaOrange = Color(0xFFFF6B3D)
val XimalayaOrangeDeep = Color(0xFFFF4D2E)
val WarmBackground = Color(0xFFFFF7F0)
val KidBlue = Color(0xFF4AA8FF)
val KidYellow = Color(0xFFFFC53D)
val KidGreen = Color(0xFF2ECC8F)
val TextPrimary = Color(0xFF333333)
val TextSecondary = Color(0xFF8A8A8A)
val CardWhite = Color(0xFFFFFFFF)
val DividerSoft = Color(0xFFF0E6DE)

private val LightColors = lightColorScheme(
    primary = XimalayaOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3D8),
    onPrimaryContainer = Color(0xFF7A2E14),
    secondary = KidBlue,
    onSecondary = Color.White,
    tertiary = KidGreen,
    onTertiary = Color.White,
    background = WarmBackground,
    onBackground = TextPrimary,
    surface = CardWhite,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFF3ECE6),
    onSurfaceVariant = TextSecondary,
    error = Color(0xFFE5484D)
)

@Composable
fun AudiobookTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography(),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(28.dp)
        ),
        content = content
    )
}
