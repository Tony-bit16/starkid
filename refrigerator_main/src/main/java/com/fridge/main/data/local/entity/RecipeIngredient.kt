package com.fridge.main.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 菜谱与“所需食材”的关联表（菜谱所需食材明细）。
 *
 * 设计取舍：
 * - 不直接外键到 [Ingredient]：冰箱里的食材会被消耗、删除，而菜谱定义必须长期稳定。
 * - 以“规范化的食材名称”作为匹配键。后续匹配服务会对名称做去空格、全半角统一等规范化处理。
 * - 同一菜谱内同名食材只保留一行，用量合并后由 [quantity] + [unit] 表示。
 */
@Entity(
    tableName = "recipe_ingredients",
    primaryKeys = ["recipe_id", "ingredient_name"],
    foreignKeys = [
        ForeignKey(
            entity = Recipe::class,
            parentColumns = ["id"],
            childColumns = ["recipe_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["recipe_id"]),
        Index(value = ["ingredient_name"]),
    ],
)
data class RecipeIngredient(
    /** 所属菜谱 id，删除菜谱时级联删除明细。 */
    @ColumnInfo(name = "recipe_id")
    val recipeId: Long,

    /** 所需食材名称（匹配键），例如“鸡蛋”。 */
    @ColumnInfo(name = "ingredient_name")
    val ingredientName: String,

    /** 所需数量；与 [unit] 一起构成用量。 */
    @ColumnInfo(name = "quantity")
    val quantity: Double,

    /** 用量单位，例如“个”“克”“勺”。 */
    @ColumnInfo(name = "unit")
    val unit: String,
)
