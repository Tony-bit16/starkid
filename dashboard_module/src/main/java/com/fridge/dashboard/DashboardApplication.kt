package com.fridge.dashboard

import android.app.Application
import com.fridge.dashboard.data.provider.IngredientProviderClient
import com.fridge.dashboard.data.repository.DashboardRepository
import com.fridge.dashboard.data.repository.DefaultDashboardRepository

/**
 * 看板进程级依赖容器。
 *
 * Repository 持有 60 秒内存缓存，必须跟随 Application 生命周期，
 * 这样用户退出再进入看板（Activity 重建但进程仍在）时可以复用最近一次 Provider 结果，
 * 避免每次打开都跨 App 查询。
 */
class DashboardApplication : Application() {

    val dashboardRepository: DashboardRepository by lazy {
        DefaultDashboardRepository(
            client = IngredientProviderClient(applicationContext),
        )
    }
}
