package com.fridge.dashboard.domain.export

import com.fridge.dashboard.data.model.DashboardIngredient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DashboardCsvFormatterTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 11, 20)

    @Test
    fun `formats header and ingredient row with bom and crlf`() {
        val csv = DashboardCsvFormatter.format(
            ingredients = listOf(ingredient(name = "番茄", category = "蔬菜", quantity = 2.50)),
            today = today,
            zoneId = zone,
        )

        assertTrue(csv.startsWith("\uFEFF食材名称,分类,数量,单位,入库时间,剩余保质期(天),过期状态\r\n"))
        assertTrue(csv.contains("番茄,蔬菜,2.5,kg,2026-11-15,2,临期\r\n"))
    }

    @Test
    fun `utf8 bytes start with bom and keep chinese readable`() {
        val csv = DashboardCsvFormatter.format(
            ingredients = listOf(ingredient(name = "番茄", category = "蔬菜")),
            today = today,
            zoneId = zone,
        )

        val bytes = csv.toByteArray(Charsets.UTF_8)
        assertArrayEquals(
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()),
            bytes.copyOfRange(0, 3),
        )
        // 用 UTF-8 重新解码后中文不应乱码。
        assertTrue(String(bytes, Charsets.UTF_8).contains("番茄,蔬菜"))
    }

    @Test
    fun `escapes commas quotes and newlines`() {
        val csv = DashboardCsvFormatter.format(
            ingredients = listOf(
                ingredient(
                    name = "番茄,大",
                    category = "叶\"菜",
                    unit = "kg",
                ),
            ),
            today = today,
            zoneId = zone,
        )

        assertTrue(csv.contains("\"番茄,大\",\"叶\"\"菜\",1,kg,2026-11-15,2,临期"))
    }

    @Test
    fun `marks expired ingredients and keeps zero days as expiring`() {
        val csv = DashboardCsvFormatter.format(
            ingredients = listOf(
                ingredient(name = "牛奶", category = "乳制品", shelfLifeDays = 5),
                ingredient(name = "酸奶", category = "乳制品", shelfLifeDays = 1),
            ),
            today = today,
            zoneId = zone,
        )

        assertTrue(csv.contains("牛奶,乳制品,1,kg,2026-11-15,0,临期"))
        assertTrue(csv.contains("酸奶,乳制品,1,kg,2026-11-15,-4,已过期"))
    }

    @Test
    fun `status boundaries match main app`() {
        assertEquals("临期", DashboardCsvFormatter.statusLabel(0))
        assertEquals("临期", DashboardCsvFormatter.statusLabel(3))
        assertEquals("新鲜", DashboardCsvFormatter.statusLabel(4))
        assertEquals("已过期", DashboardCsvFormatter.statusLabel(-1))
    }

    @Test
    fun `quantity removes meaningless trailing zeros`() {
        assertEquals("2", DashboardCsvFormatter.formatQuantity(2.0))
        assertEquals("2.5", DashboardCsvFormatter.formatQuantity(2.50))
        assertEquals("0.25", DashboardCsvFormatter.formatQuantity(0.250))
    }

    private fun ingredient(
        name: String,
        category: String,
        quantity: Double = 1.0,
        unit: String = "kg",
        shelfLifeDays: Int = 7,
    ) = DashboardIngredient(
        id = 1,
        typeId = 1,
        name = name,
        category = category,
        storedAt = LocalDate.of(2026, 11, 15)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli(),
        shelfLifeDays = shelfLifeDays,
        quantity = quantity,
        unit = unit,
        storedAtLabel = "2026-11-15",
        quantityLabel = DashboardCsvFormatter.formatQuantity(quantity),
    )
}
