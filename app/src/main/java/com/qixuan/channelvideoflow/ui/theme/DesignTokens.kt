package com.qixuan.channelvideoflow.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object ChannelVideoFlowTokens {
    object Spacing {
        val xSmall = 4.dp
        val small = 8.dp
        val medium = 12.dp
        val large = 16.dp
        val xLarge = 24.dp
        val hero = 32.dp
    }

    object Shapes {
        val small = RoundedCornerShape(12.dp)
        val control = RoundedCornerShape(16.dp)
        val medium = RoundedCornerShape(22.dp)
        val large = RoundedCornerShape(26.dp)
        val hero = RoundedCornerShape(30.dp)
        val pill = RoundedCornerShape(50)
    }

    object Elevation {
        val card = 5.dp
        val floating = 9.dp
        val border = 1.dp
    }

    object Sizes {
        val icon = 20.dp
        val touchTarget = 48.dp
        val quickAction = 72.dp
        val primaryAction = 54.dp
    }

    object Motion {
        const val pressInMillis = 90
        const val pressOutMillis = 120
        const val stateChangeMillis = 200
        const val surfaceMillis = 220
        const val contentEnterMillis = 280
        const val loadingDisclosureMillis = 420L
        const val pressedScale = 0.985f
    }

    object Feed {
        val obsidian = Color(0xFF07090E)
        val graphite = Color(0xFF10131A)
        val elevatedGraphite = Color(0xFF171B24)
        val overlay = Color(0xA3121720)
        val outline = Color(0x2EFFFFFF)
        val iceText = Color(0xFFF4F7FF)
        val secondaryText = Color(0xBFD6DCE8)
        val electricBlue = Color(0xFF5B8CFF)
    }
}

@Immutable
data class GlossColors(
    val backdropStart: Color,
    val backdropMiddle: Color,
    val backdropEnd: Color,
    val surface: Color,
    val surfaceStrong: Color,
    val border: Color,
    val highlight: Color,
    val accentGlow: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
)

internal val LightGlossColors = GlossColors(
    backdropStart = Color(0xFFF8FAFF),
    backdropMiddle = Color(0xFFF0F4FC),
    backdropEnd = Color(0xFFE9EEFA),
    surface = Color(0xE8FFFFFF),
    surfaceStrong = Color(0xFFF9FBFF),
    border = Color(0x70FFFFFF),
    highlight = Color(0xFFFFFFFF),
    accentGlow = Color(0x242866F6),
    success = Color(0xFF168B79),
    warning = Color(0xFF9B6911),
    danger = Color(0xFFBA3F52),
)

internal val DarkGlossColors = GlossColors(
    backdropStart = Color(0xFF171B24),
    backdropMiddle = Color(0xFF0F131B),
    backdropEnd = Color(0xFF07090E),
    surface = Color(0xD9171B24),
    surfaceStrong = Color(0xFF1B202A),
    border = Color(0x2EFFFFFF),
    highlight = Color(0x26FFFFFF),
    accentGlow = Color(0x335B8CFF),
    success = Color(0xFF72D8C2),
    warning = Color(0xFFFFC66A),
    danger = Color(0xFFFF8797),
)

private val LocalGlossColors = staticCompositionLocalOf { LightGlossColors }

val glossColors: GlossColors
    @Composable get() = LocalGlossColors.current

@Composable
internal fun GlossColorsProvider(
    colors: GlossColors,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalGlossColors provides colors, content = content)
}
