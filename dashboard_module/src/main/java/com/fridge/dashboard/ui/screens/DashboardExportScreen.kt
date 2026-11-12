package com.fridge.dashboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fridge.dashboard.ui.components.DashboardDemoBanner
import com.fridge.dashboard.ui.components.DashboardInfoRow

/**
 * 导出页（骨架）。
 *
 * 明确导出内容、CSV 字段与文件命名；具体 SAF 写入在导出模块实现。
 * 本轮按钮禁用，避免用户误以为已经可以生成文件。
 */
@Composable
fun DashboardExportScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        DashboardDemoBanner(text = "骨架演示 · CSV 导出尚未接入系统文件选择器（SAF）")
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ExportCard(title = "导出内容") {
                ExportItem(
                    title = "分类统计",
                    description = "每个分类的食材品种数、库存总数量、临期数量。",
                )
                ExportItem(
                    title = "临期清单",
                    description = "按剩余天数升序排列的临期/已过期食材汇总。",
                )
            }

            ExportCard(title = "CSV 字段") {
                DashboardInfoRow(label = "统计日期", value = "yyyy-MM-dd")
                DashboardInfoRow(label = "分类", value = "category")
                DashboardInfoRow(label = "食材品种数", value = "kind_count")
                DashboardInfoRow(label = "库存总数量", value = "total_quantity")
                DashboardInfoRow(label = "临期数量", value = "expiring_count")
            }

            ExportCard(title = "文件") {
                DashboardInfoRow(label = "格式", value = "CSV（UTF-8）")
                DashboardInfoRow(label = "文件名", value = "fridge_stats_yyyyMMdd.csv")
            }

            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("选择保存位置（下一阶段接入）")
            }

            Text(
                text = "实现约定：使用 ActivityResultContracts.CreateDocument(\"text/csv\") 由系统选择保存位置，" +
                    "不申请存储权限；用户取消时不视为错误，也不弹崩溃提示。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExportCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ExportItem(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
