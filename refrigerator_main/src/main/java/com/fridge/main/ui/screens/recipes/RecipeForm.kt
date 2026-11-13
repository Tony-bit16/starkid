package com.fridge.main.ui.screens.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fridge.main.ui.components.SectionCard

/**
 * 新增/编辑菜谱共用的表单。
 *
 * 只处理输入和字段级错误，不直接访问数据库；保存动作由调用方注入。
 * 食材清单支持动态增加/移除，完全空白的行在提交时会被忽略。
 */
@Composable
fun RecipeForm(
    form: RecipeFormState,
    onFormChange: (RecipeFormState) -> Unit,
    onSubmit: () -> Unit,
    submitLabel: String,
    isSubmitting: Boolean,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(title = "基本信息") {
            OutlinedTextField(
                value = form.name,
                onValueChange = { value ->
                    onFormChange(form.copy(name = value, nameError = null))
                },
                label = { Text("菜名") },
                placeholder = { Text("例如：番茄炒蛋") },
                singleLine = true,
                isError = form.nameError != null,
                supportingText = form.nameError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionCard(title = "所需食材") {
            if (form.ingredientsError != null) {
                Text(
                    text = form.ingredientsError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            form.ingredients.forEachIndexed { index, ingredient ->
                IngredientRow(
                    index = index,
                    ingredient = ingredient,
                    canRemove = form.ingredients.size > 1,
                    onNameChange = { value ->
                        onFormChange(
                            form.updateIngredient(index) {
                                it.copy(name = value, nameError = null)
                            },
                        )
                    },
                    onQuantityChange = { value ->
                        // 只保留数字与一个小数点，非法字符不会进入校验流程。
                        val filtered = value.filter { it.isDigit() || it == '.' }
                        if (filtered.count { it == '.' } <= 1) {
                            onFormChange(
                                form.updateIngredient(index) {
                                    it.copy(quantity = filtered, quantityError = null)
                                },
                            )
                        }
                    },
                    onUnitChange = { value ->
                        onFormChange(
                            form.updateIngredient(index) {
                                it.copy(unit = value, unitError = null)
                            },
                        )
                    },
                    onRemove = { onFormChange(form.removeIngredient(index)) },
                )
            }

            OutlinedButton(
                onClick = { onFormChange(form.addIngredient()) },
                enabled = form.ingredients.size < MAX_INGREDIENT_ROWS,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("添加食材")
            }

            Text(
                text = "食材名称会与食材库中的同名品类自动关联；暂无库存的食材也可以先写进菜谱。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = "做法") {
            OutlinedTextField(
                value = form.steps,
                onValueChange = { value ->
                    onFormChange(form.copy(steps = value, stepsError = null))
                },
                label = { Text("步骤") },
                placeholder = { Text("每行一步，例如：\n鸡蛋打散，加少许盐\n热锅下油，倒入蛋液炒熟") },
                minLines = 5,
                maxLines = 10,
                isError = form.stepsError != null,
                supportingText = form.stepsError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "每行保存为一个步骤，保存时自动忽略空行。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Button(
            onClick = onSubmit,
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(16.dp),
                    strokeWidth = 2.dp,
                )
            }
            Text(submitLabel)
        }

        if (onCancel != null) {
            TextButton(
                onClick = onCancel,
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("取消")
            }
        }
    }
}

@Composable
private fun IngredientRow(
    index: Int,
    ingredient: RecipeIngredientForm,
    canRemove: Boolean,
    onNameChange: (String) -> Unit,
    onQuantityChange: (String) -> Unit,
    onUnitChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "食材 ${index + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRemove, enabled = canRemove) {
                    Text("移除")
                }
            }

            OutlinedTextField(
                value = ingredient.name,
                onValueChange = onNameChange,
                label = { Text("食材名称") },
                placeholder = { Text("例如：番茄") },
                singleLine = true,
                isError = ingredient.nameError != null,
                supportingText = ingredient.nameError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = ingredient.quantity,
                    onValueChange = onQuantityChange,
                    label = { Text("用量") },
                    placeholder = { Text("例如：2") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = ingredient.quantityError != null,
                    supportingText = ingredient.quantityError?.let { { Text(it) } },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = ingredient.unit,
                    onValueChange = onUnitChange,
                    label = { Text("单位") },
                    placeholder = { Text("个 / 克 / 勺") },
                    singleLine = true,
                    isError = ingredient.unitError != null,
                    supportingText = ingredient.unitError?.let { { Text(it) } },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private const val MAX_INGREDIENT_ROWS = 30
