package com.fridge.main.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.fridge.main.FridgeApplication
import com.fridge.main.ui.common.UiState
import com.fridge.main.ui.model.RecipePreview
import com.fridge.main.ui.model.SampleData
import com.fridge.main.ui.screens.ingredients.AddIngredientScreen
import com.fridge.main.ui.screens.ingredients.AddIngredientViewModel
import com.fridge.main.ui.screens.ingredients.IngredientDetailScreen
import com.fridge.main.ui.screens.ingredients.IngredientDetailViewModel
import com.fridge.main.ui.screens.ingredients.IngredientListScreen
import com.fridge.main.ui.screens.ingredients.IngredientListViewModel
import com.fridge.main.ui.screens.recipes.RecipeDetailScreen
import com.fridge.main.ui.screens.recipes.RecipeListScreen

/**
 * 主 App 导航图。
 *
 * 食材相关页面从 [FridgeApplication] 注入同一个 Repository，再由各自的 ViewModel
 * 读取 Room；导航层只负责创建 ViewModel 和接线跳转，不直接访问数据库。
 * 菜谱页面仍使用静态 [SampleData]，待菜谱模块接入后替换。
 */
@Composable
fun FridgeNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val application = LocalContext.current.applicationContext as FridgeApplication
    val repository = remember(application) { application.ingredientRepository }

    NavHost(
        navController = navController,
        startDestination = Routes.INGREDIENTS,
        modifier = modifier,
    ) {
        composable(route = Routes.INGREDIENTS) {
            val viewModel: IngredientListViewModel = viewModel(
                factory = IngredientListViewModel.factory(repository),
            )
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            IngredientListScreen(
                state = state,
                onRetry = viewModel::retry,
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
            val viewModel: IngredientDetailViewModel = viewModel(
                factory = IngredientDetailViewModel.factory(repository, id),
            )
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            IngredientDetailScreen(
                uiState = state,
                onRetry = viewModel::retry,
                onStartEdit = viewModel::startEdit,
                onCancelEdit = viewModel::cancelEdit,
                onFormChange = viewModel::onFormChange,
                onSave = viewModel::save,
                onDelete = viewModel::delete,
                onDeleted = { navController.popBackStack() },
                onClearError = viewModel::clearError,
                onConsumeUpdateSucceeded = viewModel::consumeUpdateSucceeded,
            )
        }

        composable(route = Routes.ADD_INGREDIENT) {
            val viewModel: AddIngredientViewModel = viewModel(
                factory = AddIngredientViewModel.factory(repository),
            )
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            AddIngredientScreen(
                uiState = state,
                onFormChange = viewModel::onFormChange,
                onSave = viewModel::save,
                onSaved = { navController.popBackStack() },
            )
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
