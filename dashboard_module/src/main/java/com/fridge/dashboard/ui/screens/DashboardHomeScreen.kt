package com.fridge.dashboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fridge.dashboard.data.model.DashboardIngredient
import com.fridge.dashboard.data.provider.IngredientProviderContract
import com.fridge.dashboard.ui.components.DashboardDemoBanner
import com.fridge.dashboard.ui.components.DashboardStateContent
import com.fridge.dashboard.ui.components.MetricCard
import com.fridge.dashboard.ui.model.DashboardUiState

/**
 * 统计看板主页（数据通道版）。
 *
 * 本轮只把主 App 的食材明细以文本形式展示，验证 ContentProvider 通路；
 * 分类饼图、临期趋势图与统计聚合留到下一阶段。
 */
@Composable
fun DashboardHomeScreen(
    state: DashboardUiState,
    onRetry: () -> Unit,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        DashboardDemoBanner(
            text = "数据来自主 App 的只读 ContentProvider · 当前展示食材明细；图表与 CSV 在下一阶段接入",
        )
        DashboardStateContent(
            state = state,
            onRetry = onRetry,
            modifier = Modifier.weight(1f),
        ) { ingredients ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { SummaryRow(ingredients = ingredients) }

                item { DataSourceCard() }

                items(ingredients, key = { it.id }) { ingredient ->
                    IngredientCard(ingredient = ingredient)
                }

                item {
                    Button(
                        onClick = onExportClick,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("打开导出页（骨架）")
                    }
                }

                item {
                    Text(
                        text = "本轮仅验证跨 App 数据读取；图表统计与 CSV 导出尚未实现。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(ingredients: List<DashboardIngredient>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MetricCard(
            value = ingredients.size.toString(),
            label = "库存条目",
            accent = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        MetricCard(
            value = ingredients.distinctBy { it.typeId }.size.toString(),
            label = "食材品种",
            accent = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun IngredientCard(ingredient: DashboardIngredient) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = ingredient.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "${ingredient.quantityLabel} ${ingredient.unit}".trim(),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = "分类：${ingredient.category} · 入库：${ingredient.storedAtLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "保质期：${ingredient.shelfLifeDays} 天",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DataSourceCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = "数据来源", style = MaterialTheme.typography.titleMedium)
            Text(
                text = IngredientProviderContract.CONTENT_URI.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "通过主 App 的只读 ContentProvider + signature 权限读取；" +
                    "不直接打开主 App 私有数据库。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
