package com.fridge.dashboard.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fridge.dashboard.data.export.AndroidDashboardCsvExporter
import com.fridge.dashboard.data.export.CsvExportException
import com.fridge.dashboard.data.export.DashboardCsvExporter
import com.fridge.dashboard.data.model.DashboardIngredient
import com.fridge.dashboard.data.model.toDashboardUserMessage
import com.fridge.dashboard.data.provider.IngredientProviderClient
import com.fridge.dashboard.data.repository.DashboardRepository
import com.fridge.dashboard.data.repository.DefaultDashboardRepository
import com.fridge.dashboard.domain.stats.DashboardStatsCalculator
import com.fridge.dashboard.ui.model.DashboardExportResult
import com.fridge.dashboard.ui.model.DashboardExportUiState
import com.fridge.dashboard.ui.model.DashboardHomeContent
import com.fridge.dashboard.ui.model.DashboardUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 看板主页 ViewModel。
 *
 * 订阅 [DashboardRepository.observeIngredients]：首次订阅立即查询一次，之后主 App
 * 的 Room 写入会通过 Provider 的 `notifyChange` 推送过来，看板无需手动重试。
 * 查询在客户端内切到 IO 线程，统计在 [Dispatchers.Default] 计算；
 * 页面只消费 Loading / Empty / Error / Success 四态。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardHomeViewModel(
    private val repository: DashboardRepository,
    private val exporter: DashboardCsvExporter,
) : ViewModel() {

    private val retrySignal = MutableStateFlow(0)

    private val _exportState = MutableStateFlow(DashboardExportUiState())
    val exportState = _exportState.asStateFlow()

    val state = retrySignal
        .flatMapLatest {
            repository.observeIngredients()
                .map { result -> result.toUiState() }
                .onStart { emit(DashboardUiState.Loading) }
                .catch { error ->
                    emit(
                        DashboardUiState.Error(
                            title = "读取失败",
                            message = error.toDashboardUserMessage(),
                        ),
                    )
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DashboardUiState.Loading,
        )

    /** 把 Provider 查询结果聚合为页面四态；统计计算离开主线程。 */
    private suspend fun Result<List<DashboardIngredient>>.toUiState(): DashboardUiState = fold(
        onSuccess = { ingredients ->
            if (ingredients.isEmpty()) {
                DashboardUiState.Empty
            } else {
                val stats = withContext(Dispatchers.Default) {
                    DashboardStatsCalculator.calculate(ingredients)
                }
                DashboardUiState.Success(
                    DashboardHomeContent(
                        stats = stats,
                        ingredients = ingredients,
                    ),
                )
            }
        },
        onFailure = { error ->
            DashboardUiState.Error(
                title = "读取失败",
                message = error.toDashboardUserMessage(),
            )
        },
    )

    fun retry() {
        retrySignal.update { it + 1 }
    }

    /**
     * 导出最新全量食材。
     *
     * 导出前重新查询 Provider，避免页面上的统计/明细已经过期；
     * 查询与写文件都在 IO 线程，UI 只观察 isExporting/result。
     */
    fun exportCsv() {
        if (_exportState.value.isExporting) return
        _exportState.value = DashboardExportUiState(isExporting = true)

        viewModelScope.launch {
            val result = repository.loadIngredients().fold(
                onSuccess = { ingredients ->
                    if (ingredients.isEmpty()) {
                        DashboardExportResult.Failure("没有可导出的食材，请先在主 App 中添加食材。")
                    } else {
                        exporter.export(ingredients).fold(
                            onSuccess = { file ->
                                DashboardExportResult.Success(
                                    fileName = file.fileName,
                                    displayPath = file.displayPath,
                                    contentUri = file.contentUri,
                                )
                            },
                            onFailure = { error ->
                                DashboardExportResult.Failure(error.toExportMessage())
                            },
                        )
                    }
                },
                onFailure = { error ->
                    DashboardExportResult.Failure(error.toDashboardUserMessage())
                },
            )
            _exportState.value = DashboardExportUiState(
                isExporting = false,
                result = result,
            )
        }
    }

    fun clearExportResult() {
        _exportState.update { it.copy(result = null) }
    }

    /** 导出错误转成用户能理解、可行动的提示。 */
    private fun Throwable.toExportMessage(): String = when (this) {
        is CsvExportException.Permission ->
            "没有写入下载目录的权限。请允许存储权限，或到系统设置中开启后重试。"
        is CsvExportException.StorageFull ->
            "存储空间不足，无法写入 CSV。请清理空间后重试。"
        is CsvExportException.Write ->
            "CSV 文件写入失败，请检查下载目录权限后重试。"
        else -> toDashboardUserMessage()
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DashboardHomeViewModel(
                    repository = DefaultDashboardRepository(
                        client = IngredientProviderClient(context.applicationContext),
                    ),
                    exporter = AndroidDashboardCsvExporter(context.applicationContext),
                )
            }
        }
    }
}
