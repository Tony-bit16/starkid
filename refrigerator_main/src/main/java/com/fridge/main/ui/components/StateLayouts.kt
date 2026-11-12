package com.fridge.main.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 通用加载态布局。
 *
 * 列表、详情等数据页面统一复用，避免每个页面各写一套进度指示器。
 * 文案可由调用方覆盖，默认用于首次加载或刷新。
 */
@Composable
fun LoadingStateLayout(
    modifier: Modifier = Modifier,
    message: String = "正在加载…",
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

/**
 * 通用空态布局。
 *
 * 空态必须说明“为什么为空”和“下一步能做什么”，不能只显示一张空白页。
 * 图标暂用文字符号，后续可统一替换为矢量插画，不影响调用方。
 */
@Composable
fun EmptyStateLayout(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            // 装饰符号不进入无障碍朗读，标题与说明才是有效信息。
            text = "○",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * 通用错误态布局。
 *
 * 错误信息必须是可直接展示给用户的中文文案；保留“重试”作为唯一恢复动作。
 */
@Composable
fun ErrorStateLayout(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "加载失败",
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "！",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = 20.dp),
        ) {
            Text("重试")
        }
    }
}
