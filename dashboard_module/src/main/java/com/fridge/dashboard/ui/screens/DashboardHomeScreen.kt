package com.fridge.dashboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fridge.dashboard.data.model.DashboardIngredient
import com.fridge.dashboard.data.provider.IngredientProviderContract
import com.fridge.dashboard.ui.charts.CategoryPieChart
import com.fridge.dashboard.ui.components.ChartCard
import com.fridge.dashboard.ui.components.DashboardDemoBanner
import com.fridge.dashboard.ui.components.DashboardStateContent
import com.fridge.dashboard.ui.components.MetricCard
import com.fridge.dashboard.ui.model.DashboardHomeContent
import com.fridge.dashboard.ui.model.DashboardStats
import com.fridge.dashboard.ui.model.DashboardUiState

/**
 * 统计看板主页。
 *
 * 顶部三张指标卡展示总条目、临期、已过期；
 * 下方饼图按分类展示库存条目占比，再保留数据来源与明细，便于核对统计结果。
 */
@Composable
fun DashboardHomeScreen(
    state: DashboardUiState,
    onRetry: () -> Unit,
    isExporting: Boolean,
    onExportClick: () -> Unit,
    onOpenExportPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        DashboardDemoBanner(
            text = "数据来自主 App 的只读 ContentProvider · 分类占比按库存条目统计；CSV 支持导出",
        )
        DashboardStateContent(
            state = state,
            onRetry = onRetry,
            modifier = Modifier.weight(1f),
        ) { content ->
            DashboardContent(
                content = content,
                isExporting = isExporting,
                onExportClick = onExportClick,
                onOpenExportPage = onOpenExportPage,
            )
        }
    }
}

@Composable
private fun DashboardContent(
    content: DashboardHomeContent,
    isExporting: Boolean,
    onExportClick: () -> Unit,
    onOpenExportPage: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { StatsRow(stats = content.stats) }
        item { StatsNote() }
        item { CategoryChartCard(stats = content.stats) }
        item { DataSourceCard() }
        item { SectionTitle("食材明细") }

        items(content.ingredients, key = { it.id }) { ingredient ->
            IngredientCard(ingredient = ingredient)
        }

        item {
            Button(
                onClick = onExportClick,
                enabled = !isExporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("正在导出…")
                } else {
                    Text("导出数据（CSV）")
                }
            }
        }

        item {
            TextButton(
                onClick = onOpenExportPage,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("查看导出字段说明")
            }
        }

        item {
            Text(
                text = "CSV 会保存到公共下载目录（Downloads/Fridge）；趋势折线图在后续阶段接入。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatsRow(stats: DashboardStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MetricCard(
            value = stats.totalCount.toString(),
            label = "总食材数",
            accent = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        MetricCard(
            value = stats.expiringCount.toString(),
            label = "临期",
            accent = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f),
        )
        MetricCard(
            value = stats.expiredCount.toString(),
            label = "已过期",
            accent = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatsNote() {
    Text(
        text = "统计口径：库存条目数；到期前 3 天记为临期。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CategoryChartCard(stats: DashboardStats) {
    ChartCard(
        title = "分类库存占比",
        subtitle = "按库存条目数统计，共 ${stats.totalCount} 条",
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // 图表高度随屏宽变化，窄屏不挤压卡片，平板不显得过矮。
            val chartHeight = (maxWidth * 0.72f).coerceIn(220.dp, 340.dp)
            CategoryPieChart(
                stats = stats.categories,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun IngredientCard(ingredient: DashboardIngredient) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
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
        shape = MaterialTheme.shapes.large,
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
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                text = "分类占比按库存条目数计算，不把不同单位的数量直接相加。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
