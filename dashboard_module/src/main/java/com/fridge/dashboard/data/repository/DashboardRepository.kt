package com.fridge.dashboard.data.repository

import com.fridge.dashboard.data.model.DashboardIngredient
import com.fridge.dashboard.data.provider.DashboardIngredientSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * 看板数据仓库：只负责跨 App 读取和短时缓存，不写主 App 数据库。
 *
 * 缓存只保存成功结果，默认 60 秒；手动刷新和主 App 的变更通知会强制绕过缓存。
 */
interface DashboardRepository {
    /**
     * 一次性读取；[forceRefresh] 为 true 时跳过缓存。
     *
     * 导出前必须使用 `forceRefresh = true`，避免导出页面旧快照。
     */
    suspend fun loadIngredients(forceRefresh: Boolean = false): Result<List<DashboardIngredient>>

    /**
     * 观察主 App 食材变化。
     *
     * 首次订阅优先发射缓存（若仍在 TTL 内），否则立即查询；
     * 之后主 App 的 `notifyChange` 会强制重新查询。
     */
    fun observeIngredients(): Flow<Result<List<DashboardIngredient>>>

    /** 手动刷新前清空缓存，保证下一次订阅真正访问 Provider。 */
    fun invalidateCache()
}

class DefaultDashboardRepository(
    private val client: DashboardIngredientSource,
) : DashboardRepository {

    private data class CacheEntry(
        val ingredients: List<DashboardIngredient>,
        val savedAtMillis: Long,
    )

    @Volatile
    private var cache: CacheEntry? = null

    override suspend fun loadIngredients(
        forceRefresh: Boolean,
    ): Result<List<DashboardIngredient>> {
        if (!forceRefresh) {
            cache?.takeIf { isFresh(it) }?.let { entry ->
                return Result.success(entry.ingredients)
            }
        }

        val result = client.loadIngredients()
        result.fold(
            onSuccess = { ingredients ->
                cache = CacheEntry(
                    ingredients = ingredients,
                    savedAtMillis = nowMillis(),
                )
            },
            onFailure = {
                // 查询失败时清掉旧值，避免“错误页背后的下一次打开又显示旧数据”。
                cache = null
            },
        )
        return result
    }

    override fun observeIngredients(): Flow<Result<List<DashboardIngredient>>> = flow {
        emit(loadIngredients())
        emitAll(
            client.observeIngredientChanges().map {
                loadIngredients(forceRefresh = true)
            },
        )
    }

    override fun invalidateCache() {
        cache = null
    }

    private fun isFresh(entry: CacheEntry): Boolean =
        nowMillis() - entry.savedAtMillis < CACHE_TTL_MILLIS

    /** 使用单调时钟，避免用户调整系统时间后缓存被永久命中或立即过期。 */
    private fun nowMillis(): Long = System.nanoTime() / 1_000_000

    private companion object {
        const val CACHE_TTL_MILLIS = 60_000L
    }
}
