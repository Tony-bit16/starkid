package com.fridge.dashboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
 * 导出说明页。
 *
 * 实际导出入口在主页（顶栏“导出”或底部“导出数据（CSV）”按钮）；
 * 本页只说明导出内容、字段、编码和保存位置，避免重复放一个容易误解的禁用按钮。
 */
@Composable
fun DashboardExportScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        DashboardDemoBanner(
            text = "CSV 已支持导出 · 返回主页点击“导出数据（CSV）”；文件保存到公共下载目录",
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ExportCard(title = "导出内容") {
                ExportItem(
                    title = "全量食材库存",
                    description = "每次导出都会重新读取主 App 的最新食材，不导出看板缓存。",
                )
                ExportItem(
                    title = "过期状态",
                    description = "按本地日历日计算：0..3 天为临期，负数天为已过期。",
                )
            }

            ExportCard(title = "CSV 字段") {
                DashboardInfoRow(label = "食材名称", value = "name")
                DashboardInfoRow(label = "分类", value = "category")
                DashboardInfoRow(label = "数量", value = "quantity")
                DashboardInfoRow(label = "单位", value = "unit")
                DashboardInfoRow(label = "入库时间", value = "stored_at")
                DashboardInfoRow(label = "剩余保质期", value = "days_left（天）")
                DashboardInfoRow(label = "过期状态", value = "status")
            }

            ExportCard(title = "文件") {
                DashboardInfoRow(label = "格式", value = "CSV（UTF-8 BOM / CRLF）")
                DashboardInfoRow(label = "文件名", value = "fridge_inventory_yyyyMMdd_HHmmss.csv")
                DashboardInfoRow(label = "保存位置", value = "Downloads/Fridge")
            }

            Text(
                text = "权限说明：Android 10+ 通过 MediaStore 写入公共下载目录，不需要存储权限；" +
                    "Android 7-9 会先申请外部存储写权限。拒绝权限只影响导出，仍可正常查看统计。",
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
        shape = MaterialTheme.shapes.large,
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
