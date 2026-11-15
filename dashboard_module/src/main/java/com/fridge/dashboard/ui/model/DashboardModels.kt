package com.fridge.dashboard.ui.model

import com.fridge.dashboard.data.model.DashboardIngredient

/** 某一食材分类的统计值，保留给下一阶段图表使用。 */
data class CategoryStat(
    val name: String,
    val count: Int,
)

/** 某一天的临期食材数量，保留给下一阶段图表使用。 */
data class TrendPoint(
    val label: String,
    val count: Int,
)

/**
 * 看板页面状态：加载、空、错误、成功四态。
 *
 * Success 只携带从主 App Provider 读到的食材明细；本轮不做聚合统计。
 */
sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data object Empty : DashboardUiState
    data class Error(
        val title: String,
        val message: String,
    ) : DashboardUiState

    data class Success(
        val ingredients: List<DashboardIngredient>,
    ) : DashboardUiState
}
