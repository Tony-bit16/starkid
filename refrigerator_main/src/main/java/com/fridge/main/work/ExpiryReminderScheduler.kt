package com.fridge.main.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.fridge.main.data.settings.AppSettings
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * 每日保质期巡检的调度器。
 *
 * WorkManager 的周期任务由系统省电策略统一调度，设置中的时间是“期望时间”而非精确定时；
 * 设备深度休眠时可能延后，这比为了准点唤醒而申请精确闹钟权限更符合 Android 规范。
 *
 * 调度时机：
 * - App 启动：使用 KEEP，保留已有周期任务，避免每次冷启动都把下一次执行向后推；
 * - 用户修改设置：使用 REPLACE，让新的提醒时间立即成为下一次执行时间。
 */
object ExpiryReminderScheduler {

    const val UNIQUE_WORK_NAME = "ingredient_expiry_daily_check"
    const val WORK_TAG = "expiry_reminder"

    /** 默认每日 09:00；实际时间由 DataStore 中的用户设置覆盖。 */
    const val CHECK_HOUR = AppSettings.DEFAULT_REMINDER_HOUR
    const val CHECK_MINUTE = AppSettings.DEFAULT_REMINDER_MINUTE

    /**
     * App 启动时调用：只保证任务存在，不重置当前周期。
     * 通知总开关关闭时取消任务，避免后台无效唤醒。
     */
    fun ensureScheduled(context: Context, settings: AppSettings) {
        if (!settings.notificationsEnabled) {
            cancel(context)
            return
        }
        enqueue(
            context = context,
            hour = settings.reminderHour,
            minute = settings.reminderMinute,
            policy = ExistingPeriodicWorkPolicy.KEEP,
        )
    }

    /**
     * 设置变化时调用：REPLACE 会按新的提醒时间重新计算首次延迟。
     * 关闭通知时同时取消唯一周期任务。
     */
    fun reschedule(context: Context, settings: AppSettings) {
        if (!settings.notificationsEnabled) {
            cancel(context)
            return
        }
        enqueue(
            context = context,
            hour = settings.reminderHour,
            minute = settings.reminderMinute,
            policy = ExistingPeriodicWorkPolicy.REPLACE,
        )
    }

    /** 取消每日巡检；设置页关闭通知时调用。 */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private fun enqueue(
        context: Context,
        hour: Int,
        minute: Int,
        policy: ExistingPeriodicWorkPolicy,
    ) {
        val request = PeriodicWorkRequestBuilder<ExpiryCheckWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(
                initialDelayMillis(hour = hour, minute = minute),
                TimeUnit.MILLISECONDS,
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .addTag(WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            policy,
            request,
        )
    }

    /**
     * 距离下一个本地提醒时间的毫秒数。
     *
     * 暴露为 internal 便于 JVM 单测；生产调用只依赖系统时钟与本地时区。
     * 时间越界时收敛到合法范围，避免 DataStore 脏数据导致调度异常。
     */
    internal fun initialDelayMillis(
        now: ZonedDateTime = ZonedDateTime.now(),
        hour: Int = CHECK_HOUR,
        minute: Int = CHECK_MINUTE,
    ): Long {
        var next = now
            .withHour(hour.coerceIn(0, 23))
            .withMinute(minute.coerceIn(0, 59))
            .withSecond(0)
            .withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis().coerceAtLeast(0L)
    }
}
