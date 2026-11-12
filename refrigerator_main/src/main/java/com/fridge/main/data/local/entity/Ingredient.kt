package com.fridge.main.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 冰箱中的一条食材库存记录。
 *
 * 说明：
 * - 一条记录代表“一次入库”的食材，而不是一种食材字典；同名食材可以有多条记录，
 *   例如两次购买西红柿可分别记录不同的入库时间与数量。
 * - 到期时间不落库，由 [storedAt] + [shelfLifeDays] 派生，避免两份数据不一致。
 * - 时间统一使用 Unix 毫秒时间戳（UTC），展示时再按本地时区格式化。
 */
@Entity(
    tableName = "ingredients",
    indices = [
        Index(value = ["name"]),
        Index(value = ["category"]),
        Index(value = ["stored_at"]),
    ],
)
data class Ingredient(
    /** 本地自增主键；0 表示尚未插入数据库。 */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    /** 食材名称，例如“西红柿”“鸡蛋”。 */
    @ColumnInfo(name = "name")
    val name: String,

    /** 分类，例如“蔬菜”“肉蛋”“乳制品”“调味品”。 */
    @ColumnInfo(name = "category")
    val category: String,

    /** 入库时间（Unix 毫秒时间戳）。 */
    @ColumnInfo(name = "stored_at")
    val storedAt: Long,

    /** 保质期天数，从入库时间起算；必须为正数。 */
    @ColumnInfo(name = "shelf_life_days")
    val shelfLifeDays: Int,

    /** 当前剩余数量，允许小数（如 0.5 kg）。 */
    @ColumnInfo(name = "quantity")
    val quantity: Double,

    /** 数量单位，例如“个”“克”“毫升”“袋”。 */
    @ColumnInfo(name = "unit")
    val unit: String,
)
