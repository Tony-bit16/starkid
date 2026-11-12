package com.fridge.main.ui.screens.ingredients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fridge.main.ui.components.SectionCard

/**
 * 新增食材页。
 *
 * 表单状态由 [AddIngredientViewModel] 持有；保存成功时通过 [AddIngredientUiState.savedId]
 * 触发一次返回，返回后列表页会因 Room Flow 自动刷新。
 */
@Composable
fun AddIngredientScreen(
    uiState: AddIngredientUiState,
    onFormChange: (IngredientFormState) -> Unit,
    onSave: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(uiState.savedId) {
        if (uiState.savedId != null) onSaved()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (uiState.errorMessage != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    text = uiState.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        IngredientForm(
            form = uiState.form,
            onFormChange = onFormChange,
            onSubmit = onSave,
            submitLabel = "保存食材",
            isSubmitting = uiState.isSaving,
        )

        SectionCard(title = "说明") {
            Text(
                text = "保存后会写入本地 Room 数据库；相同名称的食材会归入同一品类，但每次入库保留独立批次与入库时间。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
