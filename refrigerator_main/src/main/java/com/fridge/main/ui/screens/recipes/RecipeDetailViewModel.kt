package com.fridge.main.ui.screens.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fridge.main.data.repository.IngredientRepository
import com.fridge.main.data.repository.RecipeRepository
import com.fridge.main.domain.matching.RecipeMatchResult
import com.fridge.main.domain.matching.RecipeMatcher
import com.fridge.main.ui.common.UiState
import com.fridge.main.ui.model.RecipeUiModel
import com.fridge.main.ui.model.toUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 菜谱详情页状态。
 *
 * [content] 只表达读取四态；编辑、保存、删除属于页面动作状态，
 * 保存失败时不会清空已读取的菜谱内容。
 */
data class RecipeDetailUiState(
    val content: UiState<RecipeUiModel> = UiState.Loading,
    val match: RecipeMatchResult? = null,
    val availableTypeIds: Set<Long> = emptySet(),
    val hasInventory: Boolean = false,
    val isEditing: Boolean = false,
    val form: RecipeFormState = RecipeFormState.newInstance(),
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val errorMessage: String? = null,
    val updateSucceeded: Boolean = false,
    val deleted: Boolean = false,
)

/**
 * 菜谱详情 ViewModel。
 *
 * - 持续观察 Room，编辑保存后详情和列表自动刷新；
 * - 更新时复用 [RecipeRepository.saveRecipe]，并在 DTO 上带回当前菜谱 id；
 * - 删除成功后置位 [RecipeDetailUiState.deleted]，由页面返回列表。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecipeDetailViewModel(
    private val repository: RecipeRepository,
    private val ingredientRepository: IngredientRepository,
    private val recipeId: Long,
) : ViewModel() {

    private val retrySignal = MutableStateFlow(0)
    private val actionState = MutableStateFlow(ActionState())

    private val dataFlow: Flow<DetailData> = retrySignal.flatMapLatest {
        if (recipeId <= 0) {
            flowOf(DetailData(content = UiState.Empty))
        } else {
            combine(
                repository.observeRecipe(recipeId),
                ingredientRepository.observeIngredients(),
            ) { recipe, ingredients ->
                val availableTypeIds = ingredients
                    .asSequence()
                    .filter { it.quantity > 0.0 }
                    .map { it.typeId }
                    .toSet()
                val hasInventory = ingredients.any { it.quantity > 0.0 }

                if (recipe == null) {
                    DetailData(
                        content = UiState.Empty,
                        availableTypeIds = availableTypeIds,
                        hasInventory = hasInventory,
                    )
                } else {
                    DetailData(
                        content = UiState.Success(recipe.toUiModel()),
                        match = RecipeMatcher.evaluate(
                            recipe = recipe.toMatchableRecipe(),
                            availableTypeIds = availableTypeIds,
                        ),
                        availableTypeIds = availableTypeIds,
                        hasInventory = hasInventory,
                    )
                }
            }.catch { error ->
                emit(
                    DetailData(
                        content = UiState.Error(error.toRecipeMessage("读取菜谱失败，请重试")),
                    ),
                )
            }
        }
    }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<RecipeDetailUiState> = combine(
        dataFlow,
        actionState,
    ) { data, action ->
        RecipeDetailUiState(
            content = data.content,
            match = data.match,
            availableTypeIds = data.availableTypeIds,
            hasInventory = data.hasInventory,
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
        initialValue = RecipeDetailUiState(),
    )

    fun retry() {
        retrySignal.update { it + 1 }
    }

    fun startEdit() {
        val recipe = (uiState.value.content as? UiState.Success)?.data ?: return
        actionState.update {
            it.copy(
                isEditing = true,
                form = RecipeFormState.from(recipe),
                errorMessage = null,
                updateSucceeded = false,
            )
        }
    }

    fun cancelEdit() {
        actionState.update {
            it.copy(
                isEditing = false,
                form = RecipeFormState.newInstance(),
                errorMessage = null,
            )
        }
    }

    fun onFormChange(form: RecipeFormState) {
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
        val draft = validated.toDraft()?.copy(id = recipeId) ?: return

        actionState.update {
            it.copy(form = validated, isSaving = true, errorMessage = null)
        }
        viewModelScope.launch {
            repository.saveRecipe(draft).fold(
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
                            errorMessage = error.toRecipeMessage("保存失败，请重试"),
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
            repository.deleteRecipe(recipeId).fold(
                onSuccess = {
                    actionState.update { it.copy(isDeleting = false, deleted = true) }
                },
                onFailure = { error ->
                    actionState.update {
                        it.copy(
                            isDeleting = false,
                            errorMessage = error.toRecipeMessage("删除失败，请重试"),
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

    private data class DetailData(
        val content: UiState<RecipeUiModel> = UiState.Loading,
        val match: RecipeMatchResult? = null,
        val availableTypeIds: Set<Long> = emptySet(),
        val hasInventory: Boolean = false,
    )

    private data class ActionState(
        val isEditing: Boolean = false,
        val form: RecipeFormState = RecipeFormState.newInstance(),
        val isSaving: Boolean = false,
        val isDeleting: Boolean = false,
        val errorMessage: String? = null,
        val updateSucceeded: Boolean = false,
        val deleted: Boolean = false,
    )

    companion object {
        fun factory(
            recipeRepository: RecipeRepository,
            ingredientRepository: IngredientRepository,
            recipeId: Long,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                RecipeDetailViewModel(
                    repository = recipeRepository,
                    ingredientRepository = ingredientRepository,
                    recipeId = recipeId,
                )
            }
        }
    }
}
