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
import com.zlight106.nvvocab.domain.WordTextParser
import com.zlight106.nvvocab.ui.MainViewModel
import com.zlight106.nvvocab.ui.components.NvvDropdown
import com.zlight106.nvvocab.ui.components.SectionCard
import com.zlight106.nvvocab.ui.icons.NvvIcons

@Composable
fun ImportScreen(viewModel: MainViewModel, tags: List<String>) {
    var text by remember { mutableStateOf(TextFieldValue()) }
    var selectedTag by remember(tags) { mutableStateOf(tags.firstOrNull().orEmpty()) }
    var editableTag by remember(selectedTag) { mutableStateOf(selectedTag) }
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
