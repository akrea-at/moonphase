package at.akrea.moonphase.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

object Sky {
    val Night = Color(0xFF070A12)
    val Panel = Color(0xFF0E1424)
    val PanelHigh = Color(0xFF18213A)
    val Line = Color(0xFF242E4A)
    val Moonlight = Color(0xFFEDE4CF)
    val Text = Color(0xFFE4E7EF)
    val Muted = Color(0xFF8B93A7)
    val NewMoon = Color(0xFF93B4FF)
    val FullMoon = Color(0xFFF3C978)
    val Error = Color(0xFFFF9C8F)
}

private val colors = darkColorScheme(
    primary = Sky.Moonlight,
    onPrimary = Sky.Night,
    secondary = Sky.NewMoon,
    tertiary = Sky.FullMoon,
    background = Sky.Night,
    onBackground = Sky.Text,
    surface = Sky.Night,
    onSurface = Sky.Text,
    surfaceVariant = Sky.Panel,
    onSurfaceVariant = Sky.Muted,
    surfaceContainerLowest = Sky.Night,
    surfaceContainerLow = Sky.Panel,
    surfaceContainer = Sky.Panel,
    surfaceContainerHigh = Sky.PanelHigh,
    surfaceContainerHighest = Sky.PanelHigh,
    outline = Sky.Line,
    outlineVariant = Sky.Line,
    error = Sky.Error,
)

private val base = Typography()

private val typography = base.copy(
    headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Serif, letterSpacing = 0.3.sp),
    titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Serif),
    titleMedium = base.titleMedium.copy(fontFamily = FontFamily.Serif),
)

@Composable
fun MoonTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
