package com.zlight106.nvvocab.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zlight106.nvvocab.data.ContrastPracticeSession
import com.zlight106.nvvocab.data.ContrastPracticePreset
import com.zlight106.nvvocab.data.ContrastPracticeType
import com.zlight106.nvvocab.data.ContrastQuestion
import com.zlight106.nvvocab.data.ContrastReviewPreferences
import com.zlight106.nvvocab.data.ParaphraseSeed
import com.zlight106.nvvocab.data.PracticeDifficulty
import com.zlight106.nvvocab.data.PracticeRangeMode
import com.zlight106.nvvocab.data.ProficiencyBand
import com.zlight106.nvvocab.data.QueueSort
import com.zlight106.nvvocab.data.QuizBank
import com.zlight106.nvvocab.data.WordEntry
import com.zlight106.nvvocab.domain.ContrastPracticePlanner
import com.zlight106.nvvocab.domain.ParaphrasePracticeGenerator
import com.zlight106.nvvocab.ui.MainViewModel
import com.zlight106.nvvocab.ui.components.NvvDropdown
import com.zlight106.nvvocab.ui.components.SectionCard
import com.zlight106.nvvocab.ui.components.SegmentedRow
import com.zlight106.nvvocab.ui.icons.NvvIcons
import java.util.UUID
import kotlinx.coroutines.delay

private data class ContrastAnswerRecord(
    val questionId: String,
    val selectedIndex: Int?,
    val correct: Boolean,
)

