package com.fridge.main.data.repository

import com.fridge.main.data.local.relation.RecipeWithIngredients
import kotlinx.coroutines.flow.Flow

/**
 * 菜谱仓库接口（本轮只定义契约，不提供实现）。
 *
 * 职责：
 * - 组合 RecipeDao、RecipeIngredientDao 与 IngredientTypeDao；
 * - 保存菜谱时在事务内解析食材品类、写入主表与关联表；
 * - 读取时返回 [RecipeWithIngredients]，由 UI 直接渲染食材与步骤；
 * - 后续在此实现“现有库存 → 能做 / 部分能做”的匹配规则。
 */
interface RecipeRepository {

    /** 观察全部菜谱及其所需食材。 */
    fun observeRecipes(): Flow<List<RecipeWithIngredients>>

    /** 观察单个菜谱；不存在时发射 null。 */
    fun observeRecipe(recipeId: Long): Flow<RecipeWithIngredients?>

    /**
     * 观察菜谱匹配结果。
     *
     * 匹配规则（后续实现）：按 typeId 聚合库存数量，全部满足为 CAN_COOK，
     * 部分满足为 PARTIAL，全部缺失为 CANNOT_COOK；本轮只保留契约。
     */
    fun observeMatches(): Flow<List<RecipeMatch>>

    /** 新增或更新菜谱；draft.id 为空表示新增，返回菜谱 id。 */
    suspend fun saveRecipe(draft: RecipeDraft): Result<Long>

    /** 删除菜谱；关联的 recipe_ingredients 由外键级联删除。 */
    suspend fun deleteRecipe(recipeId: Long): Result<Unit>
}

/** 菜谱新增/编辑表单。 */
data class RecipeDraft(
    val id: Long? = null,
    val name: String,
    val steps: String,
    val ingredients: List<RecipeIngredientDraft>,
)

/** 菜谱中的一项食材需求；品类由 Repository 按名称解析。 */
data class RecipeIngredientDraft(
    val name: String,
    val category: String,
    val quantity: Double,
    val unit: String,
)

/** 匹配状态。 */
enum class RecipeMatchStatus {
    CAN_COOK,
    PARTIAL,
    CANNOT_COOK,
}

/** 一道菜谱的匹配结果，缺失项用于详情页“还差什么”提示。 */
data class RecipeMatch(
    val recipe: RecipeWithIngredients,
    val status: RecipeMatchStatus,
    val missingIngredients: List<String>,
)
