package com.fridge.main.ui.model

/**
 * 食材新鲜度，用于界面状态色与筛选展示。
 *
 * 剩余天数由 `IngredientUiModel` 根据入库日期与保质期计算。
 */
enum class FreshnessStatus(val label: String) {
    FRESH("新鲜"),
    EXPIRING("临期"),
    EXPIRED("已过期"),
}
