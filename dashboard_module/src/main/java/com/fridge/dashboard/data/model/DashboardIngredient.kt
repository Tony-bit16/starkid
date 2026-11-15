package com.fridge.dashboard.data.model

/**
 * 看板从主 App 读取的一条食材库存（一次入库批次）。
 *
 * 看板只做展示与后续统计，不会回写主 App；因此这里是主 App Entity 的只读投影。
 */
data class DashboardIngredient(
    val id: Long,
    val typeId: Long,
    val name: String,
    val category: String,
    val storedAt: Long,
    val shelfLifeDays: Int,
    val quantity: Double,
    val unit: String,
    val storedAtLabel: String,
    val quantityLabel: String,
)

/**
 * 数据通道可预期的失败类型。
 *
 * 与普通异常分开，便于 ViewModel 给用户不同的可行动提示。
 */
sealed class DashboardDataException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** 主 App 未安装，或已安装的版本没有注册 Provider。 */
    class MainAppUnavailable : DashboardDataException("未检测到主 App 的数据通道")

    /** 两个 App 签名不一致，signature 权限未授予。 */
    class PermissionDenied(cause: Throwable? = null) :
        DashboardDataException("没有读取主 App 食材数据的权限", cause)

    /** Provider 存在，但查询或 Cursor 解析失败。 */
    class QueryFailed(cause: Throwable? = null) :
        DashboardDataException("读取主 App 食材数据失败", cause)
}

/** 将数据层异常转成用户可理解、可行动的提示。 */
fun Throwable.toDashboardUserMessage(): String {
    return when (this) {
        is DashboardDataException.MainAppUnavailable ->
            "未检测到“冰箱管家”。请先安装主 App，并在主 App 中添加食材后重试。"

        is DashboardDataException.PermissionDenied ->
            "没有读取主 App 数据的权限。请确认两个 App 使用同一签名安装后重试。"

        is DashboardDataException.QueryFailed ->
            "读取主 App 食材数据失败。请稍后重试，或重新打开主 App 后再试。"

        else -> "读取数据时发生未知错误，请稍后重试。"
    }
}
