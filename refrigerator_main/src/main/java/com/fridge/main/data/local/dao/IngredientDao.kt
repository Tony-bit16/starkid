package com.fridge.main.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fridge.main.data.local.entity.Ingredient
import kotlinx.coroutines.flow.Flow

/**
 * 食材库存数据访问对象。
 *
 * 所有查询返回 [Flow]，Room 会在表数据变化时自动推送新值；
 * 调用方应在 ViewModel 的 viewModelScope 中收集，禁止在主线程直接执行数据库 IO。
 *
 * 到期时间统一用 SQL 表达式 `stored_at + shelf_life_days * 86400000` 计算，
 * 其中 86400000 为一天的毫秒数。
 */
@Dao
interface IngredientDao {

    /** 按入库时间倒序观察全部食材（首页默认列表）。 */
    @Query("SELECT * FROM ingredients ORDER BY stored_at DESC")
    fun observeAll(): Flow<List<Ingredient>>

    /** 按到期时间从近到远观察全部食材。 */
    @Query(
        """
        SELECT * FROM ingredients
        ORDER BY stored_at + shelf_life_days * 86400000 ASC
        """,
    )
    fun observeAllByExpiry(): Flow<List<Ingredient>>

    /** 观察单个食材；不存在时返回 null。 */
    @Query("SELECT * FROM ingredients WHERE id = :ingredientId")
    fun observeById(ingredientId: Long): Flow<Ingredient?>

    /**
     * 按 id 读取单条食材（挂起查询）。
     *
     * 编辑时需要在事务内先读出旧记录，再更新；Flow 版本不适合一次性读取。
     */
    @Query("SELECT * FROM ingredients WHERE id = :ingredientId LIMIT 1")
    suspend fun findById(ingredientId: Long): Ingredient?

    /** 按名称模糊搜索（用于首页搜索框）。 */
    @Query("SELECT * FROM ingredients WHERE name LIKE '%' || :keyword || '%' ORDER BY stored_at DESC")
    fun observeByName(keyword: String): Flow<List<Ingredient>>

    /**
     * 观察某个食材品类的全部库存批次。
     *
     * 菜谱匹配时先按 type_id 聚合该品类的可用数量，再与菜谱所需数量比较；
     * 这里返回批次明细，聚合查询由 Repository 负责。
     */
    @Query("SELECT * FROM ingredients WHERE type_id = :typeId ORDER BY stored_at ASC")
    fun observeByType(typeId: Long): Flow<List<Ingredient>>

    /** 观察指定时间窗口内到期的食材，供保质期提醒与看板“即将过期”统计使用。 */
    @Query(
        """
        SELECT * FROM ingredients
        WHERE stored_at + shelf_life_days * 86400000 BETWEEN :fromInclusive AND :toInclusive
        ORDER BY stored_at + shelf_life_days * 86400000 ASC
        """,
    )
    fun observeExpiringBetween(fromInclusive: Long, toInclusive: Long): Flow<List<Ingredient>>

    /** 统计指定时间窗口内到期的食材条数。 */
    @Query(
        """
        SELECT COUNT(*) FROM ingredients
        WHERE stored_at + shelf_life_days * 86400000 BETWEEN :fromInclusive AND :toInclusive
        """,
    )
    fun observeExpiringCount(fromInclusive: Long, toInclusive: Long): Flow<Int>

    /** 统计全部食材条数。 */
    @Query("SELECT COUNT(*) FROM ingredients")
    fun observeCount(): Flow<Int>

    /** 新增食材，返回自增主键。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(ingredient: Ingredient): Long

    /** 全量更新食材，返回受影响行数（0 表示 id 不存在）。 */
    @Update
    suspend fun update(ingredient: Ingredient): Int

    /** 删除食材。 */
    @Delete
    suspend fun delete(ingredient: Ingredient)

    /** 按 id 删除，返回受影响行数（0 表示记录已不存在）。 */
    @Query("DELETE FROM ingredients WHERE id = :ingredientId")
    suspend fun deleteById(ingredientId: Long): Int
}
