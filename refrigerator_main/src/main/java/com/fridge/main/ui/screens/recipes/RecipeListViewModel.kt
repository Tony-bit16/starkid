package com.fridge.main.ui.screens.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fridge.main.data.repository.RecipeRepository
import com.fridge.main.ui.common.UiState
import com.fridge.main.ui.model.RecipeUiModel
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
 * 菜谱列表 ViewModel。
 *
 * Room 的 Flow 持续观察菜谱表；新增、编辑或删除后列表会自动刷新，
 * 页面无需在返回时手动请求一次数据。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecipeListViewModel(
    private val repository: RecipeRepository,
) : ViewModel() {

    private val retrySignal = MutableStateFlow(0)

    val uiState: StateFlow<UiState<List<RecipeUiModel>>> = retrySignal
        .flatMapLatest {
            repository.observeRecipes()
                .map { recipes ->
                    if (recipes.isEmpty()) {
                        UiState.Empty
                    } else {
                        UiState.Success(recipes.map { it.toUiModel() })
                    }
                }
                .catch { error ->
                    emit(UiState.Error(error.toRecipeMessage("读取菜谱失败，请重试")))
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState.Loading,
        )

    fun retry() {
        retrySignal.update { it + 1 }
    }

    companion object {
        fun factory(repository: RecipeRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { RecipeListViewModel(repository) }
        }
    }
}
