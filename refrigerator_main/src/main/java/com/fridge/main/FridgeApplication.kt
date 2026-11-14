package com.fridge.main

import android.app.Application
import com.fridge.main.data.local.FridgeDatabase
import com.fridge.main.data.remote.recipe.RecipeApiClient
import com.fridge.main.data.repository.DefaultIngredientRepository
import com.fridge.main.data.repository.DefaultOnlineRecipeRepository
import com.fridge.main.data.repository.DefaultRecipeRepository
import com.fridge.main.data.repository.IngredientRepository
import com.fridge.main.data.repository.OnlineRecipeRepository
import com.fridge.main.data.repository.RecipeRepository

/**
 * 应用级容器。
 *
 * 本轮用最少的依赖组装代码接入 Room：
 * - [database] 懒加载，首次真正查询时才打开；
 * - 食材/菜谱 Repository 只依赖数据库提供者，打开失败时由页面进入错误态。
 *
 * 后续模块变多后再考虑引入 Hilt；当前不引入额外框架，避免初始化阶段过度设计。
 */
class FridgeApplication : Application() {

    val database: FridgeDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        FridgeDatabase.getInstance(this)
    }

    val ingredientRepository: IngredientRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DefaultIngredientRepository(databaseProvider = { database })
    }

    val recipeRepository: RecipeRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DefaultRecipeRepository(databaseProvider = { database })
    }

    /**
     * 在线菜谱仓库复用本地菜谱仓库，保存时仍走与手动新增相同的校验/事务逻辑。
     * 接口客户端是懒加载单例，未打开在线搜索时不会发起任何网络请求。
     */
    val onlineRecipeRepository: OnlineRecipeRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DefaultOnlineRecipeRepository(
            api = RecipeApiClient.api,
            recipeRepository = recipeRepository,
        )
    }
}
