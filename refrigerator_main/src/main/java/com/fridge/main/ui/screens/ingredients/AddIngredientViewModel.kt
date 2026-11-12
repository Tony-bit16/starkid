package com.fridge.main.ui.screens.ingredients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fridge.main.data.repository.IngredientRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 新增食材页的完整状态：表单、提交中、提交错误、成功后生成的 id。 */
data class AddIngredientUiState(
    val form: IngredientFormState = IngredientFormState.newInstance(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val savedId: Long? = null,
)

/**
 * 新增食材 ViewModel。
 *
 * 表单状态放在 ViewModel 中，屏幕旋转/配置变更时输入不会丢；
 * 保存成功后通过 [AddIngredientUiState.savedId] 通知页面返回列表。
 */
class AddIngredientViewModel(
    private val repository: IngredientRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddIngredientUiState())
    val uiState: StateFlow<AddIngredientUiState> = _uiState.asStateFlow()

    fun onFormChange(form: IngredientFormState) {
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
            repository.addIngredient(draft).fold(
                onSuccess = { id ->
                    _uiState.update {
                        it.copy(isSaving = false, savedId = id, errorMessage = null)
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.toIngredientMessage("保存失败，请重试"),
                        )
                    }
                },
            )
        }
    }

    companion object {
        fun factory(repository: IngredientRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { AddIngredientViewModel(repository) }
        }
    }
}
