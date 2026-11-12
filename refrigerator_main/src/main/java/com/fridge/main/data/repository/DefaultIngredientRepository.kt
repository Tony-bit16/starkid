package com.fridge.main.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.fridge.main.data.local.FridgeDatabase
import com.fridge.main.data.local.entity.Ingredient
import com.fridge.main.data.local.entity.IngredientType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.util.Locale

/**
 * 食材仓库的 Room 实现。
 *
 * 设计要点：
 * - 通过构造参数传入 [FridgeDatabase] 提供者，而不是直接持有数据库实例：
 *   数据库首次打开失败时，异常会在具体查询/写入时抛出，由 ViewModel 转成错误态，
 *   不会在 Application 初始化阶段直接崩溃。
 * - “按名称查品类，没有就创建”与“写库存”放在同一个 Room 事务里，
 *   避免并发新增同名食材时产生重复品类或半成品数据。
 * - 所有 suspend 方法都通过 Room 的事务/查询执行器执行；调用方仍在
 *   viewModelScope 中调用即可，主线程不会执行 SQLite IO。
 */
class DefaultIngredientRepository(
    private val databaseProvider: () -> FridgeDatabase,
) : IngredientRepository {

    private val database: FridgeDatabase
        get() = databaseProvider()

    override fun observeIngredients(): Flow<List<Ingredient>> = flow {
        // 数据库在 Flow 被收集时才打开；打开失败会进入 ViewModel 的 catch。
        emitAll(database.ingredientDao().observeAll())
    }

    override fun observeIngredient(ingredientId: Long): Flow<Ingredient?> = flow {
        emitAll(database.ingredientDao().observeById(ingredientId))
    }

    override fun observeExpiring(withinDays: Int): Flow<List<Ingredient>> = flow {
        val now = System.currentTimeMillis()
        val windowEnd = now + withinDays.coerceAtLeast(0) * ONE_DAY_MILLIS
        emitAll(database.ingredientDao().observeExpiringBetween(now, windowEnd))
    }

    override suspend fun addIngredient(draft: IngredientDraft): Result<Long> = dataResult {
        val clean = draft.validated()
        database.withTransaction {
            val type = findOrCreateType(clean)
            database.ingredientDao().insert(
                Ingredient(
                    typeId = type.id,
                    name = clean.name,
                    category = clean.category,
                    storedAt = clean.storedAt,
                    shelfLifeDays = clean.shelfLifeDays,
                    quantity = clean.quantity,
                    unit = clean.unit,
                ),
            )
        }
    }

    override suspend fun updateIngredient(
        ingredientId: Long,
        draft: IngredientDraft,
    ): Result<Unit> = dataResult {
        require(ingredientId > 0) { "食材 id 无效" }
        val clean = draft.validated()

        database.withTransaction {
            val existing = database.ingredientDao().findById(ingredientId)
                ?: throw NoSuchElementException("食材不存在或已被删除")

            val type = findOrCreateType(clean)
            val updatedRows = database.ingredientDao().update(
                existing.copy(
                    typeId = type.id,
                    name = clean.name,
                    category = clean.category,
                    storedAt = clean.storedAt,
                    shelfLifeDays = clean.shelfLifeDays,
                    quantity = clean.quantity,
                    unit = clean.unit,
                ),
            )
            if (updatedRows != 1) {
                throw IllegalStateException("食材更新失败，请重试")
            }
        }
    }

    override suspend fun deleteIngredient(ingredientId: Long): Result<Unit> = dataResult {
        require(ingredientId > 0) { "食材 id 无效" }
        val deletedRows = database.ingredientDao().deleteById(ingredientId)
        if (deletedRows == 0) {
            throw NoSuchElementException("食材不存在或已被删除")
        }
    }

    /**
     * 复用已有品类，或在当前事务内创建新品类。
     *
     * 唯一索引是最后一道并发保护：两个协程同时插入同名品类时，
     * 后插入者会收到 [SQLiteConstraintException]，此时回查已存在的记录即可。
     */
    private suspend fun findOrCreateType(draft: CleanIngredientDraft): IngredientType {
        val typeDao = database.ingredientTypeDao()
        val normalizedName = normalizeName(draft.name)

        typeDao.findByNormalizedName(normalizedName)?.let { return it }

        return try {
            val id = typeDao.insert(
                IngredientType(
                    name = draft.name,
                    normalizedName = normalizedName,
                    category = draft.category,
                    defaultUnit = draft.unit,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            typeDao.findById(id)
                ?: throw IllegalStateException("食材品类创建后读取失败")
        } catch (error: SQLiteConstraintException) {
            // 并发插入同名品类：回查另一个事务已经写入的记录。
            typeDao.findByNormalizedName(normalizedName) ?: throw error
        }
    }

    /**
     * 统一校验并清洗表单数据。
     *
     * 这里是最后一道防线：即使 UI 绕过校验直接调用 Repository，也不会把
     * 空名称、负数数量或非法日期写入数据库。
     */
    private fun IngredientDraft.validated(): CleanIngredientDraft {
        val cleanName = name.trim()
        val cleanCategory = category.trim()
        val cleanUnit = unit.trim()

        require(cleanName.isNotEmpty()) { "食材名称不能为空" }
        require(cleanName.length <= 30) { "食材名称不能超过 30 个字符" }
        require(cleanCategory.isNotEmpty()) { "请选择或填写食材分类" }
        require(cleanCategory.length <= 20) { "食材分类不能超过 20 个字符" }
        require(cleanUnit.isNotEmpty()) { "数量单位不能为空" }
        require(cleanUnit.length <= 10) { "数量单位不能超过 10 个字符" }
        require(shelfLifeDays > 0) { "保质期天数必须大于 0" }
        require(shelfLifeDays <= MAX_SHELF_LIFE_DAYS) { "保质期天数不能超过 36500 天" }
        require(quantity.isFinite() && quantity > 0) { "数量必须大于 0" }
        require(quantity <= MAX_QUANTITY) { "数量超出可记录范围" }
        require(storedAt > 0) { "入库时间无效" }
        require(storedAt <= System.currentTimeMillis()) { "入库时间不能晚于当前时间" }

        return CleanIngredientDraft(
            name = cleanName,
            category = cleanCategory,
            storedAt = storedAt,
            shelfLifeDays = shelfLifeDays,
            quantity = quantity,
            unit = cleanUnit,
        )
    }

    /**
     * 统一包装数据库/校验异常。
     *
     * 不吞掉 [CancellationException]：页面退出或 ViewModel 销毁时协程需要正常取消。
     */
    private suspend fun <T> dataResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private data class CleanIngredientDraft(
        val name: String,
        val category: String,
        val storedAt: Long,
        val shelfLifeDays: Int,
        val quantity: Double,
        val unit: String,
    )

    private companion object {
        const val ONE_DAY_MILLIS = 86_400_000L
        const val MAX_SHELF_LIFE_DAYS = 36_500
        const val MAX_QUANTITY = 1_000_000.0

        /**
         * 名称匹配键：去首尾空白、折叠连续空白、转小写。
         *
         * 中文没有大小写，但英文食材名（如“Milk / milk”）需要归并；
         * 全角/半角归一化留到后续品类别名功能，不在本轮过度实现。
         */
        fun normalizeName(name: String): String = name
            .trim()
            .replace(WHITESPACE_REGEX, " ")
            .lowercase(Locale.ROOT)

        val WHITESPACE_REGEX = Regex("\\s+")
    }
}
