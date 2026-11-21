package com.fridge.dashboard.data.provider

import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.fridge.dashboard.data.model.DashboardDataException
import com.fridge.dashboard.data.model.DashboardIngredient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 主 App 食材 Provider 的只读客户端。
 *
 * 所有 Cursor/ContentResolver 操作都在 IO 线程执行；看板不持有主 App 的数据库对象。
 */
class IngredientProviderClient(
    private val context: Context,
) {

    /**
     * 持续观察主 App 的食材表。
     *
     * 主 App Provider 在 Room 的 `ingredients` 表失效时会 `notifyChange`；
     * 这里通过 ContentObserver 接收通知并重新查询，解决“看板停留在前台时
     * 主 App 新增食材，看板必须手动重试才更新”的同步延迟。
     *
     * 首次订阅先主动查询一次；失败仍以 [Result] 发射，交给 ViewModel 映射为
     * 错误态，不在这里抛异常。
     */
    fun observeIngredients(): Flow<Result<List<DashboardIngredient>>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                // 高频写入时用 conflate 合并，避免排队多次重复查询。
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(
            IngredientProviderContract.CONTENT_URI,
            true,
            observer,
        )
        trySend(Unit)
        awaitClose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }.conflate().map { loadIngredients() }

    suspend fun loadIngredients(): Result<List<DashboardIngredient>> = withContext(Dispatchers.IO) {
        try {
            val providerInfo = context.packageManager.resolveContentProvider(
                IngredientProviderContract.AUTHORITY,
                0,
            )
            if (providerInfo == null) {
                return@withContext Result.failure(DashboardDataException.MainAppUnavailable())
            }

            val cursor = context.contentResolver.query(
                IngredientProviderContract.CONTENT_URI,
                IngredientProviderContract.DEFAULT_PROJECTION,
                null,
                null,
                null,
            ) ?: return@withContext Result.failure(DashboardDataException.QueryFailed())

            cursor.use { result ->
                Result.success(result.toIngredients())
            }
        } catch (error: SecurityException) {
            Result.failure(DashboardDataException.PermissionDenied(error))
        } catch (error: IllegalArgumentException) {
            // authority 不存在/被移除时，系统可能以 IllegalArgumentException 表现。
            Result.failure(DashboardDataException.MainAppUnavailable())
        } catch (error: Exception) {
            Result.failure(DashboardDataException.QueryFailed(error))
        }
    }

    private fun Cursor.toIngredients(): List<DashboardIngredient> {
        val idIndex = getColumnIndexOrThrow(IngredientProviderContract.COLUMN_ID)
        val typeIdIndex = getColumnIndexOrThrow(IngredientProviderContract.COLUMN_TYPE_ID)
        val nameIndex = getColumnIndexOrThrow(IngredientProviderContract.COLUMN_NAME)
        val categoryIndex = getColumnIndexOrThrow(IngredientProviderContract.COLUMN_CATEGORY)
        val storedAtIndex = getColumnIndexOrThrow(IngredientProviderContract.COLUMN_STORED_AT)
        val shelfIndex = getColumnIndexOrThrow(IngredientProviderContract.COLUMN_SHELF_LIFE_DAYS)
        val quantityIndex = getColumnIndexOrThrow(IngredientProviderContract.COLUMN_QUANTITY)
        val unitIndex = getColumnIndexOrThrow(IngredientProviderContract.COLUMN_UNIT)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val result = mutableListOf<DashboardIngredient>()
        while (moveToNext()) {
            val storedAt = getLong(storedAtIndex)
            val quantity = getDouble(quantityIndex)
            result += DashboardIngredient(
                id = getLong(idIndex),
                typeId = getLong(typeIdIndex),
                name = getString(nameIndex).orEmpty().ifBlank { "未命名食材" },
                category = getString(categoryIndex).orEmpty().ifBlank { "未分类" },
                storedAt = storedAt,
                shelfLifeDays = getInt(shelfIndex),
                quantity = quantity,
                unit = getString(unitIndex).orEmpty(),
                storedAtLabel = if (storedAt > 0L) {
                    dateFormat.format(Date(storedAt))
                } else {
                    "未知"
                },
                quantityLabel = formatQuantity(quantity),
            )
        }
        return result
    }

    /** 去掉无意义的小数尾零，保持「2.5 kg」「1 L」这样的可读文本。 */
    private fun formatQuantity(value: Double): String {
        return String.format(Locale.US, "%.2f", value)
            .trimEnd('0')
            .trimEnd('.')
    }
}
