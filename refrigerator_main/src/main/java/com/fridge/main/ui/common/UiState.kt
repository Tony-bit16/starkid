package com.fridge.main.ui.common

/**
 * 页面统一的数据状态。
 *
 * 约束：加载态、空态、错误态必须由 UI 明确渲染，不能只在成功数据上做 `if (list.isEmpty())`。
 * 本轮使用静态 Success 占位数据演示骨架，后续 ViewModel 直接暴露该状态类型即可复用。
 */
sealed interface UiState<out T> {

    /** 首次加载或下拉刷新中。 */
    data object Loading : UiState<Nothing>

    /** 请求成功但结果为空。 */
    data object Empty : UiState<Nothing>

    /** 请求失败；message 应为可直接展示给用户的中文提示。 */
    data class Error(val message: String) : UiState<Nothing>

    /** 请求成功且存在数据。 */
    data class Success<T>(val data: T) : UiState<T>
}
