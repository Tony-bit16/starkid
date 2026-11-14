package com.fridge.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.fridge.main.notification.ExpiryNotifications
import com.fridge.main.ui.FridgeApp
import com.fridge.main.ui.theme.FridgeTheme

/**
 * 主 App 唯一入口。
 *
 * 页面均为 Compose，Activity 不持有任何业务逻辑；后续业务放在 ViewModel 中。
 */
class MainActivity : ComponentActivity() {

    /**
     * 通知点击信号。
     *
     * 用递增整数而不是布尔值，保证用户连续点击通知时 Compose 侧仍能收到新事件；
     * Activity 只做 Intent 解析和信号转发，不持有任何页面/业务逻辑。
     */
    private var openIngredientsSignal by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra(ExpiryNotifications.EXTRA_OPEN_INGREDIENTS, false) == true) {
            openIngredientsSignal = 1
        }
        // 让内容延伸到状态栏/导航栏下，由 Scaffold 与 TopAppBar 处理安全区。
        enableEdgeToEdge()
        setContent {
            FridgeTheme {
                FridgeApp(openIngredientsSignal = openIngredientsSignal)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(ExpiryNotifications.EXTRA_OPEN_INGREDIENTS, false)) {
            openIngredientsSignal += 1
        }
    }
}
