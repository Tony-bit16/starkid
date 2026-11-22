package com.fridge.dashboard.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fridge.dashboard.ui.model.DashboardExportResult
import com.fridge.dashboard.ui.screens.DashboardExportScreen
import com.fridge.dashboard.ui.screens.DashboardHomeScreen
import com.fridge.dashboard.ui.screens.DashboardHomeViewModel

/** 看板内部两个页面；本轮只有主页与导出页，不需要引入 Navigation 组件。 */
private enum class DashboardScreen {
    HOME,
    EXPORT,
}

/** 旧版 Android 存储权限被拒绝后的弹窗状态。 */
private data class StoragePermissionIssue(
    val canAskAgain: Boolean,
)

/**
 * 伴生看板 App 的内容骨架。
 *
 * 与主 App 完全独立：不共享代码、不共享进程，只通过 ContentProvider 数据契约通信。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardApp() {
    var screen by rememberSaveable { mutableStateOf(DashboardScreen.HOME) }
    var permissionIssue by remember { mutableStateOf<StoragePermissionIssue?>(null) }
    var openError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val homeViewModel: DashboardHomeViewModel = viewModel(
        factory = DashboardHomeViewModel.factory(context),
    )
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()
    val isRefreshing by homeViewModel.isRefreshing.collectAsStateWithLifecycle()
    val exportState by homeViewModel.exportState.collectAsStateWithLifecycle()

    val openCsv: (String) -> Boolean = { uriString ->
        try {
            val fileIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(uriString), "text/csv")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            // chooser 本身总能启动；先确认真的有查看器，避免显示空的系统选择器。
            if (fileIntent.resolveActivity(context.packageManager) == null) {
                false
            } else {
                context.startActivity(
                    Intent.createChooser(fileIntent, "打开 CSV").apply {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                )
                true
            }
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            homeViewModel.exportCsv()
        } else {
            val canAskAgain = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(
                    it,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                )
            } ?: false
            permissionIssue = StoragePermissionIssue(canAskAgain = canAskAgain)
        }
    }

    val requestExport = {
        val needsLegacyPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED

        if (needsLegacyPermission) {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            homeViewModel.exportCsv()
        }
    }

    // 导出页不是一级页面，系统返回键应回到看板主页。
    BackHandler(enabled = screen == DashboardScreen.EXPORT) {
        screen = DashboardScreen.HOME
    }

    val isHome = screen == DashboardScreen.HOME

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = if (isHome) "统计看板" else "导出说明") },
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
                        TextButton(
                            onClick = homeViewModel::refresh,
                            enabled = !isRefreshing,
                        ) {
                            Text(if (isRefreshing) "刷新中…" else "刷新")
                        }
                        TextButton(
                            onClick = { requestExport() },
                            enabled = !exportState.isExporting,
                        ) {
                            Text(if (exportState.isExporting) "导出中…" else "导出")
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
                state = homeState,
                onRetry = homeViewModel::retry,
                isRefreshing = isRefreshing,
                isExporting = exportState.isExporting,
                onExportClick = { requestExport() },
                onOpenExportPage = { screen = DashboardScreen.EXPORT },
                modifier = Modifier.padding(innerPadding),
            )

            DashboardScreen.EXPORT -> DashboardExportScreen(
                modifier = Modifier.padding(innerPadding),
            )
        }
    }

    permissionIssue?.let { issue ->
        AlertDialog(
            onDismissRequest = { permissionIssue = null },
            title = { Text("需要存储权限") },
            text = {
                Text(
                    if (issue.canAskAgain) {
                        "导出 CSV 需要写入公共下载目录。你仍可正常查看看板统计。"
                    } else {
                        "存储权限已被拒绝，无法写入下载目录。你仍可正常查看统计；" +
                            "如需导出，请到系统设置中为“食材看板”开启存储权限。"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        permissionIssue = null
                        if (issue.canAskAgain) {
                            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        }
                    },
                ) {
                    Text(if (issue.canAskAgain) "重新申请" else "去设置")
                }
            },
            dismissButton = {
                TextButton(onClick = { permissionIssue = null }) {
                    Text("取消")
                }
            },
        )
    }

    exportState.result?.let { result ->
        when (result) {
            is DashboardExportResult.Success -> AlertDialog(
                onDismissRequest = { homeViewModel.clearExportResult() },
                title = { Text("导出成功") },
                text = { Text("已保存到 ${result.displayPath}") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val opened = openCsv(result.contentUri)
                            homeViewModel.clearExportResult()
                            if (!opened) {
                                openError = "未找到可打开 CSV 的应用。请安装文件管理器后重试。"
                            }
                        },
                    ) {
                        Text("打开文件")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { homeViewModel.clearExportResult() }) {
                        Text("关闭")
                    }
                },
            )

            is DashboardExportResult.Failure -> AlertDialog(
                onDismissRequest = { homeViewModel.clearExportResult() },
                title = { Text("导出失败") },
                text = { Text(result.message) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            homeViewModel.clearExportResult()
                            requestExport()
                        },
                    ) {
                        Text("重试")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { homeViewModel.clearExportResult() }) {
                        Text("关闭")
                    }
                },
            )
        }
    }

    openError?.let { message ->
        AlertDialog(
            onDismissRequest = { openError = null },
            title = { Text("无法打开文件") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { openError = null }) {
                    Text("知道了")
                }
            },
        )
    }
}

/** 从 Compose 的 LocalContext 中取出 Activity，用于权限 rationale 判断。 */
private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
