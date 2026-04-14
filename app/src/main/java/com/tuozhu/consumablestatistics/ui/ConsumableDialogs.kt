package com.tuozhu.consumablestatistics.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRollDialog(
    onDismiss: () -> Unit,
    onConfirm: (RollInput) -> Unit,
) {
    val defaultMaterialIndex = defaultAddRollMaterialIndex()
    val defaultMaterial = BambuMaterialOptions[defaultMaterialIndex]

    var materialExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedMaterialIndex by rememberSaveable { mutableIntStateOf(defaultMaterialIndex) }
    var brand by rememberSaveable { mutableStateOf("拓竹 Bambu Lab") }
    var name by rememberSaveable { mutableStateOf("") }
    var colorName by rememberSaveable { mutableStateOf("") }
    var colorHex by rememberSaveable { mutableStateOf(defaultMaterial.defaultColorHex) }
    var initialWeight by rememberSaveable { mutableStateOf("1000") }
    var currentWeight by rememberSaveable { mutableStateOf("1000") }
    var lowStockThreshold by rememberSaveable {
        mutableStateOf(defaultMaterial.defaultThresholdGrams.toString())
    }
    var notes by rememberSaveable { mutableStateOf("") }
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    var colorManuallyEdited by rememberSaveable { mutableStateOf(false) }
    var remainingManuallyEdited by rememberSaveable { mutableStateOf(false) }
    var thresholdManuallyEdited by rememberSaveable { mutableStateOf(false) }

    val selectedMaterial = BambuMaterialOptions[selectedMaterialIndex]
    val initialWeightValue = initialWeight.toIntOrNull()
    val currentWeightValue = currentWeight.toIntOrNull()
    val thresholdValue = lowStockThreshold.toIntOrNull()
    val normalizedHex = normalizeHex(colorHex)
    val isHexValid = normalizedHex != null
    val isHexBlocking = advancedExpanded && colorHex.isNotBlank() && !isHexValid
    val remainingTooHigh = initialWeightValue != null && currentWeightValue != null && currentWeightValue > initialWeightValue
    val thresholdTooHigh = initialWeightValue != null && thresholdValue != null && thresholdValue > initialWeightValue
    val saveEnabled = initialWeightValue != null &&
        initialWeightValue in 1..2000 &&
        currentWeightValue != null &&
        currentWeightValue in 0..2000 &&
        thresholdValue != null &&
        thresholdValue in 0..250 &&
        !remainingTooHigh &&
        !thresholdTooHigh &&
        !isHexBlocking

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("新增耗材卷", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "默认按 PETG Basic 建档，先填核心库存信息，其他字段按需展开。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(20.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(color = colorFromHex(selectedMaterial.defaultColorHex), shape = CircleShape)
                            .padding(0.dp),
                    )
                    Column {
                        Text(
                            text = selectedMaterial.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "推荐预警 ${selectedMaterial.defaultThresholdGrams}g",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ExposedDropdownMenuBox(
                    expanded = materialExpanded,
                    onExpandedChange = { materialExpanded = !materialExpanded },
                ) {
                    OutlinedTextField(
                        value = selectedMaterial.label,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text("材料") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = materialExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                    )
                    DropdownMenu(
                        expanded = materialExpanded,
                        onDismissRequest = { materialExpanded = false },
                    ) {
                        BambuMaterialOptions.forEachIndexed { index, option ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(option.label, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            text = "默认预警 ${option.defaultThresholdGrams}g",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                onClick = {
                                    selectedMaterialIndex = index
                                    if (!colorManuallyEdited) {
                                        colorHex = option.defaultColorHex
                                    }
                                    if (!thresholdManuallyEdited) {
                                        lowStockThreshold = option.defaultThresholdGrams.toString()
                                    }
                                    materialExpanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = initialWeight,
                    onValueChange = {
                        val digits = it.filter(Char::isDigit)
                        initialWeight = digits
                        if (!remainingManuallyEdited) {
                            currentWeight = digits
                        }
                    },
                    label = { Text("满卷克重") },
                    isError = initialWeightValue != null && initialWeightValue !in 1..2000,
                    supportingText = { Text("建议范围 1g - 2000g") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = currentWeight,
                    onValueChange = {
                        remainingManuallyEdited = true
                        currentWeight = it.filter(Char::isDigit)
                    },
                    label = { Text("当前剩余克重") },
                    isError = remainingTooHigh,
                    supportingText = {
                        Text(
                            if (remainingTooHigh) "剩余克重不能大于满卷克重"
                            else "首次录入时建议填当前实称值",
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = lowStockThreshold,
                    onValueChange = {
                        thresholdManuallyEdited = true
                        lowStockThreshold = it.filter(Char::isDigit)
                    },
                    label = { Text("低库存阈值") },
                    isError = thresholdTooHigh,
                    supportingText = {
                        Text(
                            if (thresholdTooHigh) "阈值不能大于满卷克重"
                            else "建议设置成你能接受的最小安全余量",
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = { advancedExpanded = !advancedExpanded },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (advancedExpanded) {
                            "收起高级信息"
                        } else {
                            buildString {
                                append("展开高级信息")
                                append(" · ")
                                append(if (colorName.isBlank()) "未命名颜色" else colorName)
                                append(" · ")
                                append(normalizedHex ?: selectedMaterial.defaultColorHex)
                            }
                        },
                    )
                }
                AnimatedVisibility(visible = advancedExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = brand,
                            onValueChange = { brand = it },
                            label = { Text("品牌") },
                            supportingText = { Text("默认使用拓竹官方品牌，可按需改动") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("名称") },
                            supportingText = { Text("留空时自动按材料生成名称") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = colorName,
                            onValueChange = { colorName = it },
                            label = { Text("颜色名") },
                            supportingText = { Text("例如 玉白、炭黑、冰蓝") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = colorHex,
                            onValueChange = {
                                colorManuallyEdited = true
                                colorHex = it.trim().uppercase()
                            },
                            label = { Text("颜色 HEX") },
                            isError = isHexBlocking,
                            supportingText = {
                                Text(
                                    if (isHexBlocking) "请输入 6 位 HEX，例如 #FF6F3C"
                                    else "会用于首页色带和材料色点",
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("备注") },
                            supportingText = { Text("可选，例如适合打印用途或购入时间") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = saveEnabled,
                onClick = {
                    onConfirm(
                        RollInput(
                            brand = brand.trim(),
                            name = name.ifBlank { "${selectedMaterial.shortName}耗材卷" },
                            material = selectedMaterial.shortName,
                            colorName = colorName.ifBlank { "未命名颜色" },
                            colorHex = normalizedHex ?: selectedMaterial.defaultColorHex,
                            initialWeightGrams = initialWeightValue ?: 1000,
                            remainingWeightGrams = currentWeightValue ?: 1000,
                            lowStockThresholdGrams = thresholdValue ?: selectedMaterial.defaultThresholdGrams,
                            notes = notes.trim(),
                        ),
                    )
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
fun WeightChangeDialog(
    title: String,
    confirmLabel: String,
    description: String,
    rollName: String,
    initialValue: String,
    allowZero: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (Int, String) -> Unit,
) {
    var weightText by rememberSaveable { mutableStateOf(initialValue) }
    var note by rememberSaveable { mutableStateOf("") }
    val weightValue = weightText.toIntOrNull()
    val confirmEnabled = weightValue != null && if (allowZero) weightValue >= 0 else weightValue > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = rollName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it.filter(Char::isDigit) },
                    label = { Text("克重") },
                    supportingText = {
                        Text(if (allowZero) "校准时允许输入 0g" else "打印消耗必须大于 0g")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注") },
                    supportingText = { Text("可选，例如模型名、失败重打、重新称重") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = confirmEnabled,
                onClick = { onConfirm(weightValue ?: 0, note.trim()) },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
fun DeleteRollDialog(
    rollName: String,
    isActive: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("删除耗材卷", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "确认删除 $rollName 吗？",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (isActive) {
                        "这是当前活动卷。删除后如果还有其他耗材卷，系统会自动切换到下一卷；如果这是最后一卷，后续确认打印前需要先新增耗材卷。"
                    } else {
                        "删除后，这卷耗材的校准记录和消耗事件会一并移除，已确认的打印历史会保留，但不再绑定到这卷耗材。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "此操作不可撤销，请确认这卷耗材已经用完、录错，或你明确不再需要保留它的事件记录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB43A2E),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确认删除", color = Color(0xFFB43A2E))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

private fun normalizeHex(value: String): String? {
    val cleaned = value.removePrefix("#").uppercase()
    return if (cleaned.matches(Regex("[0-9A-F]{6}"))) "#$cleaned" else null
}

private fun colorFromHex(value: String): Color {
    return runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrElse { Color(0xFFD86A3D) }
}
