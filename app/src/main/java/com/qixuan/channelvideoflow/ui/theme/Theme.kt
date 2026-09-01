package com.qixuan.channelvideoflow.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF2866F6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE7FF),
    onPrimaryContainer = Color(0xFF0A2A69),
    secondary = Color(0xFF7257D9),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEAE3FF),
    onSecondaryContainer = Color(0xFF27145F),
    tertiary = Color(0xFF168B79),
    onTertiary = Color.White,
    background = Color(0xFFF3F6FC),
    onBackground = Color(0xFF151A23),
    surface = Color(0xFFF9FAFF),
    onSurface = Color(0xFF151A23),
    surfaceVariant = Color(0xFFE8ECF5),
    onSurfaceVariant = Color(0xFF50596A),
    outline = Color(0xFF7C8699),
    outlineVariant = Color(0xFFC7CFDD),
    error = Color(0xFFBA3F52),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8EB2FF),
    onPrimary = Color(0xFF052B73),
    primaryContainer = Color(0xFF173E8B),
    onPrimaryContainer = Color(0xFFDCE7FF),
    secondary = Color(0xFFC9B8FF),
    onSecondary = Color(0xFF35206F),
    secondaryContainer = Color(0xFF49358A),
    onSecondaryContainer = Color(0xFFEAE3FF),
    tertiary = Color(0xFF72D8C2),
    onTertiary = Color(0xFF00382F),
    background = Color(0xFF07090E),
    onBackground = Color(0xFFF4F7FF),
    surface = Color(0xFF10131A),
    onSurface = Color(0xFFF4F7FF),
    surfaceVariant = Color(0xFF202631),
    onSurfaceVariant = Color(0xFFC3CAD8),
    outline = Color(0xFF8D96A8),
    outlineVariant = Color(0xFF38404D),
    error = Color(0xFFFF8797),
    onError = Color(0xFF650019),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 43.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
)

private val AppShapes = Shapes(
    extraSmall = ChannelVideoFlowTokens.Shapes.small,
    small = ChannelVideoFlowTokens.Shapes.control,
    medium = ChannelVideoFlowTokens.Shapes.medium,
    large = ChannelVideoFlowTokens.Shapes.large,
    extraLarge = ChannelVideoFlowTokens.Shapes.hero,
)

@Composable
fun ChannelVideoFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val glossColors = if (darkTheme) DarkGlossColors else LightGlossColors

    GlossColorsProvider(glossColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
