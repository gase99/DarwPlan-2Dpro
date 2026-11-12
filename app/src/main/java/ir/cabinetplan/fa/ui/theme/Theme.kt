package ir.cabinetplan.fa.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object CadPalette {
    val BgCanvas = Color(0xFF0F141C)
    val BgDeep = Color(0xFF0B0F16)
    val BgSurface = Color(0xFF1A2230)
    val BgElevated = Color(0xFF232E40)
    val BorderHairline = Color(0xFF2E3A4E)
    val GridMinor = Color(0xFF17202E)
    val GridMajor = Color(0xFF223046)
    val AccentOak = Color(0xFFE09F3E)
    val AccentAmber = Color(0xFFFBBF24)
    val AccentCyan = Color(0xFF38BDF8)
    val AccentEmerald = Color(0xFF10B981)
    val AccentPurple = Color(0xFFC084FC)
    val AccentRed = Color(0xFFEF4444)
    val TextPrimary = Color(0xFFF1F5F9)
    val TextSecondary = Color(0xFFCBD5E1)
    val TextMuted = Color(0xFF94A3B8)
}

private val BlueprintColorScheme = darkColorScheme(
    primary = CadPalette.AccentOak,
    onPrimary = CadPalette.BgCanvas,
    primaryContainer = Color(0xFF3B2E1A),
    onPrimaryContainer = CadPalette.AccentAmber,
    secondary = CadPalette.AccentCyan,
    onSecondary = CadPalette.BgCanvas,
    secondaryContainer = Color(0xFF132D42),
    onSecondaryContainer = CadPalette.AccentCyan,
    tertiary = CadPalette.AccentEmerald,
    onTertiary = CadPalette.BgCanvas,
    background = CadPalette.BgCanvas,
    onBackground = CadPalette.TextPrimary,
    surface = CadPalette.BgSurface,
    onSurface = CadPalette.TextPrimary,
    surfaceVariant = CadPalette.BgElevated,
    onSurfaceVariant = CadPalette.TextSecondary,
    outline = CadPalette.BorderHairline,
    outlineVariant = CadPalette.GridMajor,
    error = CadPalette.AccentRed,
)

@Composable
fun CabinetPlanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BlueprintColorScheme,
        content = content,
    )
}
