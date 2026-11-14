package com.fridge.main.ui.screens.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fridge.main.data.remote.NetworkResult
import com.fridge.main.data.remote.recipe.OnlineRecipe
import com.fridge.main.data.repository.OnlineRecipeRepository
import com.fridge.main.ui.common.UiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 在线菜谱详情页状态。 */
data class OnlineRecipeDetailUiState(
    val content: UiState<OnlineRecipe> = UiState.Loading,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val message: String? = null,
    val errorDialogMessage: String? = null,
)

/**
 * 在线菜谱详情 ViewModel。
 *
 * 详情单独调用 lookup 接口，保证从搜索页进入详情时不依赖搜索结果的临时内存；
 * 保存动作复用本地 RecipeRepository，因此保存成功后本地列表会由 Room Flow 自动刷新。
 */
class OnlineRecipeDetailViewModel(
    private val repository: OnlineRecipeRepository,
    private val mealId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnlineRecipeDetailUiState())
    val uiState: StateFlow<OnlineRecipeDetailUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    content = UiState.Loading,
                    errorDialogMessage = null,
                )
            }

            when (val response = repository.getRecipe(mealId)) {
                is NetworkResult.Success -> {
                    val recipe = response.data
                    _uiState.update {
                        it.copy(
                            content = if (recipe == null) {
                                UiState.Empty
                            } else {
                                UiState.Success(recipe)
                            },
                        )
                    }
                }

                is NetworkResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            content = UiState.Error(response.message),
                            errorDialogMessage = response.message,
                        )
                    }
                }
            }
        }
    }

    /** 保存当前在线菜谱；成功后禁用按钮，避免重复点击写入多条本地记录。 */
    fun saveToLocal() {
        val recipe = (_uiState.value.content as? UiState.Success)?.data ?: return
        if (_uiState.value.isSaving || _uiState.value.saved) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    message = null,
                    errorDialogMessage = null,
                )
            }

            val result = repository.saveToLocal(recipe)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            saved = true,
                            message = "已保存到本地菜谱",
                        )
                    }
                },
                onFailure = { error ->
                    val message = error.toRecipeMessage("保存失败，请稍后重试")
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            message = message,
                            errorDialogMessage = message,
                        )
                    }
                },
            )
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun dismissErrorDialog() {
        _uiState.update { it.copy(errorDialogMessage = null) }
    }

    companion object {
        fun factory(
            repository: OnlineRecipeRepository,
            mealId: String,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { OnlineRecipeDetailViewModel(repository, mealId) }
        }
    }
}
