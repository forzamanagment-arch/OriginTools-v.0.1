package com.origintools.app.ui.theme

import android.graphics.Typeface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat

private val OriginColors = darkColorScheme(
    primary = Color(0xFF38D45A),
    onPrimary = Color(0xFF071A0B),
    secondary = Color(0xFF78A9FF),
    background = Color(0xFF090A0B),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF171819),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF222426),
    onSurfaceVariant = Color(0xFFB7BBC0),
    error = Color(0xFFF23D3D)
)

@Composable
fun OriginTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val vivoFamily = remember(context) {
        val regularId = context.resources.getIdentifier("vivo_sans_regular", "font", context.packageName)
        val regular = if (regularId != 0) ResourcesCompat.getFont(context, regularId) else null
        regular?.let { FontFamily(it) } ?: FontFamily.SansSerif
    }
    val typography = remember(vivoFamily) {
        Typography(
            headlineLarge = TextStyle(fontFamily = vivoFamily, fontWeight = FontWeight.Black, fontSize = 30.sp, lineHeight = 36.sp),
            headlineMedium = TextStyle(fontFamily = vivoFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
            titleLarge = TextStyle(fontFamily = vivoFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
            titleMedium = TextStyle(fontFamily = vivoFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
            bodyLarge = TextStyle(fontFamily = vivoFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontFamily = vivoFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
            labelLarge = TextStyle(fontFamily = vivoFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        )
    }
    MaterialTheme(colorScheme = OriginColors, typography = typography, content = content)
}
