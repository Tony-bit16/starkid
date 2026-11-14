package com.fridge.main.data.repository

import com.fridge.main.data.remote.NetworkResult
import com.fridge.main.data.remote.recipe.OnlineRecipe

/**
 * 在线菜谱仓库。
 *
 * 负责网络搜索/详情，以及把在线菜谱转换成 [RecipeDraft] 后交给本地
 * [RecipeRepository] 持久化；UI 不直接依赖 Retrofit 或 Room。
 */
interface OnlineRecipeRepository {

    /** 按菜名搜索；空关键词返回空列表，不发起网络请求。 */
    suspend fun searchRecipes(keyword: String): NetworkResult<List<OnlineRecipe>>

    /** 按 TheMealDB id 获取详情；不存在时返回 Success(null)。 */
    suspend fun getRecipe(mealId: String): NetworkResult<OnlineRecipe?>

    /** 把在线菜谱写入本地 Room；成功返回本地自增 id。 */
    suspend fun saveToLocal(recipe: OnlineRecipe): Result<Long>
}
