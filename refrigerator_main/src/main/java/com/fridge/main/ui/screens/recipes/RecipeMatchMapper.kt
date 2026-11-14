package com.fridge.main.ui.screens.recipes

import com.fridge.main.data.local.relation.RecipeWithIngredients
import com.fridge.main.domain.matching.MatchableIngredient
import com.fridge.main.domain.matching.MatchableRecipe

/**
 * Room 关系对象 → 匹配算法输入。
 *
 * 放在 UI 层而不是 domain 层，避免纯算法反向依赖 Room Entity；
 * 关联表里的品类名可能因异常数据为空，展示时用“未知食材”兜底。
 */
internal fun RecipeWithIngredients.toMatchableRecipe(): MatchableRecipe = MatchableRecipe(
    id = recipe.id,
    name = recipe.name,
    ingredients = ingredientLinks.map { link ->
        MatchableIngredient(
            typeId = link.recipeIngredient.typeId,
            name = link.type?.name ?: "未知食材",
        )
    },
)
