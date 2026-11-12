package com.fridge.main.ui.navigation

/**
 * 主 App 的路由表。
 *
 * 菜谱/食材详情使用 `xxx/detail/{id}`，新增页使用 `ingredients/new`，
 * 避免 `ingredients/{id}` 与 `ingredients/new` 被 Navigation 同时匹配。
 */
object Routes {
    const val INGREDIENTS = "ingredients"
    const val INGREDIENT_DETAIL = "ingredients/detail/{ingredientId}"
    const val ADD_INGREDIENT = "ingredients/new"

    const val RECIPES = "recipes"
    const val RECIPE_DETAIL = "recipes/detail/{recipeId}"

    fun ingredientDetail(id: Long): String = "ingredients/detail/$id"

    fun recipeDetail(id: Long): String = "recipes/detail/$id"
}
