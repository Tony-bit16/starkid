package com.fridge.dashboard.data.export

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.fridge.dashboard.data.model.DashboardIngredient
import com.fridge.dashboard.domain.export.DashboardCsvFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 导出成功后返回给 UI 的信息。contentUri 已带 FileProvider 读权限。 */
data class CsvExportFile(
    val fileName: String,
    val displayPath: String,
    val contentUri: String,
)

/** 导出失败类型，ViewModel 会转换成用户可行动的中文提示。 */
sealed class CsvExportException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    class Permission(cause: Throwable? = null) :
        CsvExportException("没有写入下载目录的权限", cause)

    class StorageFull(cause: Throwable? = null) :
        CsvExportException("存储空间不足", cause)

    class Write(cause: Throwable? = null) :
        CsvExportException("CSV 文件写入失败", cause)
}

/** 导出契约，便于后续替换为 SAF 或测试实现。 */
interface DashboardCsvExporter {
    suspend fun export(ingredients: List<DashboardIngredient>): Result<CsvExportFile>
}

/**
 * 食材 CSV 导出器。
 *
 * 流程：
 * 1. 在 cacheDir/exports 生成一份 CSV，交给 FileProvider 供“打开文件”使用；
 * 2. Android 10+ 通过 MediaStore 写入公共 Downloads/Fridge，不需要存储权限；
 * 3. Android 7-9 在已授予 WRITE 权限的前提下写入公共 Downloads，并媒体扫描；
 * 4. 任一步失败都清理缓存文件并返回可读错误。
 */
class AndroidDashboardCsvExporter(
    context: Context,
) : DashboardCsvExporter {

    private val appContext = context.applicationContext

    override suspend fun export(
        ingredients: List<DashboardIngredient>,
    ): Result<CsvExportFile> = withContext(Dispatchers.IO) {
        var cacheFile: File? = null
        try {
            val fileName = DashboardCsvFormatter.FILE_PREFIX +
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".csv"
            val csvBytes = DashboardCsvFormatter.format(ingredients).toByteArray(Charsets.UTF_8)

            val exportDir = File(appContext.cacheDir, "exports").apply {
                if (!exists() && !mkdirs()) {
                    throw IOException("无法创建导出缓存目录")
                }
            }
            cacheFile = File(exportDir, fileName).apply { writeBytes(csvBytes) }

            val uri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                cacheFile,
            )

            val displayPath = publishToDownloads(fileName, csvBytes)
            Result.success(
                CsvExportFile(
                    fileName = fileName,
                    displayPath = displayPath,
                    contentUri = uri.toString(),
                ),
            )
        } catch (error: SecurityException) {
            cacheFile?.delete()
            Result.failure(CsvExportException.Permission(error))
        } catch (error: IOException) {
            cacheFile?.delete()
            val message = error.message.orEmpty()
            val isStorageFull = message.contains("ENOSPC", ignoreCase = true) ||
                message.contains("No space left", ignoreCase = true) ||
                message.contains("space", ignoreCase = true)
            val isPermissionDenied = message.contains("EACCES", ignoreCase = true) ||
                message.contains("Permission denied", ignoreCase = true)
            Result.failure(
                when {
                    isPermissionDenied -> CsvExportException.Permission(error)
                    isStorageFull -> CsvExportException.StorageFull(error)
                    else -> CsvExportException.Write(error)
                },
            )
        } catch (error: Throwable) {
            cacheFile?.delete()
            Result.failure(CsvExportException.Write(error))
        }
    }

    /** 写到系统公共下载目录，返回给用户看的位置文案。 */
    private fun publishToDownloads(fileName: String, bytes: ByteArray): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishWithMediaStore(fileName, bytes)
        } else {
            publishLegacy(fileName, bytes)
        }
    }

    private fun publishWithMediaStore(fileName: String, bytes: ByteArray): String {
        val resolver = appContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/Fridge",
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("无法创建下载文件")
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IOException("无法打开下载文件")
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
            return "下载/Fridge/$fileName"
        } catch (error: Throwable) {
            // 失败时删除半成品，避免公共目录残留空文件。
            resolver.delete(uri, null, null)
            throw error
        }
    }

    @Suppress("DEPRECATION")
    private fun publishLegacy(fileName: String, bytes: ByteArray): String {
        val downloads = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS,
        )
        val dir = File(downloads, "Fridge")
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("无法创建公共下载目录")
        }

        val file = File(dir, fileName)
        FileOutputStream(file).use { it.write(bytes) }
        MediaScannerConnection.scanFile(
            appContext,
            arrayOf(file.absolutePath),
            arrayOf("text/csv"),
            null,
        )
        return "下载/Fridge/$fileName"
    }
}
