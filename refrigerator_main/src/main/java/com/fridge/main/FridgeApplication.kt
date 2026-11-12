package com.fridge.main

import android.app.Application
import com.fridge.main.data.local.FridgeDatabase

/**
 * 应用级容器。
 *
 * 本轮只提供数据库懒加载入口，不在这里做网络或磁盘 IO。
 * 后续新增 Repository 时，可在 Application 中组装依赖，或引入 Hilt 统一管理。
 */
class FridgeApplication : Application() {

    val database: FridgeDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        FridgeDatabase.getInstance(this)
    }
}
