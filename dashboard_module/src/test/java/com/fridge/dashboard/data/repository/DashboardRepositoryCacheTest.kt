package com.fridge.dashboard.data.repository

import com.fridge.dashboard.data.model.DashboardIngredient
import com.fridge.dashboard.data.provider.DashboardIngredientSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 缓存策略单测：不依赖真实 ContentProvider，用假数据源统计查询次数。
 */
class DashboardRepositoryCacheTest {

    @Test
    fun `repeated load uses cache until forced or invalidated`() = runBlocking {
        val source = FakeIngredientSource()
        val repository = DefaultDashboardRepository(source)

        assertTrue(repository.loadIngredients().isSuccess)
        assertEquals(1, source.loadCalls)

        // TTL 内的第二次普通读取应直接命中缓存。
        assertTrue(repository.loadIngredients().isSuccess)
        assertEquals(1, source.loadCalls)

        // 手动刷新必须绕过缓存。
        repository.loadIngredients(forceRefresh = true)
        assertEquals(2, source.loadCalls)

        // 清空缓存后再次普通读取会访问数据源。
        repository.invalidateCache()
        repository.loadIngredients()
        assertEquals(3, source.loadCalls)
    }

    @Test
    fun `failed query clears previous cache`() = runBlocking {
        val source = FakeIngredientSource()
        val repository = DefaultDashboardRepository(source)

        repository.loadIngredients()
        assertEquals(1, source.loadCalls)

        source.result = Result.failure(IllegalStateException("provider down"))
        repository.loadIngredients(forceRefresh = true)
        assertEquals(2, source.loadCalls)

        // 失败后的下一次普通读取不能被旧缓存掩盖。
        source.result = Result.success(listOf(sampleIngredient))
        repository.loadIngredients()
        assertEquals(3, source.loadCalls)
    }

    private class FakeIngredientSource : DashboardIngredientSource {
        var loadCalls = 0
            private set
        var result: Result<List<DashboardIngredient>> = Result.success(listOf(sampleIngredient))

        override suspend fun loadIngredients(): Result<List<DashboardIngredient>> {
            loadCalls++
            return result
        }

        override fun observeIngredientChanges(): Flow<Unit> = emptyFlow()
    }

    private companion object {
        val sampleIngredient = DashboardIngredient(
            id = 1L,
            typeId = 1L,
            name = "milk",
            category = "Dairy",
            storedAt = 0L,
            shelfLifeDays = 7,
            quantity = 1.0,
            unit = "L",
            storedAtLabel = "2026-11-22",
            quantityLabel = "1",
        )
    }
}
