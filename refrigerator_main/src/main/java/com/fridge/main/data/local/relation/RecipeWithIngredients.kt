package com.fridge.main.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.fridge.main.data.local.entity.Recipe
import com.fridge.main.data.local.entity.RecipeIngredient

/**
 * 菜谱及其完整所需食材列表。
 *
 * 使用 @Relation 一次查询出菜谱、关联行与品类，避免 UI 层对每条菜谱重复查询（N+1）。
 * [ingredientLinks] 中的每一项同时包含“用量”和“品类名称/分类”。
 */
data class RecipeWithIngredients(
    @Embedded
    val recipe: Recipe,

    @Relation(
        parentColumn = "id",
        entityColumn = "recipe_id",
        entity = RecipeIngredient::class,
    )
    val ingredientLinks: List<RecipeIngredientWithType>,
)
