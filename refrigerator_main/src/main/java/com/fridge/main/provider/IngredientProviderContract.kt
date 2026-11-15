package com.fridge.main.provider

import android.net.Uri

/**
 * 主 App 与伴生看板之间的只读数据契约。
 *
 * 两个 APK 不共享代码，因此看板模块里保留了同名字段的一份副本；
 * 修改列名、authority 或权限名时必须同步修改：
 * `dashboard_module/.../data/provider/IngredientProviderContract.kt`。
 */
object IngredientProviderContract {

    /** ContentProvider 的唯一 authority。 */
    const val AUTHORITY = "com.fridge.main.provider"

    /** 只读权限；signature 级别，只有与主 App 使用同一签名安装的 App 才会被授予。 */
    const val READ_PERMISSION = "com.fridge.main.permission.READ_INGREDIENTS"

    /** 写权限；当前 Provider 不开放写入，仍显式声明以避免误用。 */
    const val WRITE_PERMISSION = "com.fridge.main.permission.WRITE_INGREDIENTS"

    /** 食材列表路径。 */
    const val PATH_INGREDIENTS = "ingredients"

    /** 食材集合 URI。 */
    val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_INGREDIENTS")

    const val COLUMN_ID = "_id"
    const val COLUMN_TYPE_ID = "type_id"
    const val COLUMN_NAME = "name"
    const val COLUMN_CATEGORY = "category"
    const val COLUMN_STORED_AT = "stored_at"
    const val COLUMN_SHELF_LIFE_DAYS = "shelf_life_days"
    const val COLUMN_QUANTITY = "quantity"
    const val COLUMN_UNIT = "unit"

    /** 看板默认读取的完整字段集合。 */
    val DEFAULT_PROJECTION: Array<String> = arrayOf(
        COLUMN_ID,
        COLUMN_TYPE_ID,
        COLUMN_NAME,
        COLUMN_CATEGORY,
        COLUMN_STORED_AT,
        COLUMN_SHELF_LIFE_DAYS,
        COLUMN_QUANTITY,
        COLUMN_UNIT,
    )
}
