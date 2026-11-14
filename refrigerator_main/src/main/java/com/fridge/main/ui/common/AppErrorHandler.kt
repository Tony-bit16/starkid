package com.fridge.main.ui.common

import android.util.Log
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 全局 UI 错误总线。
 *
 * 数据库和网络异常分别在 Repository/ViewModel 中映射为页面状态；这里集中处理
 * 页面跳转等“非数据层”异常，由根 Scaffold 的 SnackbarHost 统一提示。
 * 只报告可恢复异常，不替换 Android 默认的未捕获异常处理器，避免掩盖真正的崩溃。
 */
object AppErrorHandler {

    private const val TAG = "FridgeApp"

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    fun report(message: String, throwable: Throwable? = null) {
        Log.w(TAG, message, throwable)
        _messages.tryEmit(message)
    }
}

/**
 * 安全跳转：路由不存在、NavController 状态非法时不让 App 闪退，只给出友好提示。
 */
fun NavHostController.safeNavigate(
    route: String,
    builder: NavOptionsBuilder.() -> Unit = {},
) {
    try {
        navigate(route, builder)
    } catch (error: IllegalArgumentException) {
        AppErrorHandler.report("页面打开失败，请返回后重试", error)
    } catch (error: IllegalStateException) {
        AppErrorHandler.report("页面暂时无法打开，请稍后重试", error)
    }
}

/** 安全返回；返回栈异常时保留当前页并提示。 */
fun NavHostController.safePopBackStack() {
    try {
        popBackStack()
    } catch (error: IllegalStateException) {
        AppErrorHandler.report("返回失败，请稍后重试", error)
    }
}

/** 安全导航返回；用于顶部“返回”按钮。 */
fun NavHostController.safeNavigateUp() {
    try {
        navigateUp()
    } catch (error: IllegalStateException) {
        AppErrorHandler.report("返回失败，请稍后重试", error)
    }
}
