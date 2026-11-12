package com.fridge.main.ui.screens.ingredients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import com.fridge.main.ui.common.ScreenStateContent
import com.fridge.main.ui.components.FreshnessPill
import com.fridge.main.ui.components.LabeledValue
import com.fridge.main.ui.components.SectionCard
import com.fridge.main.ui.model.IngredientUiModel

/**
 * 食材详情页。
 *
 * 读取失败/不存在由 [IngredientDetailUiState.content] 处理；
 * 保存/删除失败通过 Snackbar 提示，不清空已展示的详情。
 */
@Composable
fun IngredientDetailScreen(
    uiState: IngredientDetailUiState,
    onRetry: () -> Unit,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onFormChange: (IngredientFormState) -> Unit,
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
            emptyTitle = "没有找到这份食材",
            emptyMessage = "它可能已被删除，返回列表刷新后再试。",
            modifier = Modifier.fillMaxSize(),
        ) { item ->
            if (uiState.isEditing) {
                IngredientForm(
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
                IngredientDetailContent(
                    item = item,
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
            title = { Text("删除这份食材？") },
            text = { Text("删除后无法恢复；菜谱定义与食材品类不会受影响。") },
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
private fun IngredientDetailContent(
    item: IngredientUiModel,
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
        SectionCard(title = "基本信息") {
            LabeledValue(label = "名称", value = item.name)
            LabeledValue(label = "分类", value = item.category)
            LabeledValue(label = "数量", value = item.quantityLabel)
            LabeledValue(label = "入库时间", value = item.storedAtLabel)
        }

        SectionCard(title = "保质期") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "新鲜度",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FreshnessPill(status = item.status)
            }
            LabeledValue(label = "保质期天数", value = "${item.shelfLifeDays} 天")
            LabeledValue(label = "到期日期", value = item.expiryDateLabel)
            LabeledValue(label = "剩余时间", value = item.expiryLabel)
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
            text = "到期提醒与菜谱匹配将在后续模块接入；当前仅支持本地库存的增删改查。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
