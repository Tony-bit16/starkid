package com.fridge.main.ui.screens.ingredients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fridge.main.ui.components.SectionCard
import com.fridge.main.ui.components.SkeletonBanner

/**
 * 新增食材页（表单骨架）。
 *
 * 本轮只完成页面结构与字段清单，不申请权限、不写数据库、不做校验；
 * 因此输入框保持禁用，保存按钮不可点击，避免被误认为已实现保存能力。
 */
@Composable
fun AddIngredientScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        SkeletonBanner(text = "骨架演示 · 新增食材表单尚未接入校验与 Room 写入")
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = "基本信息") {
                PlaceholderField(label = "名称", hint = "例如：番茄")
                PlaceholderField(label = "分类", hint = "例如：蔬菜 / 蛋奶 / 乳制品")
            }

            SectionCard(title = "库存信息") {
                PlaceholderField(label = "入库时间", hint = "选择日期，存储为时间戳")
                PlaceholderField(label = "保质期天数", hint = "正整数，单位：天")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PlaceholderField(
                        label = "数量",
                        hint = "例如：3",
                        modifier = Modifier.weight(1f),
                    )
                    PlaceholderField(
                        label = "单位",
                        hint = "例如：个",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存（下一阶段接入）")
            }

            Text(
                text = "字段对应 data/local/entity/Ingredient.kt；日期最终保存为入库当天 00:00 的时间戳。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaceholderField(
    label: String,
    hint: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = "",
        onValueChange = {},
        enabled = false,
        singleLine = true,
        label = { Text(label) },
        placeholder = { Text(hint) },
        modifier = modifier.fillMaxWidth(),
    )
}
