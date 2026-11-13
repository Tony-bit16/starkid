package com.fridge.main.ui.screens.recipes

import com.fridge.main.data.repository.RecipeDraft
import com.fridge.main.data.repository.RecipeIngredientDraft
import com.fridge.main.ui.model.RecipeIngredientUiModel
import com.fridge.main.ui.model.RecipeUiModel
import com.fridge.main.ui.model.formatQuantity
import java.util.Locale

/**
 * 新增/编辑菜谱共用的表单状态。
 *
 * 菜名和做法保留原始字符串，食材清单按行动态维护；
 * 提交时统一调用 [validated]，避免把中间输入直接 `toDouble()` 造成崩溃。
 */
data class RecipeFormState(
    val name: String = "",
    val steps: String = "",
    val ingredients: List<RecipeIngredientForm> = listOf(RecipeIngredientForm()),
    val nameError: String? = null,
    val stepsError: String? = null,
    val ingredientsError: String? = null,
) {
    val hasErrors: Boolean
        get() = nameError != null ||
            stepsError != null ||
            ingredientsError != null ||
            ingredients.any { it.hasError }

    /**
     * 校验并返回带字段错误的新状态。
     *
     * 完全空白的食材行视为用户“加了行但没填”，提交时自动忽略；
     * 只要至少保留一行有效数据即可保存。
     */
    fun validated(): RecipeFormState {
        val cleanName = name.trim()
        val cleanSteps = steps
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        val nameError = when {
            cleanName.isEmpty() -> "请输入菜谱名称"
            cleanName.length > MAX_NAME_LENGTH -> "菜谱名称不能超过 $MAX_NAME_LENGTH 个字符"
            else -> null
        }

        val stepsError = when {
            cleanSteps.isEmpty() -> "请至少填写一个做法步骤"
            cleanSteps.size > MAX_STEP_COUNT -> "做法步骤不能超过 $MAX_STEP_COUNT 步"
            cleanSteps.sumOf { it.length } > MAX_STEPS_LENGTH -> "做法内容过长，请精简后重试"
            else -> null
        }

        val seenNames = mutableSetOf<String>()
        val cleanedRows = mutableListOf<RecipeIngredientForm>()
        ingredients.forEach { row ->
            val hasInput = row.name.isNotBlank() ||
                row.quantity.isNotBlank() ||
                row.unit.isNotBlank()
            if (!hasInput) return@forEach

            val cleanNameValue = row.name.trim()
            val cleanUnit = row.unit.trim()
            val amount = row.quantity.trim().toDoubleOrNull()
            val normalizedName = normalizeIngredientName(cleanNameValue)
            val duplicated = normalizedName.isNotEmpty() && !seenNames.add(normalizedName)

            cleanedRows += row.copy(
                name = cleanNameValue,
                category = row.category.trim().ifEmpty { DEFAULT_CATEGORY }.take(MAX_CATEGORY_LENGTH),
                unit = cleanUnit,
                nameError = when {
                    cleanNameValue.isEmpty() -> "请输入食材名称"
                    cleanNameValue.length > MAX_INGREDIENT_NAME_LENGTH ->
                        "食材名称不能超过 $MAX_INGREDIENT_NAME_LENGTH 个字符"
                    duplicated -> "食材重复，请合并用量"
                    else -> null
                },
                quantityError = when {
                    amount == null || !amount.isFinite() -> "请输入有效用量"
                    amount <= 0 -> "用量必须大于 0"
                    amount > MAX_QUANTITY -> "用量超出可记录范围"
                    else -> null
                },
                unitError = when {
                    cleanUnit.isEmpty() -> "请输入单位"
                    cleanUnit.length > MAX_UNIT_LENGTH -> "单位不能超过 $MAX_UNIT_LENGTH 个字符"
                    else -> null
                },
            )
        }

        val ingredientsError = when {
            cleanedRows.isEmpty() -> "请至少添加一种食材"
            cleanedRows.size > MAX_INGREDIENT_COUNT -> "所需食材不能超过 $MAX_INGREDIENT_COUNT 种"
            cleanedRows.any { it.nameError == "食材重复，请合并用量" } -> "食材名称不能重复"
            else -> null
        }

        return copy(
            nameError = nameError,
            stepsError = stepsError,
            ingredientsError = ingredientsError,
            ingredients = if (cleanedRows.isEmpty()) ingredients else cleanedRows,
        )
    }

    /** 校验通过后转为 Repository 的写入 DTO；调用前应先执行 [validated]。 */
    fun toDraft(): RecipeDraft? {
        if (hasErrors) return null
        val cleanSteps = steps
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (cleanSteps.isEmpty() || ingredients.isEmpty()) return null

        return RecipeDraft(
            id = null,
            name = name.trim(),
            steps = cleanSteps.joinToString("\n"),
            ingredients = ingredients.map { row ->
                RecipeIngredientDraft(
                    name = row.name.trim(),
                    category = row.category.trim().ifEmpty { DEFAULT_CATEGORY },
                    quantity = row.quantity.trim().toDoubleOrNull() ?: 0.0,
                    unit = row.unit.trim(),
                )
            },
        )
    }

    /** 更新某一行食材；索引由 UI 的稳定列表位置提供。 */
    fun updateIngredient(
        index: Int,
        transform: (RecipeIngredientForm) -> RecipeIngredientForm,
    ): RecipeFormState {
        if (index !in ingredients.indices) return this
        val updated = ingredients.toMutableList()
        updated[index] = transform(updated[index])
        return copy(ingredients = updated, ingredientsError = null)
    }

    /** 追加一行空白食材，最多 [MAX_INGREDIENT_COUNT] 行。 */
    fun addIngredient(): RecipeFormState {
        if (ingredients.size >= MAX_INGREDIENT_COUNT) return this
        return copy(
            ingredients = ingredients + RecipeIngredientForm(),
            ingredientsError = null,
        )
    }

    /** 移除一行；至少保留一行，避免页面变成无法继续输入的空列表。 */
    fun removeIngredient(index: Int): RecipeFormState {
        if (ingredients.size <= 1 || index !in ingredients.indices) return this
        return copy(
            ingredients = ingredients.filterIndexed { currentIndex, _ -> currentIndex != index },
            ingredientsError = null,
        )
    }

    companion object {
        fun newInstance(): RecipeFormState = RecipeFormState()

        /** 详情页进入编辑时回填；做法按原有换行还原。 */
        fun from(recipe: RecipeUiModel): RecipeFormState = RecipeFormState(
            name = recipe.name,
            steps = recipe.steps.joinToString("\n"),
            ingredients = recipe.ingredients.map(RecipeIngredientUiModel::toForm).ifEmpty {
                listOf(RecipeIngredientForm())
            },
        )
    }
}

