package com.fridge.main.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fridge.main.FridgeApplication
import com.fridge.main.domain.expiry.ExpiryCalculator
import com.fridge.main.notification.ExpiryNotifications
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 每日检查本地食材保质期。
 *
 * 只在后台读取 Room，按“今天”重新计算剩余天数：
 * - 进入用户设置的提前天数窗口（默认 3 天，含今天到期）：合并成一条“即将过期”通知；
 * - 没有临期食材：撤销旧通知；
 * - 用户在设置中关闭通知：撤销旧通知并跳过；
 * - 无系统通知权限：静默跳过，App 内状态标签仍然可用。
 */
class ExpiryCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val app = applicationContext as? FridgeApplication
                ?: return@withContext Result.failure()
            // 每次执行都读取最新设置，用户修改后下一次任务自动采用新参数。
            val settings = app.settingsRepository.settings.first()
            if (!settings.notificationsEnabled) {
                ExpiryNotifications.cancel(applicationContext)
                return@withContext Result.success()
            }
            val ingredients = app.ingredientRepository.getAllIngredients()
            val today = LocalDate.now()

            val expiring = ingredients.mapNotNull { ingredient ->
                val daysLeft = ExpiryCalculator.daysLeft(
                    storedAt = ingredient.storedAt,
                    shelfLifeDays = ingredient.shelfLifeDays,
                    today = today,
                )
                if (ExpiryCalculator.isExpiringSoon(daysLeft, settings.reminderDays)) {
                    ExpiryNotifications.Item(name = ingredient.name, daysLeft = daysLeft)
                } else {
                    null
                }
            }.sortedBy { it.daysLeft }

            if (expiring.isEmpty()) {
                ExpiryNotifications.cancel(applicationContext)
                return@withContext Result.success()
            }

            // 无权限时只降级，不把任务标记成失败，避免系统反复重试。
            if (!ExpiryNotifications.canPost(applicationContext)) {
                Log.i(TAG, "Notification permission unavailable; skip expiry reminder.")
                return@withContext Result.success()
            }

            ExpiryNotifications.createChannel(applicationContext)
            ExpiryNotifications.show(applicationContext, expiring)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Log.w(TAG, "Expiry check failed on attempt $runAttemptCount", error)
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private companion object {
        const val TAG = "ExpiryCheckWorker"
        const val MAX_ATTEMPTS = 3
    }
}
