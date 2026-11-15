package com.fridge.dashboard.domain.export

import com.fridge.dashboard.data.model.DashboardIngredient
import com.fridge.dashboard.domain.stats.DashboardStatsCalculator
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 把看板中的食材投影格式化为 CSV。
 *
 * 约定：
 * - UTF-8 BOM，Excel 直接打开中文不乱码；
 * - CRLF 换行，兼容 Windows/Excel；
 * - 所有字段按 RFC 4180 转义，包含逗号、引号或换行的字段会加双引号；
 * - “数量”只格式化单条库存的 quantity，不做跨单位求和。
 */
object DashboardCsvFormatter {

    const val FILE_PREFIX = "fridge_inventory_"

    private const val BOM = "\uFEFF"
    private const val CRLF = "\r\n"

    private val header = listOf(
        "食材名称",
        "分类",
        "数量",
        "单位",
        "入库时间",
        "剩余保质期(天)",
        "过期状态",
    )

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun format(
        ingredients: List<DashboardIngredient>,
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val builder = StringBuilder(BOM)
        builder.append(header.joinToString(",") { escape(it) }).append(CRLF)

        ingredients.forEach { ingredient ->
            val daysLeft = DashboardStatsCalculator.daysLeft(ingredient, today, zoneId)
            val storedDate = Instant.ofEpochMilli(ingredient.storedAt)
                .atZone(zoneId)
                .toLocalDate()

            builder.append(
                listOf(
                    ingredient.name,
                    ingredient.category.ifBlank { "未分类" },
                    formatQuantity(ingredient.quantity),
                    ingredient.unit,
                    storedDate.format(dateFormatter),
                    daysLeft.toString(),
                    statusLabel(daysLeft),
                ).joinToString(",") { escape(it) },
            ).append(CRLF)
        }

        return builder.toString()
    }

    /** 与主 App 一致：0..3 天临期，负数已过期，其余新鲜。 */
    fun statusLabel(daysLeft: Int): String = when {
        daysLeft < 0 -> "已过期"
        daysLeft <= DashboardStatsCalculator.EXPIRING_SOON_DAYS -> "临期"
        else -> "新鲜"
    }

    /** 去掉 Double 的无意义尾零，例如 2.0 -> 2、2.50 -> 2.5。 */
    fun formatQuantity(quantity: Double): String {
        if (quantity.isNaN() || quantity.isInfinite()) return quantity.toString()
        return BigDecimal.valueOf(quantity).stripTrailingZeros().toPlainString()
    }

    /** RFC 4180 转义：字段内有逗号、双引号或换行时，用双引号包裹并双写引号。 */
    fun escape(value: String): String {
        val needsQuoting = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuoting) return value
        return "\"${value.replace("\"", "\"\"")}\""
    }
}
