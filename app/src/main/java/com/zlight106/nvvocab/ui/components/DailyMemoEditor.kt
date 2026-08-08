package com.zlight106.nvvocab.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zlight106.nvvocab.data.DailyMemoAction
import com.zlight106.nvvocab.data.DailyMemoItem
import com.zlight106.nvvocab.data.DailyMemoSettings
import com.zlight106.nvvocab.data.DailyMemoTarget
import com.zlight106.nvvocab.data.QuizBank
import com.zlight106.nvvocab.ui.icons.NvvIcons
import java.util.UUID

private data class MemoTargetOption(
    val target: DailyMemoTarget,
    val quizBankId: String? = null,
    val quizBankName: String? = null,
    val label: String,
)

@Composable
fun DailyMemoEditor(
    settings: DailyMemoSettings,
    quizBanks: List<QuizBank>,
    onSave: (DailyMemoSettings) -> Unit,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
) {
    var items by remember(settings) { mutableStateOf(settings.items.take(3)) }
    var restDays by remember(settings) { mutableStateOf(settings.restDays) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("备忘任务", style = MaterialTheme.typography.titleMedium)
        if (items.isEmpty()) {
            Text(
                "尚未添加任务。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        items.forEachIndexed { index, item ->
            MemoItemEditor(
                item = item,
                index = index,
                quizBanks = quizBanks,
                onChange = { updated ->
                    items = items.toMutableList().also { it[index] = updated }
                },
                onDelete = {
                    items = items.filterNot { current -> current.id == item.id }
                },
            )
        }
        OutlinedButton(
            onClick = {
                items = items + DailyMemoItem(
                    id = UUID.randomUUID().toString(),
                    action = DailyMemoAction.COMPLETE,
                    target = DailyMemoTarget.DICTATION,
                    amount = 20,
                )
            },
            enabled = items.size < 3,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Icon(NvvIcons.CirclePlus, null, Modifier.size(18.dp))
            Text("添加任务", Modifier.padding(start = 8.dp))
        }

        Text("空窗日", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf(
                1 to "一",
                2 to "二",
                3 to "三",
                4 to "四",
                5 to "五",
                6 to "六",
                7 to "日",
            ).forEach { (day, label) ->
                val selected = day in restDays
                Surface(
                    modifier = Modifier.size(34.dp).clickable {
                        restDays = if (selected) restDays - day else restDays + day
                    },
                    shape = CircleShape,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(label, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onCancel?.let { cancel ->
                OutlinedButton(onClick = cancel, shape = MaterialTheme.shapes.extraLarge) {
                    Text("取消")
                }
            }
            Button(
                onClick = { onSave(DailyMemoSettings(items = items, restDays = restDays)) },
                enabled = items.isNotEmpty(),
                modifier = Modifier.padding(start = if (onCancel == null) 0.dp else 10.dp),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text("保存")
            }
        }
    }
}

@Composable
private fun MemoItemEditor(
    item: DailyMemoItem,
    index: Int,
    quizBanks: List<QuizBank>,
    onChange: (DailyMemoItem) -> Unit,
    onDelete: () -> Unit,
) {
    val targetOptions = remember(quizBanks) {
        buildList {
            add(MemoTargetOption(DailyMemoTarget.DICTATION, label = "默写复习"))
            add(MemoTargetOption(DailyMemoTarget.CONTRAST, label = "对照复习"))
            add(MemoTargetOption(DailyMemoTarget.QUIZ_BANK, label = "全部题库"))
            quizBanks.forEach { bank ->
                add(
                    MemoTargetOption(
                        target = DailyMemoTarget.QUIZ_BANK,
                        quizBankId = bank.id,
                        quizBankName = bank.name,
                        label = "题库 ${bank.name}",
                    ),
                )
            }
        }
    }
    val selectedTarget = targetOptions.firstOrNull {
        it.target == item.target && it.quizBankId == item.quizBankId
    } ?: MemoTargetOption(
        target = item.target,
        quizBankId = item.quizBankId,
        quizBankName = item.quizBankName,
        label = when (item.target) {
            DailyMemoTarget.DICTATION -> "默写复习"
            DailyMemoTarget.CONTRAST -> "对照复习"
            DailyMemoTarget.QUIZ_BANK -> "题库 ${item.quizBankName ?: "已删除题库"}"
        },
    )
    val displayedTargetOptions = if (selectedTarget in targetOptions) {
        targetOptions
    } else {
        listOf(selectedTarget) + targetOptions
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("任务 ${index + 1}", style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        NvvIcons.Trash2,
                        contentDescription = "删除任务",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NvvDropdown(
                    label = "动作",
                    value = item.action,
                    options = listOf(
                        DailyMemoAction.COMPLETE to "完成",
                        DailyMemoAction.REVIEW to "复习",
                    ),
                    icon = NvvIcons.Check,
                    onChange = { onChange(item.copy(action = it)) },
                    modifier = Modifier.weight(0.38f),
                    compact = true,
                )
                NvvDropdown(
                    label = "对象",
                    value = selectedTarget,
                    options = displayedTargetOptions.map { it to it.label },
                    icon = NvvIcons.ListChecks,
                    onChange = {
                        onChange(
                            item.copy(
                                target = it.target,
                                quizBankId = it.quizBankId,
                                quizBankName = it.quizBankName,
                            ),
                        )
                    },
                    modifier = Modifier.weight(0.62f),
                    compact = true,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = item.amount == null,
                    onClick = { onChange(item.copy(amount = null)) },
                    label = { Text("全部") },
                )
                FilterChip(
                    selected = item.amount != null,
                    onClick = { onChange(item.copy(amount = item.amount ?: 20)) },
                    label = { Text("数量") },
                )
                if (item.amount != null) {
                    OutlinedTextField(
                        value = item.amount.toString(),
                        onValueChange = { raw ->
                            raw.filter(Char::isDigit).toIntOrNull()?.let { value ->
                                onChange(item.copy(amount = value.coerceIn(1, 9_999)))
                            }
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("题量") },
                        suffix = { Text(if (item.target == DailyMemoTarget.DICTATION) "词" else "题") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                    )
                }
            }
        }
    }
}
