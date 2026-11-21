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
import com.fridge.main.data.settings.DefaultSettingsRepository
import com.fridge.main.data.settings.SettingsRepository
import com.fridge.main.notification.ExpiryNotifications
import com.fridge.main.work.ExpiryReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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

    /**
     * 进程级协程作用域：只做启动时的轻量装配，不持有页面引用。
     * 应用进程结束时随进程回收，不需要手动取消。
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // 渠道可重复创建；调度使用唯一周期任务，冷启动不会叠加多个 Worker。
        ExpiryNotifications.createChannel(this)
        // DataStore 读取是 IO，放到后台；读到首个配置后再决定是否/何时调度。
        applicationScope.launch {
            val settings = settingsRepository.settings.first()
            ExpiryReminderScheduler.ensureScheduled(this@FridgeApplication, settings)
        }
    }

    /** 用户配置仓库，全 App 单实例；设置页与 Worker 共用同一份 DataStore。 */
    val settingsRepository: SettingsRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DefaultSettingsRepository(this)
    }

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
