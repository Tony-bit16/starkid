package com.fridge.main.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fridge.main.ui.components.SectionCard

/**
 * 关于页。
 *
 * 只展示产品信息与能力边界，不读取用户数据；版本号来自安装包，避免手写后忘记同步。
 */
@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val versionName = remember(context) {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(title = "冰箱管家") {
                Text(
                    text = "版本 $versionName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "一个本地优先的食材与菜谱管理 App。",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        item {
            SectionCard(title = "功能说明") {
                Text("• 食材库存：入库时间、保质期、数量与单位管理。")
                Text("• 菜谱：手动录入、在线搜索并保存到本地。")
                Text("• 推荐：按现有食材匹配完全可做/缺少食材的菜谱。")
                Text("• 提醒：WorkManager 每日检查，临期食材合并通知。")
                Text("• 看板：独立 App 通过只读 ContentProvider 读取库存并导出 CSV。")
            }
        }

        item {
            SectionCard(title = "数据与隐私") {
                Text(
                    text = "食材与菜谱保存在本机 Room 数据库；在线搜索仅在用户主动搜索时请求 TheMealDB。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "看板只读取主 App 通过 signature 权限暴露的库存投影，不直接访问主 App 私有数据库。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Text(
                text = "健康提醒不能替代实际储存条件判断；请结合食材状态与包装说明使用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
