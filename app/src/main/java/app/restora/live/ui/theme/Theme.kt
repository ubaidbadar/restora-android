package app.restora.live.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val RestoraColors = lightColorScheme(
    primary = Color(0xFF4F46E5), onPrimary = Color.White, primaryContainer = Color(0xFFEEF2FF),
    background = Color(0xFFF6F7F9), surface = Color.White, onSurface = Color(0xFF0F172A),
    onBackground = Color(0xFF0F172A), onSurfaceVariant = Color(0xFF5B6474),
    surfaceVariant = Color(0xFFF1F3F6), outline = Color(0xFF848D9C), error = Color(0xFFA3301F)
)
@Composable fun RestoraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RestoraColors, typography = Typography,
        shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp)), content = content)
}
