package com.fridge.main.domain.matching

import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeMatcherTest {

    @Test
    fun `classifies three levels and sorts by match degree`() {
        val recipes = listOf(
            recipe(1, "A-full", 1, 2),
            recipe(2, "B-one", 1, 4),
            recipe(3, "C-one", 2, 5),
            recipe(4, "D-many", 4, 5, 6),
        )

        val result = RecipeMatcher.matchAll(recipes, setOf(1, 2, 3))

        assertEquals(listOf(1L, 2L, 3L, 4L), result.map { it.recipeId })
        assertEquals(
            listOf(
                RecipeMatchLevel.FULLY_COOKABLE,
                RecipeMatchLevel.MISSING_ONE,
                RecipeMatchLevel.MISSING_ONE,
                RecipeMatchLevel.MISSING_MANY,
            ),
            result.map { it.level },
        )
        assertEquals(listOf("A-full", "B-one", "C-one", "D-many"), result.map { it.recipeName })
    }

    @Test
    fun `returns available and missing ingredient names`() {
        val result = RecipeMatcher.evaluate(
            recipe(10, "番茄炒蛋", 1, 2, 3),
            setOf(1, 3),
        )

        assertEquals(RecipeMatchLevel.MISSING_ONE, result.level)
        assertEquals(listOf("番茄", "葱"), result.availableIngredients)
        assertEquals(listOf("鸡蛋"), result.missingIngredients)
        assertEquals(3, result.totalCount)
        assertEquals(2, result.availableCount)
    }

    @Test
    fun `empty inventory marks every ingredient missing`() {
        val result = RecipeMatcher.matchAll(
            listOf(recipe(1, "番茄炒蛋", 1, 2, 3)),
            emptySet(),
        ).single()

        assertEquals(RecipeMatchLevel.MISSING_MANY, result.level)
        assertEquals(emptyList<String>(), result.availableIngredients)
        assertEquals(listOf("番茄", "鸡蛋", "葱"), result.missingIngredients)
    }

    @Test
    fun `recipe without ingredients is not treated as cookable`() {
        val result = RecipeMatcher.evaluate(
            MatchableRecipe(id = 1, name = "空菜谱", ingredients = emptyList()),
            setOf(1, 2),
        )

        assertEquals(RecipeMatchLevel.MISSING_MANY, result.level)
        assertEquals(0, result.totalCount)
    }

    @Test
    fun `recipe without ingredients sorts after recipes with missing items`() {
        val recipes = listOf(
            MatchableRecipe(id = 1, name = "空菜谱", ingredients = emptyList()),
            recipe(2, "缺两道", 4, 5),
        )

        val result = RecipeMatcher.matchAll(recipes, setOf(1, 2, 3))

        assertEquals(listOf(2L, 1L), result.map { it.recipeId })
    }

    private fun recipe(id: Long, name: String, vararg typeIds: Long): MatchableRecipe {
        val names = listOf("番茄", "鸡蛋", "葱", "土豆", "牛肉", "胡萝卜")
        return MatchableRecipe(
            id = id,
            name = name,
            ingredients = typeIds.map { typeId ->
                MatchableIngredient(
                    typeId = typeId,
                    name = names[(typeId - 1).toInt()],
                )
            },
        )
    }
}
