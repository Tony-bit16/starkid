package com.fridge.main.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fridge.main.data.settings.AppSettings
import com.fridge.main.data.settings.SettingsRepository
import com.fridge.main.notification.ExpiryNotifications
import com.fridge.main.ui.common.UiState
import com.fridge.main.work.ExpiryReminderScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/** 设置页状态：内容四态 + 保存中 + 一次性提示。 */
data class SettingsUiState(
    val content: UiState<AppSettings> = UiState.Loading,
    val isSaving: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
)

/**
 * 设置页 ViewModel。
 *
 * - DataStore 的 Flow 负责持续推送配置，页面无需自己轮询；
 * - 每次保存成功后立即按新配置重排 WorkManager，下一次任务即生效；
 * - 关闭通知时同时撤销已有通知，避免“关了开关还收到旧提醒”。
 */
class SettingsViewModel(
    private val repository: SettingsRepository,
    context: Context,
) : ViewModel() {

    private val appContext = context.applicationContext
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        observeSettings()
    }

    /** 读取失败后的重试入口：取消旧收集并重新订阅 DataStore。 */
    fun retry() {
        observeSettings()
    }

    private fun observeSettings() {
        observeJob?.cancel()
        _uiState.update { it.copy(content = UiState.Loading, message = null) }
        observeJob = viewModelScope.launch {
            repository.settings
                .catch { error ->
                    if (error is CancellationException) throw error
                    val message = if (error is IOException) {
                        "读取设置失败，请稍后重试"
                    } else {
                        "设置暂时不可用，请稍后重试"
                    }
                    _uiState.update { it.copy(content = UiState.Error(message)) }
                }
                .collect { settings ->
                    _uiState.update { it.copy(content = UiState.Success(settings)) }
                }
        }
    }

    /**
     * 保存一次设置变更。
     *
     * 使用单次 update 事务写完四项配置；成功后再触发调度，避免 Worker 读到半更新状态。
     */
    fun update(transform: (AppSettings) -> AppSettings) {
        val current = _uiState.value
        if (current.isSaving) return
        _uiState.update { it.copy(isSaving = true, message = null) }
        viewModelScope.launch {
            try {
                val updated = repository.update(transform)
                // 先更新页面，再重排任务；DataStore 的 Flow 稍后也会再发一次相同值。
                _uiState.update {
                    it.copy(
                        content = UiState.Success(updated),
                        isSaving = false,
                        message = "设置已保存",
                        messageIsError = false,
                    )
                }
                if (updated.notificationsEnabled) {
                    ExpiryReminderScheduler.reschedule(appContext, updated)
                } else {
                    ExpiryReminderScheduler.cancel(appContext)
                    ExpiryNotifications.cancel(appContext)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        message = "保存失败，请稍后重试",
                        messageIsError = true,
                    )
                }
            }
        }
    }

    /** Snackbar 展示后清除一次性提示。 */
    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    companion object {
        fun factory(repository: SettingsRepository, context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(repository, context) as T
                }
            }
    }
}
