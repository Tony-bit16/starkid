package com.fridge.dashboard.data.repository

import com.fridge.dashboard.data.model.DashboardIngredient
import com.fridge.dashboard.data.provider.IngredientProviderClient
import kotlinx.coroutines.flow.Flow

/**
 * 看板数据仓库：只负责跨 App 读取，不缓存、不写主 App 数据库。
 */
interface DashboardRepository {
    /** 一次性读取，导出前必须重新调用，避免使用页面旧快照。 */
    suspend fun loadIngredients(): Result<List<DashboardIngredient>>

    /** 观察主 App 食材变化；首次订阅会立即查询一次。 */
    fun observeIngredients(): Flow<Result<List<DashboardIngredient>>>
}

class DefaultDashboardRepository(
    private val client: IngredientProviderClient,
) : DashboardRepository {

    override suspend fun loadIngredients(): Result<List<DashboardIngredient>> {
        return client.loadIngredients()
    }

    override fun observeIngredients(): Flow<Result<List<DashboardIngredient>>> =
        client.observeIngredients()
}
