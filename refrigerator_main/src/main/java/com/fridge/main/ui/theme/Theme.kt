package com.fridge.main.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 主色：冷藏绿，用于导航、主按钮和强调信息。 */
private val FridgeGreen = Color(0xFF2F6B4F)
private val FridgeGreenDark = Color(0xFF17452F)

/** 辅助色：成熟橙，用于“即将过期”等警示信息。 */
private val FreshOrange = Color(0xFFD9822B)

private val LightColors = lightColorScheme(
    primary = FridgeGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7EBDD),
    onPrimaryContainer = Color(0xFF123523),
    secondary = FreshOrange,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE4C4),
    onSecondaryContainer = Color(0xFF4A2600),
    background = Color(0xFFFAFAF7),
    onBackground = Color(0xFF1A1C19),
    surface = Color.White,
    onSurface = Color(0xFF1A1C19),
    surfaceVariant = Color(0xFFE6EAE3),
    onSurfaceVariant = Color(0xFF444841),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

/** 统一圆角：卡片、输入框、按钮共用一套尺度，避免各页面圆角不一致。 */
private val FridgeShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8D5B7),
    onPrimary = Color(0xFF123523),
    primaryContainer = FridgeGreenDark,
    onPrimaryContainer = Color(0xFFD7EBDD),
    secondary = Color(0xFFFFB86B),
    onSecondary = Color(0xFF4A2600),
    background = Color(0xFF111411),
    onBackground = Color(0xFFE2E3DD),
    surface = Color(0xFF1A1E1A),
    onSurface = Color(0xFFE2E3DD),
    surfaceVariant = Color(0xFF444841),
    onSurfaceVariant = Color(0xFFC4C8C0),
)

/**
 * 主 App 的 Compose 主题。
 *
 * 不启用 Material You 动态取色：食材新鲜度依赖固定的绿/橙语义色，
 * 跟随壁纸变化会削弱“安全 / 临期”的可读性。
 */
@Composable
fun FridgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = FridgeShapes,
        content = content,
    )
}
