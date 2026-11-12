package ir.cabinetplan.fa.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF176B57),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8F0D8),
    onPrimaryContainer = Color(0xFF002117),
    background = Color(0xFFF7F9F5),
    surface = Color(0xFFF7F9F5),
    surfaceVariant = Color(0xFFDDE5DD),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF96D5BD),
    onPrimary = Color(0xFF003829),
    primaryContainer = Color(0xFF00513D),
    background = Color(0xFF101512),
    surface = Color(0xFF101512),
    surfaceVariant = Color(0xFF3F4942),
)

@Composable
fun CabinetPlanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
