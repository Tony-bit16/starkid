package com.fridge.main.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.fridge.main.data.local.entity.Ingredient
import com.fridge.main.data.local.entity.IngredientType

/**
 * 库存批次 + 对应食材品类。
 *
 * 列表页展示名称快照即可；详情页、匹配与统计需要品类信息时使用本关系对象，
 * 一次查询完成，避免在 UI 层按 typeId 逐条查询（N+1）。
 */
data class IngredientWithType(
    @Embedded
    val ingredient: Ingredient,

    @Relation(
        parentColumn = "type_id",
        entityColumn = "id",
    )
    val type: IngredientType?,
)
