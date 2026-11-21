package com.fridge.main.data.settings

/**
 * 应用设置快照。
 *
 * 该模型只保存“用户可配置”的提醒参数，不包含通知权限、系统渠道状态等运行时信息；
 * 权限状态由设置页在 ON_RESUME 时重新读取，避免把系统状态误持久化。
 *
 * 所有值在 [normalized] 中做兜底，DataStore 读到脏数据时也不会让页面崩溃。
 */
data class AppSettings(
    /** 到期前多少天开始提醒，范围 1..7。 */
    val reminderDays: Int = DEFAULT_REMINDER_DAYS,
    /** 每日提醒小时，0..23。 */
    val reminderHour: Int = DEFAULT_REMINDER_HOUR,
    /** 每日提醒分钟，0..59。 */
    val reminderMinute: Int = DEFAULT_REMINDER_MINUTE,
    /** 用户是否开启过期通知（系统权限仍是最终开关）。 */
    val notificationsEnabled: Boolean = true,
) {

    /** 收敛到合法范围，避免历史配置/外部写入造成越界。 */
    fun normalized(): AppSettings = copy(
        reminderDays = reminderDays.coerceIn(MIN_REMINDER_DAYS, MAX_REMINDER_DAYS),
        reminderHour = reminderHour.coerceIn(0, 23),
        reminderMinute = reminderMinute.coerceIn(0, 59),
    )

    companion object {
        const val DEFAULT_REMINDER_DAYS = 3
        const val DEFAULT_REMINDER_HOUR = 9
        const val DEFAULT_REMINDER_MINUTE = 0
        const val MIN_REMINDER_DAYS = 1
        const val MAX_REMINDER_DAYS = 7
    }
}
