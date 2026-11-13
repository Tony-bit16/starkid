package com.fridge.main.ui.screens.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fridge.main.data.repository.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 新增菜谱页状态：表单、提交中、错误提示、成功后的菜谱 id。 */
data class AddRecipeUiState(
    val form: RecipeFormState = RecipeFormState.newInstance(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val savedId: Long? = null,
)

/**
 * 新增菜谱 ViewModel。
 *
 * 表单放在 ViewModel 中，屏幕旋转或配置变更时已输入内容不会丢失；
 * 保存成功后由页面消费 [AddRecipeUiState.savedId] 返回列表。
 */
class AddRecipeViewModel(
    private val repository: RecipeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddRecipeUiState())
    val uiState: StateFlow<AddRecipeUiState> = _uiState.asStateFlow()

    fun onFormChange(form: RecipeFormState) {
        _uiState.update { it.copy(form = form, errorMessage = null) }
    }

    fun save() {
        val current = _uiState.value
        if (current.isSaving || current.savedId != null) return

        val validated = current.form.validated()
        if (validated.hasErrors) {
            _uiState.update { it.copy(form = validated, errorMessage = null) }
            return
        }
        val draft = validated.toDraft() ?: return

        _uiState.update {
            it.copy(form = validated, isSaving = true, errorMessage = null)
        }

        viewModelScope.launch {
            repository.saveRecipe(draft).fold(
                onSuccess = { id ->
                    _uiState.update {
                        it.copy(isSaving = false, savedId = id, errorMessage = null)
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.toRecipeMessage("保存失败，请重试"),
                        )
                    }
                },
            )
        }
    }

    companion object {
        fun factory(repository: RecipeRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { AddRecipeViewModel(repository) }
        }
    }
}
