package app.restora.live.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val RestoraColors = lightColorScheme(
    primary = Color(0xFF4F46E5), onPrimary = Color.White,
    primaryContainer = Color(0xFFEEF2FF), onPrimaryContainer = Color(0xFF4338CA),
    secondary = Color(0xFF8F5A00), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFDF5E4), onSecondaryContainer = Color(0xFF7A4B00),
    background = Color(0xFFF6F7F9), onBackground = Color(0xFF0F172A),
    surface = Color.White, onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF5B6474), surfaceVariant = Color(0xFFF0F2F7),
    surfaceContainer = Color(0xFFF0F2F7), surfaceContainerLow = Color(0xFFF7F8FB),
    surfaceContainerHigh = Color(0xFFE9EDF4),
    outline = Color(0xFF929BAD), outlineVariant = Color(0xFFE3E7F0),
    error = Color(0xFFB13A48), errorContainer = Color(0xFFFFEEF0), onErrorContainer = Color(0xFF8B2937),
    tertiary = Color(0xFF23755B), tertiaryContainer = Color(0xFFE8F5EE), onTertiaryContainer = Color(0xFF1D624C),
)

@Composable fun RestoraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RestoraColors,
        typography = Typography,
        shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(28.dp)),
        content = content,
    )
}
