package com.fridge.main

import android.app.Application
import com.fridge.main.data.local.FridgeDatabase
import com.fridge.main.data.repository.DefaultIngredientRepository
import com.fridge.main.data.repository.DefaultRecipeRepository
import com.fridge.main.data.repository.IngredientRepository
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
}
