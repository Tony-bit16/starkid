package com.fridge.main.ui.screens.recipes

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException

/**
 * 把菜谱数据层异常转成用户可读的中文提示。
 *
 * SQLite 的约束名/SQL 细节不直接展示；页面只关心“能否重试”和可操作的提示。
 */
internal fun Throwable.toRecipeMessage(fallback: String): String = when (this) {
    is IllegalArgumentException -> message?.takeIf { it.isNotBlank() } ?: fallback
    is NoSuchElementException -> message?.takeIf { it.isNotBlank() } ?: fallback
    is SQLiteConstraintException -> "菜谱数据冲突，请检查食材是否重复后重试"
    is SQLiteException -> "数据库操作失败，请稍后重试"
    is IllegalStateException -> message
        ?.takeIf { it.startsWith("菜谱") || it.startsWith("食材") }
        ?: fallback
    else -> fallback
}
