package com.fridge.main.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.fridge.main.notification.ExpiryNotifications

/**
 * 通知权限的申请与降级入口。
 *
 * 交互顺序：
 * 1. Android 13+ 首次进入食材库时展示说明弹窗；用户同意后才触发系统权限框；
 * 2. 系统拒绝后在弹窗里说明“到期提醒不可用”，并提供重试/去设置；
 * 3. 永久拒绝时保留页面顶部提示条，随时可去系统设置，不阻断食材管理。
 *
 * 页面顶部提示条只在权限不可用时出现；它不影响列表的加载/空/错误态。
 */
@Composable
fun NotificationPermissionBanner(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val prefs = remember(context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var granted by remember { mutableStateOf(context.hasNotificationAccess()) }
    var systemRequested by remember {
        mutableStateOf(prefs.getBoolean(KEY_SYSTEM_REQUESTED, false))
    }
    var introShown by remember {
        mutableStateOf(prefs.getBoolean(KEY_INTRO_SHOWN, false))
    }
    var showIntro by remember { mutableStateOf(false) }
    var showDenied by remember { mutableStateOf(false) }
    var canAskAgain by remember { mutableStateOf(true) }

    fun persist(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun refreshPermissionState() {
        granted = context.hasNotificationAccess()
    }

    fun canShowSystemPrompt(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        // 权限已授予但 App/渠道通知被关闭时，系统权限框不会再出现，只能引导去设置。
        val permissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (permissionGranted) return false
        if (!systemRequested) return true
        return activity?.shouldShowRequestPermissionRationale(
            Manifest.permission.POST_NOTIFICATIONS,
        ) == true
    }

    fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            )
        }
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        refreshPermissionState()
        if (!isGranted) {
            canAskAgain = activity?.shouldShowRequestPermissionRationale(
                Manifest.permission.POST_NOTIFICATIONS,
            ) == true
            showDenied = true
        }
    }

    fun requestSystemPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            openNotificationSettings()
            return
        }
        systemRequested = true
        persist(KEY_SYSTEM_REQUESTED, true)
        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // 首次进入只弹一次说明；之后由顶部提示条提供入口，避免反复打扰。
    LaunchedEffect(Unit) {
        if (!granted && !introShown && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            showIntro = true
        }
    }

    // 从系统设置返回时刷新；用户可能在设置里刚刚开启通知。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        refreshPermissionState()
    }

    if (granted) return

    Column(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "通知权限未开启",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "到期提醒无法推送；食材状态仍可正常查看。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val canAsk = canShowSystemPrompt()
                TextButton(
                    onClick = {
                        if (canAsk) {
                            requestSystemPermission()
                        } else {
                            openNotificationSettings()
                        }
                    },
                ) {
                    Text(if (canAsk) "开启" else "去设置")
                }
            }
        }

        if (showIntro) {
            AlertDialog(
                onDismissRequest = { showIntro = false },
                title = { Text("开启保质期提醒") },
                text = {
                    Text("允许通知后，食材到期前 3 天会收到一条合并提醒。拒绝也不影响查看和管理食材。")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showIntro = false
                            introShown = true
                            persist(KEY_INTRO_SHOWN, true)
                            requestSystemPermission()
                        },
                    ) {
                        Text("开启通知")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showIntro = false
                            introShown = true
                            persist(KEY_INTRO_SHOWN, true)
                        },
                    ) {
                        Text("暂不开启")
                    }
                },
            )
        }

        if (showDenied) {
            AlertDialog(
                onDismissRequest = { showDenied = false },
                title = { Text("通知未开启") },
                text = {
                    Text(
                        if (canAskAgain) {
                            "到期提醒将无法推送；你仍可在食材列表查看新鲜度。可以再次申请，或稍后在通知提示条中开启。"
                        } else {
                            "通知权限已被拒绝，到期提醒无法推送。可前往系统设置手动开启， App 其他功能不受影响。"
                        },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDenied = false
                            if (canAskAgain) {
                                requestSystemPermission()
                            } else {
                                openNotificationSettings()
                            }
                        },
                    ) {
                        Text(if (canAskAgain) "再次申请" else "去设置")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDenied = false }) {
                        Text("以后再说")
                    }
                },
            )
        }
    }
}

/** 从 ContextWrapper 链中找到 Activity，用于判断权限是否可再次申请。 */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

/**
 * 通知是否真正可用。
 *
 * Android 13+ 先检查运行时权限，再统一检查 App/渠道总开关；
 * 任一关闭都视为不可用，仅展示提示条，不抛异常。
 */
private fun Context.hasNotificationAccess(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) return false
    }
    if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = getSystemService(android.app.NotificationManager::class.java) ?: return false
        val channel = manager.getNotificationChannel(ExpiryNotifications.CHANNEL_ID)
        if (channel != null && channel.importance == android.app.NotificationManager.IMPORTANCE_NONE) {
            return false
        }
    }
    return true
}

private const val PREFS_NAME = "notification_permission"
private const val KEY_INTRO_SHOWN = "intro_shown"
private const val KEY_SYSTEM_REQUESTED = "system_requested"
