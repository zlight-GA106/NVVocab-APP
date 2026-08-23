package com.zlight106.nvvocab.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zlight106.nvvocab.data.MixedReviewMode
import com.zlight106.nvvocab.data.MixedReviewPreferences
import com.zlight106.nvvocab.data.ParaphraseSeed
import com.zlight106.nvvocab.data.PracticeDifficulty
import com.zlight106.nvvocab.data.WordEntry
import com.zlight106.nvvocab.domain.MixedReviewPlanner
import com.zlight106.nvvocab.ui.MainViewModel
import com.zlight106.nvvocab.ui.components.SectionCard
import com.zlight106.nvvocab.ui.icons.NvvIcons

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MixedReviewPanel(
    viewModel: MainViewModel,
    words: List<WordEntry>,
    paraphraseSeeds: List<ParaphraseSeed>,
    tags: List<String>,
    initialPreferences: MixedReviewPreferences,
    onStartSession: (PracticeSessionRequest) -> Unit,
) {
    val generationProgress by viewModel.mixedGenerationProgress.collectAsStateWithLifecycle()
    val availableTags = remember(tags, paraphraseSeeds) {
        (tags + paraphraseSeeds.mapNotNull(ParaphraseSeed::sourceReference))
            .filter(String::isNotBlank)
            .toSortedSet()
    }
    var selectedTags by remember { mutableStateOf(initialPreferences.selectedTags) }
    var scopeExpanded by remember { mutableStateOf(false) }
    var questionCountText by remember { mutableStateOf(initialPreferences.questionCountText) }
    var enabledModes by remember { mutableStateOf(initialPreferences.enabledModes) }
    var percentages by remember { mutableStateOf(initialPreferences.modePercentages) }
    val difficulty = initialPreferences.difficulty
    var optionCountText by remember { mutableStateOf(initialPreferences.optionCountText) }
    var timeLimitText by remember { mutableStateOf(initialPreferences.timeLimitText) }
    var saveGeneratedBank by remember { mutableStateOf(initialPreferences.saveGeneratedBank) }
    var generating by remember { mutableStateOf(false) }
    var validationMessage by remember { mutableStateOf<String?>(null) }

    val scopedWords = remember(words, selectedTags) {
        words.filter { it.bookTag in selectedTags }.distinctBy(WordEntry::id)
    }
    val scopedParaphraseSeeds = remember(paraphraseSeeds, selectedTags) {
        paraphraseSeeds.filter { seed ->
            seed.sourceReference.isNullOrBlank() || seed.sourceReference in selectedTags
        }
    }
    val enabledPercentageTotal = enabledModes.sumOf { percentages[it] ?: 0 }
    val questionCount = questionCountText.toIntOrNull() ?: 0
    val optionCount = optionCountText.toIntOrNull() ?: 0
    val timeLimit = timeLimitText.toIntOrNull() ?: 0
    val choiceModeEnabled = enabledModes.any { it != MixedReviewMode.DICTATION }
    val wordModeEnabled = enabledModes.any { it != MixedReviewMode.ENGLISH_DEFINITION_TO_ENGLISH }
    val semanticModeEnabled = MixedReviewMode.ENGLISH_DEFINITION_TO_ENGLISH in enabledModes

    LaunchedEffect(availableTags) {
        if (availableTags.isNotEmpty()) {
            val validSelection = selectedTags.intersect(availableTags)
            selectedTags = validSelection.ifEmpty { availableTags }
        }
    }

    LaunchedEffect(
        selectedTags,
        questionCountText,
        enabledModes,
        percentages,
        optionCountText,
        timeLimitText,
        saveGeneratedBank,
    ) {
        viewModel.saveMixedReviewPreferences(
            MixedReviewPreferences(
                selectedTags = selectedTags,
                questionCountText = questionCountText,
                enabledModes = enabledModes,
                modePercentages = percentages,
                difficulty = difficulty,
                optionCountText = optionCountText,
                timeLimitText = timeLimitText,
                saveGeneratedBank = saveGeneratedBank,
            ),
        )
    }

    fun start() {
        validationMessage = null
        val assignments = runCatching {
            MixedReviewPlanner.plan(
                words = words,
                selectedTags = selectedTags,
                requestedCount = questionCount,
                enabledModes = enabledModes,
                percentages = percentages,
                paraphraseSeeds = scopedParaphraseSeeds,
            )
        }.getOrElse { error ->
            validationMessage = error.message ?: "混合复习配置无效。"
            return
        }
        generating = true
        viewModel.generateMixedReview(
            assignments = assignments,
            distractorPool = scopedWords,
            paraphraseSeeds = scopedParaphraseSeeds,
            optionCount = optionCount,
            difficulty = difficulty,
            persistGeneratedBanks = saveGeneratedBank,
        ) { result ->
            generating = false
            result.onSuccess { queue ->
                onStartSession(
                    PracticeSessionRequest.Mixed(
                        queue = queue,
                        difficulty = difficulty,
                        timeLimitSeconds = timeLimit,
                        includeTimingInXml = true,
                    ),
                )
            }.onFailure { error ->
                validationMessage = error.message ?: "混合复习生成失败。"
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("混合复习", style = MaterialTheme.typography.titleLarge)
        Text(
            "从多个词库分类中抽取内容，再按比例混排默写、翻译与语义压缩练习。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { scopeExpanded = !scopeExpanded }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("跨词库范围", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "已选 ${selectedTags.size} 个分类，共 ${scopedWords.size} 个单词、${scopedParaphraseSeeds.size} 条语义种子",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = {
                                selectedTags = if (selectedTags.size == availableTags.size) emptySet() else availableTags
                            },
                            shape = CircleShape,
                        ) {
                            Text(if (selectedTags.size == availableTags.size) "清空" else "全选")
                        }
                        Icon(
                            imageVector = NvvIcons.ChevronDown,
                            contentDescription = if (scopeExpanded) "收起词库范围" else "展开词库范围",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                AnimatedVisibility(
                    visible = scopeExpanded,
                    enter = fadeIn(tween(160)) + expandVertically(tween(220)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(180)),
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        availableTags.forEach { tag ->
                            FilterChip(
                                selected = tag in selectedTags,
                                onClick = {
                                    selectedTags = if (tag in selectedTags) selectedTags - tag else selectedTags + tag
                                },
                                label = { Text(tag) },
                                leadingIcon = if (tag in selectedTags) {
                                    { Icon(NvvIcons.Check, null) }
                                } else null,
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = questionCountText,
                    onValueChange = { value ->
                        questionCountText = value.filter(Char::isDigit).take(5)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("本次复习总量") },
                    supportingText = {
                        Text("最多抽取当前范围内 ${scopedWords.size} 个不同单词，超出时按实际词量生成。")
                    },
                    suffix = { Text("题") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                )

                Text("模式比例", style = MaterialTheme.typography.titleMedium)
                MixedReviewMode.entries.forEach { mode ->
                    MixedModeRatioRow(
                        mode = mode,
                        enabled = mode in enabledModes,
                        percentage = percentages[mode] ?: 0,
                        onEnabledChange = { enabled ->
                            enabledModes = if (enabled) enabledModes + mode else enabledModes - mode
                        },
                        onPercentageChange = { percentage ->
                            percentages = percentages + (mode to percentage)
                        },
                    )
                }
                Text(
                    "已启用模式合计 $enabledPercentageTotal%",
                    color = if (enabledPercentageTotal == 100) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    style = MaterialTheme.typography.labelLarge,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = optionCountText,
                        onValueChange = { optionCountText = it.filter(Char::isDigit).take(1) },
                        modifier = Modifier.weight(1f),
                        label = { Text("选项个数") },
                        supportingText = { Text("2 至 8") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                    )
                    OutlinedTextField(
                        value = timeLimitText,
                        onValueChange = { timeLimitText = it.filter(Char::isDigit).take(3) },
                        modifier = Modifier.weight(1f),
                        label = { Text("单题限时") },
                        supportingText = { Text("5 至 300 秒") },
                        suffix = { Text("秒") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                    )
                }
                if (
                    MixedReviewMode.CHINESE_TO_ENGLISH in enabledModes ||
                    MixedReviewMode.ENGLISH_TO_CHINESE in enabledModes
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { saveGeneratedBank = !saveGeneratedBank },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = !saveGeneratedBank, onCheckedChange = null)
                        Column(Modifier.padding(start = 8.dp)) {
                            Text("不保存本次生成的题库到本地 XML")
                            Text(
                                "本次 AI 生成题只用于当前练习，不写入本地题库。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                validationMessage?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = ::start,
                    enabled = !generating &&
                        selectedTags.isNotEmpty() &&
                        (!wordModeEnabled || scopedWords.isNotEmpty()) &&
                        (!semanticModeEnabled || scopedParaphraseSeeds.size >= 2) &&
                        questionCount > 0 &&
                        enabledModes.isNotEmpty() &&
                        enabledPercentageTotal == 100 &&
                        (!choiceModeEnabled || optionCount in 2..8) &&
                        timeLimit in 5..300,
                    modifier = Modifier.align(Alignment.End),
                    shape = CircleShape,
                ) {
                    Icon(NvvIcons.Play, null)
                    Text(if (generating) "正在生成" else "生成并开始", Modifier.padding(start = 8.dp))
                }
                if (generating) {
                    LinearProgressIndicator(
                        progress = { generationProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        strokeCap = StrokeCap.Round,
                    )
                    Text(
                        "选择题生成进度 ${(generationProgress * 100).toInt()}%",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MixedModeRatioRow(
    mode: MixedReviewMode,
    enabled: Boolean,
    percentage: Int,
    onEnabledChange: (Boolean) -> Unit,
    onPercentageChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Checkbox(checked = enabled, onCheckedChange = onEnabledChange)
        Column(Modifier.weight(1f)) {
            Text(mode.displayName(), style = MaterialTheme.typography.titleSmall)
            Text(
                mode.description(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = percentage.toString(),
            onValueChange = { value -> onPercentageChange(value.filter(Char::isDigit).take(3).toIntOrNull() ?: 0) },
            modifier = Modifier.width(92.dp),
            enabled = enabled,
            suffix = { Text("%") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = MaterialTheme.shapes.large,
        )
    }
}

private fun MixedReviewMode.displayName(): String = when (this) {
    MixedReviewMode.DICTATION -> "默写"
    MixedReviewMode.CHINESE_TO_ENGLISH -> "中翻英"
    MixedReviewMode.ENGLISH_TO_CHINESE -> "英翻中"
    MixedReviewMode.ENGLISH_DEFINITION_TO_ENGLISH -> "语义压缩"
}

private fun MixedReviewMode.description(): String = when (this) {
    MixedReviewMode.DICTATION -> "根据中文释义输入英文单词"
    MixedReviewMode.CHINESE_TO_ENGLISH -> "中文题干选择英文单词"
    MixedReviewMode.ENGLISH_TO_CHINESE -> "英文单词选择中文释义"
    MixedReviewMode.ENGLISH_DEFINITION_TO_ENGLISH -> "从原表达中选择等效压缩表达"
}
