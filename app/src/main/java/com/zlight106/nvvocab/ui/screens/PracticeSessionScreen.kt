package com.zlight106.nvvocab.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zlight106.nvvocab.data.ContrastPracticeSession
import com.zlight106.nvvocab.data.ContrastPracticeType
import com.zlight106.nvvocab.data.ContrastQuestion
import com.zlight106.nvvocab.data.ContrastQuestionResult
import com.zlight106.nvvocab.data.AnswerEvaluationResult
import com.zlight106.nvvocab.data.DictationMode
import com.zlight106.nvvocab.data.MixedReviewItem
import com.zlight106.nvvocab.data.MixedReviewMode
import com.zlight106.nvvocab.data.PracticeDifficulty
import com.zlight106.nvvocab.data.PracticeAttempt
import com.zlight106.nvvocab.data.PracticeAttemptMode
import com.zlight106.nvvocab.data.PracticeSessionRuntime
import com.zlight106.nvvocab.data.QuizQuestion
import com.zlight106.nvvocab.data.QuizQuestionType
import com.zlight106.nvvocab.data.QuizSessionAnswer
import com.zlight106.nvvocab.data.WordEntry
import com.zlight106.nvvocab.data.WordReviewResult
import com.zlight106.nvvocab.data.WrongQuestionEntry
import com.zlight106.nvvocab.data.formatOptionAnswers
import com.zlight106.nvvocab.ui.MainViewModel
import com.zlight106.nvvocab.ui.components.QuestionOptionDetails
import com.zlight106.nvvocab.ui.components.SectionCard
import com.zlight106.nvvocab.ui.icons.NvvIcons
import com.zlight106.nvvocab.domain.AttemptAnalytics
import com.zlight106.nvvocab.domain.AttemptModeTimeSummary
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
        val ignoreFillBlankCase: Boolean = true,
        val timeLimitSeconds: Int? = null,
        val includeTimingInXml: Boolean = true,
    ) : PracticeSessionRequest

    data class Contrast(
        val queue: List<ContrastQuestion>,
        val practiceType: ContrastPracticeType,
        val difficulty: PracticeDifficulty,
        val timeLimitSeconds: Int,
        val hintEnabled: Boolean,
        val includeTimingInXml: Boolean = true,
    ) : PracticeSessionRequest

    data class WrongBook(
        val queue: List<WrongQuestionEntry>,
        val ignoreFillBlankCase: Boolean = true,
    ) : PracticeSessionRequest

    data class Mixed(
        val queue: List<MixedReviewItem>,
        val difficulty: PracticeDifficulty,
        val timeLimitSeconds: Int,
        val includeTimingInXml: Boolean = true,
    ) : PracticeSessionRequest
}

private data class QuizResultRecord(
    val answer: QuizSessionAnswer,
) {
    val question: QuizQuestion
        get() = answer.question
    val selectedAnswers: Set<String>
        get() = answer.selectedAnswers
    val correct: Boolean
        get() = answer.correct
}

private fun QuizSessionAnswer.toPracticeAttempt(
    sessionId: String,
    sequenceIndex: Int,
    previous: PracticeAttempt?,
    activeTimeMs: Long,
): PracticeAttempt {
    val storedAnswer = when (question.type) {
        QuizQuestionType.MULTIPLE_CHOICE -> selectedAnswers.sorted().joinToString(",")
        QuizQuestionType.FILL_BLANK -> userAnswer.orEmpty().trim()
    }
    return PracticeAttempt(
        id = previous?.id ?: UUID.randomUUID().toString(),
        userId = previous?.userId,
        sessionId = sessionId,
        itemId = question.id,
        sourceId = question.bankId,
        mode = if (question.type == QuizQuestionType.FILL_BLANK) {
            PracticeAttemptMode.QUIZ_FILL_BLANK
        } else {
            PracticeAttemptMode.QUIZ_CHOICE
        },
        sequenceIndex = sequenceIndex,
        question = question.text,
        options = question.options,
        firstAnswer = previous?.firstAnswer ?: storedAnswer,
        finalAnswer = storedAnswer,
        referenceAnswer = when (question.type) {
            QuizQuestionType.MULTIPLE_CHOICE -> question.answers.sorted().joinToString(",")
            QuizQuestionType.FILL_BLANK -> question.referenceAnswer.orEmpty()
        },
        acceptedAnswers = if (question.type == QuizQuestionType.FILL_BLANK) {
            question.acceptedAnswers
        } else {
            question.answers
        },
        explanation = question.explanation,
        correct = correct,
        firstAnswerCorrect = previous?.firstAnswerCorrect ?: correct,
        activeTimeMs = activeTimeMs,
        hintUsed = hintUsed || previous?.hintUsed == true,
        timestamp = System.currentTimeMillis(),
        dirty = true,
    )
}

