package com.fridge.main.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * 应用设置的数据契约。
 *
 * 页面只依赖该接口，不直接接触 DataStore；这样后续替换存储实现时页面无需改动。
 */
interface SettingsRepository {

    /** 持续观察设置变化；设置页和 WorkManager 都可以复用。 */
    val settings: Flow<AppSettings>

    /**
     * 原子更新设置并返回更新后的快照。
     *
     * 使用 transform 而不是多个独立 setter，避免“读旧值再写回”在并发修改时互相覆盖。
     */
    suspend fun update(transform: (AppSettings) -> AppSettings): AppSettings
}

/** 一个进程内只创建一个 Preferences DataStore 实例。 */
private val Context.fridgeSettingsDataStore by preferencesDataStore(name = "fridge_settings")

/** DataStore key 只在本文件使用，避免页面直接拼字符串。 */
private object SettingsKeys {
    val reminderDays = intPreferencesKey("reminder_days")
    val reminderHour = intPreferencesKey("reminder_hour")
    val reminderMinute = intPreferencesKey("reminder_minute")
    val notificationsEnabled = booleanPreferencesKey("notifications_enabled")
}

/**
 * Preferences DataStore 实现。
 *
 * 所有读写都在 IO 线程；DataStore 文件损坏/读取 IOException 时回退默认值，
 * 保证设置页仍能打开，用户可以通过重新保存修复配置。
 */
class DefaultSettingsRepository(context: Context) : SettingsRepository {

    private val appContext = context.applicationContext

    override val settings: Flow<AppSettings> = appContext.fridgeSettingsDataStore.data
        .catch { error ->
            if (error is IOException) {
                // 文件暂时不可读时先给出默认配置，避免设置页直接崩溃。
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences ->
            AppSettings(
                reminderDays = preferences[SettingsKeys.reminderDays]
                    ?: AppSettings.DEFAULT_REMINDER_DAYS,
                reminderHour = preferences[SettingsKeys.reminderHour]
                    ?: AppSettings.DEFAULT_REMINDER_HOUR,
                reminderMinute = preferences[SettingsKeys.reminderMinute]
                    ?: AppSettings.DEFAULT_REMINDER_MINUTE,
                notificationsEnabled = preferences[SettingsKeys.notificationsEnabled] ?: true,
            ).normalized()
        }

    override suspend fun update(
        transform: (AppSettings) -> AppSettings,
    ): AppSettings = withContext(Dispatchers.IO) {
        var updated: AppSettings? = null
        appContext.fridgeSettingsDataStore.edit { preferences ->
            val next = transform(preferences.toAppSettings()).normalized()
            preferences[SettingsKeys.reminderDays] = next.reminderDays
            preferences[SettingsKeys.reminderHour] = next.reminderHour
            preferences[SettingsKeys.reminderMinute] = next.reminderMinute
            preferences[SettingsKeys.notificationsEnabled] = next.notificationsEnabled
            updated = next
        }
        checkNotNull(updated) { "DataStore 未返回更新后的设置" }
    }
}

private fun androidx.datastore.preferences.core.Preferences.toAppSettings(): AppSettings =
    AppSettings(
        reminderDays = this[SettingsKeys.reminderDays]
            ?: AppSettings.DEFAULT_REMINDER_DAYS,
        reminderHour = this[SettingsKeys.reminderHour]
            ?: AppSettings.DEFAULT_REMINDER_HOUR,
        reminderMinute = this[SettingsKeys.reminderMinute]
            ?: AppSettings.DEFAULT_REMINDER_MINUTE,
        notificationsEnabled = this[SettingsKeys.notificationsEnabled] ?: true,
    ).normalized()
