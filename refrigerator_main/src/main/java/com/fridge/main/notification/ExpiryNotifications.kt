package com.fridge.main.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fridge.main.MainActivity
import com.fridge.main.R

/**
 * 临期食材通知的唯一出口。
 *
 * 设计约束：
 * - 一次巡检只发一条合并通知，避免多条食材同时到期时轰炸通知栏；
 * - 通知点击统一带 [EXTRA_OPEN_INGREDIENTS]，由 MainActivity 转成“打开食材库”导航；
 * - Android 13+ 没有 POST_NOTIFICATIONS 时只跳过发送，不抛异常，不影响 App 使用。
 */
object ExpiryNotifications {

    const val CHANNEL_ID = "expiry_reminder"
    const val NOTIFICATION_ID = 1001
    const val EXTRA_OPEN_INGREDIENTS = "com.fridge.main.extra.OPEN_INGREDIENTS"

    private val CHANNEL_NAME_RES = R.string.expiry_channel_name
    private val CHANNEL_DESCRIPTION_RES = R.string.expiry_channel_description

    /** 单条临期食材，Worker 只传展示所需字段，避免通知层依赖 Room Entity。 */
    data class Item(
        val name: String,
        val daysLeft: Int,
    )

    /** 在 Application 启动时创建渠道；重复调用是幂等的。 */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(CHANNEL_NAME_RES),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(CHANNEL_DESCRIPTION_RES)
            enableVibration(true)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * 是否允许发通知。
     *
     * 同时检查 Android 13 运行时权限、App 总开关和渠道开关；任一关闭都按“无权限”降级。
     */
    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }

        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return false
            val channel = manager.getNotificationChannel(CHANNEL_ID)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
                return false
            }
        }
        return true
    }

    /** 发送合并通知；调用方应先通过 [canPost] 判断权限，未授权时此方法直接返回。 */
    fun show(context: Context, items: List<Item>) {
        if (items.isEmpty() || !canPost(context)) return

        val sorted = items.sortedBy { it.daysLeft }
        val title = context.getString(R.string.expiry_notification_title, sorted.size)
        val summary = sorted.take(2).joinToString("、") { "${it.name}（${remainingText(it.daysLeft)}）" }
        val suffix = if (sorted.size > 2) " 等 ${sorted.size} 种" else ""
        val bigText = sorted.joinToString("\n") { item ->
            "• ${item.name}：${remainingText(item.daysLeft)}"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("$summary$suffix")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setNumber(sorted.size)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context))
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    /** 巡检发现没有临期食材时清理旧通知，避免列表已空但通知仍残留。 */
    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_INGREDIENTS, true)
        }
        return PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun remainingText(daysLeft: Int): String = when {
        daysLeft < 0 -> "已过期"
        daysLeft == 0 -> "今天到期"
        daysLeft == 1 -> "明天到期"
        else -> "还剩 $daysLeft 天"
    }
}
