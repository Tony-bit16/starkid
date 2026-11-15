package com.fridge.dashboard.data.provider

import android.net.Uri

/**
 * 看板侧的主 App Provider 契约副本。
 *
 * 看板不依赖主 App 的代码，因此这里的 authority、权限名、列名必须与
 * `refrigerator_main/.../provider/IngredientProviderContract.kt` 保持一致。
 */
object IngredientProviderContract {

    const val AUTHORITY = "com.fridge.main.provider"
    const val READ_PERMISSION = "com.fridge.main.permission.READ_INGREDIENTS"
    const val PATH_INGREDIENTS = "ingredients"

    val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_INGREDIENTS")

    const val COLUMN_ID = "_id"
    const val COLUMN_TYPE_ID = "type_id"
    const val COLUMN_NAME = "name"
    const val COLUMN_CATEGORY = "category"
    const val COLUMN_STORED_AT = "stored_at"
    const val COLUMN_SHELF_LIFE_DAYS = "shelf_life_days"
    const val COLUMN_QUANTITY = "quantity"
    const val COLUMN_UNIT = "unit"

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
