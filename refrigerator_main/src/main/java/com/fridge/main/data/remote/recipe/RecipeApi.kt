package com.fridge.main.data.remote.recipe

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * TheMealDB 公开菜谱接口。
 *
 * - `search.php` 按菜名搜索，无结果时响应中的 meals 为 null；
 * - `lookup.php` 按在线 id 获取详情，供在线详情页直接加载。
 *
 * 使用免费测试 key（"1"）即可，不需要在客户端保存私钥。
 */
interface RecipeApi {

    @GET("api/json/v1/1/search.php")
    suspend fun searchMeals(@Query("s") keyword: String): MealSearchResponse

    @GET("api/json/v1/1/lookup.php")
    suspend fun lookupMeal(@Query("i") mealId: String): MealSearchResponse
}
