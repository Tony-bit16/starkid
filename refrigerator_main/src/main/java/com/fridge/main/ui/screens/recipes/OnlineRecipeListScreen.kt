package com.fridge.main.ui.screens.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fridge.main.data.remote.recipe.OnlineRecipe
import com.fridge.main.ui.components.EmptyStateLayout
import com.fridge.main.ui.components.ErrorStateLayout
import com.fridge.main.ui.components.LoadingStateLayout
import com.fridge.main.ui.components.RefreshableContent

/**
 * 在线菜谱搜索页。
 *
 * 搜索框始终可见；结果区域按 Idle/Loading/Empty/Error/Success 独立渲染，
 * 网络失败除错误页外还会弹出一次提示，但不影响底部导航回本地菜谱。
 */
@Composable
fun OnlineRecipeListScreen(
    uiState: OnlineSearchUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onRecipeClick: (String) -> Unit,
    onDismissErrorDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        OnlineSearchHeader(
            uiState = uiState,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when (val result = uiState.result) {
                OnlineSearchResult.Idle -> EmptyStateLayout(
                    title = "搜索在线菜谱",
                    message = "输入英文菜名开始搜索，例如 Arrabiata、Chicken。",
                )

                OnlineSearchResult.Loading -> LoadingStateLayout(message = "正在搜索在线菜谱…")

                OnlineSearchResult.Empty -> EmptyStateLayout(
                    title = "没有找到相关菜谱",
                    message = "换一个关键词试试，或回到本地菜谱手动新增。",
                )

                is OnlineSearchResult.Error -> ErrorStateLayout(
                    title = "搜索失败",
                    message = result.message,
                    onRetry = onRetry,
                )

                is OnlineSearchResult.Success -> RefreshableContent(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    OnlineSearchResults(
                        recipes = result.recipes,
                        onRecipeClick = onRecipeClick,
                    )
                }
            }
        }
    }

    if (uiState.errorDialogMessage != null) {
        AlertDialog(
            onDismissRequest = onDismissErrorDialog,
            title = { Text("网络请求失败") },
            text = {
                Text(
                    text = uiState.errorDialogMessage +
                        "\n\n本地菜谱仍可正常使用。",
                )
            },
            confirmButton = {
                TextButton(onClick = onDismissErrorDialog) {
                    Text("知道了")
                }
            },
        )
    }
}

@Composable
private fun OnlineSearchHeader(
    uiState: OnlineSearchUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val isLoading = uiState.result is OnlineSearchResult.Loading
    val isBusy = isLoading || uiState.isRefreshing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                label = { Text("菜名") },
                placeholder = { Text("例如 Arrabiata") },
                singleLine = true,
                isError = uiState.queryError != null,
                supportingText = {
                    if (uiState.queryError != null) {
                        Text(uiState.queryError)
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            )
            Button(
                onClick = onSearch,
                // 空关键词也允许点击，由 ViewModel 给出即时错误提示，避免“按钮灰着但不知道为什么”。
                enabled = !isBusy,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(
                    when {
                        isLoading -> "搜索中"
                        uiState.isRefreshing -> "刷新中"
                        else -> "搜索"
                    },
                )
            }
        }
        Text(
            text = "数据来自 TheMealDB 公开接口，需要网络；本地菜谱不受影响。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun OnlineSearchResults(
    recipes: List<OnlineRecipe>,
    onRecipeClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "找到 ${recipes.size} 道在线菜谱",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(items = recipes, key = { it.id }) { recipe ->
            OnlineRecipeCard(
                recipe = recipe,
                onClick = { onRecipeClick(recipe.id) },
            )
        }
    }
}

@Composable
private fun OnlineRecipeCard(
    recipe: OnlineRecipe,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = recipe.name.take(1),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recipe.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${recipe.category} · ${recipe.area}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = "${recipe.ingredients.size} 种食材 · ${recipe.steps.size} 个步骤",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "›",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
