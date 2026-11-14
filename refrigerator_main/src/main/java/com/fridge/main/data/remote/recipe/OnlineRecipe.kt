package com.fridge.main.data.remote.recipe

/**
 * 在线菜谱的领域模型（网络 DTO 已在 [MealDto] 中隔离）。
 *
 * [measure] 保留接口返回的原始用量文本用于展示；
 * [quantity]/[unit] 是“一键保存到本地”时写入 Room 的结构化结果。
 */
data class OnlineRecipe(
    val id: String,
    val name: String,
    val category: String,
    val area: String,
    val thumbnailUrl: String?,
    val steps: List<String>,
    val ingredients: List<OnlineIngredient>,
)

/** 在线菜谱的一项食材。 */
data class OnlineIngredient(
    val name: String,
    val measure: String,
    val quantity: Double,
    val unit: String,
)
