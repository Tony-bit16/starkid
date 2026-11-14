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
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 每日检查本地食材保质期。
 *
 * 只在后台读取 Room，按“今天”重新计算剩余天数：
 * - 0～3 天：合并成一条“即将过期”通知；
 * - 没有临期食材：撤销旧通知；
 * - 无通知权限：静默跳过，App 内状态标签仍然可用。
 */
class ExpiryCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val app = applicationContext as? FridgeApplication
                ?: return@withContext Result.failure()
            val ingredients = app.ingredientRepository.getAllIngredients()
            val today = LocalDate.now()

            val expiring = ingredients.mapNotNull { ingredient ->
                val daysLeft = ExpiryCalculator.daysLeft(
                    storedAt = ingredient.storedAt,
                    shelfLifeDays = ingredient.shelfLifeDays,
                    today = today,
                )
                if (ExpiryCalculator.isExpiringSoon(daysLeft)) {
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
