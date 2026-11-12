package com.fridge.dashboard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fridge.dashboard.ui.model.DashboardSampleData
import com.fridge.dashboard.ui.model.DashboardUiState
import com.fridge.dashboard.ui.screens.DashboardExportScreen
import com.fridge.dashboard.ui.screens.DashboardHomeScreen

/** 看板内部两个页面；本轮只有主页与导出页，不需要引入 Navigation 组件。 */
private enum class DashboardScreen {
    HOME,
    EXPORT,
}

/**
 * 伴生看板 App 的内容骨架。
 *
 * 与主 App 完全独立：不共享代码、不共享进程，后续仅通过 ContentProvider 数据契约通信。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardApp() {
    var screen by rememberSaveable { mutableStateOf(DashboardScreen.HOME) }

    // 导出页不是一级页面，系统返回键应回到看板主页。
    BackHandler(enabled = screen == DashboardScreen.EXPORT) {
        screen = DashboardScreen.HOME
    }

    val isHome = screen == DashboardScreen.HOME

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = if (isHome) "统计看板" else "导出统计") },
                navigationIcon = {
                    if (!isHome) {
                        TextButton(
                            onClick = { screen = DashboardScreen.HOME },
                            modifier = Modifier.padding(start = 4.dp),
                        ) {
                            Text("返回")
                        }
                    }
                },
                actions = {
                    if (isHome) {
                        TextButton(onClick = { screen = DashboardScreen.EXPORT }) {
                            Text("导出")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        when (screen) {
            DashboardScreen.HOME -> DashboardHomeScreen(
                state = DashboardUiState.Success(DashboardSampleData.stats),
                onRetry = {},
                onExportClick = { screen = DashboardScreen.EXPORT },
                modifier = Modifier.padding(innerPadding),
            )

            DashboardScreen.EXPORT -> DashboardExportScreen(
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}
