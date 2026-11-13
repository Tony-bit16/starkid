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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fridge.main.ui.common.ScreenStateContent
import com.fridge.main.ui.components.SectionCard
import com.fridge.main.ui.model.RecipeIngredientUiModel
import com.fridge.main.ui.model.RecipeUiModel

/**
 * 菜谱详情页。
 *
 * 展示所需食材与按顺序编号的做法；读取失败/不存在由 [RecipeDetailUiState.content]
 * 处理，保存/删除失败通过 Snackbar 提示，不会清空已加载的菜谱。
 */
@Composable
fun RecipeDetailScreen(
    uiState: RecipeDetailUiState,
    onRetry: () -> Unit,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onFormChange: (RecipeFormState) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDeleted: () -> Unit,
    onClearError: () -> Unit,
    onConsumeUpdateSucceeded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) onDeleted()
    }
    LaunchedEffect(uiState.updateSucceeded) {
        if (uiState.updateSucceeded) {
            snackbarHostState.showSnackbar("已保存修改")
            onConsumeUpdateSucceeded()
        }
    }
    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onClearError()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        ScreenStateContent(
            state = uiState.content,
            onRetry = onRetry,
            emptyTitle = "没有找到这道菜谱",
            emptyMessage = "它可能已被删除，返回列表刷新后再试。",
            modifier = Modifier.fillMaxSize(),
        ) { recipe ->
            if (uiState.isEditing) {
                RecipeForm(
                    form = uiState.form,
                    onFormChange = onFormChange,
                    onSubmit = onSave,
                    submitLabel = "保存修改",
                    isSubmitting = uiState.isSaving,
                    onCancel = onCancelEdit,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                )
            } else {
                RecipeDetailContent(
                    recipe = recipe,
                    isDeleting = uiState.isDeleting,
                    onEdit = onStartEdit,
                    onDelete = { showDeleteDialog = true },
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!uiState.isDeleting) showDeleteDialog = false },
            title = { Text("删除这道菜谱？") },
            text = { Text("删除后无法恢复；食材库中的库存记录不会受到影响。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    enabled = !uiState.isDeleting,
                ) {
                    Text(
                        text = if (uiState.isDeleting) "删除中…" else "删除",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    enabled = !uiState.isDeleting,
                ) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun RecipeDetailContent(
    recipe: RecipeUiModel,
    isDeleting: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(title = "菜谱") {
            Text(
                text = recipe.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${recipe.ingredients.size} 种食材 · ${recipe.stepCount} 个步骤",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        SectionCard(title = "所需食材") {
            if (recipe.ingredients.isEmpty()) {
                Text(
                    text = "这道菜谱还没有填写食材。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                recipe.ingredients.forEach { ingredient ->
                    IngredientLine(ingredient = ingredient)
                }
            }
        }

        SectionCard(title = "做法") {
            if (recipe.steps.isEmpty()) {
                Text(
                    text = "这道菜谱还没有填写做法。",
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onEdit,
                enabled = !isDeleting,
                modifier = Modifier.weight(1f),
            ) {
                Text("编辑")
            }
            OutlinedButton(
                onClick = onDelete,
                enabled = !isDeleting,
                modifier = Modifier.weight(1f),
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(16.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text(if (isDeleting) "删除中…" else "删除")
            }
        }

        Text(
            text = "提示：根据现有食材判断“能做 / 部分能做”的匹配将在后续模块接入。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IngredientLine(ingredient: RecipeIngredientUiModel) {
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
            Column {
                Text(
                    text = ingredient.name,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (ingredient.category.isNotBlank()) {
                    Text(
                        text = ingredient.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            text = ingredient.quantityLabel,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}
