package com.fridge.main.data.repository

import com.fridge.main.data.remote.BaseRemoteDataSource
import com.fridge.main.data.remote.NetworkResult
import com.fridge.main.data.remote.recipe.OnlineRecipe
import com.fridge.main.data.remote.recipe.RecipeApi
import com.fridge.main.data.remote.recipe.toOnlineRecipe
import com.fridge.main.data.remote.recipe.toRecipeDraft
import kotlinx.coroutines.CancellationException

/**
 * 在线菜谱仓库实现。
 *
 * 网络异常统一由 [BaseRemoteDataSource.execute] 映射；保存失败则沿用本地
 * [RecipeRepository] 的校验与异常结果，由详情页展示。
 */
class DefaultOnlineRecipeRepository(
    private val api: RecipeApi,
    private val recipeRepository: RecipeRepository,
) : BaseRemoteDataSource(), OnlineRecipeRepository {

    override suspend fun searchRecipes(keyword: String): NetworkResult<List<OnlineRecipe>> {
        val query = keyword.trim()
        if (query.isEmpty()) return NetworkResult.Success(emptyList())

        return execute(fallbackMessage = "搜索菜谱失败，请稍后重试") {
            api.searchMeals(query)
                .meals
                .orEmpty()
                .mapNotNull { it.toOnlineRecipe() }
        }
    }

    override suspend fun getRecipe(mealId: String): NetworkResult<OnlineRecipe?> {
        val id = mealId.trim()
        if (id.isEmpty()) {
            return NetworkResult.Failure("在线菜谱 id 无效")
        }

        return execute(fallbackMessage = "加载在线菜谱失败，请稍后重试") {
            api.lookupMeal(id)
                .meals
                ?.firstOrNull()
                ?.toOnlineRecipe()
        }
    }

    override suspend fun saveToLocal(recipe: OnlineRecipe): Result<Long> = try {
        recipeRepository.saveRecipe(recipe.toRecipeDraft())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        // 映射阶段（如极端脏数据）也不应让点击事件崩到主线程。
        Result.failure(error)
    }
}
