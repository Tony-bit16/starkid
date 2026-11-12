package com.fridge.main.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.fridge.main.ui.components.EmptyStateLayout
import com.fridge.main.ui.components.ErrorStateLayout
import com.fridge.main.ui.components.LoadingStateLayout

/**
 * 把 [UiState] 渲染为加载 / 空 / 错误 / 成功四种界面。
 *
 * 所有数据页面都应通过该组件输出状态，保证同类反馈的位置和可访问性一致；
 * 具体布局由 components 包中的通用组件承载，页面只关心状态分支。
 */
@Composable
fun <T> ScreenStateContent(
    state: UiState<T>,
    onRetry: () -> Unit,
    emptyTitle: String,
    emptyMessage: String,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (state) {
            UiState.Loading -> LoadingStateLayout()
            UiState.Empty -> EmptyStateLayout(
                title = emptyTitle,
                message = emptyMessage,
            )
            is UiState.Error -> ErrorStateLayout(
                message = state.message,
                onRetry = onRetry,
            )
            is UiState.Success -> content(state.data)
        }
    }
}
