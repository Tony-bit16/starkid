package com.fridge.dashboard.domain.stats

import com.fridge.dashboard.data.model.DashboardIngredient
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardStatsCalculatorTest {

    private val today = LocalDate.of(2026, 11, 15)

    @Test
    fun `counts total expiring expired and categories`() {
        val ingredients = listOf(
            ingredient(
                name = "番茄",
                category = "蔬菜",
                storedDate = LocalDate.of(2026, 11, 15),
                shelfLifeDays = 10,
            ),
            ingredient(
                name = "牛奶",
                category = "乳制品",
                storedDate = LocalDate.of(2026, 11, 12),
                shelfLifeDays = 3,
            ),
            ingredient(
                name = "酸奶",
                category = "乳制品",
                storedDate = LocalDate.of(2026, 11, 10),
                shelfLifeDays = 1,
            ),
            ingredient(
                name = "鸡蛋",
                category = "乳制品",
                storedDate = LocalDate.of(2026, 11, 15),
                shelfLifeDays = 3,
            ),
        )

        val stats = DashboardStatsCalculator.calculate(
            ingredients = ingredients,
            today = today,
            zoneId = ZoneOffset.UTC,
        )

        assertEquals(4, stats.totalCount)
        assertEquals(2, stats.expiringCount)
        assertEquals(1, stats.expiredCount)
        assertEquals(
            listOf("乳制品" to 3, "蔬菜" to 1),
            stats.categories.map { it.name to it.count },
        )
        assertEquals(
            listOf(1, 0, 0, 1, 0, 0, 0),
            stats.trend.map { it.count },
        )
    }

    @Test
    fun `trend includes today through day six and excludes day seven`() {
        val ingredients = listOf(
            ingredient("今天", "蔬菜", LocalDate.of(2026, 11, 15), 0),
            ingredient("第六天", "蔬菜", LocalDate.of(2026, 11, 15), 6),
            ingredient("第七天", "蔬菜", LocalDate.of(2026, 11, 15), 7),
            ingredient("昨天", "蔬菜", LocalDate.of(2026, 11, 14), 0),
        )

        val stats = DashboardStatsCalculator.calculate(
            ingredients = ingredients,
            today = today,
            zoneId = ZoneOffset.UTC,
        )

        assertEquals(
            listOf(1, 0, 0, 0, 0, 0, 1),
            stats.trend.map { it.count },
        )
        assertEquals(
            listOf("11/15", "11/16", "11/17", "11/18", "11/19", "11/20", "11/21"),
            stats.trend.map { it.label },
        )
    }

    @Test
    fun `trend is seven zero points when nothing expires soon`() {
        val stats = DashboardStatsCalculator.calculate(
            ingredients = listOf(
                ingredient("长期", "蔬菜", LocalDate.of(2026, 11, 15), 30),
            ),
            today = today,
            zoneId = ZoneOffset.UTC,
        )

        assertEquals(7, stats.trend.size)
        assertEquals(0, stats.trend.sumOf { it.count })
    }

    @Test
    fun `blank category falls back to uncategorized and categories are sorted`() {
        val ingredients = listOf(
            ingredient("A", "", LocalDate.of(2026, 11, 15), 1),
            ingredient("B", "", LocalDate.of(2026, 11, 15), 1),
            ingredient("C", "蔬菜", LocalDate.of(2026, 11, 15), 1),
            ingredient("D", "肉类", LocalDate.of(2026, 11, 15), 1),
        )

        val stats = DashboardStatsCalculator.calculate(
            ingredients = ingredients,
            today = today,
            zoneId = ZoneOffset.UTC,
        )

        assertEquals(
            listOf("未分类" to 2, "肉类" to 1, "蔬菜" to 1),
            stats.categories.map { it.name to it.count },
        )
    }

    @Test
    fun `days left crosses month boundary`() {
        val ingredient = ingredient(
            name = "豆腐",
            category = "豆制品",
            storedDate = LocalDate.of(2026, 10, 30),
            shelfLifeDays = 5,
        )

        val daysLeft = DashboardStatsCalculator.daysLeft(
            ingredient = ingredient,
            today = LocalDate.of(2026, 11, 2),
            zoneId = ZoneOffset.UTC,
        )

        assertEquals(2, daysLeft)
    }

    private fun ingredient(
        name: String,
        category: String,
        storedDate: LocalDate,
        shelfLifeDays: Int,
    ): DashboardIngredient {
        return DashboardIngredient(
            id = name.hashCode().toLong(),
            typeId = category.hashCode().toLong(),
            name = name,
            category = category,
            storedAt = storedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            shelfLifeDays = shelfLifeDays,
            quantity = 1.0,
            unit = "份",
            storedAtLabel = storedDate.toString(),
            quantityLabel = "1",
        )
    }
}
