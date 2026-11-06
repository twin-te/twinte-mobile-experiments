package net.twinte.mobile_experiments.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

@Immutable
internal data class TwinteColors(
    val background: Color,
    val surfaceStart: Color,
    val surfaceEnd: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val accentText: Color,
    val onAccent: Color,
    val accentStart: Color,
    val accentEnd: Color,
    val course: Color,
    val empty: Color,
    val shadowLight: Color,
    val shadowDark: Color,
    val insetLight: Color,
    val insetDark: Color,
    val isDark: Boolean,
) {
    val surface: Brush get() = Brush.linearGradient(listOf(surfaceStart, surfaceEnd))
    val accentFill: Brush get() = Brush.horizontalGradient(listOf(accentStart, accentEnd))
}

private val lightColors = TwinteColors(
    background = Color(0xFFEFF1F6),
    surfaceStart = Color(0xFFF3F5FA),
    surfaceEnd = Color(0xFFE5E7ED),
    text = Color(0xFF626A76),
    muted = Color(0xFF838E95),
    accent = Color(0xFF00C0C0),
    accentText = Color(0xFF226D76),
    onAccent = Color(0xFF123F43),
    accentStart = Color(0xFF6BCEDC),
    accentEnd = Color(0xFF71E2DC),
    course = Color(0x99C8EFEF),
    empty = Color(0x66DEE6EC),
    shadowLight = Color(0xF7FFFFFF),
    shadowDark = Color(0x739BB1BF),
    insetLight = Color(0x73FFFFFF),
    insetDark = Color(0x3EAEBECD),
    isDark = false,
)

private val darkColors = TwinteColors(
    background = Color(0xFF1C222D),
    surfaceStart = Color(0xFF202530),
    surfaceEnd = Color(0xFF141821),
    text = Color(0xFFD4DAE3),
    muted = Color(0xFFADB8BD),
    accent = Color(0xFF19C6C6),
    accentText = Color(0xFF8DDCD9),
    onAccent = Color(0xFF061D21),
    accentStart = Color(0xFF3C919E),
    accentEnd = Color(0xFF499893),
    course = Color(0x69424C5A),
    empty = Color(0x284C5255),
    shadowLight = Color(0x5E516073),
    shadowDark = Color(0xE80B0C0D),
    insetLight = Color(0x513D435A),
    insetDark = Color(0xD612141B),
    isDark = true,
)

private val LocalColors = staticCompositionLocalOf { lightColors }

internal object TwinteTheme {
    val colors: TwinteColors @Composable get() = LocalColors.current
}

private val typography = Typography(
    bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 14.sp),
)

@Composable
internal fun TwinteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) darkColors else lightColors
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.accent, onPrimary = colors.background,
            secondary = colors.accentText, primaryContainer = colors.course,
            background = colors.background, surface = colors.background,
            surfaceContainer = colors.background, surfaceContainerHigh = colors.surfaceStart,
            onBackground = colors.text, onSurface = colors.text,
            onSurfaceVariant = colors.muted,
        )
    } else {
        lightColorScheme(
            primary = colors.accentText, onPrimary = Color.White,
            secondary = colors.accentText, primaryContainer = colors.course,
            background = colors.background, surface = colors.background,
            surfaceContainer = colors.background, surfaceContainerHigh = colors.surfaceStart,
            onBackground = colors.text, onSurface = colors.text,
            onSurfaceVariant = colors.muted,
        )
    }
    CompositionLocalProvider(LocalColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
