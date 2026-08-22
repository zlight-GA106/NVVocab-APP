package com.zlight106.nvvocab.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.zlight106.nvvocab.data.ParaphraseSeed
import com.zlight106.nvvocab.domain.WordTextParser
import com.zlight106.nvvocab.ui.MainViewModel
import com.zlight106.nvvocab.ui.components.NvvDropdown
import com.zlight106.nvvocab.ui.components.SectionCard
import com.zlight106.nvvocab.ui.icons.NvvIcons

@Composable
fun ImportScreen(
    viewModel: MainViewModel,
    tags: List<String>,
    paraphraseSeeds: List<ParaphraseSeed>,
) {
    var text by remember { mutableStateOf(TextFieldValue()) }
    var selectedTag by remember(tags) { mutableStateOf(tags.firstOrNull().orEmpty()) }
    var editableTag by remember(selectedTag) { mutableStateOf(selectedTag) }
    var editingSeedId by remember { mutableStateOf<String?>(null) }
    var seedSourceText by remember { mutableStateOf("") }
    var seedTargetText by remember { mutableStateOf("") }
    var seedContextText by remember { mutableStateOf("") }
    var seedSourceReference by remember { mutableStateOf("") }
    var seedNotes by remember { mutableStateOf("") }
    var seedBatchText by remember { mutableStateOf("") }
    val parsed = remember(text.text) { WordTextParser.parse(text.text) }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(remember { ScrollState(0) }).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("词库导入", style = MaterialTheme.typography.headlineMedium)
        Text(
            "粘贴教辅文本，解析结果会先进入本机 SQLite，再由同步任务写入云端。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (tags.isNotEmpty()) {
                    NvvDropdown(
                        label = "使用已有分类",
                        value = selectedTag,
                        options = tags.map { it to it },
                        icon = NvvIcons.Tags,
                        onChange = {
                            selectedTag = it
                            editableTag = it
                        },
                    )
                }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = editableTag,
                    onValueChange = { editableTag = it },
                    label = { Text("分类标签") },
                    singleLine = true,
                    leadingIcon = { Icon(NvvIcons.Tags, null) },
                    shape = MaterialTheme.shapes.large,
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("批量文本") },
                    placeholder = { Text("abandon [əˈbændən] vt. 放弃") },
                    shape = MaterialTheme.shapes.extraLarge,
                )
                Button(
                    modifier = Modifier.align(Alignment.End),
                    enabled = parsed.isNotEmpty(),
                    shape = CircleShape,
                    onClick = {
                        viewModel.importText(text.text, editableTag) { imported ->
                            if (imported > 0) text = TextFieldValue()
                        }
                    },
                ) {
                    Icon(NvvIcons.Upload, null)
                    Text("执行导入", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("语义压缩种子", style = MaterialTheme.typography.titleMedium)
                Text(
                    "维护原表达与等效压缩表达。练习题在本地生成，不会实时调用 AI。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = seedSourceText,
                    onValueChange = { seedSourceText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("原表达") },
                    shape = MaterialTheme.shapes.large,
                )
                OutlinedTextField(
                    value = seedTargetText,
                    onValueChange = { seedTargetText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("等效表达") },
                    shape = MaterialTheme.shapes.large,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = seedContextText,
                        onValueChange = { seedContextText = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("上下文") },
                        shape = MaterialTheme.shapes.large,
                    )
                    OutlinedTextField(
                        value = seedSourceReference,
                        onValueChange = { seedSourceReference = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("来源") },
                        shape = MaterialTheme.shapes.large,
                    )
                }
                OutlinedTextField(
                    value = seedNotes,
                    onValueChange = { seedNotes = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("备注") },
                    shape = MaterialTheme.shapes.large,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (editingSeedId != null) {
                        OutlinedButton(
                            onClick = {
                                editingSeedId = null
                                seedSourceText = ""
                                seedTargetText = ""
                                seedContextText = ""
                                seedSourceReference = ""
                                seedNotes = ""
                            },
                            shape = CircleShape,
                        ) {
                            Text("取消编辑")
                        }
                    }
                    Button(
                        onClick = {
                            viewModel.saveParaphraseSeed(
                                id = editingSeedId,
                                sourceText = seedSourceText,
                                targetText = seedTargetText,
                                contextText = seedContextText,
                                sourceReference = seedSourceReference,
                                notes = seedNotes,
                            ) {
                                editingSeedId = null
                                seedSourceText = ""
                                seedTargetText = ""
                                seedContextText = ""
                                seedSourceReference = ""
                                seedNotes = ""
                            }
                        },
                        enabled = seedSourceText.isNotBlank() && seedTargetText.isNotBlank(),
                        modifier = Modifier.padding(start = 8.dp),
                        shape = CircleShape,
                    ) {
                        Icon(NvvIcons.Check, null)
                        Text(if (editingSeedId == null) "新增种子" else "保存修改", Modifier.padding(start = 8.dp))
                    }
                }
                OutlinedTextField(
                    value = seedBatchText,
                    onValueChange = { seedBatchText = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                    label = { Text("批量导入") },
                    placeholder = { Text("原表达 => 等效表达 | 上下文 | 来源 | 备注") },
                    shape = MaterialTheme.shapes.extraLarge,
                )
                Button(
                    onClick = {
                        viewModel.importParaphraseSeedText(seedBatchText) { count ->
                            if (count > 0) seedBatchText = ""
                        }
                    },
                    enabled = seedBatchText.isNotBlank(),
                    modifier = Modifier.align(Alignment.End),
                    shape = CircleShape,
                ) {
                    Icon(NvvIcons.Upload, null)
                    Text("批量导入种子", Modifier.padding(start = 8.dp))
                }
                if (paraphraseSeeds.isEmpty()) {
                    Text("尚未录入语义压缩种子。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    paraphraseSeeds.forEach { seed ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(seed.sourceText, style = MaterialTheme.typography.titleSmall)
                                Text(seed.targetText, color = MaterialTheme.colorScheme.primary)
                                listOfNotNull(
                                    seed.contextText?.takeIf(String::isNotBlank)?.let { "上下文：$it" },
                                    seed.sourceReference?.takeIf(String::isNotBlank)?.let { "来源：$it" },
                                    seed.notes?.takeIf(String::isNotBlank)?.let { "备注：$it" },
                                ).forEach { metadata ->
                                    Text(
                                        metadata,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Row(
                                    modifier = Modifier.align(Alignment.End),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            editingSeedId = seed.id
                                            seedSourceText = seed.sourceText
                                            seedTargetText = seed.targetText
                                            seedContextText = seed.contextText.orEmpty()
                                            seedSourceReference = seed.sourceReference.orEmpty()
                                            seedNotes = seed.notes.orEmpty()
                                        },
                                        shape = CircleShape,
                                    ) {
                                        Icon(NvvIcons.Pencil, null)
                                        Text("编辑", Modifier.padding(start = 6.dp))
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.deleteParaphraseSeed(seed.id) },
                                        shape = CircleShape,
                                    ) {
                                        Icon(NvvIcons.Trash2, null)
                                        Text("删除", Modifier.padding(start = 6.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("解析预览 ${parsed.size} 词", style = MaterialTheme.typography.titleMedium)
                if (parsed.isEmpty()) {
                    Text("输入文本后将在这里显示解析结果。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    parsed.take(100).forEachIndexed { index, word ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                Text(
                                    text = (index + 1).toString(),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(word.spelling, style = MaterialTheme.typography.titleSmall)
                                if (word.phonetic.isNotBlank()) {
                                    Text("[${word.phonetic}]", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text(word.translation, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
