package com.zlight106.nvvocab.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zlight106.nvvocab.data.PracticeDifficulty
import com.zlight106.nvvocab.data.WordEntry
import com.zlight106.nvvocab.ui.MainViewModel
import com.zlight106.nvvocab.ui.components.NvvDropdown
import com.zlight106.nvvocab.ui.components.SectionCard
import com.zlight106.nvvocab.ui.icons.NvvIcons

@Composable
fun WordUsageReviewPanel(
    viewModel: MainViewModel,
    words: List<WordEntry>,
    tags: List<String>,
    onStartSession: (PracticeSessionRequest) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val generating by viewModel.usageGenerating.collectAsStateWithLifecycle()
    val progress by viewModel.usageGenerationProgress.collectAsStateWithLifecycle()
    var selectedTag by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var countText by rememberSaveable { mutableStateOf("5") }
    var difficulty by rememberSaveable { mutableStateOf(PracticeDifficulty.MEDIUM) }
    val available = remember(words, selectedTag, search) {
        words.filter {
            (selectedTag == null || it.bookTag == selectedTag) &&
                (search.isBlank() || it.spelling.contains(search.trim(), true) || it.translation.contains(search.trim(), true))
        }.distinctBy { it.spelling.lowercase() }.sortedBy { it.spelling.lowercase() }
    }
    val count = countText.toIntOrNull()
    val countValid = count != null && count in 1..30
    val targets = available.take(count?.coerceIn(1, 30) ?: 5)
    val configured = state.aiSettings.apiKey.isNotBlank() && state.aiSettings.model.isNotBlank()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("以单词为核心，学习常见词组与真实用法", style = MaterialTheme.typography.titleLarge)
        Text(
            "调用设置中的 AI 配置（默认 DeepSeek），为每个单词补全常用搭配、介词用法与例句，并生成两道练习题。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                NvvDropdown(
                    label = "词库分类", value = selectedTag,
                    options = listOf(null to "全部词库") + tags.map { it to it },
                    icon = NvvIcons.Tags, onChange = { selectedTag = it },
                )
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    label = { Text("搜索目标单词或释义") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                )
                NvvDropdown(
                    label = "用法难度", value = difficulty,
                    options = listOf(PracticeDifficulty.EASY to "简单 · 日常搭配", PracticeDifficulty.MEDIUM to "中等 · 学习与工作", PracticeDifficulty.HARD to "困难 · 语义与介词辨析"),
                    icon = NvvIcons.BrainCircuit, onChange = { difficulty = it },
                )
                OutlinedTextField(
                    value = countText, onValueChange = { countText = it },
                    label = { Text("单词数量（1–30）") }, singleLine = true,
                    isError = !countValid, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                )
                Text("匹配 ${available.size} 个单词，本次 ${targets.size} 个单词 · ${targets.size * 2} 道题")
                if (targets.isNotEmpty()) Text(targets.joinToString("、") { it.spelling }, color = MaterialTheme.colorScheme.primary)
                Text("生成结果会保存为题库，方便重复复习。点击英文选项自动朗读；喇叭可以重听单词、词组和解析中的例句。", style = MaterialTheme.typography.bodySmall)
                if (!configured) Text("请先在设置中保存 DeepSeek API Key 和模型。", color = MaterialTheme.colorScheme.error)
                if (generating) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text("正在补全用法并生成练习题 ${ (progress * 100).toInt() }%")
                }
                Button(
                    onClick = {
                        viewModel.generateWordUsageQuestions(targets, difficulty) { result ->
                            result.onSuccess { onStartSession(PracticeSessionRequest.Quiz(queue = it)) }
                        }
                    },
                    enabled = configured && countValid && targets.isNotEmpty() && !generating,
                    modifier = Modifier.fillMaxWidth(), shape = CircleShape,
                ) {
                    Icon(NvvIcons.Sparkles, null)
                    Text(if (generating) "正在生成" else "生成用法题并开始复习", Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
