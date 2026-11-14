package com.fridge.main.data.remote.recipe

import com.fridge.main.data.repository.RecipeDraft
import com.fridge.main.data.repository.RecipeIngredientDraft
import java.util.Locale

/**
 * TheMealDB DTO → 在线菜谱领域模型。
 *
 * 没有 id 或菜名的条目直接丢弃，避免搜索结果里出现无法进入详情/无法保存的脏数据。
 */
internal fun MealDto.toOnlineRecipe(): OnlineRecipe? {
    val cleanId = idMeal?.trim().orEmpty()
    val cleanName = strMeal?.trim().orEmpty()
    if (cleanId.isEmpty() || cleanName.isEmpty()) return null

    val ingredients = ingredientValues
        .zip(measureValues)
        .mapNotNull { (rawName, rawMeasure) ->
            val name = rawName?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null

            val measure = rawMeasure?.trim().orEmpty()
            val (quantity, unit) = parseMeasure(measure)
            OnlineIngredient(
                name = name.take(MAX_INGREDIENT_NAME_LENGTH).trim(),
                measure = measure.ifEmpty { "适量" },
                quantity = quantity,
                unit = unit,
            )
        }

    val instructions = strInstructions.orEmpty()
    return OnlineRecipe(
        id = cleanId,
        name = cleanName,
        category = strCategory?.trim().orEmpty().ifBlank { "未分类" },
        area = strArea?.trim().orEmpty().ifBlank { "未知菜系" },
        thumbnailUrl = strMealThumb?.trim()?.takeIf { it.isNotEmpty() },
        steps = instructions.toInstructionSteps(),
        ingredients = ingredients,
    )
}

/** 把在线菜谱映射为本地 Room 写入 DTO。 */
internal fun OnlineRecipe.toRecipeDraft(): RecipeDraft = RecipeDraft(
    id = null,
    name = name.trim().take(MAX_RECIPE_NAME_LENGTH),
    steps = steps.toSaveSteps(),
    ingredients = ingredients
        .take(MAX_INGREDIENT_COUNT)
        .map { ingredient ->
            RecipeIngredientDraft(
                name = ingredient.name.trim().take(MAX_INGREDIENT_NAME_LENGTH),
                // TheMealDB 只提供菜谱分类，不提供单个食材分类；不要把它误当成食材分类。
                category = "未分类",
                quantity = ingredient.quantity,
                unit = ingredient.unit.trim().take(MAX_UNIT_LENGTH).ifBlank { "适量" },
            )
        },
)

/**
 * 把接口返回的一段做法拆成步骤。
 *
 * 优先按换行拆分；只有一行时再按中英文句号/问号/感叹号拆分，避免整道菜只有一个超长步骤。
 * 最多保留 30 步，与本地菜谱的校验上限保持一致。
 */
internal fun String.toInstructionSteps(): List<String> {
    val normalized = trim()
        .replace("\r\n", "\n")
        .replace('\r', '\n')
    if (normalized.isEmpty()) return emptyList()

    val lines = normalized.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toList()

    val chunks = if (lines.size > 1) {
        lines
    } else {
        normalized.split(SENTENCE_BOUNDARY_REGEX)
    }

    return chunks
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .take(MAX_STEP_COUNT)
}

/**
 * 保存到本地时限制步骤总长度。
 *
 * 网络数据可能远超表单允许的长度；这里按顺序截断，保证“一键保存”不会因长度校验失败。
 */
internal fun List<String>.toSaveSteps(): String {
    val builder = StringBuilder()
    for (step in take(MAX_STEP_COUNT)) {
        val clean = step.trim()
        if (clean.isEmpty()) continue

        val separator = if (builder.isEmpty()) "" else "\n"
        if (builder.length + separator.length + clean.length > MAX_STEPS_LENGTH) break
        builder.append(separator).append(clean)
    }
    return builder.toString()
}

