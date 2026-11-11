package computer.kolektiv.hold

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object HoldThemeColors {
    val bg = Color(0xFF12140F)
    val fg = Color(0xFFEBE6D6)
    val muted = Color(0xFFA39E8E)
    val accent = Color(0xFFE0A106)
}

@Composable
fun HoldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = HoldThemeColors.accent,
            onPrimary = HoldThemeColors.bg,
            background = HoldThemeColors.bg,
            onBackground = HoldThemeColors.fg,
            surface = Color(0xFF1C2118),
            onSurface = HoldThemeColors.fg,
            surfaceVariant = Color(0xFF1C2118),
            onSurfaceVariant = HoldThemeColors.muted,
        ),
        content = content,
    )
}
