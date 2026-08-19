package com.zlight106.nvvocab.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zlight106.nvvocab.data.ContrastPracticeSession
import com.zlight106.nvvocab.data.ContrastPracticeType
import com.zlight106.nvvocab.data.ContrastQuestion
import com.zlight106.nvvocab.data.ContrastQuestionResult
import com.zlight106.nvvocab.data.DictationMode
import com.zlight106.nvvocab.data.PracticeDifficulty
import com.zlight106.nvvocab.data.QuizQuestion
import com.zlight106.nvvocab.data.QuizSessionAnswer
import com.zlight106.nvvocab.data.WordEntry
import com.zlight106.nvvocab.data.WordReviewResult
import com.zlight106.nvvocab.data.WrongQuestionEntry
import com.zlight106.nvvocab.data.formatOptionAnswers
import com.zlight106.nvvocab.ui.MainViewModel
import com.zlight106.nvvocab.ui.components.QuestionOptionDetails
import com.zlight106.nvvocab.ui.components.SectionCard
import com.zlight106.nvvocab.ui.icons.NvvIcons
import java.util.UUID
import kotlinx.coroutines.delay

sealed interface PracticeSessionRequest {
    data class Words(
        val queue: List<WordEntry>,
        val mode: DictationMode,
    ) : PracticeSessionRequest

    data class Quiz(
        val queue: List<QuizQuestion>,
        val unifiedSettlement: Boolean = false,
    ) : PracticeSessionRequest

    data class Contrast(
        val queue: List<ContrastQuestion>,
        val practiceType: ContrastPracticeType,
        val difficulty: PracticeDifficulty,
        val timeLimitSeconds: Int,
        val hintEnabled: Boolean,
    ) : PracticeSessionRequest

    data class WrongBook(
        val queue: List<WrongQuestionEntry>,
    ) : PracticeSessionRequest
}

private data class QuizResultRecord(
    val question: QuizQuestion,
    val selectedAnswers: Set<String>,
) {
    val correct: Boolean
        get() = selectedAnswers == question.answers
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeSessionScreen(
    request: PracticeSessionRequest,
    viewModel: MainViewModel,
    administratorMode: Boolean,
    onExit: () -> Unit,
) {
    var settled by remember(request) { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, settled) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (!settled) viewModel.startStudyTimeTracking()
                Lifecycle.Event.ON_STOP -> viewModel.stopStudyTimeTracking()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (!settled && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            viewModel.startStudyTimeTracking()
        } else if (settled) {
            viewModel.stopStudyTimeTracking()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopStudyTimeTracking()
        }
    }

    fun requestExit() {
        if (settled) onExit() else showExitDialog = true
    }

    BackHandler(onBack = ::requestExit)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(request.title()) },
                navigationIcon = {
                    IconButton(onClick = ::requestExit) {
                        Icon(NvvIcons.X, contentDescription = "退出当前练习")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            when (request) {
                is PracticeSessionRequest.Words -> WordSession(
                    request = request,
                    viewModel = viewModel,
                    onSettled = { settled = true },
                    onExit = onExit,
                )
                is PracticeSessionRequest.Quiz -> QuizSession(
                    queue = request.queue,
                    wrongBookSession = false,
                    unifiedSettlement = request.unifiedSettlement,
                    viewModel = viewModel,
                    showAnswers = administratorMode,
                    onSettled = { settled = true },
                    onExit = onExit,
                )
                is PracticeSessionRequest.Contrast -> ContrastSession(
                    request = request,
                    viewModel = viewModel,
                    showAnswers = administratorMode,
                    onSettled = { settled = true },
                    onExit = onExit,
                )
                is PracticeSessionRequest.WrongBook -> QuizSession(
                    queue = request.queue.map(WrongQuestionEntry::toQuizQuestion),
                    wrongBookSession = true,
                    unifiedSettlement = false,
                    viewModel = viewModel,
                    showAnswers = true,
                    onSettled = { settled = true },
                    onExit = onExit,
                )
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            icon = { Icon(NvvIcons.AlertCircle, null) },
            title = { Text("退出当前练习？") },
            text = { Text("是否退出，未结算的进度将不会保存") },
            dismissButton = {
                OutlinedButton(
                    onClick = { showExitDialog = false },
                    shape = CircleShape,
                ) {
                    Text("继续练习")
                }
            },
            confirmButton = {
                Button(onClick = onExit, shape = CircleShape) {
                    Text("确认退出")
                }
            },
            shape = MaterialTheme.shapes.extraLarge,
        )
    }
}

