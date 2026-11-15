package com.fridge.dashboard.data.repository

import com.fridge.dashboard.data.model.DashboardIngredient
import com.fridge.dashboard.data.provider.IngredientProviderClient

/**
 * 看板数据仓库：只负责跨 App 读取，不缓存、不写主 App 数据库。
 */
interface DashboardRepository {
    suspend fun loadIngredients(): Result<List<DashboardIngredient>>
}

class DefaultDashboardRepository(
    private val client: IngredientProviderClient,
) : DashboardRepository {

    override suspend fun loadIngredients(): Result<List<DashboardIngredient>> {
        return client.loadIngredients()
    }
}
