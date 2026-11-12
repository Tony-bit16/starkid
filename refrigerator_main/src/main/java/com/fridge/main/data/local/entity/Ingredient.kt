package com.fridge.main.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 冰箱中的一条食材库存记录（一次入库批次）。
 *
 * 说明：
 * - 一条记录代表“一次入库”的食材，而不是一种食材字典；同名食材可以有多条记录，
 *   例如两次购买西红柿可分别记录不同的入库时间与数量。
 * - [typeId] 指向稳定的食材品类 [IngredientType]，菜谱匹配、分类统计都以它为准；
 *   [name] 与 [category] 是入库时的展示快照，避免品类改名后历史记录难以辨认。
 * - 到期时间不落库，由 [storedAt] + [shelfLifeDays] 派生，避免两份数据不一致。
 * - 时间统一使用 Unix 毫秒时间戳（UTC），展示时再按本地时区格式化。
 */
@Entity(
    tableName = "ingredients",
    foreignKeys = [
        ForeignKey(
            entity = IngredientType::class,
            parentColumns = ["id"],
            childColumns = ["type_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["type_id"]),
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

    /**
     * 所属食材品类 id，外键 → [IngredientType.id]。
     *
     * 删除品类前必须先确认没有库存或菜谱引用（RESTRICT），避免出现孤儿数据。
     */
    @ColumnInfo(name = "type_id")
    val typeId: Long,

    /** 食材名称快照，例如“西红柿”“鸡蛋”；展示时优先使用该值。 */
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
