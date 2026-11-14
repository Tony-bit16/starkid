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
import com.fridge.main.ui.common.safeNavigate
import com.fridge.main.ui.common.safePopBackStack
import com.fridge.main.ui.screens.ingredients.AddIngredientScreen
import com.fridge.main.ui.screens.ingredients.AddIngredientViewModel
import com.fridge.main.ui.screens.ingredients.IngredientDetailScreen
import com.fridge.main.ui.screens.ingredients.IngredientDetailViewModel
import com.fridge.main.ui.screens.ingredients.IngredientListScreen
import com.fridge.main.ui.screens.ingredients.IngredientListViewModel
import com.fridge.main.ui.screens.recipes.AddRecipeScreen
import com.fridge.main.ui.screens.recipes.AddRecipeViewModel
import com.fridge.main.ui.screens.recipes.OnlineRecipeDetailScreen
import com.fridge.main.ui.screens.recipes.OnlineRecipeDetailViewModel
import com.fridge.main.ui.screens.recipes.OnlineRecipeListScreen
import com.fridge.main.ui.screens.recipes.OnlineRecipeListViewModel
import com.fridge.main.ui.screens.recipes.RecipeDetailScreen
import com.fridge.main.ui.screens.recipes.RecipeDetailViewModel
import com.fridge.main.ui.screens.recipes.RecipeListScreen
import com.fridge.main.ui.screens.recipes.RecipeListViewModel

/**
 * 主 App 导航图。
 *
 * 页面所需 Repository 统一从 [FridgeApplication] 注入，由各自 ViewModel 读取 Room；
 * 导航层只负责创建 ViewModel 与接线跳转，不直接访问数据库。
 */
@Composable
fun FridgeNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val application = LocalContext.current.applicationContext as FridgeApplication
    val ingredientRepository = remember(application) { application.ingredientRepository }
    val recipeRepository = remember(application) { application.recipeRepository }
    val onlineRecipeRepository = remember(application) { application.onlineRecipeRepository }

    NavHost(
        navController = navController,
        startDestination = Routes.INGREDIENTS,
        modifier = modifier,
    ) {
        composable(route = Routes.INGREDIENTS) {
            val viewModel: IngredientListViewModel = viewModel(
                factory = IngredientListViewModel.factory(ingredientRepository),
            )
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
            IngredientListScreen(
                state = state,
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh,
                onRetry = viewModel::retry,
                onIngredientClick = { id ->
                    navController.safeNavigate(Routes.ingredientDetail(id))
                },
            )
        }

        composable(
            route = Routes.INGREDIENT_DETAIL,
            arguments = listOf(navArgument("ingredientId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("ingredientId") ?: -1L
            val viewModel: IngredientDetailViewModel = viewModel(
                factory = IngredientDetailViewModel.factory(ingredientRepository, id),
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
                onDeleted = { navController.safePopBackStack() },
                onClearError = viewModel::clearError,
                onConsumeUpdateSucceeded = viewModel::consumeUpdateSucceeded,
            )
        }

        composable(route = Routes.ADD_INGREDIENT) {
            val viewModel: AddIngredientViewModel = viewModel(
                factory = AddIngredientViewModel.factory(ingredientRepository),
            )
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            AddIngredientScreen(
                uiState = state,
                onFormChange = viewModel::onFormChange,
                onSave = viewModel::save,
                onSaved = { navController.safePopBackStack() },
            )
        }

        composable(route = Routes.RECIPES) {
            val viewModel: RecipeListViewModel = viewModel(
                factory = RecipeListViewModel.factory(
                    recipeRepository = recipeRepository,
                    ingredientRepository = ingredientRepository,
                ),
            )
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
            RecipeListScreen(
                uiState = state,
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh,
                onFilterChange = viewModel::setFilter,
                onRetry = viewModel::retry,
                onRecipeClick = { id ->
                    navController.safeNavigate(Routes.recipeDetail(id))
                },
                onGoOnline = { navController.safeNavigate(Routes.ONLINE_RECIPES) },
                onAddIngredient = { navController.safeNavigate(Routes.ADD_INGREDIENT) },
            )
        }

        composable(route = Routes.ADD_RECIPE) {
            val viewModel: AddRecipeViewModel = viewModel(
                factory = AddRecipeViewModel.factory(recipeRepository),
            )
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            AddRecipeScreen(
                uiState = state,
                onFormChange = viewModel::onFormChange,
                onSave = viewModel::save,
                onSaved = { navController.safePopBackStack() },
            )
        }

        composable(
            route = Routes.RECIPE_DETAIL,
            arguments = listOf(navArgument("recipeId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("recipeId") ?: -1L
            val viewModel: RecipeDetailViewModel = viewModel(
                factory = RecipeDetailViewModel.factory(
                    recipeRepository = recipeRepository,
                    ingredientRepository = ingredientRepository,
                    recipeId = id,
                ),
            )
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            RecipeDetailScreen(
                uiState = state,
                onRetry = viewModel::retry,
                onStartEdit = viewModel::startEdit,
                onCancelEdit = viewModel::cancelEdit,
                onFormChange = viewModel::onFormChange,
                onSave = viewModel::save,
                onDelete = viewModel::delete,
                onDeleted = { navController.safePopBackStack() },
                onClearError = viewModel::clearError,
                onConsumeUpdateSucceeded = viewModel::consumeUpdateSucceeded,
            )
        }
        composable(route = Routes.ONLINE_RECIPES) {
            val viewModel: OnlineRecipeListViewModel = viewModel(
                factory = OnlineRecipeListViewModel.factory(onlineRecipeRepository),
            )
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            OnlineRecipeListScreen(
                uiState = state,
                onQueryChange = viewModel::onQueryChange,
                onSearch = viewModel::search,
                onRefresh = viewModel::refresh,
                onRetry = viewModel::retry,
                onRecipeClick = { mealId ->
                    navController.safeNavigate(Routes.onlineRecipeDetail(mealId))
                },
                onDismissErrorDialog = viewModel::dismissErrorDialog,
            )
        }

        composable(
            route = Routes.ONLINE_RECIPE_DETAIL,
            arguments = listOf(navArgument("mealId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val mealId = backStackEntry.arguments?.getString("mealId").orEmpty()
            val viewModel: OnlineRecipeDetailViewModel = viewModel(
                factory = OnlineRecipeDetailViewModel.factory(onlineRecipeRepository, mealId),
            )
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            OnlineRecipeDetailScreen(
                uiState = state,
                onRetry = viewModel::load,
                onSave = viewModel::saveToLocal,
                onDismissErrorDialog = viewModel::dismissErrorDialog,
                onClearMessage = viewModel::clearMessage,
            )
        }
    }
}
