package com.fridge.main.ui.model

/**
 * 食材新鲜度，用于界面状态色与筛选展示。
 *
 * 剩余天数由 `IngredientUiModel` 根据入库日期与保质期计算。
 */
enum class FreshnessStatus(val label: String) {
    FRESH("新鲜"),
    EXPIRING("临期"),
    EXPIRED("已过期"),
}

/** 菜谱列表/详情共用的展示模型。步骤以字符串列表保存，详情页按顺序编号。 */
data class RecipePreview(
    val id: Long,
    val name: String,
    val ingredientNames: List<String>,
    val steps: List<String>,
)

/**
 * 菜谱页面的静态占位数据。
 *
 * 本轮只接入食材模块；菜谱模块仍为静态原型，接入菜谱 Repository 后删除。
 * 食材列表已改为读取 Room，不再保留任何食材静态数据。
 */
object SampleData {

    val recipes: List<RecipePreview> = listOf(
        RecipePreview(
            id = 1L,
            name = "番茄炒蛋",
            ingredientNames = listOf("番茄", "鸡蛋", "小葱"),
            steps = listOf(
                "番茄洗净切块，鸡蛋加少许盐打散。",
                "热锅倒油，鸡蛋炒至凝固后盛出。",
                "下番茄翻炒出汁，倒回鸡蛋。",
                "加盐调味，撒葱花出锅。",
            ),
        ),
        RecipePreview(
            id = 2L,
            name = "蒜蓉西兰花",
            ingredientNames = listOf("西兰花", "大蒜"),
            steps = listOf(
                "西兰花切小朵，用淡盐水浸泡后沥干。",
                "沸水焯 1 分钟，捞出过凉水。",
                "蒜末小火爆香，转大火下西兰花。",
                "加盐快速翻炒均匀即可。",
            ),
        ),
        RecipePreview(
            id = 3L,
            name = "土豆炖牛腩",
            ingredientNames = listOf("土豆", "牛腩", "胡萝卜"),
            steps = listOf(
                "牛腩切块冷水下锅，焯水后洗净。",
                "牛腩加姜片、热水，小火炖 40 分钟。",
                "土豆、胡萝卜切滚刀块。",
                "放入蔬菜继续炖 20 分钟。",
                "加盐调味，收汁后出锅。",
            ),
        ),
    )

    fun recipe(id: Long): RecipePreview? = recipes.firstOrNull { it.id == id }
}
