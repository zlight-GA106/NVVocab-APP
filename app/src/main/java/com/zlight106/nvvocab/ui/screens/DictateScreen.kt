package com.zlight106.nvvocab.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zlight106.nvvocab.data.DictationMode
import com.zlight106.nvvocab.data.QueueSort
import com.zlight106.nvvocab.data.QuizBank
import com.zlight106.nvvocab.data.QuizQuestion
import com.zlight106.nvvocab.data.QuizQueueMode
import com.zlight106.nvvocab.data.ReviewCategory
import com.zlight106.nvvocab.data.WordEntry
import com.zlight106.nvvocab.ui.MainViewModel
import com.zlight106.nvvocab.ui.components.NvvDropdown
import com.zlight106.nvvocab.ui.components.SectionCard
import com.zlight106.nvvocab.ui.components.SegmentedRow
import com.zlight106.nvvocab.ui.icons.NvvIcons
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class QuizAnswerRecord(
    val question: QuizQuestion,
    val selectedAnswers: Set<String>,
    val correct: Boolean,
)

@Composable
fun DictateScreen(
    viewModel: MainViewModel,
    tags: List<String>,
    quizBanks: List<QuizBank>,
    words: List<WordEntry>,
    onStartSession: (PracticeSessionRequest) -> Unit,
) {
    var category by remember { mutableStateOf(ReviewCategory.WORDS) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(remember { ScrollState(0) }).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("沉浸复习", style = MaterialTheme.typography.headlineMedium)
        Text(
            "在单词拼写、本地题库与 AI 对照练习之间切换。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SegmentedRow(Modifier.fillMaxWidth()) {
            ModeSegment(
                modifier = Modifier.weight(1f),
                selected = category == ReviewCategory.WORDS,
                onClick = { category = ReviewCategory.WORDS },
                label = "单词复习",
                icon = NvvIcons.BrainCircuit,
            )
            ModeSegment(
                modifier = Modifier.weight(1f),
                selected = category == ReviewCategory.QUESTIONS,
                onClick = { category = ReviewCategory.QUESTIONS },
                label = "题库练习",
                icon = NvvIcons.FileQuestion,
            )
            ModeSegment(
                modifier = Modifier.weight(1f),
                selected = category == ReviewCategory.CONTRAST,
                onClick = { category = ReviewCategory.CONTRAST },
                label = "对照练习",
                icon = NvvIcons.Sparkles,
            )
        }
        AnimatedContent(
            targetState = category,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "review-category",
        ) { selectedCategory ->
            when (selectedCategory) {
                ReviewCategory.WORDS -> WordReviewPanel(viewModel, tags, onStartSession)
                ReviewCategory.QUESTIONS -> QuizReviewPanel(viewModel, quizBanks, onStartSession)
                ReviewCategory.CONTRAST -> ContrastPracticePanel(viewModel, words, tags, onStartSession)
            }
        }
    }
}

