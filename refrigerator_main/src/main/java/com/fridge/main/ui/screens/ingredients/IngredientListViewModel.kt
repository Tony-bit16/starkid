package com.fridge.main.ui.screens.ingredients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fridge.main.data.repository.IngredientRepository
import com.fridge.main.ui.common.UiState
import com.fridge.main.ui.model.IngredientUiModel
import com.fridge.main.ui.model.toUiModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * 食材列表页 ViewModel。
 *
 * - Room 的 Flow 在 `viewModelScope` 中收集，数据库查询由 Room 自己切到查询线程；
 * - Flow 的异常用 `catch` 转成 [UiState.Error]，页面不会因数据库打开/读取失败而崩溃；
 * - `retrySignal` 让“重试”重新订阅 Flow，而不是重新创建 ViewModel。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IngredientListViewModel(
    private val repository: IngredientRepository,
) : ViewModel() {

    private val retrySignal = MutableStateFlow(0)

    val uiState: StateFlow<UiState<List<IngredientUiModel>>> = retrySignal
        .flatMapLatest {
            repository.observeIngredients()
                .map { ingredients ->
                    if (ingredients.isEmpty()) {
                        UiState.Empty
                    } else {
                        UiState.Success(ingredients.map { it.toUiModel() })
                    }
                }
                .catch { error ->
                    emit(UiState.Error(error.toIngredientMessage("读取食材失败，请重试")))
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState.Loading,
        )

    /** 重试读取；同时作为首次订阅失败后的恢复入口。 */
    fun retry() {
        retrySignal.update { it + 1 }
    }

    companion object {
        fun factory(repository: IngredientRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { IngredientListViewModel(repository) }
        }
    }
}
