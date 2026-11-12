package com.fridge.main.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 菜谱与食材品类的多对多关联表（菜谱所需食材明细）。
 *
 * 关系：
 * ```text
 * Recipe 1 ─── N RecipeIngredient N ─── 1 IngredientType
 * ```
 * 同一菜谱可以需要多种食材，同一食材品类也可以出现在多个菜谱中；
 * 关联行上的 [quantity] + [unit] 表示“这道菜需要多少”。
 *
 * 设计取舍：
 * - 关联到 [IngredientType] 而不是 [Ingredient]：冰箱库存是会被消耗、删除的批次，
 *   菜谱定义必须长期稳定；同一品类也可能同时存在多条库存批次，无法作为唯一外键目标。
 * - 菜谱可以先于库存存在：新增菜谱时若品类不存在，由 Repository 在同一事务内
 *   创建 [IngredientType]，再写入关联行。
 * - `(recipe_id, type_id)` 作为复合主键，天然保证同一菜谱内同一品类只出现一次。
 * - 删除菜谱级联删除关联行；删除品类使用 RESTRICT，必须先解除引用。
 */
@Entity(
    tableName = "recipe_ingredients",
    primaryKeys = ["recipe_id", "type_id"],
    foreignKeys = [
        ForeignKey(
            entity = Recipe::class,
            parentColumns = ["id"],
            childColumns = ["recipe_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = IngredientType::class,
            parentColumns = ["id"],
            childColumns = ["type_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["recipe_id"]),
        Index(value = ["type_id"]),
    ],
)
data class RecipeIngredient(
    /** 所属菜谱 id，删除菜谱时级联删除明细。 */
    @ColumnInfo(name = "recipe_id")
    val recipeId: Long,

    /** 所需食材品类 id，外键 → [IngredientType.id]。 */
    @ColumnInfo(name = "type_id")
    val typeId: Long,

    /** 所需数量；与 [unit] 一起构成用量。 */
    @ColumnInfo(name = "quantity")
    val quantity: Double,

    /** 用量单位，例如“个”“克”“勺”。 */
    @ColumnInfo(name = "unit")
    val unit: String,
)
