package com.fridge.main.ui.model

/**
 * 食材新鲜度，仅用于界面骨架的状态色与筛选展示。
 *
 * 真实到期天数在接入 Room 后由 `stored_at + shelf_life_days` 计算，不能使用该静态模型。
 */
enum class FreshnessStatus(val label: String) {
    FRESH("新鲜"),
    EXPIRING("临期"),
    EXPIRED("已过期"),
}

/**
 * 列表/详情共用的食材展示模型。
 *
 * 与 `Ingredient` Entity 分离：Entity 面向数据库，本模型只承载页面需要的派生字段
 * （如剩余天数、格式化后的数量），避免 UI 直接依赖 Room 对象。
 */
data class IngredientPreview(
    val id: Long,
    val name: String,
    val category: String,
    val quantity: String,
    val unit: String,
    val storedAt: String,
    val shelfLifeDays: Int,
    val daysLeft: Int,
) {
    val quantityLabel: String get() = "$quantity $unit"

    val status: FreshnessStatus
        get() = when {
            daysLeft < 0 -> FreshnessStatus.EXPIRED
            daysLeft <= 2 -> FreshnessStatus.EXPIRING
            else -> FreshnessStatus.FRESH
        }

    val expiryLabel: String
        get() = when {
            daysLeft > 1 -> "还剩 $daysLeft 天"
            daysLeft == 1 -> "明天到期"
            daysLeft == 0 -> "今天到期"
            else -> "已过期 ${-daysLeft} 天"
        }
}

/** 列表/详情共用的菜谱展示模型。步骤以字符串列表保存，详情页按顺序编号。 */
data class RecipePreview(
    val id: Long,
    val name: String,
    val ingredientNames: List<String>,
    val steps: List<String>,
)

/**
 * 仅用于项目骨架演示的静态数据。
 *
 * 本轮不接入 ViewModel / Repository，页面通过 [SampleData] 展示列表→详情导航；
 * 接入 Room 后应删除页面中的直接引用，改由 ViewModel 暴露 UiState。
 */
object SampleData {

    val ingredients: List<IngredientPreview> = listOf(
        IngredientPreview(
            id = 1L,
            name = "番茄",
            category = "蔬菜",
            quantity = "3",
            unit = "个",
            storedAt = "2026-11-09",
            shelfLifeDays = 5,
            daysLeft = 2,
        ),
        IngredientPreview(
            id = 2L,
            name = "鸡蛋",
            category = "蛋奶",
            quantity = "8",
            unit = "枚",
            storedAt = "2026-11-02",
            shelfLifeDays = 20,
            daysLeft = 10,
        ),
        IngredientPreview(
            id = 3L,
            name = "西兰花",
            category = "蔬菜",
            quantity = "1",
            unit = "颗",
            storedAt = "2026-11-11",
            shelfLifeDays = 7,
            daysLeft = 6,
        ),
        IngredientPreview(
            id = 4L,
            name = "牛奶",
            category = "乳制品",
            quantity = "1",
            unit = "盒",
            storedAt = "2026-11-01",
            shelfLifeDays = 10,
            daysLeft = -1,
        ),
    )

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

    fun ingredient(id: Long): IngredientPreview? = ingredients.firstOrNull { it.id == id }

    fun recipe(id: Long): RecipePreview? = recipes.firstOrNull { it.id == id }
}
