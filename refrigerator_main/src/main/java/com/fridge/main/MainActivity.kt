package com.fridge.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fridge.main.ui.FridgeApp
import com.fridge.main.ui.theme.FridgeTheme

/**
 * 主 App 唯一入口。
 *
 * 页面均为 Compose，Activity 不持有任何业务逻辑；后续业务放在 ViewModel 中。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 让内容延伸到状态栏/导航栏下，由 Scaffold 与 TopAppBar 处理安全区。
        enableEdgeToEdge()
        setContent {
            FridgeTheme {
                FridgeApp()
            }
        }
    }
}
