package com.fridge.main.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.fridge.main.ui.common.UiState
import com.fridge.main.ui.model.IngredientPreview
import com.fridge.main.ui.model.RecipePreview
import com.fridge.main.ui.model.SampleData
import com.fridge.main.ui.screens.ingredients.AddIngredientScreen
import com.fridge.main.ui.screens.ingredients.IngredientDetailScreen
import com.fridge.main.ui.screens.ingredients.IngredientListScreen
import com.fridge.main.ui.screens.recipes.RecipeDetailScreen
import com.fridge.main.ui.screens.recipes.RecipeListScreen

/**
 * 主 App 导航图。
 *
 * 本轮所有数据均来自 [SampleData]，因此重试回调暂为空实现；
 * 接入 ViewModel 后由各页面自己的 `retry()` 替换，导航层不做业务处理。
 */
@Composable
fun FridgeNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.INGREDIENTS,
        modifier = modifier,
    ) {
        composable(route = Routes.INGREDIENTS) {
            IngredientListScreen(
                state = UiState.Success(SampleData.ingredients),
                onRetry = {},
                onIngredientClick = { id ->
                    navController.navigate(Routes.ingredientDetail(id))
                },
            )
        }

        composable(
            route = Routes.INGREDIENT_DETAIL,
            arguments = listOf(navArgument("ingredientId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("ingredientId") ?: -1L
            val ingredient = SampleData.ingredient(id)
            val state: UiState<IngredientPreview> =
                if (ingredient != null) UiState.Success(ingredient) else UiState.Empty
            IngredientDetailScreen(
                ingredientId = id,
                state = state,
                onRetry = {},
            )
        }

        composable(route = Routes.ADD_INGREDIENT) {
            AddIngredientScreen()
        }

        composable(route = Routes.RECIPES) {
            RecipeListScreen(
                state = UiState.Success(SampleData.recipes),
                onRetry = {},
                onRecipeClick = { id ->
                    navController.navigate(Routes.recipeDetail(id))
                },
            )
        }

        composable(
            route = Routes.RECIPE_DETAIL,
            arguments = listOf(navArgument("recipeId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("recipeId") ?: -1L
            val recipe = SampleData.recipe(id)
            val state: UiState<RecipePreview> =
                if (recipe != null) UiState.Success(recipe) else UiState.Empty
            RecipeDetailScreen(
                recipeId = id,
                state = state,
                onRetry = {},
            )
        }
    }
}
