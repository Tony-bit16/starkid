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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 食材详情页状态。
 *
 * [content] 只表达“读取”的四态；编辑/保存/删除是页面内的操作状态，
 * 两者分开可以避免保存失败时把已经读到的详情内容清空。
 */
data class IngredientDetailUiState(
    val content: UiState<IngredientUiModel> = UiState.Loading,
    val isEditing: Boolean = false,
    val form: IngredientFormState = IngredientFormState.newInstance(),
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val errorMessage: String? = null,
    val updateSucceeded: Boolean = false,
    val deleted: Boolean = false,
)

/**
 * 食材详情 ViewModel。
 *
 * - `observeIngredient` 持续观察 Room；编辑保存后列表和详情会自动刷新，
 *   不需要页面手动回传结果；
 * - 保存/删除都返回 [Result]，失败时保留当前内容并展示错误，不会清空页面；
 * - 删除成功置位 [IngredientDetailUiState.deleted]，由页面负责返回列表。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IngredientDetailViewModel(
    private val repository: IngredientRepository,
    private val ingredientId: Long,
) : ViewModel() {

    private val retrySignal = MutableStateFlow(0)
    private val actionState = MutableStateFlow(ActionState())

    private val contentFlow: Flow<UiState<IngredientUiModel>> = retrySignal.flatMapLatest {
        if (ingredientId <= 0) {
            flowOf(UiState.Empty)
        } else {
            repository.observeIngredient(ingredientId)
                .map { entity ->
                    if (entity == null) {
                        UiState.Empty
                    } else {
                        UiState.Success(entity.toUiModel())
                    }
                }
                .catch { error ->
                    emit(UiState.Error(error.toIngredientMessage("读取食材失败，请重试")))
                }
        }
    }

    val uiState: StateFlow<IngredientDetailUiState> = combine(contentFlow, actionState) { content, action ->
        IngredientDetailUiState(
            content = content,
            isEditing = action.isEditing,
            form = action.form,
            isSaving = action.isSaving,
            isDeleting = action.isDeleting,
            errorMessage = action.errorMessage,
            updateSucceeded = action.updateSucceeded,
            deleted = action.deleted,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = IngredientDetailUiState(),
    )

    fun retry() {
        retrySignal.update { it + 1 }
    }

    fun startEdit() {
        val item = (uiState.value.content as? UiState.Success)?.data ?: return
        actionState.update {
            it.copy(
                isEditing = true,
                form = IngredientFormState.from(item),
                errorMessage = null,
                updateSucceeded = false,
            )
        }
    }

    fun cancelEdit() {
        actionState.update {
            it.copy(isEditing = false, form = IngredientFormState.newInstance(), errorMessage = null)
        }
    }

    fun onFormChange(form: IngredientFormState) {
        actionState.update { it.copy(form = form, errorMessage = null) }
    }

    fun save() {
        val current = uiState.value
        if (current.isSaving || current.isDeleting) return

        val validated = current.form.validated()
        if (validated.hasErrors) {
            actionState.update { it.copy(form = validated, errorMessage = null) }
            return
        }
        val draft = validated.toDraft() ?: return

        actionState.update { it.copy(form = validated, isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            repository.updateIngredient(ingredientId, draft).fold(
                onSuccess = {
                    actionState.update {
                        it.copy(
                            isSaving = false,
                            isEditing = false,
                            updateSucceeded = true,
                            errorMessage = null,
                        )
                    }
                },
                onFailure = { error ->
                    actionState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.toIngredientMessage("保存失败，请重试"),
                        )
                    }
                },
            )
        }
    }

    fun delete() {
        val current = uiState.value
        if (current.isSaving || current.isDeleting) return

        actionState.update { it.copy(isDeleting = true, errorMessage = null) }
        viewModelScope.launch {
            repository.deleteIngredient(ingredientId).fold(
                onSuccess = {
                    actionState.update { it.copy(isDeleting = false, deleted = true) }
                },
                onFailure = { error ->
                    actionState.update {
                        it.copy(
                            isDeleting = false,
                            errorMessage = error.toIngredientMessage("删除失败，请重试"),
                        )
                    }
                },
            )
        }
    }

    fun clearError() {
        actionState.update { it.copy(errorMessage = null) }
    }

    fun consumeUpdateSucceeded() {
        actionState.update { it.copy(updateSucceeded = false) }
    }

    private data class ActionState(
        val isEditing: Boolean = false,
        val form: IngredientFormState = IngredientFormState.newInstance(),
        val isSaving: Boolean = false,
        val isDeleting: Boolean = false,
        val errorMessage: String? = null,
        val updateSucceeded: Boolean = false,
        val deleted: Boolean = false,
    )

    companion object {
        fun factory(
            repository: IngredientRepository,
            ingredientId: Long,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { IngredientDetailViewModel(repository, ingredientId) }
        }
    }
}
