package app.legwork.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Legwork {
    val Bg = Color(0xFFFAF7F2)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceAlt = Color(0xFFF3EEE6)
    val Ink = Color(0xFF17130F)
    val Muted = Color(0xFF6E665D)
    val Line = Color(0xFFE8E1D8)
    val Accent = Color(0xFFFF5A1F)
    val AccentSoft = Color(0xFFFFE9DF)
    val Money = Color(0xFF0E9F6E)
    val MoneySoft = Color(0xFFDDF5EA)
    val Seeker = Color(0xFF7C3AED)
    val SeekerSoft = Color(0xFFEDE4FF)
    val Warn = Color(0xFFD97706)
    val WarnSoft = Color(0xFFFFF1DB)
    val Danger = Color(0xFFDC2626)
    val DangerSoft = Color(0xFFFEE2E2)
    val Sky = Color(0xFF2563EB)
    val SkySoft = Color(0xFFDBEAFE)
}

private val scheme = lightColorScheme(
    primary = Legwork.Accent,
    onPrimary = Color.White,
    primaryContainer = Legwork.AccentSoft,
    onPrimaryContainer = Legwork.Ink,
    secondary = Legwork.Money,
    onSecondary = Color.White,
    secondaryContainer = Legwork.MoneySoft,
    tertiary = Legwork.Seeker,
    tertiaryContainer = Legwork.SeekerSoft,
    background = Legwork.Bg,
    onBackground = Legwork.Ink,
    surface = Legwork.Surface,
    onSurface = Legwork.Ink,
    surfaceVariant = Legwork.SurfaceAlt,
    onSurfaceVariant = Legwork.Muted,
    outline = Legwork.Line,
    outlineVariant = Legwork.Line,
    error = Legwork.Danger,
    errorContainer = Legwork.DangerSoft,
)

private val type = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-1).sp),
    displayMedium = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.8).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 24.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.4.sp),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun LegworkTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, shapes = shapes, content = content)
}
