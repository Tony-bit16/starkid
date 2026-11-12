package com.fridge.dashboard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DashboardGreen = Color(0xFF2F6B4F)
private val DashboardOrange = Color(0xFFD9822B)

private val LightColors = lightColorScheme(
    primary = DashboardGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7EBDD),
    onPrimaryContainer = Color(0xFF123523),
    secondary = DashboardOrange,
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

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8D5B7),
    onPrimary = Color(0xFF123523),
    primaryContainer = Color(0xFF17452F),
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
 * 看板主题与主 App 保持同一套绿/橙语义色，但不依赖主 App 的资源或代码。
 * 两个 APK 完全独立，只有后续的 ContentProvider 数据契约是共享边界。
 */
@Composable
fun DashboardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
