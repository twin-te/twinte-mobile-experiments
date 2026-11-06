package net.twinte.mobile_experiments

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val lightColors = lightColorScheme(
    primary = Color(0xFF176B57),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB4EED8),
    onPrimaryContainer = Color(0xFF002116),
    secondary = Color(0xFF4C626D),
    secondaryContainer = Color(0xFFCFE6F2),
    tertiary = Color(0xFF795900),
)

private val darkColors = darkColorScheme(
    primary = Color(0xFF98D2BD),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF00513E),
    onPrimaryContainer = Color(0xFFB4EED8),
    secondary = Color(0xFFB3CAD6),
    secondaryContainer = Color(0xFF344A54),
    tertiary = Color(0xFFF2C040),
)

@Composable
internal fun TwinteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = if (darkTheme) darkColors else lightColors, content = content)
}
