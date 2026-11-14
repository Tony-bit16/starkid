package com.fridge.main.domain.expiry

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 食材保质期的唯一计算口径。
 *
 * 页面展示与 WorkManager 后台巡检都必须调用这里，避免“UI 认为临期、通知却不发”
 * 这类由两套日期算法造成的边界不一致。
 *
 * 坐标说明：入库时间保存为“当地日期 00:00”对应的时间戳；这里先按设备本地时区
 * 还原为日历日，再用日历日相减，避免直接除 86_400_000 在夏令时切换日出现 ±1 天。
 */
object ExpiryCalculator {

    /** 剩余 0～3 天（含今天到期）统一视为“临期”。 */
    const val EXPIRING_SOON_DAYS = 3

    fun expiryDate(
        storedAt: Long,
        shelfLifeDays: Int,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): LocalDate = Instant.ofEpochMilli(storedAt)
        .atZone(zoneId)
        .toLocalDate()
        .plusDays(shelfLifeDays.toLong())

    fun daysLeft(
        storedAt: Long,
        shelfLifeDays: Int,
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Int = ChronoUnit.DAYS.between(
        today,
        expiryDate(storedAt = storedAt, shelfLifeDays = shelfLifeDays, zoneId = zoneId),
    ).toInt()

    fun isExpiringSoon(daysLeft: Int): Boolean = daysLeft in 0..EXPIRING_SOON_DAYS

    fun isExpired(daysLeft: Int): Boolean = daysLeft < 0
}
