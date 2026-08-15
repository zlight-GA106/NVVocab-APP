package com.zlight106.nvvocab.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zlight106.nvvocab.data.QuizQuestion
import com.zlight106.nvvocab.data.WrongQuestionEntry
import com.zlight106.nvvocab.data.WrongQuestionSort
import com.zlight106.nvvocab.ui.components.NvvDropdown
import com.zlight106.nvvocab.ui.icons.NvvIcons

@Composable
fun QuizPreviewDialog(
    bankName: String,
    questions: List<QuizQuestion>,
    wrongQuestions: List<WrongQuestionEntry>,
    onDismiss: () -> Unit,
) {
    var sort by remember { mutableStateOf(WrongQuestionSort.LATEST) }
    val proficiency = remember(wrongQuestions) { wrongQuestions.associateBy(WrongQuestionEntry::questionKey) }
    val visible = remember(questions, proficiency, sort) {
        when (sort) {
            WrongQuestionSort.LATEST -> questions.sortedBy(QuizQuestion::originalIndex)
            WrongQuestionSort.WRONG_COUNT -> questions.sortedByDescending { proficiency[it.id]?.wrongCount ?: 0 }
            WrongQuestionSort.PROFICIENCY_LOW -> questions.sortedBy { proficiency[it.id]?.proficiencyPercent ?: 0 }
            WrongQuestionSort.PROFICIENCY_HIGH -> questions.sortedByDescending { proficiency[it.id]?.proficiencyPercent ?: 0 }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.9f)
                .widthIn(max = 920.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("题库预览", style = MaterialTheme.typography.titleLarge)
                        Text(bankName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss) { Icon(NvvIcons.X, "关闭题库预览") }
                }
                NvvDropdown(
                    label = "预览排序",
                    value = sort,
                    options = listOf(
                        WrongQuestionSort.LATEST to "题号顺序",
                        WrongQuestionSort.PROFICIENCY_LOW to "熟练度从低到高",
                        WrongQuestionSort.PROFICIENCY_HIGH to "熟练度从高到低",
                        WrongQuestionSort.WRONG_COUNT to "错误次数最多优先",
                    ),
                    icon = NvvIcons.RefreshCw,
                    onChange = { sort = it },
                    compact = true,
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(visible, key = QuizQuestion::id) { question ->
                        val record = proficiency[question.id]
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("第 ${question.originalIndex + 1} 题", color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        record?.let { "熟练度 ${it.proficiencyPercent}%" } ?: "熟练度 暂无记录",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(question.text, fontWeight = FontWeight.SemiBold)
                                question.options.forEach { option ->
                                    Text("${option.id}. ${option.text}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                LinearProgressIndicator(
                                    progress = { (record?.proficiencyPercent ?: 0) / 100f },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
