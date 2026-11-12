package com.fridge.dashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fridge.dashboard.ui.DashboardApp
import com.fridge.dashboard.ui.theme.DashboardTheme

/**
 * 伴生看板 App 的入口。
 *
 * 独立 APK、独立进程；本轮没有 ViewModel / Repository / ContentProvider 实现，
 * 页面只展示项目骨架与静态示意图表。
 */
class DashboardActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DashboardTheme {
                DashboardApp()
            }
        }
    }
}
