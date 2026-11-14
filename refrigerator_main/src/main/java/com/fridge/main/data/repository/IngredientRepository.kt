package com.fridge.main.data.repository

import com.fridge.main.data.local.entity.Ingredient
import kotlinx.coroutines.flow.Flow

/**
 * 食材库存仓库契约。
 *
 * 职责：
 * - 组合 IngredientDao 与 IngredientTypeDao；
 * - 校验名称/分类/数量/保质期；
 * - 把“名称规范化 → 查找或创建品类 → 写入库存批次”放在同一数据库事务中；
 * - 对外暴露 Flow / suspend，保证调用方不在主线程做 IO。
 *
 * 线程约定：
 * - 所有 suspend 方法由实现方切换到 Room 的 IO 执行器或 Dispatchers.IO；
 * - 返回的 Flow 由 Room 在查询执行器上发射，UI 只在 viewModelScope 中收集。
 */
interface IngredientRepository {

    /** 按入库时间倒序观察全部库存批次。 */
    fun observeIngredients(): Flow<List<Ingredient>>

    /** 观察单个库存批次；不存在时发射 null。 */
    fun observeIngredient(ingredientId: Long): Flow<Ingredient?>

    /** 观察未来 [withinDays] 天内到期的库存，供首页提醒卡与看板使用。 */
    fun observeExpiring(withinDays: Int): Flow<List<Ingredient>>

    /** 一次性读取全部库存，供 WorkManager 后台检查使用。 */
    suspend fun getAllIngredients(): List<Ingredient>

    /** 新增一批食材；成功后返回新库存 id。 */
    suspend fun addIngredient(draft: IngredientDraft): Result<Long>

    /** 更新指定库存批次；id 不存在时返回失败。 */
    suspend fun updateIngredient(ingredientId: Long, draft: IngredientDraft): Result<Unit>

    /** 删除库存批次；不影响菜谱定义与食材品类。 */
    suspend fun deleteIngredient(ingredientId: Long): Result<Unit>
}

/**
 * 新增/编辑食材的表单数据。
 *
 * 与 [Ingredient] 分开，避免 UI 直接构造带 id/typeId 的数据库实体；
 * typeId 由 Repository 根据规范化名称查找或创建。
 */
data class IngredientDraft(
    val name: String,
    val category: String,
    val storedAt: Long,
    val shelfLifeDays: Int,
    val quantity: Double,
    val unit: String,
)
