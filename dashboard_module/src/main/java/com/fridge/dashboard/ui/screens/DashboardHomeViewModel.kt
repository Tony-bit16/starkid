package com.fridge.dashboard.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fridge.dashboard.data.model.toDashboardUserMessage
import com.fridge.dashboard.data.provider.IngredientProviderClient
import com.fridge.dashboard.data.repository.DashboardRepository
import com.fridge.dashboard.data.repository.DefaultDashboardRepository
import com.fridge.dashboard.ui.model.DashboardUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * 看板主页 ViewModel。
 *
 * 每次进入页面/点击重试都重新查询 ContentProvider；查询在客户端内切到 IO 线程，
 * 页面只消费 Loading / Empty / Error / Success 四态。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardHomeViewModel(
    private val repository: DashboardRepository,
) : ViewModel() {

    private val retrySignal = MutableStateFlow(0)

    val state = retrySignal
        .flatMapLatest {
            flow {
                emit(DashboardUiState.Loading)
                val result = repository.loadIngredients()
                emit(
                    result.fold(
                        onSuccess = { ingredients ->
                            if (ingredients.isEmpty()) {
                                DashboardUiState.Empty
                            } else {
                                DashboardUiState.Success(ingredients)
                            }
                        },
                        onFailure = { error ->
                            DashboardUiState.Error(
                                title = "读取失败",
                                message = error.toDashboardUserMessage(),
                            )
                        },
                    ),
                )
            }
        }
        .catch { error ->
            emit(
                DashboardUiState.Error(
                    title = "读取失败",
                    message = error.toDashboardUserMessage(),
                ),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DashboardUiState.Loading,
        )

    fun retry() {
        retrySignal.update { it + 1 }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DashboardHomeViewModel(
                    repository = DefaultDashboardRepository(
                        client = IngredientProviderClient(context.applicationContext),
                    ),
                )
            }
        }
    }
}
