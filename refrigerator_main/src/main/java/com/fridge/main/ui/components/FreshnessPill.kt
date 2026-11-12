package com.fridge.main.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import com.fridge.main.ui.model.FreshnessStatus

/**
 * 新鲜度标签的统一配色：
 * 绿=新鲜、橙=临期、红=已过期。颜色与文字同时传达状态，避免只靠颜色区分。
 */
@Composable
fun FreshnessPill(
    status: FreshnessStatus,
    modifier: Modifier = Modifier,
) {
    val containerColor = when (status) {
        FreshnessStatus.FRESH -> MaterialTheme.colorScheme.primaryContainer
        FreshnessStatus.EXPIRING -> MaterialTheme.colorScheme.secondaryContainer
        FreshnessStatus.EXPIRED -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (status) {
        FreshnessStatus.FRESH -> MaterialTheme.colorScheme.onPrimaryContainer
        FreshnessStatus.EXPIRING -> MaterialTheme.colorScheme.onSecondaryContainer
        FreshnessStatus.EXPIRED -> MaterialTheme.colorScheme.onErrorContainer
    }
    MetaPill(
        text = status.label,
        containerColor = containerColor,
        contentColor = contentColor,
        modifier = modifier,
    )
}
