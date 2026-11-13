package com.fridge.main.ui.model

import com.fridge.main.data.local.relation.RecipeWithIngredients

/**
 * 菜谱列表/详情页的展示模型。
 *
 * 把 Room 的“主表 + 关联行 + 品类”结构拍平成 UI 可直接渲染的食材列表与步骤列表，
 * 页面不需要再关心 @Relation 的嵌套结构。
 */
data class RecipeUiModel(
    val id: Long,
    val name: String,
    val steps: List<String>,
    val ingredients: List<RecipeIngredientUiModel>,
) {
    /** 列表卡片上的一行食材摘要，例如“番茄、鸡蛋”。 */
    val ingredientSummary: String
        get() = if (ingredients.isEmpty()) {
            "暂无食材"
        } else {
            ingredients.joinToString("、") { it.name }
        }

    val stepCount: Int get() = steps.size
}

/** 菜谱中的一项食材需求。 */
data class RecipeIngredientUiModel(
    val typeId: Long,
    val name: String,
    val category: String,
    val quantity: Double,
    val unit: String,
) {
    val quantityLabel: String get() = "${formatQuantity(quantity)} $unit"
}

/**
 * Entity/Relation → UI 模型。
 *
 * 品类理论上不会为空（recipe_ingredients.type_id 有外键约束）；
 * 这里仍对 null 做兜底，避免数据库损坏时详情页直接崩溃。
 */
fun RecipeWithIngredients.toUiModel(): RecipeUiModel {
    val ingredientModels = ingredientLinks
        .map { link ->
            val type = link.type
            RecipeIngredientUiModel(
                typeId = link.recipeIngredient.typeId,
                name = type?.name ?: "未知食材 #${link.recipeIngredient.typeId}",
                category = type?.category.orEmpty(),
                quantity = link.recipeIngredient.quantity,
                unit = link.recipeIngredient.unit,
            )
        }
        .sortedBy { it.name }

    return RecipeUiModel(
        id = recipe.id,
        name = recipe.name,
        steps = recipe.steps.toStepList(),
        ingredients = ingredientModels,
    )
}

/**
 * 做法按换行拆成步骤，忽略空行。
 *
 * 保存时 Repository 也会统一去除空行，因此历史数据即使多打了换行也能正确展示。
 */
fun String.toStepList(): List<String> = lineSequence()
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .toList()
