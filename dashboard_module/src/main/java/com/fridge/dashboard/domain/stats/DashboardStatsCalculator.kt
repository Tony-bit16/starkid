package com.fridge.dashboard.domain.stats

import com.fridge.dashboard.data.model.DashboardIngredient
import com.fridge.dashboard.ui.model.CategoryStat
import com.fridge.dashboard.ui.model.DashboardStats
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 看板统计口径。
 *
 * 主 App 的保质期按“本地日历日”计算，看板必须保持一致：
 * 到期日 = 入库日的本地日期 + 保质期天数；
 * 剩余天数 = 到期日 - 今天。
 *
 * 不把 quantity 直接相加：不同食材单位不同（kg、L、个、包），
 * 相加没有业务意义。因此分类占比按“库存条目数”统计。
 */
object DashboardStatsCalculator {

    const val EXPIRING_SOON_DAYS = 3

    fun calculate(
        ingredients: List<DashboardIngredient>,
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): DashboardStats {
        val daysLeftList = ingredients.map { daysLeft(it, today, zoneId) }
        val categoryCounts = ingredients
            .groupingBy { it.category.ifBlank { "未分类" } }
            .eachCount()

        return DashboardStats(
            totalCount = ingredients.size,
            expiringCount = daysLeftList.count { it in 0..EXPIRING_SOON_DAYS },
            expiredCount = daysLeftList.count { it < 0 },
            categories = categoryCounts
                .map { (category, count) -> CategoryStat(name = category, count = count) }
                .sortedWith(compareByDescending<CategoryStat> { it.count }.thenBy { it.name }),
        )
    }

    /** 返回剩余天数；负数表示已经过期。 */
    fun daysLeft(
        ingredient: DashboardIngredient,
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Int {
        val storedDate = Instant.ofEpochMilli(ingredient.storedAt)
            .atZone(zoneId)
            .toLocalDate()
        val expiryDate = storedDate.plusDays(ingredient.shelfLifeDays.toLong())
        return ChronoUnit.DAYS.between(today, expiryDate).toInt()
    }

    /** 返回到期日的本地日期，便于后续导出或详情展示复用。 */
    fun expiryDate(
        ingredient: DashboardIngredient,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): LocalDate {
        val storedDate = Instant.ofEpochMilli(ingredient.storedAt)
            .atZone(zoneId)
            .toLocalDate()
        return storedDate.plusDays(ingredient.shelfLifeDays.toLong())
    }
}
