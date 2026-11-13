package com.fridge.main.ui.screens.recipes

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
 * 新增菜谱页。
 *
 * 表单状态由 [AddRecipeViewModel] 持有；保存成功后通过 [AddRecipeUiState.savedId]
 * 触发一次返回，列表页会因 Room Flow 自动刷新。
 */
@Composable
fun AddRecipeScreen(
    uiState: AddRecipeUiState,
    onFormChange: (RecipeFormState) -> Unit,
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

        RecipeForm(
            form = uiState.form,
            onFormChange = onFormChange,
            onSubmit = onSave,
            submitLabel = "保存菜谱",
            isSubmitting = uiState.isSaving,
        )

        SectionCard(title = "说明") {
            Text(
                text = "菜谱会保存到本地 Room 数据库；所需食材关联到食材品类，而不是某一批库存，因此菜谱可以先于库存存在。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
