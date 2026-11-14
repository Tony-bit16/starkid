package com.fridge.main.domain.matching

/**
 * 推荐等级。顺序即列表排序优先级：越靠前越容易做。
 */
enum class RecipeMatchLevel(val label: String) {
    FULLY_COOKABLE("完全可制作"),
    MISSING_ONE("缺少1种食材"),
    MISSING_MANY("缺少多种食材"),
}

/**
 * 匹配算法只认识“食材品类 id 与名称”，不依赖 Room Entity、Dao 或 UI 模型。
 * 这样算法可以独立做单元测试，也能在后续看板/通知模块复用。
 */
data class MatchableIngredient(
    val typeId: Long,
    val name: String,
)

data class MatchableRecipe(
    val id: Long,
    val name: String,
    val ingredients: List<MatchableIngredient>,
)

data class RecipeMatchResult(
    val recipeId: Long,
    val recipeName: String,
    val level: RecipeMatchLevel,
    val availableIngredients: List<String>,
    val missingIngredients: List<String>,
) {
    val totalCount: Int
        get() = availableIngredients.size + missingIngredients.size

    val availableCount: Int
        get() = availableIngredients.size
}

/**
 * 现有食材匹配菜谱的纯函数算法。
 *
 * 匹配口径：只判断食材品类“是否有库存”，不比较库存数量是否足够。
 * 同一品类有多条库存批次时，只要存在一条数量大于 0 的批次即视为已有。
 * 数量满足度、临期食材是否参与推荐属于后续增强，本类不引入这些规则。
 */
object RecipeMatcher {

    /**
     * 对全部菜谱分类并按匹配度排序。
     *
     * 排序规则：
     * 1. 完全可制作；
     * 2. 缺少 1 种食材；
     * 3. 缺少多种食材，缺少越少越靠前；
     * 4. 同等级按菜名排序，保证结果稳定。
     */
    fun matchAll(
        recipes: List<MatchableRecipe>,
        availableTypeIds: Set<Long>,
    ): List<RecipeMatchResult> = recipes
        .map { evaluate(it, availableTypeIds) }
        .sortedWith(
            compareBy<RecipeMatchResult> { it.level.ordinal }
                // 空菜谱没有食材清单，不能因为 missing.size == 0 排到“缺多种”前面。
                .thenBy { if (it.totalCount == 0) Int.MAX_VALUE else it.missingIngredients.size }
                .thenBy { it.recipeName },
        )

    /**
     * 计算单道菜谱的匹配结果，供详情页展示“已有 / 缺少”状态。
     */
    fun evaluate(
        recipe: MatchableRecipe,
        availableTypeIds: Set<Long>,
    ): RecipeMatchResult {
        val available = recipe.ingredients
            .filter { it.typeId in availableTypeIds }
            .map { it.name }
        val missing = recipe.ingredients
            .filterNot { it.typeId in availableTypeIds }
            .map { it.name }

        // 正常菜谱至少有一种食材；空菜谱按“缺少多种食材”处理，避免误判为可做。
        val level = when {
            recipe.ingredients.isEmpty() -> RecipeMatchLevel.MISSING_MANY
            missing.isEmpty() -> RecipeMatchLevel.FULLY_COOKABLE
            missing.size == 1 -> RecipeMatchLevel.MISSING_ONE
            else -> RecipeMatchLevel.MISSING_MANY
        }

        return RecipeMatchResult(
            recipeId = recipe.id,
            recipeName = recipe.name,
            level = level,
            availableIngredients = available,
            missingIngredients = missing,
        )
    }
}
