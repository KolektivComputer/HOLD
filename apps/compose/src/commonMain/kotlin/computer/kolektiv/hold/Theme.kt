package computer.kolektiv.hold

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object HoldThemeColors {
    val bg = Color(0xFF12140F)
    val fg = Color(0xFFEBE6D6)
    val muted = Color(0xFFA39E8E)
    val accent = Color(0xFFE0A106)
    val surface = Color(0xFF1C2118)
}

@Composable
fun HoldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = HoldThemeColors.accent,
            onPrimary = HoldThemeColors.bg,
            background = HoldThemeColors.bg,
            onBackground = HoldThemeColors.fg,
            surface = HoldThemeColors.surface,
            onSurface = HoldThemeColors.fg,
            surfaceVariant = HoldThemeColors.surface,
            onSurfaceVariant = HoldThemeColors.muted,
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(32.dp),
        ),
        content = content,
    )
}