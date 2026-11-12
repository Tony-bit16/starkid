package com.fridge.dashboard.ui.model

/** 某一食材分类的统计值。 */
data class CategoryStat(
    val name: String,
    val count: Int,
)

/** 某一天的临期食材数量。label 为横轴展示文本，例如 "11-12"。 */
data class TrendPoint(
    val label: String,
    val count: Int,
)

/**
 * 看板主页所需的统计快照。
 *
 * 真实实现应由 Repository 通过主 App 的只读 ContentProvider 查询后组装；
 * 本轮只用 [DashboardSampleData] 演示页面与图表。
 */
data class DashboardStats(
    val ingredientKinds: Int,
    val expiringCount: Int,
    val expiredCount: Int,
    val categoryStats: List<CategoryStat>,
    val trend: List<TrendPoint>,
)

/** 看板页面状态：加载、空、错误、成功四态。 */
sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data object Empty : DashboardUiState
    data class Error(val message: String) : DashboardUiState
    data class Success(val stats: DashboardStats) : DashboardUiState
}

/**
 * 仅用于骨架演示的静态统计数据，必须与真实数据区分展示。
 * 接入 ContentProvider 后删除该对象及页面对它的直接引用。
 */
object DashboardSampleData {

    val stats: DashboardStats = DashboardStats(
        ingredientKinds = 12,
        expiringCount = 3,
        expiredCount = 1,
        categoryStats = listOf(
            CategoryStat(name = "蔬菜", count = 5),
            CategoryStat(name = "蛋奶", count = 3),
            CategoryStat(name = "肉类", count = 2),
            CategoryStat(name = "乳制品", count = 1),
            CategoryStat(name = "调味品", count = 1),
        ),
        trend = listOf(
            TrendPoint(label = "11-06", count = 1),
            TrendPoint(label = "11-07", count = 2),
            TrendPoint(label = "11-08", count = 1),
            TrendPoint(label = "11-09", count = 3),
            TrendPoint(label = "11-10", count = 2),
            TrendPoint(label = "11-11", count = 4),
            TrendPoint(label = "11-12", count = 3),
        ),
    )
}
