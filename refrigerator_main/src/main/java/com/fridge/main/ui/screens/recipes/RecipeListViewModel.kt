package com.fridge.main.ui.screens.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fridge.main.data.local.relation.RecipeWithIngredients
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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** 菜谱列表筛选：全部菜谱 / 按现有食材推荐。 */
enum class RecipeListFilter(val label: String) {
    ALL("全部菜谱"),
    RECOMMENDED("推荐菜谱"),
}

/** 推荐页没有内容时的具体原因，用于给出不同的下一步引导。 */
enum class RecipeListEmptyReason {
    NO_RECIPES,
    NO_INGREDIENTS,
    NO_MATCHES,
}

/**
 * 列表项同时携带可选的匹配结果。
 *
 * “全部菜谱”下 [match] 为空；“推荐菜谱”下由 [RecipeMatcher] 计算后填入。
 */
data class RecipeListItem(
    val recipe: RecipeUiModel,
    val match: RecipeMatchResult? = null,
)

data class RecipeListUiState(
    val filter: RecipeListFilter = RecipeListFilter.ALL,
    val content: UiState<List<RecipeListItem>> = UiState.Loading,
    val emptyReason: RecipeListEmptyReason? = null,
)

/**
 * 菜谱列表 ViewModel。
 *
 * 同时观察菜谱与库存两条 Room Flow：库存变化后推荐等级会自动刷新。
 * 匹配计算放在 [Dispatchers.Default]，避免大列表计算阻塞主线程。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecipeListViewModel(
    private val recipeRepository: RecipeRepository,
    private val ingredientRepository: IngredientRepository,
) : ViewModel() {

    private val retrySignal = MutableStateFlow(0)
    private val filter = MutableStateFlow(RecipeListFilter.ALL)

    private val sourceFlow: Flow<Source> = retrySignal
        .flatMapLatest {
            // 用 flow<Source> 明确异常分支的类型，让同一个流既能发 Loaded 也能发 Failed。
            flow<Source> {
                emitAll(
                    combine(
                        recipeRepository.observeRecipes(),
                        ingredientRepository.observeIngredients(),
                    ) { recipes, ingredients ->
                        Source.Loaded(
                            recipes = recipes,
                            availableTypeIds = ingredients
                                .asSequence()
                                .filter { it.quantity > 0.0 }
                                .map { it.typeId }
                                .toSet(),
                        )
                    },
                )
            }.catch { error ->
                emit(Source.Failed(error.toRecipeMessage("读取菜谱失败，请重试")))
            }
        }

    val uiState: StateFlow<RecipeListUiState> = combine(sourceFlow, filter) { source, filter ->
        source.toUiState(filter)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RecipeListUiState(),
        )

    fun setFilter(value: RecipeListFilter) {
        if (filter.value != value) {
            filter.value = value
        }
    }

    fun retry() {
        retrySignal.update { it + 1 }
    }

    private fun Source.toUiState(filter: RecipeListFilter): RecipeListUiState = when (this) {
        is Source.Failed -> RecipeListUiState(
            filter = filter,
            content = UiState.Error(message),
        )

        is Source.Loaded -> buildLoadedState(filter)
    }

    private fun Source.Loaded.buildLoadedState(filter: RecipeListFilter): RecipeListUiState {
        val recipesUi = recipes.map { it.toUiModel() }

        if (recipesUi.isEmpty()) {
            return RecipeListUiState(
                filter = filter,
                content = UiState.Empty,
                emptyReason = RecipeListEmptyReason.NO_RECIPES,
            )
        }

        if (filter == RecipeListFilter.ALL) {
            return RecipeListUiState(
                filter = filter,
                content = UiState.Success(recipesUi.map { RecipeListItem(recipe = it) }),
            )
        }

        if (availableTypeIds.isEmpty()) {
            return RecipeListUiState(
                filter = filter,
                content = UiState.Empty,
                emptyReason = RecipeListEmptyReason.NO_INGREDIENTS,
            )
        }

        val byId = recipesUi.associateBy { it.id }
        val matches = RecipeMatcher.matchAll(
            recipes = recipes.map { it.toMatchableRecipe() },
            availableTypeIds = availableTypeIds,
        )
        val items = matches.mapNotNull { match ->
            byId[match.recipeId]?.let { recipe ->
                RecipeListItem(recipe = recipe, match = match)
            }
        }

        return if (items.isEmpty()) {
            RecipeListUiState(
                filter = filter,
                content = UiState.Empty,
                emptyReason = RecipeListEmptyReason.NO_MATCHES,
            )
        } else {
            RecipeListUiState(
                filter = filter,
                content = UiState.Success(items),
            )
        }
    }

    private sealed interface Source {
        data class Loaded(
            val recipes: List<RecipeWithIngredients>,
            val availableTypeIds: Set<Long>,
        ) : Source

        data class Failed(val message: String) : Source
    }

    companion object {
        fun factory(
            recipeRepository: RecipeRepository,
            ingredientRepository: IngredientRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                RecipeListViewModel(
                    recipeRepository = recipeRepository,
                    ingredientRepository = ingredientRepository,
                )
            }
        }
    }
}
