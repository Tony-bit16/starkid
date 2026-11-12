package com.fridge.main.ui.screens.ingredients

import com.fridge.main.data.repository.IngredientDraft
import com.fridge.main.ui.model.IngredientUiModel
import com.fridge.main.ui.model.formatQuantity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * 新增/编辑食材共用的表单状态。
 *
 * 所有输入先保留为字符串，提交时统一校验，避免输入过程中的中间值
 * 被 `toInt()` 等操作直接抛异常。错误信息跟随字段展示，不用 Toast 打断填写。
 */
data class IngredientFormState(
    val name: String = "",
    val category: String = "",
    val storedAtMillis: Long = startOfTodayMillis(),
    val shelfLifeDays: String = "",
    val quantity: String = "",
    val unit: String = "",
    val nameError: String? = null,
    val categoryError: String? = null,
    val storedAtError: String? = null,
    val shelfLifeDaysError: String? = null,
    val quantityError: String? = null,
    val unitError: String? = null,
) {
    val storedAtLabel: String
        get() = Instant.ofEpochMilli(storedAtMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DATE_FORMAT)

    val hasErrors: Boolean
        get() = listOf(
            nameError,
            categoryError,
            storedAtError,
            shelfLifeDaysError,
            quantityError,
            unitError,
        ).any { it != null }

    /**
     * 校验并返回带错误信息的新状态。
     *
     * 仓库层还会再校验一次；这里负责即时反馈，仓库层负责防止绕过 UI 的非法数据。
     */
    fun validated(nowMillis: Long = System.currentTimeMillis()): IngredientFormState {
        val cleanName = name.trim()
        val cleanCategory = category.trim()
        val cleanUnit = unit.trim()
        val days = shelfLifeDays.trim().toIntOrNull()
        val amount = quantity.trim().toDoubleOrNull()

        return copy(
            nameError = when {
                cleanName.isEmpty() -> "请输入食材名称"
                cleanName.length > 30 -> "名称不能超过 30 个字符"
                else -> null
            },
            categoryError = when {
                cleanCategory.isEmpty() -> "请输入食材分类"
                cleanCategory.length > 20 -> "分类不能超过 20 个字符"
                else -> null
            },
            storedAtError = when {
                storedAtMillis <= 0 -> "请选择入库时间"
                storedAtMillis > nowMillis -> "入库时间不能晚于当前时间"
                else -> null
            },
            shelfLifeDaysError = when {
                days == null -> "请输入整数天数"
                days <= 0 -> "保质期必须大于 0 天"
                days > 36500 -> "保质期不能超过 36500 天"
                else -> null
            },
            quantityError = when {
                amount == null || !amount.isFinite() -> "请输入有效数量"
                amount <= 0 -> "数量必须大于 0"
                amount > 1_000_000 -> "数量超出可记录范围"
                else -> null
            },
            unitError = when {
                cleanUnit.isEmpty() -> "请输入数量单位"
                cleanUnit.length > 10 -> "单位不能超过 10 个字符"
                else -> null
            },
        )
    }

    /** 校验通过后转成 Repository 的写入 DTO；失败时返回 null（前提是已调用 [validated]）。 */
    fun toDraft(): IngredientDraft? {
        if (hasErrors) return null
        val days = shelfLifeDays.trim().toIntOrNull() ?: return null
        val amount = quantity.trim().toDoubleOrNull() ?: return null
        return IngredientDraft(
            name = name.trim(),
            category = category.trim(),
            storedAt = storedAtMillis,
            shelfLifeDays = days,
            quantity = amount,
            unit = unit.trim(),
        )
    }

    companion object {
        /** 新增页默认值：今天入库、空名称/分类/数量。 */
        fun newInstance(): IngredientFormState = IngredientFormState()

        /**
         * 详情页进入编辑时回填。
         *
         * 数量用去尾零的格式，避免 3.0 回填成 "3.0" 后用户再次保存时困惑。
         */
        fun from(ingredient: IngredientUiModel): IngredientFormState = IngredientFormState(
            name = ingredient.name,
            category = ingredient.category,
            storedAtMillis = ingredient.storedAtMillis,
            shelfLifeDays = ingredient.shelfLifeDays.toString(),
            quantity = formatQuantity(ingredient.quantity),
            unit = ingredient.unit,
        )
    }
}

/** 本地当天 00:00 的时间戳，作为新增食材的默认入库时间。 */
fun startOfTodayMillis(): Long =
    LocalDate.now()
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

/** Material3 DatePicker 返回 UTC 日期，这里转成设备本地时区的当天 00:00。 */
fun utcDateToLocalStartOfDay(utcDateMillis: Long): Long =
    Instant.ofEpochMilli(utcDateMillis)
        .atZone(ZoneId.of("UTC"))
        .toLocalDate()
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

/** 本地日期 00:00 转成 DatePicker 需要的 UTC 日期。 */
fun localStartOfDayToUtcDate(localMillis: Long): Long =
    Instant.ofEpochMilli(localMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .atStartOfDay(ZoneId.of("UTC"))
        .toInstant()
        .toEpochMilli()

/** 供 UI 断言/调试使用的“同一天”判断。 */
fun isSameLocalDay(firstMillis: Long, secondMillis: Long): Boolean =
    abs(firstMillis - secondMillis) < 86_400_000L &&
        Instant.ofEpochMilli(firstMillis).atZone(ZoneId.systemDefault()).toLocalDate() ==
        Instant.ofEpochMilli(secondMillis).atZone(ZoneId.systemDefault()).toLocalDate()

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA)
