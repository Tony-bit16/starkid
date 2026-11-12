package com.fridge.main.ui.screens.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fridge.main.ui.common.ScreenStateContent
import com.fridge.main.ui.common.UiState
import com.fridge.main.ui.components.SectionCard
import com.fridge.main.ui.components.SkeletonBanner
import com.fridge.main.ui.model.RecipePreview

/**
 * 菜谱详情页：所需食材 + 按顺序编号的做法。
 *
 * 本轮仅展示静态数据；库存匹配结果、缺失食材提示留给菜谱匹配模块。
 */
@Composable
fun RecipeDetailScreen(
    recipeId: Long,
    state: UiState<RecipePreview>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        SkeletonBanner(text = "骨架演示 · 菜谱详情 #$recipeId，数据为静态占位")
        ScreenStateContent(
            state = state,
            onRetry = onRetry,
            emptyTitle = "没有找到这道菜谱",
            emptyMessage = "它可能已被删除，返回列表刷新后再试。",
            modifier = Modifier.weight(1f),
        ) { recipe ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionCard(title = "所需食材") {
                    recipe.ingredientNames.forEach { name ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                SectionCard(title = "做法") {
                    recipe.steps.forEachIndexed { index, step ->
                        Row(verticalAlignment = Alignment.Top) {
                            Surface(
                                modifier = Modifier.size(26.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = step,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(top = 3.dp),
                            )
                        }
                    }
                }

                Text(
                    text = "提示：菜谱编辑与“能做 / 部分能做”匹配将在后续模块接入。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
