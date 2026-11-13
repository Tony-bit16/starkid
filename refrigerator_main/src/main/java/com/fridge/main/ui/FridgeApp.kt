package com.fridge.main.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fridge.main.ui.navigation.FridgeNavHost
import com.fridge.main.ui.navigation.Routes

/**
 * 主 App 的唯一 Activity 内容：Scaffold 统一管理顶栏、底部导航与页面容器。
 *
 * 底部导航只在两个一级页面（食材 / 菜谱）显示；详情页、新增页显示返回按钮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridgeApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val isIngredients = currentRoute == Routes.INGREDIENTS
    val isRecipes = currentRoute == Routes.RECIPES
    val isTopLevel = isIngredients || isRecipes

    val title = when (currentRoute) {
        Routes.INGREDIENTS -> "食材库"
        Routes.ADD_INGREDIENT -> "新增食材"
        Routes.INGREDIENT_DETAIL -> "食材详情"
        Routes.RECIPES -> "菜谱库"
        Routes.ADD_RECIPE -> "新增菜谱"
        Routes.RECIPE_DETAIL -> "菜谱详情"
        else -> "冰箱管家"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = title) },
                navigationIcon = {
                    if (!isTopLevel) {
                        TextButton(
                            onClick = { navController.navigateUp() },
                            modifier = Modifier.padding(start = 4.dp),
                        ) {
                            Text("返回")
                        }
                    }
                },
                actions = {
                    if (isIngredients) {
                        TextButton(onClick = { navController.navigate(Routes.ADD_INGREDIENT) }) {
                            Text("新增")
                        }
                    }
                    if (isRecipes) {
                        TextButton(onClick = { navController.navigate(Routes.ADD_RECIPE) }) {
                            Text("新增")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        bottomBar = {
            if (isTopLevel) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = isIngredients,
                        onClick = { navController.navigateTopLevel(Routes.INGREDIENTS) },
                        icon = {
                            Text(
                                text = "食",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        },
                        label = { Text("食材库") },
                    )
                    NavigationBarItem(
                        selected = isRecipes,
                        onClick = { navController.navigateTopLevel(Routes.RECIPES) },
                        icon = {
                            Text(
                                text = "谱",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        },
                        label = { Text("菜谱库") },
                    )
                }
            }
        },
    ) { innerPadding ->
        FridgeNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

/**
 * 一级页面切换：保留各自的滚动位置，不叠加返回栈。
 *
 * `popUpTo(startDestination)` 防止反复点击底部导航把返回栈越堆越深。
 */
private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
