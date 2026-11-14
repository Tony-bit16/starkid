package com.fridge.main.ui.navigation

/**
 * 主 App 的路由表。
 *
 * 菜谱/食材详情使用 `xxx/detail/{id}`，新增页使用 `ingredients/new`、
 * `recipes/new`，避免 `xxx/{id}` 与 `xxx/new` 被 Navigation 同时匹配。
 */
object Routes {
    const val INGREDIENTS = "ingredients"
    const val INGREDIENT_DETAIL = "ingredients/detail/{ingredientId}"
    const val ADD_INGREDIENT = "ingredients/new"

    const val RECIPES = "recipes"
    const val RECIPE_DETAIL = "recipes/detail/{recipeId}"
    const val ADD_RECIPE = "recipes/new"
    const val ONLINE_RECIPES = "recipes/online"
    const val ONLINE_RECIPE_DETAIL = "recipes/online/{mealId}"

    fun ingredientDetail(id: Long): String = "ingredients/detail/$id"

    fun recipeDetail(id: Long): String = "recipes/detail/$id"

    fun onlineRecipeDetail(mealId: String): String = "recipes/online/$mealId"
}
