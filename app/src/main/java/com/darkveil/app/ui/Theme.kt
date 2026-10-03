package com.darkveil.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 暗纱配色 —— 夜之屏的微距视界。
 * 背景 = LCD 黑电平；面板 = 深焊板；唯一合成色 = 像素品红。
 * 文字 = 辉光白与冷灰蓝（均从场景色相派生，不用纯灰）。 */
val Ink = Color(0xFF0B0D10)          // LCD 黑电平
val Panel = Color(0xFF12161B)        // 面板
val PanelUp = Color(0xFF1A2027)      // 抬升面板
val PixelPink = Color(0xFFE85C96)    // 合成色：像素品红
val GlowWhite = Color(0xFFE8ECF1)    // 辉光白
val CoolGray = Color(0xFF8A93A0)     // 冷灰蓝
val HairLine = Color(0xFF2A3138)     // 发丝线

private val DarkVeilScheme = darkColorScheme(
    background = Ink,
    surface = Panel,
    surfaceVariant = PanelUp,
    primary = PixelPink,
    onPrimary = Ink,
    onBackground = GlowWhite,
    onSurface = GlowWhite,
    onSurfaceVariant = CoolGray,
    outline = HairLine,
    error = Color(0xFFFF6B6B),
    onError = Ink,
)

/** 类型：系统字承担正文与标签；品牌与读数由自制位图字形（PixelGlyphs）承担。 */
private val VeilTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = 0.2.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.2.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyMedium = TextStyle(fontSize = 13.5.sp, lineHeight = 21.sp, letterSpacing = 0.1.sp),
    bodySmall = TextStyle(fontSize = 11.5.sp, lineHeight = 17.sp, letterSpacing = 0.15.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.6.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.6.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.8.sp),
)

private val VeilShapes = Shapes(
    extraSmall = RoundedCornerShape(3.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(10.dp),
)

@Composable
fun DarkVeilTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkVeilScheme,
        typography = VeilTypography,
        shapes = VeilShapes,
        content = content
    )
}
