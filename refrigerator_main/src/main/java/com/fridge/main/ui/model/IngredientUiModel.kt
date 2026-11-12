package com.fridge.main.ui.model

import com.fridge.main.data.local.entity.Ingredient
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 食材列表/详情页的展示模型。
 *
 * 与 Room 的 [Ingredient] 分开：
 * - Entity 只保存原始字段，保质期天数由入库时间派生；
 * - 本模型承载格式化后的日期、数量、剩余天数和新鲜度状态，UI 不直接做时间/数值计算。
 */
data class IngredientUiModel(
    val id: Long,
    val typeId: Long,
    val name: String,
    val category: String,
    val storedAtMillis: Long,
    val storedAtLabel: String,
    val shelfLifeDays: Int,
    val quantity: Double,
    val unit: String,
    val expiryDateLabel: String,
    val daysLeft: Int,
) {
    val quantityLabel: String get() = "${formatQuantity(quantity)} $unit"

    val status: FreshnessStatus
        get() = when {
            daysLeft < 0 -> FreshnessStatus.EXPIRED
            daysLeft <= 2 -> FreshnessStatus.EXPIRING
            else -> FreshnessStatus.FRESH
        }

    val expiryLabel: String
        get() = when {
            daysLeft > 1 -> "还剩 $daysLeft 天"
            daysLeft == 1 -> "明天到期"
            daysLeft == 0 -> "今天到期"
            else -> "已过期 ${-daysLeft} 天"
        }
}

/**
 * Entity → UI 模型。
 *
 * 日期按设备本地时区解释；剩余天数用“本地日历日”相减，避免直接除以
 * 86400000 在夏令时切换日出现 ±1 天误差。
 */
fun Ingredient.toUiModel(today: LocalDate = LocalDate.now()): IngredientUiModel {
    val storedDate = Instant.ofEpochMilli(storedAt)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    val expiryDate = storedDate.plusDays(shelfLifeDays.toLong())
    val daysLeft = ChronoUnit.DAYS.between(today, expiryDate).toInt()

    return IngredientUiModel(
        id = id,
        typeId = typeId,
        name = name,
        category = category,
        storedAtMillis = storedAt,
        storedAtLabel = storedDate.format(DATE_FORMAT),
        shelfLifeDays = shelfLifeDays,
        quantity = quantity,
        unit = unit,
        expiryDateLabel = expiryDate.format(DATE_FORMAT),
        daysLeft = daysLeft,
    )
}

/** 数量展示：去掉无意义的尾随 0，同时保留 0.5 这类小数。 */
fun formatQuantity(value: Double): String =
    BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