private fun PracticeSessionRequest.title(): String = when (this) {
    is PracticeSessionRequest.Words -> if (mode == DictationMode.REVIEW) "复习默写" else "拼写练习"
    is PracticeSessionRequest.Quiz -> "题库答题"
    is PracticeSessionRequest.Contrast -> "对照练习"
    is PracticeSessionRequest.WrongBook -> "错题复习"
}

@Composable
private fun WordSession(
    request: PracticeSessionRequest.Words,
    viewModel: MainViewModel,
    onSettled: () -> Unit,
    onExit: () -> Unit,
) {
    var currentIndex by remember(request) { mutableIntStateOf(0) }
    var results by remember(request) { mutableStateOf<List<WordReviewResult>>(emptyList()) }
    var finished by remember(request) { mutableStateOf(false) }
    var settling by remember(request) { mutableStateOf(false) }

    SessionBody {
        if (finished) {
            SessionResult(
                title = "本轮已结算",
                summary = "已完成 ${request.queue.size} 个单词",
                onExit = onExit,
            )
        } else {
            WordQuestion(
                mode = request.mode,
                word = request.queue[currentIndex],
                position = currentIndex + 1,
                total = request.queue.size,
                enabled = !settling,
                canGoPrevious = currentIndex > 0,
                onPrevious = { currentIndex -= 1 },
                onComplete = { quality ->
                    val currentWord = request.queue[currentIndex]
                    val updated = results.filterNot { it.word.id == currentWord.id } +
                        WordReviewResult(currentWord, quality)
                    results = updated
                    if (currentIndex + 1 < request.queue.size) {
                        currentIndex += 1
                    } else if (request.mode == DictationMode.REVIEW) {
                        settling = true
                        viewModel.recordReviewSession(
                            results = updated,
                            onComplete = {
                                settling = false
                                finished = true
                                onSettled()
                            },
                            onFailure = { settling = false },
                        )
                    } else {
                        finished = true
                        onSettled()
                    }
                },
            )
            if (settling) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    strokeCap = StrokeCap.Round,
                )
                Text("正在结算本轮复习", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun WordQuestion(
    mode: DictationMode,
    word: WordEntry,
    position: Int,
    total: Int,
    enabled: Boolean,
    canGoPrevious: Boolean,
    onPrevious: () -> Unit,
    onComplete: (Int) -> Unit,
) {
    var answer by remember(word.id) { mutableStateOf("") }
    var checked by remember(word.id) { mutableStateOf(false) }
    var hinted by remember(word.id) { mutableStateOf(false) }
    val correct = answer.trim().equals(word.spelling.trim(), ignoreCase = true)
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    fun submit() {
        if (!enabled) return
        if (!checked) {
            checked = true
        } else {
            onComplete(
                when {
                    !correct -> 0
                    hinted -> 3
                    else -> 5
                },
            )
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
                enabled = enabled,
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onPrevious,
                    enabled = enabled && canGoPrevious,
                    shape = CircleShape,
                ) {
                    Icon(NvvIcons.ArrowLeft, null)
                    Text("上一题", Modifier.padding(start = 6.dp))
                }
                Box(Modifier.weight(1f))
                if (!checked) {
                    OutlinedButton(
                        onClick = { hinted = true },
                        enabled = enabled,
                        shape = CircleShape,
                    ) {
                        Text("查看首字母")
                    }
                }
                Button(onClick = ::submit, enabled = enabled, shape = CircleShape) {
                    Text(if (checked) "下一词" else "检查")
                }
            }
        }
    }
}

@Composable
private fun QuizSession(
    queue: List<QuizQuestion>,
    wrongBookSession: Boolean,
    unifiedSettlement: Boolean,
    viewModel: MainViewModel,
    showAnswers: Boolean,
    onSettled: () -> Unit,
    onExit: () -> Unit,
) {
    var currentIndex by remember(queue, wrongBookSession, unifiedSettlement) { mutableIntStateOf(0) }
    var records by remember(queue, wrongBookSession, unifiedSettlement) {
        mutableStateOf<List<QuizResultRecord>>(emptyList())
    }
    var finished by remember(queue, wrongBookSession, unifiedSettlement) { mutableStateOf(false) }
    var settling by remember(queue, wrongBookSession, unifiedSettlement) { mutableStateOf(false) }
    var showWrongAnswers by remember(queue, finished) { mutableStateOf(false) }

    SessionBody {
        if (finished) {
            val correctCount = records.count(QuizResultRecord::correct)
            val totalScore = records.sumOf { if (it.correct) it.question.score else 0 }
            val possibleScore = records.sumOf { it.question.score }
            SessionResult(
                title = "答题已结算",
                summary = "正确 $correctCount / ${records.size}，得分 $totalScore / $possibleScore",
                onExit = onExit,
            ) {
                val wrongRecords = records.filterNot(QuizResultRecord::correct)
                OutlinedButton(
                    onClick = { showWrongAnswers = !showWrongAnswers },
                    enabled = wrongRecords.isNotEmpty(),
                    shape = CircleShape,
                ) {
                    Icon(NvvIcons.Eye, null)
                    Text(
                        if (showWrongAnswers) "收起错题" else "显示错题（${wrongRecords.size}）",
                        Modifier.padding(start = 8.dp),
                    )
                }
                if (showWrongAnswers) {
                    wrongRecords.forEach { record ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(record.question.text, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "你的答案：${formatOptionAnswers(record.question.options, record.selectedAnswers)}",
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    "正确答案：${formatOptionAnswers(record.question.options, record.question.answers)}",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                QuestionOptionDetails(
                                    options = record.question.options,
                                    correctAnswers = record.question.answers,
                                    selectedAnswers = record.selectedAnswers,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        } else {
            val currentQuestion = queue[currentIndex]
            val existingRecord = records.firstOrNull { it.question.id == currentQuestion.id }
            QuizQuestion(
                question = currentQuestion,
                position = currentIndex + 1,
                total = queue.size,
                enabled = !settling,
                showAnswer = showAnswers,
                initialSelectedAnswers = existingRecord?.selectedAnswers.orEmpty(),
                initialChecked = !unifiedSettlement && existingRecord != null,
                unifiedSettlement = unifiedSettlement,
                canGoPrevious = currentIndex > 0,
                onPrevious = { selectedAnswers ->
                    if (unifiedSettlement && selectedAnswers.isNotEmpty()) {
                        records = records.filterNot { it.question.id == currentQuestion.id } +
                            QuizResultRecord(currentQuestion, selectedAnswers)
                    }
                    currentIndex -= 1
                },
                onComplete = { selectedAnswers ->
                    val updated = records.filterNot { it.question.id == currentQuestion.id } +
                        QuizResultRecord(currentQuestion, selectedAnswers)
                    records = updated
                    if (currentIndex + 1 < queue.size) {
                        currentIndex += 1
                    } else {
                        settling = true
                        val complete = {
                            settling = false
                            finished = true
                            onSettled()
                        }
                        val failure = { settling = false }
                        if (wrongBookSession) viewModel.recordWrongQuestionSession(
                            answers = updated.map { QuizSessionAnswer(it.question, it.selectedAnswers) },
                            onComplete = complete,
                            onFailure = failure,
                        ) else viewModel.recordQuizSession(
                            answers = updated.map { QuizSessionAnswer(it.question, it.selectedAnswers) },
                            onComplete = complete,
                            onFailure = failure,
                        )
                    }
                },
            )
            if (settling) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), strokeCap = StrokeCap.Round)
                Text("正在结算答题记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun QuizQuestion(
    question: QuizQuestion,
    position: Int,
    total: Int,
    enabled: Boolean,
    showAnswer: Boolean,
    initialSelectedAnswers: Set<String>,
    initialChecked: Boolean,
    unifiedSettlement: Boolean,
    canGoPrevious: Boolean,
    onPrevious: (Set<String>) -> Unit,
    onComplete: (Set<String>) -> Unit,
) {
    var selectedAnswers by remember(question.id, initialSelectedAnswers) {
        mutableStateOf(initialSelectedAnswers)
    }
    var checked by remember(question.id, initialChecked) { mutableStateOf(initialChecked) }
    val multipleChoice = question.answers.size > 1
    val correct = selectedAnswers == question.answers

    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text("题目 $position / $total", color = MaterialTheme.colorScheme.primary)
                Column(horizontalAlignment = Alignment.End) {
                    if (showAnswer) {
                        Text(
                            administratorAnswerText(question.options, question.answers),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text("${question.score} 分", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
                        if (!checked && enabled) {
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
                        if (multipleChoice) Checkbox(selected, null) else RadioButton(selected, null)
                        Text("${option.id}. ${option.text}", modifier = Modifier.weight(1f))
                    }
                }
            }
            if (checked) {
                Text(
                    if (correct) {
                        "回答正确"
                    } else {
                        "正确答案：${formatOptionAnswers(question.options, question.answers)}"
                    },
                    color = if (correct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { onPrevious(selectedAnswers) },
                    enabled = enabled && canGoPrevious,
                    shape = CircleShape,
                ) {
                    Icon(NvvIcons.ArrowLeft, null)
                    Text("上一题", Modifier.padding(start = 6.dp))
                }
                Button(
                    onClick = {
                        if (unifiedSettlement || checked) {
                            onComplete(selectedAnswers)
                        } else {
                            checked = true
                        }
                    },
                    enabled = enabled && selectedAnswers.isNotEmpty(),
                    shape = CircleShape,
                ) {
                    Text(
                        when {
                            unifiedSettlement && position == total -> "统一结算"
                            unifiedSettlement -> "下一题"
                            checked && position == total -> "完成并结算"
                            checked -> "下一题"
                            else -> "提交答案"
                        },
                    )
                }
            }
        }
    }
}

internal fun administratorAnswerText(
    options: List<com.zlight106.nvvocab.data.QuizOption>,
    answers: Set<String>,
): String = "答案：${formatOptionAnswers(options, answers)}"

@Composable
private fun ContrastSession(
    request: PracticeSessionRequest.Contrast,
    viewModel: MainViewModel,
    showAnswers: Boolean,
    onSettled: () -> Unit,
    onExit: () -> Unit,
) {
    var currentIndex by remember(request) { mutableIntStateOf(0) }
    var records by remember(request) { mutableStateOf<List<ContrastQuestionResult>>(emptyList()) }
    var finished by remember(request) { mutableStateOf(false) }
    var settling by remember(request) { mutableStateOf(false) }
    val startedAt = remember(request) { System.currentTimeMillis() }
    var elapsedSeconds by remember(request) { mutableIntStateOf(0) }

    SessionBody {
        if (finished) {
            val correctCount = records.count(ContrastQuestionResult::correct)
            val accuracy = if (records.isEmpty()) 0 else correctCount * 100 / records.size
            SessionResult(
                title = "对照练习已结算",
                summary = "正确 $correctCount / ${records.size}，正确率 $accuracy%，用时 $elapsedSeconds 秒",
                onExit = onExit,
            )
        } else {
            ContrastQuestion(
                question = request.queue[currentIndex],
                position = currentIndex + 1,
                total = request.queue.size,
                timeLimitSeconds = request.timeLimitSeconds,
                hintEnabled = request.hintEnabled,
                showAnswer = showAnswers,
                enabled = !settling,
                initialSelectedIndex = records.firstOrNull {
                    it.question.id == request.queue[currentIndex].id
                }?.selectedIndex,
                canGoPrevious = currentIndex > 0,
                onPrevious = { currentIndex -= 1 },
                onComplete = { selectedIndex ->
                    val question = request.queue[currentIndex]
                    val updated = records.filterNot { it.question.id == question.id } +
                        ContrastQuestionResult(
                            question = question,
                            selectedIndex = selectedIndex,
                        )
                    records = updated
                    if (currentIndex + 1 < request.queue.size) {
                        currentIndex += 1
                    } else {
                        settling = true
                        elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1_000).toInt()
                        viewModel.recordContrastPracticeSession(
                            ContrastPracticeSession(
                                id = UUID.randomUUID().toString(),
                                completedAt = System.currentTimeMillis(),
                                practiceType = request.practiceType,
                                difficulty = request.difficulty,
                                questionCount = request.queue.size,
                                correctCount = updated.count(ContrastQuestionResult::correct),
                                elapsedSeconds = elapsedSeconds,
                                hintEnabled = request.hintEnabled,
                            ),
                            results = updated,
                            onComplete = {
                                settling = false
                                finished = true
                                onSettled()
                            },
                            onFailure = { settling = false },
                        )
                    }
                },
            )
            if (settling) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), strokeCap = StrokeCap.Round)
                Text("正在结算对照练习", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ContrastQuestion(
    question: ContrastQuestion,
    position: Int,
    total: Int,
    timeLimitSeconds: Int,
    hintEnabled: Boolean,
    showAnswer: Boolean,
    enabled: Boolean,
    initialSelectedIndex: Int?,
    canGoPrevious: Boolean,
    onPrevious: () -> Unit,
    onComplete: (Int?) -> Unit,
) {
    var selectedIndex by remember(question.id, initialSelectedIndex) {
        mutableStateOf(initialSelectedIndex)
    }
    var checked by remember(question.id) { mutableStateOf(false) }
    var remainingSeconds by remember(question.id) { mutableIntStateOf(timeLimitSeconds) }

    LaunchedEffect(question.id, checked) {
        while (!checked && remainingSeconds > 0) {
            delay(1_000)
            remainingSeconds -= 1
        }
        if (!checked && remainingSeconds == 0) checked = true
    }

    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("题目 $position / $total", color = MaterialTheme.colorScheme.primary)
                Column(horizontalAlignment = Alignment.End) {
                    if (showAnswer) {
                        Text(
                            "答案：${question.options[question.correctIndex]}",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text("${remainingSeconds}s", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LinearProgressIndicator(
                progress = { remainingSeconds.toFloat() / timeLimitSeconds.coerceAtLeast(1) },
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
                    onClick = { if (!checked && enabled) selectedIndex = index },
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
                        Text("${('A'.code + index).toChar()}. $option", modifier = Modifier.weight(1f))
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onPrevious,
                    enabled = enabled && canGoPrevious,
                    shape = CircleShape,
                ) {
                    Icon(NvvIcons.ArrowLeft, null)
                    Text("上一题", Modifier.padding(start = 6.dp))
                }
                Button(
                    onClick = { if (checked) onComplete(selectedIndex) else checked = true },
                    enabled = enabled && (checked || selectedIndex != null),
                    shape = CircleShape,
                ) {
                    Text(if (checked) if (position == total) "完成并结算" else "下一题" else "提交答案")
                }
            }
        }
    }
}

@Composable
private fun SessionBody(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 920.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun SessionResult(
    title: String,
    summary: String,
    onExit: () -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    SectionCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(NvvIcons.Check, null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
            Button(onClick = onExit, shape = CircleShape) {
                Text("返回沉浸复习")
            }
        }
    }
}

private fun WrongQuestionEntry.toQuizQuestion(): QuizQuestion = QuizQuestion(
    id = questionKey,
    bankId = bankId ?: "wrong-book",
    originalIndex = 0,
    score = 10,
    text = questionText,
    options = options,
    answers = correctAnswers,
)
