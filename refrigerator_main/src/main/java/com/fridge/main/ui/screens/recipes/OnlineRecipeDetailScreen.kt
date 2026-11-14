package com.fridge.main.ui.screens.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fridge.main.data.remote.recipe.OnlineIngredient
import com.fridge.main.data.remote.recipe.OnlineRecipe
import com.fridge.main.ui.common.ScreenStateContent
import com.fridge.main.ui.components.SectionCard

/**
 * 在线菜谱详情页。
 *
 * 数据来自 lookup 接口；保存按钮把在线数据转换为本地菜谱，保存成功后
 * 本地菜谱列表通过 Room Flow 自动刷新，本页只负责显示保存结果。
 */
@Composable
fun OnlineRecipeDetailScreen(
    uiState: OnlineRecipeDetailUiState,
    onRetry: () -> Unit,
    onSave: () -> Unit,
    onDismissErrorDialog: () -> Unit,
    onClearMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        val message = uiState.message
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onClearMessage()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        ScreenStateContent(
            state = uiState.content,
            onRetry = onRetry,
            emptyTitle = "没有找到这道在线菜谱",
            emptyMessage = "它可能已下架，返回搜索页换一道菜试试。",
            modifier = Modifier.fillMaxSize(),
        ) { recipe ->
            OnlineRecipeDetailContent(
                recipe = recipe,
                isSaving = uiState.isSaving,
                saved = uiState.saved,
                onSave = onSave,
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }

    if (uiState.errorDialogMessage != null) {
        AlertDialog(
            onDismissRequest = onDismissErrorDialog,
            title = { Text("提示") },
            text = {
                Text(
                    text = uiState.errorDialogMessage +
                        "\n\n你可以关闭提示后重试，或返回使用本地菜谱。",
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
private fun OnlineRecipeDetailContent(
    recipe: OnlineRecipe,
    isSaving: Boolean,
    saved: Boolean,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(title = "在线菜谱") {
            Text(
                text = recipe.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${recipe.category} · ${recipe.area}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = "来源：TheMealDB · 在线数据",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        SectionCard(title = "所需食材") {
            if (recipe.ingredients.isEmpty()) {
                Text(
                    text = "该菜谱未提供食材清单。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                recipe.ingredients.forEach { ingredient ->
                    OnlineIngredientLine(ingredient)
                }
            }
        }

        SectionCard(title = "做法") {
            if (recipe.steps.isEmpty()) {
                Text(
                    text = "该菜谱未提供做法步骤，暂时无法保存到本地。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                recipe.steps.forEachIndexed { index, step ->
                    Row(verticalAlignment = Alignment.Top) {
                        Surface(
                            modifier = Modifier.size(26.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = 3.dp),
                        )
                    }
                }
            }
        }

        Button(
            onClick = onSave,
            enabled = !isSaving && !saved && recipe.ingredients.isNotEmpty() && recipe.steps.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Text(
                text = when {
                    saved -> "已保存到本地"
                    isSaving -> "保存中…"
                    else -> "保存到本地"
                },
            )
        }

        Text(
            text = "保存后会写入本地 Room 数据库，可在“菜谱库”中查看、编辑或删除。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OnlineIngredientLine(ingredient: OnlineIngredient) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "•",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = ingredient.name,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            text = ingredient.measure.ifBlank { "适量" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}
