package com.fridge.main.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.fridge.main.data.local.FridgeDatabase
import com.fridge.main.data.local.entity.IngredientType
import com.fridge.main.data.local.entity.Recipe
import com.fridge.main.data.local.entity.RecipeIngredient
import com.fridge.main.data.local.relation.RecipeWithIngredients
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.util.Locale

/**
 * 菜谱仓库的 Room 实现。
 *
 * 设计要点：
 * - 菜谱主表与 `recipe_ingredients` 关联表始终在同一个事务内写入；
 * - 关联行引用 [IngredientType] 而不是库存批次，因此菜谱可以先于库存存在；
 * - 食材品类不存在时，在当前事务内创建“未分类”品类，保证关联表外键完整；
 * - 更新菜谱采用“先删旧明细、再写新明细”的整体替换，避免遗留已删除的食材；
 * - 所有 suspend 方法由 Room 在事务/查询执行器上执行，调用方不需要切线程。
 */
class DefaultRecipeRepository(
    private val databaseProvider: () -> FridgeDatabase,
) : RecipeRepository {

    private val database: FridgeDatabase
        get() = databaseProvider()

    override fun observeRecipes(): Flow<List<RecipeWithIngredients>> = flow {
        // 数据库在 Flow 被收集时才打开；打开失败会进入 ViewModel 的 catch。
        emitAll(database.recipeDao().observeAllWithIngredients())
    }

    override fun observeRecipe(recipeId: Long): Flow<RecipeWithIngredients?> = flow {
        emitAll(database.recipeDao().observeByIdWithIngredients(recipeId))
    }

    override suspend fun saveRecipe(draft: RecipeDraft): Result<Long> = dataResult {
        val clean = draft.validated()

        database.withTransaction {
            val recipeDao = database.recipeDao()

            // 更新前先确认记录存在，避免用户停留详情页期间记录被其他入口删除。
            val existing = clean.id?.let { recipeId ->
                recipeDao.findRecipeById(recipeId)
                    ?: throw NoSuchElementException("菜谱不存在或已被删除")
            }

            val recipeId = if (existing == null) {
                recipeDao.insertRecipe(
                    Recipe(
                        name = clean.name,
                        steps = clean.steps,
                    ),
                )
            } else {
                recipeDao.updateRecipe(
                    existing.copy(
                        name = clean.name,
                        steps = clean.steps,
                    ),
                )
                existing.id
            }

            // 先把所有品类解析/创建完，再替换关联行；任一步失败都会回滚整个事务。
            val links = clean.ingredients.map { ingredient ->
                val type = findOrCreateType(ingredient)
                RecipeIngredient(
                    recipeId = recipeId,
                    typeId = type.id,
                    quantity = ingredient.quantity,
                    unit = ingredient.unit,
                )
            }

            recipeDao.deleteIngredientsOfRecipe(recipeId)
            recipeDao.insertRecipeIngredients(links)
            recipeId
        }
    }

    override suspend fun deleteRecipe(recipeId: Long): Result<Unit> = dataResult {
        require(recipeId > 0) { "菜谱 id 无效" }
        val deletedRows = database.recipeDao().deleteRecipeById(recipeId)
        if (deletedRows == 0) {
            throw NoSuchElementException("菜谱不存在或已被删除")
        }
    }

    /**
     * 复用已有食材品类，或在当前事务内创建一个新品类。
     *
     * 菜谱允许引用冰箱里暂时没有的食材，所以新品类分类记为“未分类”；
     * 若用户此前已通过食材模块录入过同名品类，则沿用其分类与单位，不覆盖主数据。
     */
    private suspend fun findOrCreateType(ingredient: CleanRecipeIngredient): IngredientType {
        val typeDao = database.ingredientTypeDao()
        val normalizedName = normalizeName(ingredient.name)

        typeDao.findByNormalizedName(normalizedName)?.let { return it }

        return try {
            val id = typeDao.insert(
                IngredientType(
                    name = ingredient.name,
                    normalizedName = normalizedName,
                    category = ingredient.category,
                    defaultUnit = ingredient.unit,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            typeDao.findById(id)
                ?: throw IllegalStateException("食材品类创建后读取失败")
        } catch (error: SQLiteConstraintException) {
            // 并发创建同名品类：唯一索引冲突后回查另一个事务写入的记录。
            typeDao.findByNormalizedName(normalizedName) ?: throw error
        }
    }

    /**
     * 校验并清洗菜谱表单。
     *
     * UI 已经做过一次即时校验，这里仍完整校验，防止绕过 UI 的调用把
     * 空菜名、空步骤、重复食材或非法数量写入数据库。
     */
    private fun RecipeDraft.validated(): CleanRecipeDraft {
        require(id == null || id > 0) { "菜谱 id 无效" }

        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "菜谱名称不能为空" }
        require(cleanName.length <= MAX_NAME_LENGTH) {
            "菜谱名称不能超过 $MAX_NAME_LENGTH 个字符"
        }

        val cleanSteps = steps
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        require(cleanSteps.isNotEmpty()) { "请至少填写一个做法步骤" }
        require(cleanSteps.size <= MAX_STEP_COUNT) {
            "做法步骤不能超过 $MAX_STEP_COUNT 步"
        }
        require(cleanSteps.sumOf { it.length } <= MAX_STEPS_LENGTH) {
            "做法内容过长，请精简后重试"
        }

        require(ingredients.isNotEmpty()) { "请至少添加一种食材" }
        require(ingredients.size <= MAX_INGREDIENT_COUNT) {
            "所需食材不能超过 $MAX_INGREDIENT_COUNT 种"
        }

        val seenNames = mutableSetOf<String>()
        val cleanIngredients = ingredients.map { item ->
            val cleanIngredientName = item.name.trim()
            val cleanCategory = item.category.trim().ifEmpty { DEFAULT_CATEGORY }
            val cleanUnit = item.unit.trim()

            require(cleanIngredientName.isNotEmpty()) { "食材名称不能为空" }
            require(cleanIngredientName.length <= MAX_INGREDIENT_NAME_LENGTH) {
                "食材名称不能超过 $MAX_INGREDIENT_NAME_LENGTH 个字符"
            }
            require(cleanCategory.length <= MAX_CATEGORY_LENGTH) {
                "食材分类不能超过 $MAX_CATEGORY_LENGTH 个字符"
            }
            require(cleanUnit.isNotEmpty()) { "食材用量单位不能为空" }
            require(cleanUnit.length <= MAX_UNIT_LENGTH) {
                "食材单位不能超过 $MAX_UNIT_LENGTH 个字符"
            }
            require(item.quantity.isFinite() && item.quantity > 0) {
                "食材用量必须大于 0"
            }
            require(item.quantity <= MAX_QUANTITY) { "食材用量超出可记录范围" }

            val normalizedName = normalizeName(cleanIngredientName)
            require(seenNames.add(normalizedName)) {
                "食材「$cleanIngredientName」重复，请合并用量"
            }

            CleanRecipeIngredient(
                name = cleanIngredientName,
                category = cleanCategory,
                quantity = item.quantity,
                unit = cleanUnit,
            )
        }

        return CleanRecipeDraft(
            id = id,
            name = cleanName,
            steps = cleanSteps.joinToString("\n"),
            ingredients = cleanIngredients,
        )
    }

    /** 统一包装数据库/校验异常，同时保证协程取消不被吞掉。 */
    private suspend fun <T> dataResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private data class CleanRecipeDraft(
        val id: Long?,
        val name: String,
        val steps: String,
        val ingredients: List<CleanRecipeIngredient>,
    )

    private data class CleanRecipeIngredient(
        val name: String,
        val category: String,
        val quantity: Double,
        val unit: String,
    )

    private companion object {
        const val DEFAULT_CATEGORY = "未分类"
        const val MAX_NAME_LENGTH = 40
        const val MAX_STEP_COUNT = 30
        const val MAX_STEPS_LENGTH = 4_000
        const val MAX_INGREDIENT_COUNT = 30
        const val MAX_INGREDIENT_NAME_LENGTH = 30
        const val MAX_CATEGORY_LENGTH = 20
        const val MAX_UNIT_LENGTH = 10
        const val MAX_QUANTITY = 1_000_000.0

        /** 与食材模块保持相同的名称匹配规则：trim、折叠连续空白、转小写。 */
        fun normalizeName(name: String): String = name
            .trim()
            .replace(WHITESPACE_REGEX, " ")
            .lowercase(Locale.ROOT)

        val WHITESPACE_REGEX = Regex("\\s+")
    }
}
