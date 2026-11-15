package com.fridge.dashboard.ui.model

import com.fridge.dashboard.data.model.DashboardIngredient

/**
 * 某一食材分类的库存条目数。
 *
 * 看板不对不同单位的数量求和，因此这里的“数量”统一指库存条目数，
 * 与主页“总食材数”的口径保持一致。
 */
data class CategoryStat(
    val name: String,
    val count: Int,
)

/** 某一天的临期食材数量，保留给后续趋势图使用。 */
data class TrendPoint(
    val label: String,
    val count: Int,
)

/** 看板主页需要的全部聚合结果。 */
data class DashboardStats(
    val totalCount: Int,
    val expiringCount: Int,
    val expiredCount: Int,
    val categories: List<CategoryStat>,
)

/** 成功态同时携带统计结果与原始明细，避免页面重复计算。 */
data class DashboardHomeContent(
    val stats: DashboardStats,
    val ingredients: List<DashboardIngredient>,
)

/** 导出状态：进行中与最后一次结果分离，页面可以持续展示加载态。 */
data class DashboardExportUiState(
    val isExporting: Boolean = false,
    val result: DashboardExportResult? = null,
)

sealed interface DashboardExportResult {
    data class Success(
        val fileName: String,
        val displayPath: String,
        val contentUri: String,
    ) : DashboardExportResult

    data class Failure(
        val message: String,
    ) : DashboardExportResult
}

/**
 * 看板页面状态：加载、空、错误、成功四态。
 *
 * Success 中的统计结果已在 ViewModel 的后台协程中计算完成，
 * 页面只负责渲染，不做任何聚合计算。
 */
sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data object Empty : DashboardUiState
    data class Error(
        val title: String,
        val message: String,
    ) : DashboardUiState

    data class Success(
        val content: DashboardHomeContent,
    ) : DashboardUiState
}
