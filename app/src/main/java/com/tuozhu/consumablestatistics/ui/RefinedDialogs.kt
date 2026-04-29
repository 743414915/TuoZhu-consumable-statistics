package com.tuozhu.consumablestatistics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun RefinedWeightChangeDialog(
    title: String,
    confirmLabel: String,
    description: String,
    rollName: String,
    initialValue: String,
    allowZero: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (Int, String) -> Unit,
) {
    RefinedWeightChangeDialog(
        title = title,
        confirmLabel = confirmLabel,
        description = description,
        rollName = rollName,
        initialValue = initialValue,
        valueLabel = if (allowZero) "当前实称重量（g）" else "本次补记消耗（g）",
        valueSupportingText = if (allowZero) {
            "可输入 0g，表示这卷已经用完。"
        } else {
            "请输入大于 0 的克重。"
        },
        noteSupportingText = if (allowZero) {
            "可选，例如：重新复核、刚换秤。"
        } else {
            "可选，例如：补录旧任务、试打件、失败件。"
        },
        allowZero = allowZero,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

@Composable
fun RefinedWeightChangeDialog(
    title: String,
    confirmLabel: String,
    description: String,
    rollName: String,
    initialValue: String,
    valueLabel: String,
    valueSupportingText: String,
    noteSupportingText: String,
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
                    label = { Text(valueLabel) },
                    supportingText = { Text(valueSupportingText) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注") },
                    supportingText = { Text(noteSupportingText) },
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
fun ImportPreviewDialog(
    rollCount: Int,
    printJobCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("导入数据预览", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "即将导入以下数据到当前设备：",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "· $rollCount 卷耗材（含校准和消耗记录）\n· $printJobCount 条已确认打印记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "导入后，库存会自动刷新。重复的打印记录不会被重复添加。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9EADBF),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确认导入", color = MaterialTheme.colorScheme.primary)
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
fun ArchiveRollDialog(
    rollName: String,
    isActive: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("归档耗材卷", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "确认将 $rollName 移出当前库存吗？",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (isActive) {
                        "这是当前活动卷。归档后，系统会自动切换到下一卷；如果没有其他库存卷，活动卷区域会清空。"
                    } else {
                        "归档后，这卷不会再出现在当前库存和校准操作里，但历史记录、打印关联和称重基线都会保留。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "这个动作不会删除历史，只是把这卷从当前库存视图中移出。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB43A2E),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("归档并移出", color = Color(0xFFB43A2E))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
