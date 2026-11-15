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
 * 独立 APK、独立进程；通过 ContentResolver 读取主 App 的只读食材 Provider，
 * 本轮展示数据明细，图表与 CSV 导出留到后续阶段。
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
