package com.fridge.dashboard.data.provider

import android.content.Context
import android.database.Cursor
import com.fridge.dashboard.data.model.DashboardDataException
import com.fridge.dashboard.data.model.DashboardIngredient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 主 App 食材 Provider 的只读客户端。
 *
 * 所有 Cursor/ContentResolver 操作都在 IO 线程执行；看板不持有主 App 的数据库对象。
 */
class IngredientProviderClient(
    private val context: Context,
) {

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