@Composable
private fun WordReviewPanel(
    viewModel: MainViewModel,
    tags: List<String>,
    onStartSession: (PracticeSessionRequest) -> Unit,
) {
    var mode by remember { mutableStateOf(DictationMode.REVIEW) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(QueueSort.PROFICIENCY_LOW) }
    var limitText by remember { mutableStateOf("") }
    var queue by remember { mutableStateOf<List<WordEntry>>(emptyList()) }
    var currentIndex by remember { mutableStateOf(0) }
    var started by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }

    val restart = {
        val preparedQueue = viewModel.buildQueue(mode, selectedTag, sort, limitText.toIntOrNull())
        queue = preparedQueue
        currentIndex = 0
        started = false
        completed = false
        if (preparedQueue.isNotEmpty()) {
            onStartSession(PracticeSessionRequest.Words(preparedQueue, mode))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            if (mode == DictationMode.REVIEW) "只看释义，敲出单词" else "看见单词，完成拼写练习",
            style = MaterialTheme.typography.titleLarge,
        )
        SegmentedRow(Modifier.fillMaxWidth()) {
            ModeSegment(
                modifier = Modifier.weight(1f),
                selected = mode == DictationMode.REVIEW,
                onClick = { mode = DictationMode.REVIEW; started = false; completed = false },
                label = "复习模式",
                icon = NvvIcons.BrainCircuit,
            )
            ModeSegment(
                modifier = Modifier.weight(1f),
                selected = mode == DictationMode.PRACTICE,
                onClick = { mode = DictationMode.PRACTICE; started = false; completed = false },
                label = "练习模式",
                icon = NvvIcons.Keyboard,
            )
        }
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    NvvDropdown(
                        label = "复习范围",
                        value = selectedTag,
                        options = listOf(
                            null to if (mode == DictationMode.REVIEW) "全部到期词库" else "全部词库",
                        ) + tags.map { it to it },
                        icon = NvvIcons.Tags,
                        onChange = { selectedTag = it; started = false },
                        modifier = Modifier.weight(1f),
                        compact = true,
                    )
                    NvvDropdown(
                        label = "排序方式",
                        value = sort,
                        options = listOf(
                            QueueSort.PROFICIENCY_LOW to "熟练度从低到高",
                            QueueSort.PROFICIENCY_HIGH to "熟练度从高到低",
                            QueueSort.EARLIEST to "最早导入优先",
                            QueueSort.LATEST to "最近导入优先",
                            QueueSort.RANDOM to "随机乱序",
                        ),
                        icon = NvvIcons.RefreshCw,
                        onChange = { sort = it; started = false },
                        modifier = Modifier.weight(1f),
                        compact = true,
                    )
                }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = limitText,
                    onValueChange = { input -> limitText = input.filter(Char::isDigit).take(3) },
                    label = { Text("本次数量") },
                    placeholder = { Text("系统自动计算，上限 100") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                )
                Button(onClick = restart, modifier = Modifier.align(Alignment.End), shape = CircleShape) {
                    Icon(NvvIcons.Play, null)
                    Text(if (started) "重新开始" else "开始复习", Modifier.padding(start = 8.dp))
                }
            }
        }
        AnimatedContent(
            targetState = Triple(started, completed, currentIndex),
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
            label = "dictation-state",
        ) { state ->
            when {
                state.second -> DictationFinished(queue.size, onRestart = restart)
                !state.first -> DictationReady(mode)
                else -> DictationCard(
                    mode = mode,
                    word = queue[state.third],
                    position = state.third + 1,
                    total = queue.size,
                    onAdvance = { quality ->
                        val advance = {
                            if (currentIndex + 1 >= queue.size) {
                                completed = true
                                started = false
                            } else {
                                currentIndex += 1
                            }
                        }
                        if (mode == DictationMode.REVIEW) {
                            viewModel.recordReview(queue[state.third], quality, advance)
                        } else {
                            advance()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun QuizReviewPanel(
    viewModel: MainViewModel,
    banks: List<QuizBank>,
    onStartSession: (PracticeSessionRequest) -> Unit,
) {
    var selectedBankId by remember { mutableStateOf<String?>(null) }
    var queueMode by remember { mutableStateOf(QuizQueueMode.SEQUENTIAL) }
    var rangeStart by remember { mutableStateOf("1") }
    var rangeEnd by remember { mutableStateOf("") }
    var randomCount by remember { mutableStateOf("20") }
    var queue by remember { mutableStateOf<List<QuizQuestion>>(emptyList()) }
    var currentIndex by remember { mutableStateOf(0) }
    var records by remember { mutableStateOf<List<QuizAnswerRecord>>(emptyList()) }
    var started by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var showBankManager by remember { mutableStateOf(false) }
    var configurationError by remember { mutableStateOf<String?>(null) }
    val selectedBank = banks.firstOrNull { it.id == selectedBankId }
    val xmlLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importQuizXml)
    }

    LaunchedEffect(banks) {
        if (banks.none { it.id == selectedBankId }) selectedBankId = banks.firstOrNull()?.id
    }
    LaunchedEffect(selectedBankId) {
        selectedBank?.let { rangeEnd = it.questionCount.toString() }
        started = false
        finished = false
        configurationError = null
    }

    fun startQuiz() {
        val bank = selectedBank ?: return
        val start = rangeStart.toIntOrNull()
        val end = rangeEnd.toIntOrNull()
        if (start == null || end == null || start !in 1..bank.questionCount || end !in start..bank.questionCount) {
            configurationError = "题号范围无效，请检查起始和结束题号。"
            return
        }
        viewModel.loadQuizQuestions(bank.id) { allQuestions ->
            val ranged = allQuestions.filter { it.originalIndex + 1 in start..end }
            val prepared = if (queueMode == QuizQueueMode.RANDOM) {
                val count = randomCount.toIntOrNull()
                if (count == null || count !in 1..ranged.size) {
                    configurationError = "随机抽题数量必须位于当前题号范围内。"
                    return@loadQuizQuestions
                }
                ranged.shuffled().take(count)
            } else {
                ranged
            }
            queue = prepared
            currentIndex = 0
            records = emptyList()
            started = false
            finished = false
            configurationError = if (prepared.isEmpty()) "当前范围内没有可练习的题目。" else null
            if (prepared.isNotEmpty()) {
                onStartSession(PracticeSessionRequest.Quiz(prepared))
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                QuizBankHeader(
                    onManage = { showBankManager = true },
                    onImport = { xmlLauncher.launch(arrayOf("text/xml", "application/xml", "*/*")) },
                )
                if (banks.isEmpty()) {
                    Text("尚未导入题库，请先选择 XML 文件。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    NvvDropdown(
                        label = "选择题库",
                        value = selectedBankId,
                        options = banks.map { it.id as String? to "${it.displayName()}（${it.questionCount} 题）" },
                        icon = NvvIcons.FileQuestion,
                        onChange = { selectedBankId = it },
                    )
                    SegmentedRow(Modifier.fillMaxWidth()) {
                        ModeSegment(
                            modifier = Modifier.weight(1f),
                            selected = queueMode == QuizQueueMode.SEQUENTIAL,
                            onClick = { queueMode = QuizQueueMode.SEQUENTIAL; started = false },
                            label = "顺序练习",
                            icon = NvvIcons.ListChecks,
                        )
                        ModeSegment(
                            modifier = Modifier.weight(1f),
                            selected = queueMode == QuizQueueMode.RANDOM,
                            onClick = { queueMode = QuizQueueMode.RANDOM; started = false },
                            label = "随机抽题",
                            icon = NvvIcons.RefreshCw,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberField(
                            modifier = Modifier.weight(1f),
                            value = rangeStart,
                            onValueChange = { rangeStart = it },
                            label = "起始题号",
                        )
                        NumberField(
                            modifier = Modifier.weight(1f),
                            value = rangeEnd,
                            onValueChange = { rangeEnd = it },
                            label = "结束题号",
                        )
                    }
                    if (queueMode == QuizQueueMode.RANDOM) {
                        NumberField(
                            modifier = Modifier.fillMaxWidth(),
                            value = randomCount,
                            onValueChange = { randomCount = it },
                            label = "随机抽取数量",
                        )
                    }
                    configurationError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(
                            onClick = ::startQuiz,
                            enabled = selectedBank != null,
                            shape = CircleShape,
                            modifier = Modifier.padding(start = 10.dp),
                        ) {
                            Icon(NvvIcons.Play, null)
                            Text("开始答题", Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
        when {
            finished -> QuizResultPanel(records = records, onRestart = ::startQuiz)
            started && queue.isNotEmpty() -> QuizQuestionCard(
                question = queue[currentIndex],
                position = currentIndex + 1,
                total = queue.size,
                onAnswered = { question, selectedAnswers ->
                    viewModel.recordQuizAnswer(question, selectedAnswers)
                    records = records + QuizAnswerRecord(
                        question = question,
                        selectedAnswers = selectedAnswers,
                        correct = selectedAnswers == question.answers,
                    )
                },
                onNext = {
                    if (currentIndex + 1 >= queue.size) {
                        started = false
                        finished = true
                    } else {
                        currentIndex += 1
                    }
                },
            )
            else -> QuizReadyPanel()
        }
    }
    if (showBankManager) {
        QuizBankManagerDialog(
            banks = banks,
            onRename = viewModel::renameQuizBank,
            onDelete = viewModel::deleteQuizBank,
            onDismiss = { showBankManager = false },
        )
    }
}

@Composable
private fun QuizBankHeader(
    onManage: () -> Unit,
    onImport: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 310.dp
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("本地题库", style = MaterialTheme.typography.titleLarge)
                QuizBankActions(
                    onManage = onManage,
                    onImport = onImport,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("本地题库", style = MaterialTheme.typography.titleLarge)
                QuizBankActions(onManage = onManage, onImport = onImport)
            }
        }
    }
}

@Composable
private fun QuizBankActions(
    onManage: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onManage, shape = CircleShape) {
            Icon(NvvIcons.Settings, null)
            Text("管理", Modifier.padding(start = 6.dp))
        }
        OutlinedButton(onClick = onImport, shape = CircleShape) {
            Icon(NvvIcons.Upload, null)
            Text("导入", Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun QuizBankManagerDialog(
    banks: List<QuizBank>,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var editingBankId by remember { mutableStateOf<String?>(null) }
    var editingName by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<QuizBank?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 720.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.verticalScroll(remember { ScrollState(0) }).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("题库管理", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "重命名和删除会在同步时更新到服务器。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    OutlinedButton(onClick = onDismiss, shape = CircleShape) { Text("完成") }
                }
                if (banks.isEmpty()) {
                    Text("暂无题库。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                banks.forEach { bank ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (editingBankId == bank.id) {
                                Text(
                                    "题库时间 ${bank.formattedTimestamp()}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                OutlinedTextField(
                                    value = editingName,
                                    onValueChange = { editingName = it.take(80) },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("题库名称") },
                                    singleLine = true,
                                    shape = MaterialTheme.shapes.large,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    OutlinedButton(
                                        onClick = { editingBankId = null },
                                        shape = CircleShape,
                                    ) { Text("取消") }
                                    Button(
                                        onClick = {
                                            onRename(bank.id, editingName)
                                            editingBankId = null
                                        },
                                        enabled = editingName.trim().isNotEmpty() && editingName.trim() != bank.name,
                                        modifier = Modifier.padding(start = 8.dp),
                                        shape = CircleShape,
                                    ) { Text("保存") }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(NvvIcons.FileQuestion, null, tint = MaterialTheme.colorScheme.primary)
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            bank.displayName(),
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            "题库时间 ${bank.formattedTimestamp()}",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        Text(
                                            "${bank.questionCount} 题",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            editingBankId = bank.id
                                            editingName = bank.name
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = CircleShape,
                                    ) {
                                        Icon(NvvIcons.Pencil, null)
                                        Text("重命名", Modifier.padding(start = 6.dp))
                                    }
                                    OutlinedButton(
                                        onClick = { pendingDelete = bank },
                                        modifier = Modifier.weight(1f),
                                        shape = CircleShape,
                                    ) {
                                        Icon(NvvIcons.Trash2, null, tint = MaterialTheme.colorScheme.error)
                                        Text("删除", Modifier.padding(start = 6.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { bank ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = { Icon(NvvIcons.AlertCircle, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("删除题库") },
            text = { Text("将删除 ${bank.displayName()} 及其答题记录，此操作会同步到服务器。") },
            dismissButton = {
                OutlinedButton(onClick = { pendingDelete = null }, shape = CircleShape) { Text("取消") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(bank.id)
                        pendingDelete = null
                    },
                    shape = CircleShape,
                ) { Text("确认删除") }
            },
            shape = RoundedCornerShape(28.dp),
        )
    }
}

private fun QuizBank.displayName(): String {
    if (!name.startsWith("AI 对照练习 ")) return name
    return AI_QUIZ_NAME_PATTERN.matchEntire(name)?.groupValues?.getOrNull(1) ?: name
}

private fun QuizBank.formattedTimestamp(): String = QUIZ_BANK_TIMESTAMP_FORMAT.format(
    Instant.ofEpochMilli(importedAt).atZone(ZoneId.systemDefault()),
)

private val AI_QUIZ_NAME_PATTERN = Regex("^(.+?)\\s+\\d{8}-\\d{6}(?:-\\d{1,3})?$")
private val QUIZ_BANK_TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        modifier = modifier,
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(4)) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
        singleLine = true,
        shape = MaterialTheme.shapes.large,
    )
}

@Composable
private fun QuizReadyPanel() {
    SectionCard {
        Box(Modifier.fillMaxWidth().padding(vertical = 56.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(NvvIcons.FileQuestion, null, tint = MaterialTheme.colorScheme.primary)
                Text("准备题库练习", style = MaterialTheme.typography.titleLarge)
                Text("选择题库、题号范围与抽题方式后开始。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun QuizQuestionCard(
    question: QuizQuestion,
    position: Int,
    total: Int,
    onAnswered: (QuizQuestion, Set<String>) -> Unit,
    onNext: () -> Unit,
) {
    var selectedAnswers by remember(question.id) { mutableStateOf<Set<String>>(emptySet()) }
    var checked by remember(question.id) { mutableStateOf(false) }
    val multipleChoice = question.answers.size > 1
    val correct = selectedAnswers == question.answers

    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("题目 $position / $total", color = MaterialTheme.colorScheme.primary)
                Text("${question.score} 分", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(question.text, style = MaterialTheme.typography.titleLarge)
            Text(if (multipleChoice) "多选题" else "单选题", color = MaterialTheme.colorScheme.onSurfaceVariant)
            question.options.forEach { option ->
                val selected = option.id in selectedAnswers
                val optionCorrect = option.id in question.answers
                val containerColor = when {
                    checked && optionCorrect -> MaterialTheme.colorScheme.primaryContainer
                    checked && selected && !optionCorrect -> MaterialTheme.colorScheme.errorContainer
                    selected -> MaterialTheme.colorScheme.secondaryContainer
                    else -> Color.Transparent
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (!checked) {
                            selectedAnswers = if (multipleChoice) {
                                if (selected) selectedAnswers - option.id else selectedAnswers + option.id
                            } else {
                                setOf(option.id)
                            }
                        }
                    },
                    shape = MaterialTheme.shapes.large,
                    color = containerColor,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (multipleChoice) {
                            Checkbox(checked = selected, onCheckedChange = null)
                        } else {
                            RadioButton(selected = selected, onClick = null)
                        }
                        Text("${option.id}. ${option.text}", modifier = Modifier.weight(1f))
                    }
                }
            }
            if (checked) {
                Text(
                    if (correct) "回答正确" else "正确答案：${question.answers.sorted().joinToString("、")}",
                    color = if (correct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = {
                    if (checked) {
                        onNext()
                    } else {
                        checked = true
                        onAnswered(question, selectedAnswers)
                    }
                },
                enabled = selectedAnswers.isNotEmpty(),
                modifier = Modifier.align(Alignment.End),
                shape = CircleShape,
            ) {
                Text(if (checked) if (position == total) "完成并结算" else "下一题" else "提交答案")
            }
        }
    }
}

@Composable
private fun QuizResultPanel(records: List<QuizAnswerRecord>, onRestart: () -> Unit) {
    val correctCount = records.count(QuizAnswerRecord::correct)
    val totalScore = records.sumOf { if (it.correct) it.question.score else 0 }
    val possibleScore = records.sumOf { it.question.score }
    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(NvvIcons.Check, null, tint = MaterialTheme.colorScheme.primary)
            Text("答题结算", style = MaterialTheme.typography.headlineSmall)
            Text("正确 $correctCount / ${records.size}", style = MaterialTheme.typography.titleLarge)
            Text("得分 $totalScore / $possibleScore", color = MaterialTheme.colorScheme.onSurfaceVariant)
            records.filterNot(QuizAnswerRecord::correct).forEach { record ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("第 ${record.question.originalIndex + 1} 题", color = MaterialTheme.colorScheme.error)
                        Text(record.question.text)
                        Text("你的答案：${record.selectedAnswers.sorted().joinToString("、").ifBlank { "未选择" }}")
                        Text("正确答案：${record.question.answers.sorted().joinToString("、")}")
                    }
                }
            }
            Button(onClick = onRestart, shape = CircleShape, modifier = Modifier.align(Alignment.End)) {
                Text("再来一轮")
            }
        }
    }
}

@Composable
private fun ModeSegment(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = 48.dp),
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null)
            Text(label, modifier = Modifier.padding(start = 4.dp), maxLines = 1)
        }
    }
}

@Composable
private fun DictationReady(mode: DictationMode) {
    SectionCard(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(vertical = 72.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(if (mode == DictationMode.REVIEW) NvvIcons.BrainCircuit else NvvIcons.Keyboard, null)
                Text("准备开始", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (mode == DictationMode.REVIEW) "只加载已经到期的词条。" else "练习队列结束后会自动停止。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DictationFinished(total: Int, onRestart: () -> Unit) {
    SectionCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(NvvIcons.Check, null, tint = MaterialTheme.colorScheme.primary)
            Text("本轮已结束", style = MaterialTheme.typography.titleLarge)
            Text("已完成 $total 个单词", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onRestart, shape = CircleShape) { Text("再来一轮") }
        }
    }
}

@Composable
private fun DictationCard(
    mode: DictationMode,
    word: WordEntry,
    position: Int,
    total: Int,
    onAdvance: (Int) -> Unit,
) {
    var answer by remember(word.id) { mutableStateOf("") }
    var checked by remember(word.id) { mutableStateOf(false) }
    var hinted by remember(word.id) { mutableStateOf(false) }
    val correct = answer.trim().equals(word.spelling.trim(), ignoreCase = true)
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    fun submit() {
        if (!checked) {
            checked = true
        } else {
            val quality = when {
                !correct -> 0
                hinted -> 3
                else -> 5
            }
            onAdvance(quality)
        }
    }

    LaunchedEffect(word.id) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("$position / $total", color = MaterialTheme.colorScheme.primary)
                Text(word.bookTag, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (mode == DictationMode.PRACTICE) {
                Text(word.spelling, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            }
            Text(word.translation, style = MaterialTheme.typography.headlineSmall)
            word.phonetic?.let { Text("[$it]", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (hinted) Text("首字母：${word.spelling.firstOrNull()?.uppercaseChar() ?: ""}")
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                value = answer,
                onValueChange = { if (!checked) answer = it },
                label = { Text("输入拼写") },
                isError = checked && !correct,
                supportingText = if (checked) {
                    { Text(if (correct) "拼写正确，再次提交进入下一词" else "正确答案：${word.spelling}，再次提交继续") }
                } else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!checked) {
                    Surface(
                        onClick = { hinted = true },
                        shape = CircleShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        color = Color.Transparent,
                    ) { Text("查看首字母", Modifier.padding(horizontal = 18.dp, vertical = 11.dp)) }
                }
                Button(onClick = { submit() }, shape = CircleShape) {
                    Text(if (checked) "下一词" else "检查")
                }
            }
        }
    }
}
