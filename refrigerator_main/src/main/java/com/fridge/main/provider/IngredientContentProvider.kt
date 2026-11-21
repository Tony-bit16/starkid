package com.fridge.main.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Process
import androidx.room.InvalidationTracker
import com.fridge.main.FridgeApplication
import com.fridge.main.data.local.entity.Ingredient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * 主 App 暴露给伴生看板的只读食材 Provider。
 *
 * 安全边界：
 * - `android:exported=true`，但读取必须持有 signature 级 [IngredientProviderContract.READ_PERMISSION]；
 * - 不共享数据库文件、不使用 sharedUserId；
 * - 只实现 query，insert/update/delete 一律拒绝，避免看板误改主 App 数据。
 *
 * ContentProvider 的 query 是同步 API，系统会在 Binder 线程调用；
 * 这里用 `runBlocking(Dispatchers.IO)` 把 Room 查询放到 IO 线程，避免占用主线程。
 */
class IngredientContentProvider : ContentProvider() {

    /**
     * Room 写入不会自动通知 ContentResolver。
     *
     * 看板通过 ContentObserver 监听 [IngredientProviderContract.CONTENT_URI]；
     * 这里把 Room 的 `ingredients` 表失效事件转成 ContentResolver 通知，
     * 看板无需重启即可看到主 App 新增/修改/删除后的最新数据。
     */
    private val ingredientTableObserver = object : InvalidationTracker.Observer("ingredients") {
        override fun onInvalidated(tables: Set<String>) {
            context?.contentResolver?.notifyChange(IngredientProviderContract.CONTENT_URI, null)
        }
    }

    override fun onCreate(): Boolean {
        // Provider 可能在没有页面时被系统创建；初始化失败不应让进程崩溃，
        // query 仍会返回可读的错误结果。
        runCatching {
            (context?.applicationContext as? FridgeApplication)
                ?.database
                ?.invalidationTracker
                ?.addObserver(ingredientTableObserver)
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        if (uri.pathSegments != listOf(IngredientProviderContract.PATH_INGREDIENTS)) {
            return null
        }

        val appContext = context ?: return null
        val app = appContext.applicationContext as? FridgeApplication ?: return null

        // Provider 已通过 manifest 的 readPermission 保护；这里再加一层显式校验，
        // 防止未来误把 provider 改成 exported 且忘记权限。
        val callingUid = Binder.getCallingUid()
        if (callingUid != Process.myUid() &&
            appContext.checkCallingPermission(IngredientProviderContract.READ_PERMISSION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("缺少读取食材数据的权限")
        }

        val requestedColumns = normalizeProjection(projection)
        val ingredients = runBlocking(Dispatchers.IO) {
            app.database.ingredientDao().findAll()
        }

        return MatrixCursor(requestedColumns.toTypedArray(), ingredients.size).apply {
            ingredients.forEach { ingredient ->
                addRow(ingredient.toRow(requestedColumns))
            }
            setNotificationUri(appContext.contentResolver, IngredientProviderContract.CONTENT_URI)
        }
    }

    override fun getType(uri: Uri): String? {
        return if (uri.pathSegments == listOf(IngredientProviderContract.PATH_INGREDIENTS)) {
            "vnd.android.cursor.dir/vnd.com.fridge.main.ingredient"
        } else {
            null
        }
    }

    /** 只读 Provider：拒绝任何写入，返回 0 / null 而不抛异常。 */
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun bulkInsert(uri: Uri, values: Array<out ContentValues>): Int = 0

    /**
     * 只允许契约中的列，避免调用方传入未知列导致 MatrixCursor 行长度不一致。
     * 未传 projection 时返回默认全字段。
     */
    private fun normalizeProjection(projection: Array<out String>?): List<String> {
        if (projection.isNullOrEmpty()) {
            return IngredientProviderContract.DEFAULT_PROJECTION.toList()
        }
        val allowed = IngredientProviderContract.DEFAULT_PROJECTION.toSet()
        val unknown = projection.filterNot { it in allowed }
        require(unknown.isEmpty()) { "不支持的查询列: ${unknown.joinToString()}" }
        return projection.toList()
    }

    private fun Ingredient.toRow(columns: List<String>): Array<Any?> {
        return columns.map { column ->
            when (column) {
                IngredientProviderContract.COLUMN_ID -> id
                IngredientProviderContract.COLUMN_TYPE_ID -> typeId
                IngredientProviderContract.COLUMN_NAME -> name
                IngredientProviderContract.COLUMN_CATEGORY -> category
                IngredientProviderContract.COLUMN_STORED_AT -> storedAt
                IngredientProviderContract.COLUMN_SHELF_LIFE_DAYS -> shelfLifeDays
                IngredientProviderContract.COLUMN_QUANTITY -> quantity
                IngredientProviderContract.COLUMN_UNIT -> unit
                else -> null
            }
        }.toTypedArray()
    }
}
