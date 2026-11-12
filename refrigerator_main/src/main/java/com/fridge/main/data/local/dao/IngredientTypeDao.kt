package com.fridge.main.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fridge.main.data.local.entity.IngredientType
import kotlinx.coroutines.flow.Flow

/**
 * 食材品类（主数据）数据访问对象。
 *
 * 本 DAO 只负责品类表的读写；把“按名称查找，不存在则创建”的事务性逻辑
 * 留在 Repository，避免 DAO 承担业务规则。
 */
@Dao
interface IngredientTypeDao {

    /** 按分类、名称观察全部品类，供新增食材/菜谱时的选择器使用。 */
    @Query("SELECT * FROM ingredient_types ORDER BY category ASC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<IngredientType>>

    /** 按规范化名称精确查找；用于新增库存/菜谱时复用已有品类。 */
    @Query("SELECT * FROM ingredient_types WHERE normalized_name = :normalizedName LIMIT 1")
    suspend fun findByNormalizedName(normalizedName: String): IngredientType?

    /** 按 id 查询单个品类。 */
    @Query("SELECT * FROM ingredient_types WHERE id = :typeId")
    suspend fun findById(typeId: Long): IngredientType?

    /**
     * 新增品类，返回自增主键。
     *
     * 唯一索引冲突时抛异常，由 Repository 捕获后回查已有记录，
     * 不在这里使用 REPLACE，避免误删被库存/菜谱引用的品类。
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(type: IngredientType): Long

    /** 更新品类展示信息。 */
    @Update
    suspend fun update(type: IngredientType)

    /** 删除品类；存在库存或菜谱引用时会被外键 RESTRICT 拒绝。 */
    @Delete
    suspend fun delete(type: IngredientType)
}
