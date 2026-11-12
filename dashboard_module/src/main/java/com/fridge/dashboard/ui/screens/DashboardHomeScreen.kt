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
import com.fridge.dashboard.ui.charts.CategoryPieChart
import com.fridge.dashboard.ui.charts.ExpiringTrendChart
import com.fridge.dashboard.ui.components.ChartCard
import com.fridge.dashboard.ui.components.DashboardDemoBanner
import com.fridge.dashboard.ui.components.DashboardStateContent
import com.fridge.dashboard.ui.components.MetricCard
import com.fridge.dashboard.ui.model.DashboardStats
import com.fridge.dashboard.ui.model.DashboardUiState

/**
 * 统计看板主页。
 *
 * 结构：关键指标 → 分类占比饼图 → 近 7 天临期折线图 → 数据来源说明 → 导出入口。
 * 本轮全部使用静态示意数据，不连接主 App 数据库。
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
            text = "骨架演示 · 尚未连接主 App，以下指标与图表为静态示意数据",
        )
        DashboardStateContent(
            state = state,
            onRetry = onRetry,
            modifier = Modifier.weight(1f),
        ) { stats ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { MetricRow(stats = stats) }

                item {
                    ChartCard(
                        title = "食材分类占比",
                        subtitle = "按食材品种数统计，百分比为品种占比",
                    ) {
                        CategoryPieChart(stats = stats.categoryStats)
                    }
                }

                item {
                    ChartCard(
                        title = "近 7 天临期食材",
                        subtitle = "每天进入临期范围（剩余 ≤ 2 天）的食材数量",
                    ) {
                        ExpiringTrendChart(points = stats.trend)
                    }
                }

                item { DataSourceCard() }

                item {
                    Button(
                        onClick = onExportClick,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("导出统计 CSV")
                    }
                }

                item {
                    Text(
                        text = "导出内容仅包含统计结果，不包含食材名称等明细。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricRow(stats: DashboardStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MetricCard(
            value = stats.ingredientKinds.toString(),
            label = "食材品种",
            accent = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        MetricCard(
            value = stats.expiringCount.toString(),
            label = "即将过期",
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
private fun DataSourceCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = "数据来源", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "正式实现中，看板通过主 App 暴露的只读 ContentProvider（signature 权限）" +
                    "读取统计所需字段，不直接打开主 App 私有数据库，也不使用 sharedUserId。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "本轮仅完成独立 APK 的页面与图表骨架。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