/** 菜谱中的一行食材输入。 */
data class RecipeIngredientForm(
    val name: String = "",
    val category: String = DEFAULT_CATEGORY,
    val quantity: String = "",
    val unit: String = "",
    val nameError: String? = null,
    val quantityError: String? = null,
    val unitError: String? = null,
) {
    val hasError: Boolean
        get() = nameError != null || quantityError != null || unitError != null
}

private fun RecipeIngredientUiModel.toForm(): RecipeIngredientForm = RecipeIngredientForm(
    name = name,
    category = category.ifBlank { DEFAULT_CATEGORY },
    quantity = formatQuantity(quantity),
    unit = unit,
)

/** 与 Repository 保持一致的重复判断：trim、折叠空白、转小写。 */
private fun normalizeIngredientName(name: String): String = name
    .trim()
    .replace(WHITESPACE_REGEX, " ")
    .lowercase(Locale.ROOT)

private const val DEFAULT_CATEGORY = "未分类"
private const val MAX_NAME_LENGTH = 40
private const val MAX_STEP_COUNT = 30
private const val MAX_STEPS_LENGTH = 4_000
private const val MAX_INGREDIENT_COUNT = 30
private const val MAX_INGREDIENT_NAME_LENGTH = 30
private const val MAX_CATEGORY_LENGTH = 20
private const val MAX_UNIT_LENGTH = 10
private const val MAX_QUANTITY = 1_000_000.0
private val WHITESPACE_REGEX = Regex("\\s+")
