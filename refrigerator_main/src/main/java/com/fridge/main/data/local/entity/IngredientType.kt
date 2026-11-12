package com.fridge.main.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 食材品类（食材字典）。
 *
 * 为什么需要单独一张表：
 * - [Ingredient] 表示“一次入库”的库存批次，同一种食材会有多条记录；
 * - 菜谱需要引用稳定的“食材品类”，不能绑定到某一批库存；
 * - 因此用本表作为菜谱与库存共同引用的主数据，菜谱-食材的多对多关系
 *   由 [RecipeIngredient] 关联到本表来表达。
 *
 * 名称规范化：
 * - [name] 保留用户看到的展示名；
 * - [normalizedName] 用于匹配，写入前由 Repository 统一做 trim、转小写、
 *   全角转半角、连续空白折叠，并建立唯一索引，避免“鸡蛋 / 鸡蛋 ”重复建品类。
 */
@Entity(
    tableName = "ingredient_types",
    indices = [
        Index(value = ["normalized_name"], unique = true),
        Index(value = ["category"]),
    ],
)
data class IngredientType(
    /** 本地自增主键；0 表示尚未插入数据库。 */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    /** 展示名称，例如“西红柿”。 */
    @ColumnInfo(name = "name")
    val name: String,

    /** 规范化名称，匹配键；同一品类唯一。 */
    @ColumnInfo(name = "normalized_name")
    val normalizedName: String,

    /** 分类，例如“蔬菜”“肉蛋”“乳制品”“调味品”。 */
    @ColumnInfo(name = "category")
    val category: String,

    /** 该品类默认计量单位，例如“个”“克”；仅作为新增表单的默认值。 */
    @ColumnInfo(name = "default_unit")
    val defaultUnit: String,

    /** 品类创建时间（Unix 毫秒时间戳），用于审计与稳定排序。 */
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
