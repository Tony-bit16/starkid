package com.fridge.main.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 菜谱主表。
 *
 * “所需食材列表”不直接存成字符串，而是由 [RecipeIngredient] 关联表表达，
 * 便于按食材名称反查菜谱、统计用量，并为后续“能做 / 部分能做”匹配提供结构化数据。
 */
@Entity(
    tableName = "recipes",
    indices = [Index(value = ["name"])],
)
data class Recipe(
    /** 本地自增主键；0 表示尚未插入数据库。 */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    /** 菜名，例如“番茄炒蛋”。 */
    @ColumnInfo(name = "name")
    val name: String,

    /** 做法。建议按步骤用换行分隔，例如“1. 打散鸡蛋\n2. 热锅下油……”。 */
    @ColumnInfo(name = "steps")
    val steps: String,
)
