package com.fridge.main.ui.screens.ingredients

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fridge.main.ui.common.ScreenStateContent
import com.fridge.main.ui.common.UiState
import com.fridge.main.ui.components.FreshnessPill
import com.fridge.main.ui.components.NotificationPermissionBanner
import com.fridge.main.ui.model.IngredientUiModel

/**
 * 首页：冰箱食材列表。
 *
 * 数据由 [IngredientListViewModel] 从 Room 读取并通过 [state] 传入；
 * 页面只负责四态渲染和列表→详情跳转，不直接访问数据库。
 */
@Composable
fun IngredientListScreen(
    state: UiState<List<IngredientUiModel>>,
    onRetry: () -> Unit,
    onIngredientClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // 通知权限不可用时只占一条提示，不改变列表的数据加载与错误处理。
        NotificationPermissionBanner()
        ScreenStateContent(
            state = state,
            onRetry = onRetry,
            emptyTitle = "冰箱还是空的",
            emptyMessage = "点击右上角“新增”，记录第一份食材。",
            modifier = Modifier.weight(1f),
        ) { items ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        text = "共 ${items.size} 条库存",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(items = items, key = { it.id }) { item ->
                    IngredientCard(
                        item = item,
                        onClick = { onIngredientClick(item.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun IngredientCard(
    item: IngredientUiModel,
    onClick: () -> Unit,
) {
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
            // 名称首字作为占位图标，避免为每种食材引入图片资源。
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = item.name.take(1),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = item.expiryLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = item.quantityLabel,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.size(6.dp))
                FreshnessPill(status = item.status)
            }
        }
    }
}