@Composable
fun ContrastPracticePanel(
    viewModel: MainViewModel,
    words: List<WordEntry>,
    paraphraseSeeds: List<ParaphraseSeed>,
    tags: List<String>,
    quizBanks: List<QuizBank>,
    onStartSession: (PracticeSessionRequest) -> Unit,
) {
    val generationProgress by viewModel.contrastGenerationProgress.collectAsStateWithLifecycle()
    val appState by viewModel.uiState.collectAsStateWithLifecycle()
    val savedPreferences = appState.contrastReviewPreferences
    var type by remember { mutableStateOf(savedPreferences.type) }
    var difficulty by remember { mutableStateOf(savedPreferences.difficulty) }
    var rangeMode by remember { mutableStateOf(savedPreferences.rangeMode) }
    var selectedTag by remember { mutableStateOf(savedPreferences.selectedTag) }
    var proficiencyBand by remember { mutableStateOf(savedPreferences.proficiencyBand) }
    var selectedWordIds by remember { mutableStateOf(savedPreferences.selectedWordIds) }
    var sort by remember { mutableStateOf(savedPreferences.sort) }
    var optionCountText by remember { mutableStateOf(savedPreferences.optionCountText) }
    var questionCountText by remember { mutableStateOf(savedPreferences.questionCountText) }
    var timeLimitText by remember { mutableStateOf(savedPreferences.timeLimitText) }
    var saveGeneratedBank by remember { mutableStateOf(savedPreferences.saveGeneratedBank) }
    var selectedQuizBankId by remember { mutableStateOf(savedPreferences.selectedQuizBankId) }
    var showWordPicker by remember { mutableStateOf(false) }
    var showPresetEditor by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    var queue by remember { mutableStateOf<List<ContrastQuestion>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var records by remember { mutableStateOf<List<ContrastAnswerRecord>>(emptyList()) }
    var startedAt by remember { mutableStateOf(0L) }
    var finalElapsedSeconds by remember { mutableIntStateOf(0) }
    var started by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    fun persist() {
        viewModel.saveContrastReviewPreferences(
            ContrastReviewPreferences(
                type = type,
                difficulty = difficulty,
                rangeMode = rangeMode,
                selectedTag = selectedTag,
                proficiencyBand = proficiencyBand,
                selectedWordIds = selectedWordIds,
                sort = sort,
                optionCountText = optionCountText,
                questionCountText = questionCountText,
                timeLimitText = timeLimitText,
                hintEnabled = false,
                selectedQuizBankId = selectedQuizBankId,
                saveGeneratedBank = saveGeneratedBank,
            ),
        )
    }

    LaunchedEffect(quizBanks, selectedQuizBankId) {
        if (selectedQuizBankId != null && quizBanks.none { it.id == selectedQuizBankId }) {
            selectedQuizBankId = null
            persist()
        }
    }

    val scopedWords = remember(words, rangeMode, selectedTag, proficiencyBand, selectedWordIds, sort) {
        ContrastPracticePlanner.selectWords(
            words = words,
            rangeMode = rangeMode,
            selectedTag = selectedTag,
            proficiencyBand = proficiencyBand,
            selectedWordIds = selectedWordIds,
            sort = sort,
        )
    }
    val scopedParaphraseSeeds = remember(paraphraseSeeds, rangeMode, selectedTag) {
        paraphraseSeeds.asSequence()
            .filter { seed -> rangeMode != PracticeRangeMode.CATEGORY || seed.sourceReference == selectedTag }
            .sortedByDescending(ParaphraseSeed::updatedAt)
            .toList()
    }
    val categoryOptions = remember(tags, paraphraseSeeds, type) {
        if (type == ContrastPracticeType.ENGLISH_DEFINITION_TO_ENGLISH) {
            (tags + paraphraseSeeds.mapNotNull(ParaphraseSeed::sourceReference))
                .filter(String::isNotBlank)
                .distinct()
                .sorted()
        } else {
            tags
        }
    }
    val availableChoiceCount = remember(words, scopedParaphraseSeeds, type) {
        if (type == ContrastPracticeType.ENGLISH_DEFINITION_TO_ENGLISH) {
            scopedParaphraseSeeds.map(ParaphraseSeed::targetText)
        } else {
            words.map { word ->
                when (type) {
                ContrastPracticeType.CHINESE_TO_ENGLISH,
                ContrastPracticeType.ENGLISH_DEFINITION_TO_ENGLISH,
                -> word.spelling.trim()
                ContrastPracticeType.ENGLISH_TO_CHINESE -> word.translation.trim()
                }
            }
        }.filter(String::isNotBlank).distinctBy(String::lowercase).size
    }

    fun applyDifficultyPreset(newDifficulty: PracticeDifficulty) {
        difficulty = newDifficulty
        val preset = appState.contrastPracticePresets.forDifficulty(newDifficulty)
        optionCountText = preset.optionCount.toString()
        questionCountText = preset.questionCount.toString()
        timeLimitText = preset.timeLimitSeconds.toString()
        started = false
        finished = false
        persist()
    }

    fun generate() {
        val optionCount = optionCountText.toIntOrNull() ?: return
        val maximumQuestionCount = questionCountText.toIntOrNull() ?: return
        if (optionCount !in 2..8 || maximumQuestionCount < 0) return
        if (type == ContrastPracticeType.ENGLISH_DEFINITION_TO_ENGLISH) {
            val targets = if (maximumQuestionCount == 0) {
                scopedParaphraseSeeds
            } else {
                scopedParaphraseSeeds.take(maximumQuestionCount)
            }
            val generated = runCatching {
                targets.map { seed ->
                    ParaphrasePracticeGenerator.generate(seed, scopedParaphraseSeeds, optionCount)
                }
            }.getOrElse { return }
            if (generated.isNotEmpty()) {
                onStartSession(
                    PracticeSessionRequest.Contrast(
                        queue = generated,
                        practiceType = type,
                        difficulty = difficulty,
                        timeLimitSeconds = timeLimitText.toIntOrNull()?.coerceIn(5, 300) ?: 30,
                        hintEnabled = false,
                        includeTimingInXml = true,
                    ),
                )
            }
            return
        }
        if (scopedWords.isEmpty()) return
        val targets = ContrastPracticePlanner.applyMaximum(scopedWords, maximumQuestionCount)
        generating = true
        started = false
        finished = false
        viewModel.generateContrastQuestions(
            targets = targets,
            distractorPool = words.sortedByDescending(WordEntry::introTime),
            type = type,
            optionCount = optionCount,
            difficulty = difficulty,
            persistGeneratedBank = saveGeneratedBank,
        ) { result ->
            generating = false
            result.onSuccess { generated ->
                queue = generated
                currentIndex = 0
                records = emptyList()
                startedAt = System.currentTimeMillis()
                finalElapsedSeconds = 0
                started = false
                finished = false
                if (generated.isNotEmpty()) {
                    onStartSession(
                        PracticeSessionRequest.Contrast(
                            queue = generated,
                            practiceType = type,
                            difficulty = difficulty,
                            timeLimitSeconds = timeLimitText.toIntOrNull()?.coerceIn(5, 300) ?: 30,
                            hintEnabled = false,
                            includeTimingInXml = true,
                        ),
                    )
                }
            }
        }
    }

    fun startPractice() {
        if (type == ContrastPracticeType.ENGLISH_DEFINITION_TO_ENGLISH) {
            generate()
            return
        }
        val bankId = selectedQuizBankId
        if (bankId == null) {
            generate()
            return
        }
        val maximumQuestionCount = questionCountText.toIntOrNull() ?: return
        if (maximumQuestionCount < 0) return
        viewModel.loadQuizQuestions(bankId) { questions ->
            val prepared = if (maximumQuestionCount == 0) {
                questions
            } else {
                questions.take(maximumQuestionCount)
            }
            if (prepared.isNotEmpty()) {
                onStartSession(
                    PracticeSessionRequest.Quiz(
                        queue = prepared,
                        timeLimitSeconds = timeLimitText.toIntOrNull()?.coerceIn(5, 300),
                        includeTimingInXml = true,
                    ),
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("对照练习", style = MaterialTheme.typography.titleLarge)
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                NvvDropdown(
                    label = "练习方式",
                    value = type,
                    options = listOf(
                        ContrastPracticeType.CHINESE_TO_ENGLISH to "中文翻译英文多选一",
                        ContrastPracticeType.ENGLISH_DEFINITION_TO_ENGLISH to "语义压缩练习",
                        ContrastPracticeType.ENGLISH_TO_CHINESE to "英文翻译中文多选一",
                    ),
                    icon = NvvIcons.Sparkles,
                    onChange = {
                        type = it
                        started = false
                        finished = false
                        persist()
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("难度预设", style = MaterialTheme.typography.labelLarge)
                    OutlinedButton(
                        onClick = { showPresetEditor = true },
                        shape = CircleShape,
                    ) {
                        Icon(NvvIcons.Pencil, null)
                        Text("编辑预设", Modifier.padding(start = 8.dp))
                    }
                }
                SegmentedRow(Modifier.fillMaxWidth()) {
                    DifficultySegment("简单", difficulty == PracticeDifficulty.EASY, Modifier.weight(1f)) {
                        applyDifficultyPreset(PracticeDifficulty.EASY)
                    }
                    DifficultySegment("中等", difficulty == PracticeDifficulty.MEDIUM, Modifier.weight(1f)) {
                        applyDifficultyPreset(PracticeDifficulty.MEDIUM)
                    }
                    DifficultySegment("困难", difficulty == PracticeDifficulty.HARD, Modifier.weight(1f)) {
                        applyDifficultyPreset(PracticeDifficulty.HARD)
                    }
                }
                NvvDropdown(
                    label = "题目范围",
                    value = rangeMode,
                    options = listOf(
                        PracticeRangeMode.ALL to "全部词库",
                        PracticeRangeMode.CATEGORY to "按分类选择",
                        PracticeRangeMode.PROFICIENCY to "按熟练度选择",
                        PracticeRangeMode.CUSTOM to "自由选择单词",
                    ),
                    icon = NvvIcons.Tags,
                    onChange = {
                        rangeMode = it
                        started = false
                        finished = false
                        persist()
                    },
                )
                when (rangeMode) {
                    PracticeRangeMode.CATEGORY -> NvvDropdown(
                        label = "词库分类",
                        value = selectedTag,
                        options = listOf(null to "请选择分类") + categoryOptions.map { tag -> tag to tag },
                        icon = NvvIcons.Tags,
                        onChange = { selectedTag = it; persist() },
                    )
                    PracticeRangeMode.PROFICIENCY -> NvvDropdown(
                        label = "熟练度区间",
                        value = proficiencyBand,
                        options = listOf(
                            ProficiencyBand.LOW to "低熟练度 0 至 39",
                            ProficiencyBand.MEDIUM to "中熟练度 40 至 69",
                            ProficiencyBand.HIGH to "高熟练度 70 至 100",
                        ),
                        icon = NvvIcons.RefreshCw,
                        onChange = { proficiencyBand = it; persist() },
                    )
                    PracticeRangeMode.CUSTOM -> OutlinedButton(
                        onClick = { showWordPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = CircleShape,
                    ) {
                        Icon(NvvIcons.ListChecks, null)
                        Text("自由选择 ${selectedWordIds.size} 个单词", Modifier.padding(start = 8.dp))
                    }
                    PracticeRangeMode.ALL -> Unit
                }
                NvvDropdown(
                    label = "排序方式",
                    value = sort,
                    options = listOf(
                        QueueSort.WRONG_COUNT to "根据错误次数排序",
                        QueueSort.PROFICIENCY_LOW to "熟练度从低到高",
                        QueueSort.PROFICIENCY_HIGH to "熟练度从高到低",
                        QueueSort.LATEST to "最近导入优先",
                        QueueSort.EARLIEST to "最早导入优先",
                        QueueSort.RANDOM to "随机排序",
                    ),
                    icon = NvvIcons.RefreshCw,
                    onChange = {
                        sort = it
                        started = false
                        finished = false
                        persist()
                    },
                )
                Text(
                    if (type == ContrastPracticeType.ENGLISH_DEFINITION_TO_ENGLISH) {
                        "当前范围 ${scopedParaphraseSeeds.size} 条语义种子，最大题量为 0 时生成全部种子。"
                    } else {
                        "当前范围 ${scopedWords.size} 词，最大题量为 0 时生成当前分类的全部词。"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CompactNumberField(
                        value = optionCountText,
                        onValueChange = { optionCountText = it; persist() },
                        label = "选项数",
                        supportingText = "2 至 8",
                        modifier = Modifier.weight(1f),
                    )
                    CompactNumberField(
                        value = questionCountText,
                        onValueChange = { questionCountText = it; persist() },
                        label = "最大题量",
                        supportingText = "0 为全部",
                        modifier = Modifier.weight(1f),
                    )
                    CompactNumberField(
                        value = timeLimitText,
                        onValueChange = { timeLimitText = it; persist() },
                        label = "单题秒数",
                        supportingText = "5 至 300",
                        modifier = Modifier.weight(1f),
                    )
                }
                if (type != ContrastPracticeType.ENGLISH_DEFINITION_TO_ENGLISH) {
                    NvvDropdown(
                        label = "从题库选择练习",
                        value = selectedQuizBankId,
                        options = listOf(null to "根据当前词库生成") + quizBanks.map { bank ->
                            bank.id as String? to "${bank.name.ifBlank { "未命名题库" }}（${bank.questionCount} 题）"
                        },
                        icon = NvvIcons.FileQuestion,
                        onChange = {
                            selectedQuizBankId = it
                            started = false
                            finished = false
                            persist()
                        },
                    )
                }
                if (type != ContrastPracticeType.ENGLISH_DEFINITION_TO_ENGLISH && selectedQuizBankId == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                saveGeneratedBank = !saveGeneratedBank
                                persist()
                            },
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
                val optionCount = optionCountText.toIntOrNull() ?: 0
                val questionCount = questionCountText.toIntOrNull() ?: 0
                val timeLimit = timeLimitText.toIntOrNull() ?: 0
                val selectedQuizBank = quizBanks.firstOrNull { it.id == selectedQuizBankId }
                val configurationValid = if (type == ContrastPracticeType.ENGLISH_DEFINITION_TO_ENGLISH) {
                    optionCount in 2..8 &&
                        questionCount >= 0 &&
                        timeLimit in 5..300 &&
                        scopedParaphraseSeeds.isNotEmpty() &&
                        availableChoiceCount >= optionCount
                } else if (selectedQuizBankId != null) {
                    selectedQuizBank != null && selectedQuizBank.questionCount > 0 &&
                        questionCount >= 0 && timeLimit in 5..300
                } else {
                    optionCount in 2..8 &&
                        questionCount >= 0 &&
                        timeLimit in 5..300 &&
                        scopedWords.isNotEmpty() &&
                        availableChoiceCount >= optionCount
                }
                if (selectedQuizBankId == null && optionCount in 2..8 && availableChoiceCount < optionCount) {
                    Text(
                        "当前词库只有 $availableChoiceCount 个不重复候选项，请减少选项数量。",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = ::startPractice,
                    enabled = configurationValid && !generating,
                    modifier = Modifier.align(Alignment.End),
                    shape = CircleShape,
                ) {
                    Icon(NvvIcons.Sparkles, null)
                    Text(
                        when {
                            generating -> "正在生成"
                            type != ContrastPracticeType.ENGLISH_DEFINITION_TO_ENGLISH &&
                                selectedQuizBankId != null -> "开始题库练习"
                            else -> "生成并开始"
                        },
                        Modifier.padding(start = 8.dp),
                    )
                }
                if (generating) {
                    LinearProgressIndicator(
                        progress = { generationProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        strokeCap = StrokeCap.Round,
                    )
                    Text(
                        "生成进度 ${(generationProgress * 100).toInt()}%",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        when {
            finished -> ContrastResultPanel(
                records = records,
                elapsedSeconds = finalElapsedSeconds,
                onRestart = ::startPractice,
            )
            started && queue.isNotEmpty() -> ContrastQuestionCard(
                question = queue[currentIndex],
                position = currentIndex + 1,
                total = queue.size,
                timeLimitSeconds = timeLimitText.toIntOrNull()?.coerceIn(5, 300) ?: 30,
                hintEnabled = false,
                onAnswered = { selectedIndex ->
                    if (records.none { it.questionId == queue[currentIndex].id }) {
                        records = records + ContrastAnswerRecord(
                            questionId = queue[currentIndex].id,
                            selectedIndex = selectedIndex,
                            correct = selectedIndex == queue[currentIndex].correctIndex,
                        )
                    }
                },
                onNext = {
                    if (currentIndex + 1 >= queue.size) {
                        val elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
                        finalElapsedSeconds = elapsedSeconds
                        viewModel.recordContrastPracticeSession(
                            ContrastPracticeSession(
                                id = UUID.randomUUID().toString(),
                                completedAt = System.currentTimeMillis(),
                                practiceType = type,
                                difficulty = difficulty,
                                questionCount = queue.size,
                                correctCount = records.count(ContrastAnswerRecord::correct),
                                elapsedSeconds = elapsedSeconds,
                                hintEnabled = false,
                            ),
                        )
                        started = false
                        finished = true
                    } else {
                        currentIndex += 1
                    }
                },
            )
            else -> ContrastReadyPanel()
        }
    }

    if (showWordPicker) {
        WordPickerDialog(
            words = words,
            selectedIds = selectedWordIds,
            onSelectionChange = {
                selectedWordIds = it
                persist()
            },
            onDismiss = { showWordPicker = false },
        )
    }
    if (showPresetEditor) {
        val currentPreset = ContrastPracticePreset(
            optionCount = optionCountText.toIntOrNull()
                ?: appState.contrastPracticePresets.forDifficulty(difficulty).optionCount,
            questionCount = questionCountText.toIntOrNull()
                ?: appState.contrastPracticePresets.forDifficulty(difficulty).questionCount,
            timeLimitSeconds = timeLimitText.toIntOrNull()
                ?: appState.contrastPracticePresets.forDifficulty(difficulty).timeLimitSeconds,
        )
        PresetEditorDialog(
            difficulty = difficulty,
            initialPreset = currentPreset,
            onDismiss = { showPresetEditor = false },
            onSave = { preset ->
                optionCountText = preset.optionCount.toString()
                questionCountText = preset.questionCount.toString()
                timeLimitText = preset.timeLimitSeconds.toString()
                viewModel.saveContrastPracticePreset(difficulty, preset)
                persist()
                started = false
                finished = false
                showPresetEditor = false
            },
        )
    }
}

@Composable
private fun PresetEditorDialog(
    difficulty: PracticeDifficulty,
    initialPreset: ContrastPracticePreset,
    onDismiss: () -> Unit,
    onSave: (ContrastPracticePreset) -> Unit,
) {
    var optionCountText by remember(initialPreset, difficulty) {
        mutableStateOf(initialPreset.optionCount.toString())
    }
    var questionCountText by remember(initialPreset, difficulty) {
        mutableStateOf(initialPreset.questionCount.toString())
    }
    var timeLimitText by remember(initialPreset, difficulty) {
        mutableStateOf(initialPreset.timeLimitSeconds.toString())
    }
    val optionCount = optionCountText.toIntOrNull()
    val questionCount = questionCountText.toIntOrNull()
    val timeLimit = timeLimitText.toIntOrNull()
    val valid = optionCount != null && optionCount in 2..8 &&
        questionCount != null && questionCount >= 0 &&
        timeLimit != null && timeLimit in 5..300

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑${difficulty.label()}预设") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactNumberField(
                    value = optionCountText,
                    onValueChange = { optionCountText = it },
                    label = "选项数",
                    supportingText = "2 至 8",
                    modifier = Modifier.fillMaxWidth(),
                )
                CompactNumberField(
                    value = questionCountText,
                    onValueChange = { questionCountText = it },
                    label = "最大题量",
                    supportingText = "0 为全部",
                    modifier = Modifier.fillMaxWidth(),
                )
                CompactNumberField(
                    value = timeLimitText,
                    onValueChange = { timeLimitText = it },
                    label = "单题秒数",
                    supportingText = "5 至 300",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = CircleShape) {
                Text("取消")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (optionCount != null && questionCount != null && timeLimit != null) {
                        onSave(ContrastPracticePreset(optionCount, questionCount, timeLimit))
                    }
                },
                enabled = valid,
                shape = CircleShape,
            ) {
                Text("保存预设")
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
    )
}

private fun PracticeDifficulty.label(): String = when (this) {
    PracticeDifficulty.EASY -> "简单"
    PracticeDifficulty.MEDIUM -> "中等"
    PracticeDifficulty.HARD -> "困难"
}

@Composable
private fun DifficultySegment(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
private fun CompactNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supportingText: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        modifier = modifier,
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit)) },
        label = { Text(label) },
        supportingText = { Text(supportingText) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        shape = MaterialTheme.shapes.large,
    )
}

@Composable
private fun ContrastReadyPanel() {
    SectionCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(NvvIcons.Sparkles, null, tint = MaterialTheme.colorScheme.primary)
            Text("准备生成对照练习", style = MaterialTheme.typography.titleLarge)
            Text("选择题型、范围和难度后生成题目。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ContrastQuestionCard(
    question: ContrastQuestion,
    position: Int,
    total: Int,
    timeLimitSeconds: Int,
    hintEnabled: Boolean,
    onAnswered: (Int?) -> Unit,
    onNext: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedIndex by remember(question.id) { mutableStateOf<Int?>(null) }
    var checked by remember(question.id) { mutableStateOf(false) }
    var remainingSeconds by remember(question.id) { mutableIntStateOf(timeLimitSeconds) }

    LaunchedEffect(question.id, checked) {
        while (!checked && remainingSeconds > 0) {
            delay(1_000)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                remainingSeconds -= 1
            }
        }
        if (!checked && remainingSeconds == 0) {
            checked = true
            onAnswered(null)
        }
    }

    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("题目 $position / $total", color = MaterialTheme.colorScheme.primary)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(NvvIcons.Timer, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${remainingSeconds}s", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LinearProgressIndicator(
                progress = { remainingSeconds.toFloat() / timeLimitSeconds },
                modifier = Modifier.fillMaxWidth(),
                strokeCap = StrokeCap.Round,
            )
            if (hintEnabled) {
                Surface(
                    modifier = Modifier.align(Alignment.End),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Text(
                        "提示：${question.options[question.correctIndex]}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
            Text(question.prompt, style = MaterialTheme.typography.headlineSmall)
            question.options.forEachIndexed { index, option ->
                val selected = selectedIndex == index
                val correct = index == question.correctIndex
                val color = when {
                    checked && correct -> MaterialTheme.colorScheme.primaryContainer
                    checked && selected && !correct -> MaterialTheme.colorScheme.errorContainer
                    selected -> MaterialTheme.colorScheme.secondaryContainer
                    else -> Color.Transparent
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { if (!checked) selectedIndex = index },
                    shape = MaterialTheme.shapes.large,
                    color = color,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Text(option, modifier = Modifier.weight(1f))
                    }
                }
            }
            if (checked) {
                val correct = selectedIndex == question.correctIndex
                Text(
                    if (correct) "回答正确" else "正确答案：${question.options[question.correctIndex]}",
                    color = if (correct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = {
                    if (checked) {
                        onNext()
                    } else {
                        checked = true
                        onAnswered(selectedIndex)
                    }
                },
                enabled = checked || selectedIndex != null,
                modifier = Modifier.align(Alignment.End),
                shape = CircleShape,
            ) {
                Text(if (checked) if (position == total) "完成并结算" else "下一题" else "提交答案")
            }
        }
    }
}

@Composable
private fun ContrastResultPanel(
    records: List<ContrastAnswerRecord>,
    elapsedSeconds: Int,
    onRestart: () -> Unit,
) {
    val correctCount = records.count(ContrastAnswerRecord::correct)
    val accuracy = if (records.isEmpty()) 0 else (correctCount * 100f / records.size).toInt()
    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(NvvIcons.Check, null, tint = MaterialTheme.colorScheme.primary)
            Text("对照练习结算", style = MaterialTheme.typography.headlineSmall)
            Text("正确 $correctCount / ${records.size}", style = MaterialTheme.typography.titleLarge)
            Text("正确率 $accuracy%，用时 ${elapsedSeconds.coerceAtLeast(0)} 秒", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onRestart, modifier = Modifier.align(Alignment.End), shape = CircleShape) {
                Text("重新生成")
            }
        }
    }
}

@Composable
private fun WordPickerDialog(
    words: List<WordEntry>,
    selectedIds: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(words, search) {
        words.filter { word ->
            search.isBlank() || word.spelling.contains(search.trim(), ignoreCase = true) ||
                word.translation.contains(search.trim(), ignoreCase = true)
        }.sortedByDescending(WordEntry::introTime)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自由选择单词") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("搜索单词或释义") },
                    leadingIcon = { Icon(NvvIcons.Search, null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onSelectionChange(selectedIds + filtered.map(WordEntry::id)) },
                        shape = CircleShape,
                    ) { Text("全选当前结果") }
                    OutlinedButton(onClick = { onSelectionChange(emptySet()) }, shape = CircleShape) {
                        Text("清空")
                    }
                }
                Text("已选择 ${selectedIds.size} 词", color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filtered, key = WordEntry::id) { word ->
                        val selected = word.id in selectedIds
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                onSelectionChange(if (selected) selectedIds - word.id else selectedIds + word.id)
                            },
                            shape = MaterialTheme.shapes.large,
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = selected, onCheckedChange = null)
                                Column(Modifier.weight(1f)) {
                                    Text(word.spelling, fontWeight = FontWeight.SemiBold)
                                    Text(word.translation, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss, shape = CircleShape) { Text("完成") } },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    )
}
