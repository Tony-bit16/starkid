package com.fridge.main.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.fridge.main.data.local.entity.IngredientType
import com.fridge.main.data.local.entity.RecipeIngredient

/**
 * 一条菜谱所需食材明细 + 对应食材品类。
 *
 * [RecipeIngredient] 只存 typeId 与用量；展示“鸡蛋 2 个”时还需要品类的名称与分类，
 * 因此通过本关系对象把关联行和品类主数据一起查出。
 */
data class RecipeIngredientWithType(
    @Embedded
    val recipeIngredient: RecipeIngredient,

    @Relation(
        parentColumn = "type_id",
        entityColumn = "id",
    )
    val type: IngredientType?,
)
