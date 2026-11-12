package com.fridge.main.ui.screens.ingredients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fridge.main.ui.common.ScreenStateContent
import com.fridge.main.ui.common.UiState
import com.fridge.main.ui.components.FreshnessPill
import com.fridge.main.ui.components.LabeledValue
import com.fridge.main.ui.components.SectionCard
import com.fridge.main.ui.components.SkeletonBanner
import com.fridge.main.ui.model.IngredientPreview

/**
 * 食材详情页。
 *
 * 通过导航参数 `ingredientId` 展示对应食材；本轮数据来自静态 [com.fridge.main.ui.model.SampleData]，
 * 查不到时展示空态。真实实现应由 ViewModel 读取 Room 并暴露 [UiState]。
 */
@Composable
fun IngredientDetailScreen(
    ingredientId: Long,
    state: UiState<IngredientPreview>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        SkeletonBanner(text = "骨架演示 · 食材详情 #$ingredientId，数据为静态占位")
        ScreenStateContent(
            state = state,
            onRetry = onRetry,
            emptyTitle = "没有找到这份食材",
            emptyMessage = "它可能已被删除，返回列表刷新后再试。",
            modifier = Modifier.weight(1f),
        ) { item ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionCard(title = "基本信息") {
                    LabeledValue(label = "名称", value = item.name)
                    LabeledValue(label = "分类", value = item.category)
                    LabeledValue(label = "数量", value = item.quantityLabel)
                    LabeledValue(label = "入库时间", value = item.storedAt)
                }

                SectionCard(title = "保质期") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "新鲜度",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FreshnessPill(status = item.status)
                    }
                    LabeledValue(label = "保质期天数", value = "${item.shelfLifeDays} 天")
                    LabeledValue(label = "剩余时间", value = item.expiryLabel)
                }

                Text(
                    text = "提示：到期推送、编辑与删除将在后续模块接入，本轮不提供操作入口。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
