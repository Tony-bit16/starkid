package com.fridge.main.ui.screens.recipes

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fridge.main.domain.matching.RecipeMatchLevel

/**
 * 匹配等级标签。颜色语义：
 * - 绿色：食材齐全；
 * - 橙色：只缺一种；
 * - 红色：缺多种，需要先补货。
 */
@Composable
fun RecipeMatchBadge(
    level: RecipeMatchLevel,
    modifier: Modifier = Modifier,
) {
    val colors = when (level) {
        RecipeMatchLevel.FULLY_COOKABLE -> MatchBadgeColors(
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        RecipeMatchLevel.MISSING_ONE -> MatchBadgeColors(
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        RecipeMatchLevel.MISSING_MANY -> MatchBadgeColors(
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
        )
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = colors.container,
        contentColor = colors.content,
    ) {
        Text(
            text = level.label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

private data class MatchBadgeColors(
    val container: Color,
    val content: Color,
)
