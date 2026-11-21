package com.fridge.main.ui.screens.settings

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.fridge.main.data.settings.AppSettings
import com.fridge.main.ui.common.ScreenStateContent
import com.fridge.main.ui.common.UiState
import com.fridge.main.ui.components.SectionCard
import com.fridge.main.ui.components.hasNotificationAccess

/**
 * 设置页。
 *
 * 页面只负责展示与收集用户操作，配置读取/写入、WorkManager 重排都在 ViewModel 中完成。
 * 通知权限属于系统状态，进入页面和从系统设置返回时重新检查，避免展示过期状态。
 */
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onRetry: () -> Unit,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onAboutClick: () -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onConsumeMessage()
    }

    Box(modifier = modifier.fillMaxSize()) {
        ScreenStateContent(
            state = uiState.content,
            onRetry = onRetry,
            emptyTitle = "暂无设置",
            emptyMessage = "设置读取完成后会显示在这里。",
            modifier = Modifier.fillMaxSize(),
        ) { settings ->
            SettingsContent(
                settings = settings,
                isSaving = uiState.isSaving,
                onUpdate = onUpdate,
                onAboutClick = onAboutClick,
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}

@Composable
private fun SettingsContent(
    settings: AppSettings,
    isSaving: Boolean,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onAboutClick: () -> Unit,
) {
    val context = LocalContext.current
    val canPostNotifications = remember(context) { context.hasNotificationAccess() }
    var notificationAccess by remember { mutableStateOf(canPostNotifications) }

    // 从系统设置返回后刷新；用户可能在设置里刚打开/关闭了通知。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationAccess = context.hasNotificationAccess()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            NotificationAccessCard(
                granted = notificationAccess,
                onOpenSettings = { openNotificationSettings(context) },
            )
        }

        item {
            SectionCard(title = "保质期提醒") {
                ReminderDaysRow(settings = settings, isSaving = isSaving, onUpdate = onUpdate)
                ReminderTimeRow(settings = settings, isSaving = isSaving, onUpdate = onUpdate)
                Text(
                    text = "提醒由 WorkManager 每日执行；系统省电策略可能让实际时间稍有延后。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionCard(title = "通知") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("过期通知", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "关闭后不再发送到期提醒，食材状态仍可查看。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.notificationsEnabled,
                        onCheckedChange = { enabled ->
                            onUpdate { it.copy(notificationsEnabled = enabled) }
                        },
                        enabled = !isSaving,
                    )
                }
            }
        }

        item {
            SectionCard(title = "关于") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAboutClick)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("关于冰箱管家", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "版本、功能说明与数据边界",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("查看", color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        item {
            Text(
                text = "设置通过 Preferences DataStore 保存在本机；修改后会立即重排下一次提醒任务。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun NotificationAccessCard(
    granted: Boolean,
    onOpenSettings: () -> Unit,
) {
    SectionCard(title = "系统通知权限") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (granted) "已开启" else "未开启",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (granted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                Text(
                    text = if (granted) {
                        "到期提醒可以正常推送。"
                    } else {
                        "到期提醒无法推送；App 内食材状态仍可正常查看。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!granted) {
                TextButton(onClick = onOpenSettings) { Text("去设置") }
            }
        }
    }
}

@Composable
private fun ReminderDaysRow(
    settings: AppSettings,
    isSaving: Boolean,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("提前提醒天数", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "默认 3 天，范围 ${AppSettings.MIN_REMINDER_DAYS}–${AppSettings.MAX_REMINDER_DAYS} 天",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = {
                    onUpdate { it.copy(reminderDays = it.reminderDays - 1) }
                },
                enabled = !isSaving && settings.reminderDays > AppSettings.MIN_REMINDER_DAYS,
            ) {
                Text("-")
            }
            Text(
                text = "${settings.reminderDays} 天",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            TextButton(
                onClick = {
                    onUpdate { it.copy(reminderDays = it.reminderDays + 1) }
                },
                enabled = !isSaving && settings.reminderDays < AppSettings.MAX_REMINDER_DAYS,
            ) {
                Text("+")
            }
        }
    }
}

@Composable
private fun ReminderTimeRow(
    settings: AppSettings,
    isSaving: Boolean,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    val context = LocalContext.current
    val label = "%02d:%02d".format(settings.reminderHour, settings.reminderMinute)

    fun showTimePicker() {
        TimePickerDialog(
            context,
            { _, hour, minute ->
                onUpdate { it.copy(reminderHour = hour, reminderMinute = minute) }
            },
            settings.reminderHour,
            settings.reminderMinute,
            DateFormat.is24HourFormat(context),
        ).show()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isSaving) { showTimePicker() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("每日提醒时间", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "WorkManager 每日期望执行时间",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun openNotificationSettings(context: Context) {
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
    if (context !is android.app.Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
