package com.fridge.main.ui.screens.ingredients

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fridge.main.ui.components.SectionCard

/**
 * 新增/编辑食材共用的表单。
 *
 * 只负责输入与展示字段级错误，不做数据库操作；提交由调用方传入 [onSubmit]。
 * 这样新增页和详情页编辑态可以复用同一套校验与布局。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientForm(
    form: IngredientFormState,
    onFormChange: (IngredientFormState) -> Unit,
    onSubmit: () -> Unit,
    submitLabel: String,
    isSubmitting: Boolean,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(title = "基本信息") {
            OutlinedTextField(
                value = form.name,
                onValueChange = { onFormChange(form.copy(name = it, nameError = null)) },
                label = { Text("名称") },
                placeholder = { Text("例如：番茄") },
                singleLine = true,
                isError = form.nameError != null,
                supportingText = form.nameError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.category,
                onValueChange = { onFormChange(form.copy(category = it, categoryError = null)) },
                label = { Text("分类") },
                placeholder = { Text("例如：蔬菜 / 蛋奶 / 乳制品") },
                singleLine = true,
                isError = form.categoryError != null,
                supportingText = form.categoryError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionCard(title = "库存信息") {
            // 只读输入框 + 透明点击层：点击整行都能唤起日期选择器。
            Box {
                OutlinedTextField(
                    value = form.storedAtLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("入库时间") },
                    trailingIcon = { Text("选择") },
                    isError = form.storedAtError != null,
                    supportingText = form.storedAtError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { showDatePicker = true },
                )
            }

            OutlinedTextField(
                value = form.shelfLifeDays,
                onValueChange = { value ->
                    onFormChange(
                        form.copy(
                            shelfLifeDays = value.filter { it.isDigit() }.take(5),
                            shelfLifeDaysError = null,
                        ),
                    )
                },
                label = { Text("保质期天数") },
                placeholder = { Text("正整数，单位：天") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = form.shelfLifeDaysError != null,
                supportingText = form.shelfLifeDaysError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = form.quantity,
                    onValueChange = { value ->
                        // 只保留数字和一个小数点，避免非法字符进入校验流程。
                        val filtered = value.filter { it.isDigit() || it == '.' }
                        if (filtered.count { it == '.' } <= 1) {
                            onFormChange(form.copy(quantity = filtered, quantityError = null))
                        }
                    },
                    label = { Text("数量") },
                    placeholder = { Text("例如：3") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = form.quantityError != null,
                    supportingText = form.quantityError?.let { { Text(it) } },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = form.unit,
                    onValueChange = { onFormChange(form.copy(unit = it, unitError = null)) },
                    label = { Text("单位") },
                    placeholder = { Text("个 / 克 / 袋") },
                    singleLine = true,
                    isError = form.unitError != null,
                    supportingText = form.unitError?.let { { Text(it) } },
                    modifier = Modifier.weight(1f),
                )
            }
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

        Text(
            text = "数量支持小数（如 0.5 kg）；入库时间保存为当天 00:00 的时间戳。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = localStartOfDayToUtcDate(form.storedAtMillis),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selected ->
                            onFormChange(
                                form.copy(
                                    storedAtMillis = utcDateToLocalStartOfDay(selected),
                                    storedAtError = null,
                                ),
                            )
                        }
                        showDatePicker = false
                    },
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
