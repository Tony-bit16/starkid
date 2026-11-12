package com.fridge.main.ui.screens.ingredients

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException

/**
 * 把数据层异常转成用户可读的中文提示。
 *
 * 原则：
 * - 校验异常（IllegalArgumentException）与“记录不存在”使用业务代码里写好的中文；
 * - SQLite 细节只写日志/不直接展示，避免把表名、SQL 暴露给用户；
 * - 其它未知异常统一使用页面兜底文案，保证 UI 有错误态而不是崩溃。
 */
internal fun Throwable.toIngredientMessage(fallback: String): String = when (this) {
    is IllegalArgumentException -> message?.takeIf { it.isNotBlank() } ?: fallback
    is NoSuchElementException -> message?.takeIf { it.isNotBlank() } ?: fallback
    is SQLiteConstraintException -> "数据冲突，请检查食材名称后重试"
    is SQLiteException -> "数据库操作失败，请稍后重试"
    is IllegalStateException -> message
        ?.takeIf { it.startsWith("食材") || it.startsWith("品类") }
        ?: fallback
    else -> fallback
}
