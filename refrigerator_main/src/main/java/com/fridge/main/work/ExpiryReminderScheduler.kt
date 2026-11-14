package com.fridge.main.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * 每日保质期巡检的调度器。
 *
 * WorkManager 的周期任务由系统省电策略统一调度，09:00 是“期望时间”而非精确定时；
 * 设备深度休眠时可能延后，这比为了准点唤醒而申请精确闹钟权限更符合 Android 规范。
 */
object ExpiryReminderScheduler {

    const val UNIQUE_WORK_NAME = "ingredient_expiry_daily_check"
    const val WORK_TAG = "expiry_reminder"

    /** 每天 09:00 执行；修改后需同步文档与验收说明。 */
    const val CHECK_HOUR = 9
    const val CHECK_MINUTE = 0

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ExpiryCheckWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMillis(), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .addTag(WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            // KEEP 避免每次冷启动都把“下一次执行”向后推一天。
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * 距离下一个本地 09:00 的毫秒数。
     *
     * 暴露为 internal 便于 JVM 单测；生产调用只依赖系统时钟与本地时区。
     */
    internal fun initialDelayMillis(
        now: ZonedDateTime = ZonedDateTime.now(),
        hour: Int = CHECK_HOUR,
        minute: Int = CHECK_MINUTE,
    ): Long {
        var next = now
            .withHour(hour)
            .withMinute(minute)
            .withSecond(0)
            .withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis().coerceAtLeast(0L)
    }
}
