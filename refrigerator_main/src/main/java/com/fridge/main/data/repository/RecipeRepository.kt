package com.fridge.main.data.repository

import com.fridge.main.data.local.relation.RecipeWithIngredients
import kotlinx.coroutines.flow.Flow

/**
 * 菜谱仓库接口。
 *
 * 职责：
 * - 组合 RecipeDao 与 IngredientTypeDao；
 * - 保存菜谱时在事务内解析食材品类、写入主表与关联表；
 * - 读取时返回 [RecipeWithIngredients]，由 UI 直接渲染食材与步骤。
 *
 * 匹配是纯 CPU 规则，由 `domain.matching.RecipeMatcher` 在 ViewModel 层调用，
 * 不属于 Repository 职责；因此这里不提供 `observeMatches`。
 *
 * 具体实现见 [DefaultRecipeRepository]。
 */
interface RecipeRepository {

    /** 观察全部菜谱及其所需食材。 */
    fun observeRecipes(): Flow<List<RecipeWithIngredients>>

    /** 观察单个菜谱；不存在时发射 null。 */
    fun observeRecipe(recipeId: Long): Flow<RecipeWithIngredients?>

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