private fun PracticeAttempt.toQuizResultRecord(queue: List<QuizQuestion>): QuizResultRecord? {
    val source = queue.getOrNull(sequenceIndex)?.takeIf { it.id == itemId }
        ?: queue.firstOrNull { it.id == itemId }
        ?: return null
    val answer = QuizSessionAnswer(
        question = source,
        selectedAnswers = if (source.type == QuizQuestionType.MULTIPLE_CHOICE) {
            finalAnswer.split(',').map(String::trim).filter(String::isNotBlank).toSet()
        } else {
            emptySet()
        },
        userAnswer = finalAnswer.takeIf { source.type == QuizQuestionType.FILL_BLANK },
        hintUsed = hintUsed,
        evaluation = if (source.type == QuizQuestionType.FILL_BLANK) {
            com.zlight106.nvvocab.data.FillBlankEvaluation(
                result = if (correct) AnswerEvaluationResult.CORRECT else AnswerEvaluationResult.INCORRECT,
                reason = "已结算记录",
                confidence = 1.0,
                evaluatedByAi = false,
            )
        } else {
            null
        },
    )
    return QuizResultRecord(answer)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeSessionScreen(
    request: PracticeSessionRequest,
    viewModel: MainViewModel,
    administratorMode: Boolean,
    onExit: () -> Unit,
) {
    val runtime by viewModel.practiceSessionRuntime.collectAsStateWithLifecycle()
    val sessionRuntime = runtime ?: return
    val settled = sessionRuntime.finished
    var showExitDialog by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, settled) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (!settled) {
                    viewModel.startStudyTimeTracking()
                    viewModel.setPracticeLifecycleActive(true)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.setPracticeLifecycleActive(false)
                    viewModel.stopStudyTimeTracking()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (!settled && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            viewModel.startStudyTimeTracking()
            viewModel.setPracticeLifecycleActive(true)
        } else if (settled) {
            viewModel.setPracticeLifecycleActive(false)
            viewModel.stopStudyTimeTracking()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setPracticeLifecycleActive(false)
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
                    runtime = sessionRuntime,
                    viewModel = viewModel,
                    onSettled = viewModel::markPracticeSessionFinished,
                    onExit = onExit,
                )
                is PracticeSessionRequest.Quiz -> QuizSession(
                    queue = request.queue,
                    runtime = sessionRuntime,
                    wrongBookSession = false,
                    unifiedSettlement = request.unifiedSettlement,
                    ignoreFillBlankCase = request.ignoreFillBlankCase,
                    timeLimitSeconds = request.timeLimitSeconds,
                    includeTimingInXml = request.includeTimingInXml,
                    viewModel = viewModel,
                    showAnswers = administratorMode,
                    onSettled = viewModel::markPracticeSessionFinished,
                    onExit = onExit,
                )
                is PracticeSessionRequest.Contrast -> ContrastSession(
                    request = request,
                    runtime = sessionRuntime,
                    viewModel = viewModel,
                    showAnswers = administratorMode,
                    onSettled = viewModel::markPracticeSessionFinished,
                    onExit = onExit,
                )
                is PracticeSessionRequest.WrongBook -> QuizSession(
                    queue = request.queue.map(WrongQuestionEntry::toQuizQuestion),
                    runtime = sessionRuntime,
                    wrongBookSession = true,
                    unifiedSettlement = false,
                    ignoreFillBlankCase = request.ignoreFillBlankCase,
                    timeLimitSeconds = null,
                    includeTimingInXml = true,
                    viewModel = viewModel,
                    showAnswers = true,
                    onSettled = viewModel::markPracticeSessionFinished,
                    onExit = onExit,
                )
                is PracticeSessionRequest.Mixed -> MixedSession(
                    request = request,
                    runtime = sessionRuntime,
                    viewModel = viewModel,
                    showAnswers = administratorMode,
                    onSettled = viewModel::markPracticeSessionFinished,
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
    is PracticeSessionRequest.Mixed -> "混合复习"
}

@Composable
private fun WordSession(
    request: PracticeSessionRequest.Words,
    runtime: PracticeSessionRuntime,
    viewModel: MainViewModel,
    onSettled: () -> Unit,
    onExit: () -> Unit,
) {
    var settling by remember(request) { mutableStateOf(false) }
    val currentIndex = runtime.currentIndex.coerceIn(0, request.queue.lastIndex.coerceAtLeast(0))

    LaunchedEffect(runtime.sessionId, currentIndex, runtime.finished) {
        if (!runtime.finished) viewModel.beginQuestionTiming(runtime.sessionId, currentIndex)
    }

    SessionBody {
        if (runtime.finished) {
            SessionResult(
                title = "本轮已结算",
                summary = "已完成 ${request.queue.size} 个单词",
                sessionId = runtime.sessionId,
                attempts = runtime.attempts,
                viewModel = viewModel,
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
                onPrevious = { viewModel.setPracticeSessionIndex(currentIndex - 1) },
                onComplete = { outcome ->
                    val currentWord = request.queue[currentIndex]
                    val previous = runtime.attempts.firstOrNull { it.sequenceIndex == currentIndex }
                    val attempt = PracticeAttempt(
                        id = previous?.id ?: UUID.randomUUID().toString(),
                        userId = previous?.userId,
                        sessionId = runtime.sessionId,
                        itemId = currentWord.id,
                        sourceId = currentWord.bookTag,
                        mode = if (request.mode == DictationMode.REVIEW) {
                            PracticeAttemptMode.WORD_DICTATION
                        } else {
                            PracticeAttemptMode.WORD_SPELLING
                        },
                        sequenceIndex = currentIndex,
                        question = currentWord.translation,
                        options = emptyList(),
                        firstAnswer = previous?.firstAnswer ?: outcome.answer,
                        finalAnswer = outcome.answer,
                        referenceAnswer = currentWord.spelling,
                        acceptedAnswers = setOf(currentWord.spelling),
                        explanation = currentWord.phonetic,
                        correct = outcome.correct,
                        firstAnswerCorrect = previous?.firstAnswerCorrect ?: outcome.correct,
                        activeTimeMs = viewModel.snapshotQuestionTime(runtime.sessionId, currentIndex),
                        hintUsed = outcome.hinted,
                        timestamp = System.currentTimeMillis(),
                        dirty = true,
                    )
                    viewModel.stagePracticeAttempt(attempt)
                    val updatedAttempts = runtime.attempts
                        .filterNot { it.sequenceIndex == currentIndex }
                        .plus(attempt)
                        .sortedBy(PracticeAttempt::sequenceIndex)
                    val updatedResults = updatedAttempts.mapNotNull { recorded ->
                        request.queue.getOrNull(recorded.sequenceIndex)?.let { word ->
                            WordReviewResult(
                                word = word,
                                quality = when {
                                    !recorded.correct -> 0
                                    recorded.hintUsed -> 3
                                    else -> 5
                                },
                            )
                        }
                    }
                    if (currentIndex + 1 < request.queue.size) {
                        viewModel.setPracticeSessionIndex(currentIndex + 1)
                    } else {
                        settling = true
                        viewModel.recordReviewSession(
                            results = if (request.mode == DictationMode.REVIEW) updatedResults else emptyList(),
                            attempts = updatedAttempts,
                            onComplete = {
                                settling = false
                                viewModel.clearQuestionTimers(runtime.sessionId)
                                onSettled()
                            },
                            onFailure = { settling = false },
                        )
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
    onComplete: (WordAnswerOutcome) -> Unit,
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
                WordAnswerOutcome(
                    answer = answer.trim(),
                    correct = correct,
                    hinted = hinted,
                ),
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
    runtime: PracticeSessionRuntime,
    wrongBookSession: Boolean,
    unifiedSettlement: Boolean,
    ignoreFillBlankCase: Boolean,
    timeLimitSeconds: Int?,
    includeTimingInXml: Boolean,
    viewModel: MainViewModel,
    showAnswers: Boolean,
    onSettled: () -> Unit,
    onExit: () -> Unit,
) {
    var settling by remember(queue, wrongBookSession, unifiedSettlement) { mutableStateOf(false) }
    var showWrongAnswers by remember(queue, runtime.finished) { mutableStateOf(false) }
    val currentIndex = runtime.currentIndex.coerceIn(0, queue.lastIndex.coerceAtLeast(0))
    val records = runtime.attempts.mapNotNull { it.toQuizResultRecord(queue) }

    LaunchedEffect(runtime.sessionId, currentIndex, runtime.finished) {
        if (!runtime.finished) viewModel.beginQuestionTiming(runtime.sessionId, currentIndex)
    }

    SessionBody {
        if (runtime.finished) {
            val correctCount = records.count(QuizResultRecord::correct)
            val totalScore = records.sumOf { if (it.correct) it.question.score else 0 }
            val possibleScore = records.sumOf { it.question.score }
            SessionResult(
                title = "答题已结算",
                summary = "正确 $correctCount / ${records.size}，得分 $totalScore / $possibleScore",
                sessionId = runtime.sessionId,
                attempts = runtime.attempts,
                viewModel = viewModel,
                includeTimingInXml = includeTimingInXml,
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
                                Text("你的答案：${record.answer.displayAnswer()}", color = MaterialTheme.colorScheme.error)
                                Text(
                                    "正确答案：${record.question.displayCorrectAnswer()}",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (record.question.type == QuizQuestionType.MULTIPLE_CHOICE) {
                                    QuestionOptionDetails(
                                        options = record.question.options,
                                        correctAnswers = record.question.answers,
                                        selectedAnswers = record.selectedAnswers,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                record.question.explanation?.takeIf(String::isNotBlank)?.let { explanation ->
                                    Text("解析：$explanation", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val currentQuestion = queue[currentIndex]
            val existingRecord = records.firstOrNull { it.question.id == currentQuestion.id }
            fun saveAndAdvance(answer: QuizSessionAnswer) {
                    val attempt = answer.toPracticeAttempt(
                        sessionId = runtime.sessionId,
                        sequenceIndex = currentIndex,
                        previous = runtime.attempts.firstOrNull { it.sequenceIndex == currentIndex },
                        activeTimeMs = viewModel.snapshotQuestionTime(runtime.sessionId, currentIndex),
                    )
                    viewModel.stagePracticeAttempt(attempt)
                    val updatedAttempts = runtime.attempts
                        .filterNot { it.sequenceIndex == currentIndex }
                        .plus(attempt)
                        .sortedBy(PracticeAttempt::sequenceIndex)
                    val updated = updatedAttempts.mapNotNull { it.toQuizResultRecord(queue) }
                    if (currentIndex + 1 < queue.size) {
                        viewModel.setPracticeSessionIndex(currentIndex + 1)
                    } else {
                        settling = true
                        val complete = {
                            settling = false
                            viewModel.clearQuestionTimers(runtime.sessionId)
                            onSettled()
                        }
                        val failure = { settling = false }
                        if (wrongBookSession) viewModel.recordWrongQuestionSession(
                            answers = updated.map(QuizResultRecord::answer),
                            attempts = updatedAttempts,
                            onComplete = complete,
                            onFailure = failure,
                        ) else viewModel.recordQuizSession(
                            answers = updated.map(QuizResultRecord::answer),
                            attempts = updatedAttempts,
                            onComplete = complete,
                            onFailure = failure,
                        )
                    }
            }
            val returnToPrevious: () -> Unit = {
                viewModel.snapshotQuestionTime(runtime.sessionId, currentIndex)
                viewModel.setPracticeSessionIndex(currentIndex - 1)
            }
            when (currentQuestion.type) {
                QuizQuestionType.MULTIPLE_CHOICE -> ChoiceQuestion(
                    question = currentQuestion,
                    position = currentIndex + 1,
                    total = queue.size,
                    enabled = !settling,
                    showAnswer = showAnswers,
                    initialAnswer = existingRecord?.answer,
                    unifiedSettlement = unifiedSettlement,
                    timeLimitSeconds = timeLimitSeconds,
                    canGoPrevious = currentIndex > 0,
                    onPrevious = returnToPrevious,
                    onComplete = ::saveAndAdvance,
                )
                QuizQuestionType.FILL_BLANK -> FillBlankQuestion(
                    question = currentQuestion,
                    position = currentIndex + 1,
                    total = queue.size,
                    enabled = !settling,
                    showAnswer = showAnswers,
                    initialAnswer = existingRecord?.answer,
                    unifiedSettlement = unifiedSettlement,
                    timeLimitSeconds = timeLimitSeconds,
                    canGoPrevious = currentIndex > 0,
                    ignoreCase = ignoreFillBlankCase,
                    viewModel = viewModel,
                    onPrevious = returnToPrevious,
                    onComplete = ::saveAndAdvance,
                )
            }
            if (settling) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), strokeCap = StrokeCap.Round)
                Text("正在结算答题记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ChoiceQuestion(
    question: QuizQuestion,
    position: Int,
    total: Int,
    enabled: Boolean,
    showAnswer: Boolean,
    initialAnswer: QuizSessionAnswer?,
    unifiedSettlement: Boolean,
    timeLimitSeconds: Int?,
    canGoPrevious: Boolean,
    onPrevious: () -> Unit,
    onComplete: (QuizSessionAnswer) -> Unit,
) {
    var selectedAnswers by remember(question.id, initialAnswer) {
        mutableStateOf(initialAnswer?.selectedAnswers.orEmpty())
    }
    var checked by remember(question.id, initialAnswer, unifiedSettlement) {
        mutableStateOf(!unifiedSettlement && initialAnswer != null)
    }
    val multipleChoice = question.answers.size > 1
    val correct = selectedAnswers == question.answers
    var remainingSeconds by remember(question.id, timeLimitSeconds) {
        mutableIntStateOf(timeLimitSeconds ?: 0)
    }

    LaunchedEffect(question.id, timeLimitSeconds, checked) {
        if (timeLimitSeconds == null || checked || initialAnswer != null) return@LaunchedEffect
        while (!checked && remainingSeconds > 0) {
            delay(1_000)
            if (!checked) remainingSeconds -= 1
        }
        if (!checked && remainingSeconds == 0) checked = true
    }

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
                    onClick = onPrevious,
                    enabled = enabled && canGoPrevious,
                    shape = CircleShape,
                ) {
                    Icon(NvvIcons.ArrowLeft, null)
                    Text("上一题", Modifier.padding(start = 6.dp))
                }
                Button(
                    onClick = {
                        if (unifiedSettlement || checked) {
                            onComplete(QuizSessionAnswer(question, selectedAnswers, hintUsed = showAnswer))
                        } else {
                            checked = true
                        }
                    },
                    enabled = enabled && (selectedAnswers.isNotEmpty() || checked),
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
    runtime: PracticeSessionRuntime,
    viewModel: MainViewModel,
    showAnswers: Boolean,
    onSettled: () -> Unit,
    onExit: () -> Unit,
) {
    var settling by remember(request) { mutableStateOf(false) }
    val currentIndex = runtime.currentIndex.coerceIn(0, request.queue.lastIndex.coerceAtLeast(0))
    val records = runtime.attempts.mapNotNull { it.toContrastResult(request.queue) }
    val elapsedSeconds = (runtime.attempts.sumOf(PracticeAttempt::activeTimeMs) / 1_000L).toInt()

    LaunchedEffect(runtime.sessionId, currentIndex, runtime.finished) {
        if (!runtime.finished) viewModel.beginQuestionTiming(runtime.sessionId, currentIndex)
    }

    SessionBody {
        if (runtime.finished) {
            val correctCount = records.count(ContrastQuestionResult::correct)
            val accuracy = if (records.isEmpty()) 0 else correctCount * 100 / records.size
            SessionResult(
                title = "对照练习已结算",
                summary = "正确 $correctCount / ${records.size}，正确率 $accuracy%，用时 $elapsedSeconds 秒",
                sessionId = runtime.sessionId,
                attempts = runtime.attempts,
                viewModel = viewModel,
                includeTimingInXml = request.includeTimingInXml,
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
                onPrevious = { viewModel.setPracticeSessionIndex(currentIndex - 1) },
                onComplete = { selectedIndex ->
                    val question = request.queue[currentIndex]
                    val previous = runtime.attempts.firstOrNull { it.sequenceIndex == currentIndex }
                    val correct = selectedIndex == question.correctIndex
                    val attempt = PracticeAttempt(
                        id = previous?.id ?: UUID.randomUUID().toString(),
                        userId = previous?.userId,
                        sessionId = runtime.sessionId,
                        itemId = question.wordId,
                        mode = request.practiceType.toAttemptMode(),
                        sequenceIndex = currentIndex,
                        question = question.prompt,
                        options = question.options.mapIndexed { index, text ->
                            com.zlight106.nvvocab.data.QuizOption(('A'.code + index).toChar().toString(), text)
                        },
                        firstAnswer = previous?.firstAnswer ?: selectedIndex?.toString().orEmpty(),
                        finalAnswer = selectedIndex?.toString().orEmpty(),
                        referenceAnswer = question.correctIndex.toString(),
                        acceptedAnswers = setOf(question.correctIndex.toString()),
                        explanation = null,
                        correct = correct,
                        firstAnswerCorrect = previous?.firstAnswerCorrect ?: correct,
                        activeTimeMs = viewModel.snapshotQuestionTime(runtime.sessionId, currentIndex),
                hintUsed = request.hintEnabled || showAnswers || previous?.hintUsed == true,
                        timestamp = System.currentTimeMillis(),
                        dirty = true,
                    )
                    viewModel.stagePracticeAttempt(attempt)
                    val updatedAttempts = runtime.attempts
                        .filterNot { it.sequenceIndex == currentIndex }
                        .plus(attempt)
                        .sortedBy(PracticeAttempt::sequenceIndex)
                    val updated = updatedAttempts.mapNotNull { it.toContrastResult(request.queue) }
                    if (currentIndex + 1 < request.queue.size) {
                        viewModel.setPracticeSessionIndex(currentIndex + 1)
                    } else {
                        settling = true
                        val totalSeconds = (updatedAttempts.sumOf(PracticeAttempt::activeTimeMs) / 1_000L).toInt()
                        viewModel.recordContrastPracticeSession(
                            ContrastPracticeSession(
                                id = UUID.randomUUID().toString(),
                                completedAt = System.currentTimeMillis(),
                                practiceType = request.practiceType,
                                difficulty = request.difficulty,
                                questionCount = request.queue.size,
                                correctCount = updated.count(ContrastQuestionResult::correct),
                                elapsedSeconds = totalSeconds,
                                hintEnabled = request.hintEnabled,
                            ),
                            results = updated,
                            attempts = updatedAttempts,
                            onComplete = {
                                settling = false
                                viewModel.clearQuestionTimers(runtime.sessionId)
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
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedIndex by remember(question.id, initialSelectedIndex) {
        mutableStateOf(initialSelectedIndex)
    }
    var checked by remember(question.id) { mutableStateOf(false) }
    var remainingSeconds by remember(question.id) { mutableIntStateOf(timeLimitSeconds) }

    LaunchedEffect(question.id, checked) {
        while (!checked && remainingSeconds > 0) {
            delay(1_000)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                remainingSeconds -= 1
            }
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

private fun ContrastPracticeType.toAttemptMode(): PracticeAttemptMode = when (this) {
    ContrastPracticeType.CHINESE_TO_ENGLISH -> PracticeAttemptMode.CHINESE_TO_ENGLISH
    ContrastPracticeType.ENGLISH_TO_CHINESE -> PracticeAttemptMode.ENGLISH_TO_CHINESE
    ContrastPracticeType.ENGLISH_DEFINITION_TO_ENGLISH ->
        PracticeAttemptMode.ENGLISH_DEFINITION_TO_ENGLISH
}

private fun PracticeAttempt.toContrastResult(queue: List<ContrastQuestion>): ContrastQuestionResult? {
    val source = queue.getOrNull(sequenceIndex)?.takeIf { it.wordId == itemId }
        ?: queue.firstOrNull { it.wordId == itemId }
        ?: return null
    return ContrastQuestionResult(source, finalAnswer.toIntOrNull())
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

private data class WordAnswerOutcome(
    val answer: String,
    val correct: Boolean,
    val hinted: Boolean,
)

@Composable
private fun FillBlankQuestion(
    question: QuizQuestion,
    position: Int,
    total: Int,
    enabled: Boolean,
    showAnswer: Boolean,
    initialAnswer: QuizSessionAnswer?,
    unifiedSettlement: Boolean,
    timeLimitSeconds: Int?,
    canGoPrevious: Boolean,
    ignoreCase: Boolean,
    viewModel: MainViewModel,
    onPrevious: () -> Unit,
    onComplete: (QuizSessionAnswer) -> Unit,
) {
    var userAnswer by remember(question.id, initialAnswer) { mutableStateOf(initialAnswer?.userAnswer.orEmpty()) }
    var evaluation by remember(question.id, initialAnswer) { mutableStateOf(initialAnswer?.evaluation) }
    var hintUsed by remember(question.id, initialAnswer) { mutableStateOf(initialAnswer?.hintUsed == true) }
    var evaluating by remember(question.id) { mutableStateOf(false) }
    var checked by remember(question.id, initialAnswer, unifiedSettlement) {
        mutableStateOf(!unifiedSettlement && initialAnswer?.evaluation != null)
    }
    var hintText by remember(question.id) { mutableStateOf<String?>(null) }
    val focusRequester = remember(question.id) { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var remainingSeconds by remember(question.id, timeLimitSeconds) {
        mutableIntStateOf(timeLimitSeconds ?: 0)
    }

    fun evaluate(complete: (QuizSessionAnswer) -> Unit) {
        if (userAnswer.isBlank() || evaluating) return
        evaluating = true
        keyboard?.hide()
        viewModel.evaluateFillBlankAnswer(question, userAnswer, ignoreCase) { result ->
            evaluating = false
            evaluation = result
            complete(
                QuizSessionAnswer(
                    question = question,
                    selectedAnswers = emptySet(),
                    userAnswer = userAnswer,
                    hintUsed = hintUsed || showAnswer,
                    evaluation = result,
                ),
            )
        }
    }

    fun submit() {
        if (evaluation != null && (checked || unifiedSettlement)) {
            onComplete(QuizSessionAnswer(question, emptySet(), userAnswer, hintUsed || showAnswer, evaluation))
        } else {
            evaluate { answer ->
                if (unifiedSettlement) onComplete(answer) else checked = true
            }
        }
    }

    LaunchedEffect(question.id) { focusRequester.requestFocus() }
    LaunchedEffect(question.id, timeLimitSeconds, checked) {
        if (timeLimitSeconds == null || checked || initialAnswer != null) return@LaunchedEffect
        while (!checked && remainingSeconds > 0) {
            delay(1_000)
            if (!checked) remainingSeconds -= 1
        }
        if (!checked && remainingSeconds == 0) {
            evaluation = com.zlight106.nvvocab.data.FillBlankEvaluation(
                result = AnswerEvaluationResult.INCORRECT,
                reason = "单题限时已结束",
                confidence = 1.0,
                evaluatedByAi = false,
            )
            checked = true
        }
    }

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
                            "答案：${question.displayCorrectAnswer()}",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text("${question.score} 分", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(question.text, style = MaterialTheme.typography.titleLarge)
            Text("填空题", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (timeLimitSeconds != null) {
                Text("剩余 ${remainingSeconds} 秒", color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinearProgressIndicator(
                    progress = { remainingSeconds.toFloat() / timeLimitSeconds.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth(),
                    strokeCap = StrokeCap.Round,
                )
            }
            OutlinedTextField(
                value = userAnswer,
                onValueChange = {
                    if (!checked && !evaluating) {
                        userAnswer = it
                        evaluation = null
                    }
                },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                label = { Text("填写答案") },
                enabled = enabled && !checked && !evaluating,
                singleLine = false,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                supportingText = {
                    Text(if (ignoreCase) "判定时忽略英文字母大小写" else "判定时区分英文字母大小写")
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        hintUsed = true
                        hintText = question.referenceAnswer?.firstOrNull()?.let { "首字符：$it" } ?: "暂无首字符提示"
                    },
                    enabled = enabled,
                    shape = CircleShape,
                ) { Text("首字符") }
                OutlinedButton(
                    onClick = {
                        hintUsed = true
                        hintText = question.referenceAnswer?.let { "字符数：${it.length}" } ?: "暂无字符数提示"
                    },
                    enabled = enabled,
                    shape = CircleShape,
                ) { Text("字符数") }
                OutlinedButton(
                    onClick = {
                        hintUsed = true
                        hintText = question.explanation?.takeIf(String::isNotBlank) ?: "暂无题目解析"
                    },
                    enabled = enabled,
                    shape = CircleShape,
                ) { Text("查看提示") }
            }
            hintText?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (checked) {
                val result = evaluation?.result
                Text(
                    when (result) {
                        AnswerEvaluationResult.CORRECT -> if (hintUsed) "回答正确，已使用提示" else "回答正确"
                        AnswerEvaluationResult.INCORRECT -> "回答错误，参考答案：${question.displayCorrectAnswer()}"
                        AnswerEvaluationResult.REVIEW -> "答案需要人工复核，参考答案：${question.displayCorrectAnswer()}"
                        null -> "正在判定"
                    },
                    color = if (result == AnswerEvaluationResult.CORRECT) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            if (evaluating) LinearProgressIndicator(Modifier.fillMaxWidth(), strokeCap = StrokeCap.Round)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onPrevious,
                    enabled = enabled && canGoPrevious && !evaluating,
                    shape = CircleShape,
                ) {
                    Icon(NvvIcons.ArrowLeft, null)
                    Text("上一题", Modifier.padding(start = 6.dp))
                }
                Button(
                    onClick = ::submit,
                    enabled = enabled && (userAnswer.isNotBlank() || checked) && !evaluating,
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

private fun QuizSessionAnswer.displayAnswer(): String = when (question.type) {
    QuizQuestionType.MULTIPLE_CHOICE -> formatOptionAnswers(question.options, selectedAnswers)
    QuizQuestionType.FILL_BLANK -> userAnswer.orEmpty().ifBlank { "未作答" }
}

private fun QuizQuestion.displayCorrectAnswer(): String = when (type) {
    QuizQuestionType.MULTIPLE_CHOICE -> formatOptionAnswers(options, answers)
    QuizQuestionType.FILL_BLANK -> referenceAnswer.orEmpty().ifBlank { acceptedAnswers.firstOrNull().orEmpty() }
}

private data class MixedAnswerRecord(
    val item: MixedReviewItem,
    val wordQuality: Int? = null,
    val selectedIndex: Int? = null,
)

@Composable
private fun MixedSession(
    request: PracticeSessionRequest.Mixed,
    runtime: PracticeSessionRuntime,
    viewModel: MainViewModel,
    showAnswers: Boolean,
    onSettled: () -> Unit,
    onExit: () -> Unit,
) {
    var settling by remember(request) { mutableStateOf(false) }
    val currentIndex = runtime.currentIndex.coerceIn(0, request.queue.lastIndex.coerceAtLeast(0))
    val records = runtime.attempts.mapNotNull { it.toMixedAnswerRecord(request.queue) }

    LaunchedEffect(runtime.sessionId, currentIndex, runtime.finished) {
        if (!runtime.finished) viewModel.beginQuestionTiming(runtime.sessionId, currentIndex)
    }

    fun completeCurrent(attempt: PracticeAttempt) {
        viewModel.stagePracticeAttempt(attempt)
        val updatedAttempts = runtime.attempts
            .filterNot { it.sequenceIndex == currentIndex }
            .plus(attempt)
            .sortedBy(PracticeAttempt::sequenceIndex)
        val updated = updatedAttempts.mapNotNull { it.toMixedAnswerRecord(request.queue) }
        if (currentIndex + 1 < request.queue.size) {
            viewModel.setPracticeSessionIndex(currentIndex + 1)
            return
        }

        settling = true
        val wordResults = updated.mapNotNull { answer ->
            answer.wordQuality?.let { quality -> WordReviewResult(requireNotNull(answer.item.word), quality) }
        }
        val contrastResults = updated.asSequence()
            .filter { it.item.mode != MixedReviewMode.DICTATION }
            .groupBy(
                keySelector = { it.item.mode },
                valueTransform = { answer ->
                    ContrastQuestionResult(
                        question = requireNotNull(answer.item.contrastQuestion),
                        selectedIndex = answer.selectedIndex,
                    )
                },
            )
        viewModel.recordMixedReviewSession(
            wordResults = wordResults,
            contrastResults = contrastResults,
            difficulty = request.difficulty,
            elapsedSeconds = (updatedAttempts.sumOf(PracticeAttempt::activeTimeMs) / 1_000L).toInt(),
            attempts = updatedAttempts,
            onComplete = {
                settling = false
                viewModel.clearQuestionTimers(runtime.sessionId)
                onSettled()
            },
            onFailure = { settling = false },
        )
    }

    SessionBody {
        if (runtime.finished) {
            SessionResult(
                title = "混合复习已结算",
                summary = "已完成 ${request.queue.size} 个不同单词",
                sessionId = runtime.sessionId,
                attempts = runtime.attempts,
                viewModel = viewModel,
                onExit = onExit,
            )
        } else {
            val item = request.queue[currentIndex]
            Text(
                item.mode.sessionLabel(),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
            if (item.mode == MixedReviewMode.DICTATION) {
                val word = requireNotNull(item.word)
                WordQuestion(
                    mode = DictationMode.REVIEW,
                    word = word,
                    position = currentIndex + 1,
                    total = request.queue.size,
                    enabled = !settling,
                    canGoPrevious = currentIndex > 0,
                    onPrevious = { viewModel.setPracticeSessionIndex(currentIndex - 1) },
                    onComplete = { outcome ->
                        val previous = runtime.attempts.firstOrNull { it.sequenceIndex == currentIndex }
                        completeCurrent(
                            PracticeAttempt(
                                id = previous?.id ?: UUID.randomUUID().toString(),
                                userId = previous?.userId,
                                sessionId = runtime.sessionId,
                                itemId = word.id,
                                sourceId = word.bookTag,
                                mode = PracticeAttemptMode.WORD_DICTATION,
                                sequenceIndex = currentIndex,
                                question = word.translation,
                                options = emptyList(),
                                firstAnswer = previous?.firstAnswer ?: outcome.answer,
                                finalAnswer = outcome.answer,
                                referenceAnswer = word.spelling,
                                acceptedAnswers = setOf(word.spelling),
                                explanation = word.phonetic,
                                correct = outcome.correct,
                                firstAnswerCorrect = previous?.firstAnswerCorrect ?: outcome.correct,
                                activeTimeMs = viewModel.snapshotQuestionTime(runtime.sessionId, currentIndex),
                                hintUsed = outcome.hinted || previous?.hintUsed == true,
                                timestamp = System.currentTimeMillis(),
                                dirty = true,
                            ),
                        )
                    },
                )
            } else {
                val question = requireNotNull(item.contrastQuestion)
                ContrastQuestion(
                    question = question,
                    position = currentIndex + 1,
                    total = request.queue.size,
                    timeLimitSeconds = request.timeLimitSeconds,
                    hintEnabled = false,
                    showAnswer = showAnswers,
                    enabled = !settling,
                    initialSelectedIndex = records.firstOrNull { it.item.itemId == item.itemId }?.selectedIndex,
                    canGoPrevious = currentIndex > 0,
                    onPrevious = { viewModel.setPracticeSessionIndex(currentIndex - 1) },
                    onComplete = { selectedIndex ->
                        val previous = runtime.attempts.firstOrNull { it.sequenceIndex == currentIndex }
                        val correct = selectedIndex == question.correctIndex
                        completeCurrent(
                            PracticeAttempt(
                                id = previous?.id ?: UUID.randomUUID().toString(),
                                userId = previous?.userId,
                                sessionId = runtime.sessionId,
                                itemId = item.itemId,
                                sourceId = item.sourceId,
                                mode = item.mode.toAttemptMode(),
                                sequenceIndex = currentIndex,
                                question = question.prompt,
                                options = question.options.mapIndexed { index, text ->
                                    com.zlight106.nvvocab.data.QuizOption(
                                        ('A'.code + index).toChar().toString(),
                                        text,
                                    )
                                },
                                firstAnswer = previous?.firstAnswer ?: selectedIndex?.toString().orEmpty(),
                                finalAnswer = selectedIndex?.toString().orEmpty(),
                                referenceAnswer = question.correctIndex.toString(),
                                acceptedAnswers = setOf(question.correctIndex.toString()),
                                explanation = null,
                                correct = correct,
                                firstAnswerCorrect = previous?.firstAnswerCorrect ?: correct,
                                activeTimeMs = viewModel.snapshotQuestionTime(runtime.sessionId, currentIndex),
                                hintUsed = showAnswers || previous?.hintUsed == true,
                                timestamp = System.currentTimeMillis(),
                                dirty = true,
                            ),
                        )
                    },
                )
            }
            if (settling) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), strokeCap = StrokeCap.Round)
                Text("正在结算混合复习", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun MixedReviewMode.sessionLabel(): String = when (this) {
    MixedReviewMode.DICTATION -> "当前模式：默写"
    MixedReviewMode.CHINESE_TO_ENGLISH -> "当前模式：中翻英"
    MixedReviewMode.ENGLISH_TO_CHINESE -> "当前模式：英翻中"
    MixedReviewMode.ENGLISH_DEFINITION_TO_ENGLISH -> "当前模式：语义压缩"
}

private fun MixedReviewMode.toAttemptMode(): PracticeAttemptMode = when (this) {
    MixedReviewMode.DICTATION -> PracticeAttemptMode.WORD_DICTATION
    MixedReviewMode.CHINESE_TO_ENGLISH -> PracticeAttemptMode.CHINESE_TO_ENGLISH
    MixedReviewMode.ENGLISH_TO_CHINESE -> PracticeAttemptMode.ENGLISH_TO_CHINESE
    MixedReviewMode.ENGLISH_DEFINITION_TO_ENGLISH ->
        PracticeAttemptMode.ENGLISH_DEFINITION_TO_ENGLISH
}

private fun PracticeAttempt.toMixedAnswerRecord(queue: List<MixedReviewItem>): MixedAnswerRecord? {
    val item = queue.getOrNull(sequenceIndex)?.takeIf { it.itemId == itemId }
        ?: queue.firstOrNull { it.itemId == itemId }
        ?: return null
    return if (item.mode == MixedReviewMode.DICTATION) {
        MixedAnswerRecord(
            item = item,
            wordQuality = when {
                !correct -> 0
                hintUsed -> 3
                else -> 5
            },
        )
    } else {
        MixedAnswerRecord(item = item, selectedIndex = finalAnswer.toIntOrNull())
    }
}

@Composable
private fun SessionResult(
    title: String,
    summary: String,
    sessionId: String,
    attempts: List<PracticeAttempt>,
    viewModel: MainViewModel,
    includeTimingInXml: Boolean = true,
    onExit: () -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val wrongExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/xml"),
    ) { uri ->
        uri?.let { viewModel.exportWrongAttemptSession(sessionId, attempts, it, includeTimingInXml) }
    }
    val telemetryExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/xml"),
    ) { uri ->
        uri?.let { viewModel.exportSessionTelemetry(sessionId, attempts, it, includeTimingInXml) }
    }
    SectionCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(NvvIcons.Check, null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
            AttemptTimeDistribution(attempts)
            OutlinedButton(
                onClick = { wrongExportLauncher.launch("nvvocab-wrong-$sessionId.xml") },
                enabled = attempts.any { !it.correct },
                shape = CircleShape,
            ) {
                Icon(NvvIcons.Download, null)
                Text("导出本次错题 XML", Modifier.padding(start = 8.dp))
            }
            OutlinedButton(
                onClick = { telemetryExportLauncher.launch("nvvocab-telemetry-$sessionId.xml") },
                enabled = attempts.isNotEmpty(),
                shape = CircleShape,
            ) {
                Icon(NvvIcons.Download, null)
                Text("导出遥测数据", Modifier.padding(start = 8.dp))
            }
            Button(onClick = onExit, shape = CircleShape) {
                Text("返回沉浸复习")
            }
        }
    }
}

private enum class AttemptTimeSort {
    ANSWER_ORDER,
    DURATION_DESC,
}

@Composable
private fun AttemptTimeDistribution(attempts: List<PracticeAttempt>) {
    if (attempts.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
        ) {
            Text("答题用时分布", modifier = Modifier.weight(1f))
            Icon(NvvIcons.ChevronDown, if (expanded) "折叠" else "展开")
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            AttemptTimeDistributionContent(attempts)
        }
    }
}

@Composable
private fun AttemptTimeDistributionContent(attempts: List<PracticeAttempt>) {
    if (attempts.isEmpty()) return
    var sort by remember { mutableStateOf(AttemptTimeSort.ANSWER_ORDER) }
    val ordered = when (sort) {
        AttemptTimeSort.ANSWER_ORDER -> attempts.sortedBy(PracticeAttempt::sequenceIndex)
        AttemptTimeSort.DURATION_DESC -> attempts.sortedByDescending(PracticeAttempt::activeTimeMs)
    }
    val maximum = attempts.maxOfOrNull(PracticeAttempt::activeTimeMs)?.coerceAtLeast(1L) ?: 1L
    val summaries = AttemptAnalytics.modeTimeSummaries(attempts)
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("答题用时分布", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AttemptSortButton(
                text = "按答题顺序",
                selected = sort == AttemptTimeSort.ANSWER_ORDER,
                onClick = { sort = AttemptTimeSort.ANSWER_ORDER },
            )
            AttemptSortButton(
                text = "按耗时降序",
                selected = sort == AttemptTimeSort.DURATION_DESC,
                onClick = { sort = AttemptTimeSort.DURATION_DESC },
            )
        }
        ordered.forEach { attempt ->
            val fraction = (attempt.activeTimeMs.toFloat() / maximum).coerceIn(0.025f, 1f)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${attempt.sequenceIndex + 1}. ${attempt.question.compactLabel()}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!attempt.correct) Text("错误", color = MaterialTheme.colorScheme.error)
                        if (attempt.hintUsed) Text("提示", color = MaterialTheme.colorScheme.tertiary)
                        Text(formatSeconds(attempt.activeTimeMs))
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().height(12.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row {
                        Surface(
                            modifier = Modifier.fillMaxWidth(fraction).height(12.dp),
                            shape = CircleShape,
                            color = when {
                                !attempt.correct -> MaterialTheme.colorScheme.error
                                attempt.hintUsed -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.primary
                            },
                        ) {}
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("0 秒", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                formatSeconds(maximum),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("各模式用时", style = MaterialTheme.typography.titleMedium)
        summaries.forEach { summary -> AttemptModeSummaryRow(summary) }
    }
}

@Composable
private fun AttemptSortButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(text, Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

@Composable
private fun AttemptModeSummaryRow(summary: AttemptModeTimeSummary) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(summary.mode.displayName(), fontWeight = FontWeight.SemiBold)
            Text(
                "共 ${summary.attemptCount} 题，总用时 ${formatSeconds(summary.totalTimeMs)}，" +
                    "平均 ${formatSeconds(summary.averageTimeMs)}，中位 ${formatSeconds(summary.medianTimeMs)}，" +
                    "占比 ${(summary.timeShare * 100).toInt()}%",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun PracticeAttemptMode.displayName(): String = when (this) {
    PracticeAttemptMode.WORD_DICTATION -> "默写"
    PracticeAttemptMode.WORD_SPELLING -> "拼写练习"
    PracticeAttemptMode.QUIZ_CHOICE -> "选择题"
    PracticeAttemptMode.QUIZ_FILL_BLANK -> "填空题"
    PracticeAttemptMode.CHINESE_TO_ENGLISH -> "中翻英"
    PracticeAttemptMode.ENGLISH_TO_CHINESE -> "英翻中"
    PracticeAttemptMode.ENGLISH_DEFINITION_TO_ENGLISH -> "语义压缩"
}

private fun String.compactLabel(): String = replace(Regex("\\s+"), " ").trim().let {
    if (it.length <= 18) it else it.take(18) + "…"
}

private fun formatSeconds(milliseconds: Long): String =
    String.format(java.util.Locale.getDefault(), "%.1f 秒", milliseconds.coerceAtLeast(0L) / 1_000.0)

private fun WrongQuestionEntry.toQuizQuestion(): QuizQuestion = QuizQuestion(
    id = questionKey,
    bankId = bankId ?: "wrong-book",
    originalIndex = 0,
    score = 10,
    text = questionText,
    options = options,
    answers = correctAnswers,
    type = questionType,
    referenceAnswer = referenceAnswer,
    acceptedAnswers = acceptedAnswers,
    explanation = explanation,
    category = category,
    sourceReference = sourceReference,
)