/**
 * 把 "1/2 cup"、"2 tbsp"、"to taste" 等自由文本解析成 Room 需要的数量与单位。
 *
 * 解析失败时退回 1 + “适量”，不阻断保存；原始 measure 仍会在在线详情页完整展示。
 */
internal fun parseMeasure(raw: String): Pair<Double, String> {
    val value = raw.trim()
    if (value.isEmpty()) return 1.0 to "适量"

    MIXED_FRACTION_REGEX.matchEntire(value)?.let { match ->
        val whole = match.groupValues[1].toDoubleOrNull() ?: 0.0
        val numerator = match.groupValues[2].toDoubleOrNull() ?: 0.0
        val denominator = match.groupValues[3].toDoubleOrNull() ?: 0.0
        if (denominator > 0) {
            return (whole + numerator / denominator) to sanitizeUnit(match.groupValues[4])
        }
    }

    FRACTION_REGEX.matchEntire(value)?.let { match ->
        val numerator = match.groupValues[1].toDoubleOrNull() ?: 0.0
        val denominator = match.groupValues[2].toDoubleOrNull() ?: 0.0
        if (denominator > 0) {
            return (numerator / denominator) to sanitizeUnit(match.groupValues[3])
        }
    }

    RANGE_REGEX.matchEntire(value)?.let { match ->
        val low = match.groupValues[1].toDoubleOrNull() ?: 0.0
        val high = match.groupValues[2].toDoubleOrNull() ?: low
        return ((low + high) / 2.0) to sanitizeUnit(match.groupValues[3])
    }

    DECIMAL_REGEX.matchEntire(value)?.let { match ->
        val quantity = match.groupValues[1].replace(',', '.').toDoubleOrNull()
        if (quantity != null && quantity > 0) {
            return quantity to sanitizeUnit(match.groupValues[2])
        }
    }

    // “a pinch”“to taste”等无法可靠结构化的描述：数量按 1 保存，单位提示为适量。
    return 1.0 to "适量"
}

/** 把常见英文单位缩写到本地表单允许的 10 个字符以内。 */
private fun sanitizeUnit(raw: String): String {
    val value = raw.trim()
    if (value.isEmpty()) return "适量"

    val lower = value.lowercase(Locale.ROOT)
    if ("to taste" in lower || "as needed" in lower || "pinch" in lower) return "适量"

    return when {
        lower.startsWith("tablespoon") -> "tbsp"
        lower.startsWith("teaspoon") -> "tsp"
        lower.startsWith("ounce") -> "oz"
        lower.startsWith("pound") -> "lb"
        lower.startsWith("kilogram") -> "kg"
        lower.startsWith("gram") -> "g"
        lower.startsWith("milliliter") -> "ml"
        lower.startsWith("liter") -> "l"
        lower.startsWith("cup") -> "cup"
        else -> value.take(MAX_UNIT_LENGTH).trim()
    }
}

private val MIXED_FRACTION_REGEX = Regex("""^(\d+)\s+(\d+)\s*/\s*(\d+)\s*(.*)$""")
private val FRACTION_REGEX = Regex("""^(\d+)\s*/\s*(\d+)\s*(.*)$""")
private val RANGE_REGEX = Regex("""^(\d+)\s*[-–]\s*(\d+)\s*(.*)$""")
private val DECIMAL_REGEX = Regex("""^(\d+(?:[.,]\d+)?)\s*(.*)$""")
private val SENTENCE_BOUNDARY_REGEX = Regex("""(?<=[.!?。！？])\s+""")

private const val MAX_RECIPE_NAME_LENGTH = 40
private const val MAX_STEP_COUNT = 30
private const val MAX_STEPS_LENGTH = 4_000
private const val MAX_INGREDIENT_COUNT = 30
private const val MAX_INGREDIENT_NAME_LENGTH = 30
private const val MAX_UNIT_LENGTH = 10
