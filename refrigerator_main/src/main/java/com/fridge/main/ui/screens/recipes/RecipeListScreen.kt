package com.fridge.main.ui.screens.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fridge.main.domain.matching.RecipeMatchResult
import com.fridge.main.ui.common.ScreenStateContent
import com.fridge.main.ui.model.RecipeUiModel

/**
 * 菜谱列表页。
 *
 * 顶部提供“全部菜谱 / 推荐菜谱”筛选；推荐结果由 [RecipeListViewModel] 在后台
 * 使用 [com.fridge.main.domain.matching.RecipeMatcher] 计算，页面只负责展示等级与缺失食材。
 */
@Composable
fun RecipeListScreen(
    uiState: RecipeListUiState,
    onFilterChange: (RecipeListFilter) -> Unit,
    onRetry: () -> Unit,
    onRecipeClick: (Long) -> Unit,
    onGoOnline: () -> Unit,
    onAddIngredient: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val emptyTitle = when {
        uiState.filter == RecipeListFilter.ALL -> "还没有菜谱"
        uiState.emptyReason == RecipeListEmptyReason.NO_INGREDIENTS -> "冰箱里还没有食材"
        uiState.emptyReason == RecipeListEmptyReason.NO_RECIPES -> "还没有本地菜谱"
        else -> "暂无推荐菜谱"
    }
    val emptyMessage = when {
        uiState.filter == RecipeListFilter.ALL -> "点击右上角“新增”，记录第一道菜谱。"
        uiState.emptyReason == RecipeListEmptyReason.NO_INGREDIENTS -> "添加食材后查看推荐菜谱。"
        uiState.emptyReason == RecipeListEmptyReason.NO_RECIPES ->
            "先到在线搜索保存一道菜谱，再回来查看推荐。"
        else -> "换一道菜谱，或先补充一些食材后再试。"
    }
    val emptyActionLabel = when {
        uiState.filter == RecipeListFilter.RECOMMENDED &&
            uiState.emptyReason == RecipeListEmptyReason.NO_INGREDIENTS -> "去添加食材"
        uiState.filter == RecipeListFilter.RECOMMENDED &&
            uiState.emptyReason == RecipeListEmptyReason.NO_RECIPES -> "去在线搜索"
        else -> null
    }
    val onEmptyAction: (() -> Unit)? = when {
        uiState.filter == RecipeListFilter.RECOMMENDED &&
            uiState.emptyReason == RecipeListEmptyReason.NO_INGREDIENTS -> onAddIngredient
        uiState.filter == RecipeListFilter.RECOMMENDED &&
            uiState.emptyReason == RecipeListEmptyReason.NO_RECIPES -> onGoOnline
        else -> null
    }

    Column(modifier = modifier.fillMaxSize()) {
        RecipeFilterRow(
            selected = uiState.filter,
            onSelected = onFilterChange,
        )

        ScreenStateContent(
            state = uiState.content,
            onRetry = onRetry,
            emptyTitle = emptyTitle,
            emptyMessage = emptyMessage,
            emptyActionLabel = emptyActionLabel,
            onEmptyAction = onEmptyAction,
            modifier = Modifier.weight(1f),
        ) { items ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        text = if (uiState.filter == RecipeListFilter.ALL) {
                            "共 ${items.size} 道菜谱"
                        } else {
                            "推荐 ${items.size} 道菜谱"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(items = items, key = { it.recipe.id }) { item ->
                    RecipeCard(
                        item = item,
                        onClick = { onRecipeClick(item.recipe.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecipeFilterRow(
    selected: RecipeListFilter,
    onSelected: (RecipeListFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RecipeListFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelected(filter) },
                label = { Text(filter.label) },
            )
        }
    }
}

@Composable
private fun RecipeCard(
    item: RecipeListItem,
    onClick: () -> Unit,
) {
    val recipe = item.recipe
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 菜名首字作为占位图标，避免为每道菜引入图片资源。
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = recipe.name.take(1),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recipe.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.match != null) {
                    MatchSummary(
                        match = item.match,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                } else {
                    Text(
                        text = "所需：${recipe.ingredientSummary}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "${recipe.stepCount} 步",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun MatchSummary(
    match: RecipeMatchResult,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RecipeMatchBadge(level = match.level)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${match.availableCount}/${match.totalCount} 已备齐",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (match.totalCount == 0) {
            Text(
                text = "没有食材清单，无法判断",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else if (match.missingIngredients.isEmpty()) {
            Text(
                text = "食材齐全，可以开做",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            Text(
                text = "缺少：${match.missingIngredients.joinToString("、")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
