package app.suical.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF15302A)
private val Yolk = Color(0xFFF2B705)

private val Light = lightColorScheme(
    primary = Ink, onPrimary = Color(0xFFEEF2EF),
    secondary = Yolk, onSecondary = Ink,
    tertiary = Color(0xFF2F7A5B),
    background = Color(0xFFEEF2EF), onBackground = Ink,
    surface = Color.White, onSurface = Ink,
    surfaceVariant = Color(0xFFE2E9E5), onSurfaceVariant = Color(0xFF5B6E68),
    outline = Color(0xFFD3DDD8), outlineVariant = Color(0xFFD3DDD8),
    error = Color(0xFFC8412F), errorContainer = Color(0xFFFBE9E6), onErrorContainer = Color(0xFFC8412F),
    primaryContainer = Yolk, onPrimaryContainer = Ink,
)

private val Dark = darkColorScheme(
    primary = Color(0xFFE4EEEA), onPrimary = Color(0xFF0F1E1B),
    secondary = Yolk, onSecondary = Ink,
    tertiary = Color(0xFF6CC49D),
    background = Color(0xFF0F1E1B), onBackground = Color(0xFFE4EEEA),
    surface = Color(0xFF162925), onSurface = Color(0xFFE4EEEA),
    surfaceVariant = Color(0xFF1D332E), onSurfaceVariant = Color(0xFF93A7A0),
    outline = Color(0xFF28403A), outlineVariant = Color(0xFF28403A),
    error = Color(0xFFF06A55), errorContainer = Color(0xFF3A1F1B), onErrorContainer = Color(0xFFF06A55),
    primaryContainer = Yolk, onPrimaryContainer = Ink,
)

@Composable
fun SuicalTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
