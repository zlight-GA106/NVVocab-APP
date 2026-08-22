package com.zlight106.nvvocab.ui

import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zlight106.nvvocab.NvvocabApplication
import com.zlight106.nvvocab.data.AuthSession
import com.zlight106.nvvocab.data.AiSettings
import com.zlight106.nvvocab.data.ContrastPracticeSession
import com.zlight106.nvvocab.data.ContrastPracticePreset
import com.zlight106.nvvocab.data.ContrastPracticePresets
import com.zlight106.nvvocab.data.ContrastPracticeType
import com.zlight106.nvvocab.data.ContrastQuestion
import com.zlight106.nvvocab.data.ContrastQuestionResult
import com.zlight106.nvvocab.data.ContrastReviewPreferences
import com.zlight106.nvvocab.data.DailyProgressReference
import com.zlight106.nvvocab.data.DailyProgressSettings
import com.zlight106.nvvocab.data.DailyMemoSettings
import com.zlight106.nvvocab.data.DictationMode
import com.zlight106.nvvocab.data.FillBlankEvaluation
import com.zlight106.nvvocab.data.MixedReviewItem
import com.zlight106.nvvocab.data.MixedReviewMode
import com.zlight106.nvvocab.data.MixedReviewPreferences
import com.zlight106.nvvocab.data.ParaphraseSeed
import com.zlight106.nvvocab.data.PracticeDifficulty
import com.zlight106.nvvocab.data.PracticeAttempt
import com.zlight106.nvvocab.data.PracticeSessionRuntime
import com.zlight106.nvvocab.data.QueueSort
import com.zlight106.nvvocab.data.QuizAttempt
import com.zlight106.nvvocab.data.QuizQuestion
import com.zlight106.nvvocab.data.QuizReviewPreferences
import com.zlight106.nvvocab.data.ReminderSettings
import com.zlight106.nvvocab.data.ReviewCategory
import com.zlight106.nvvocab.data.SupabaseConfig
import com.zlight106.nvvocab.data.SyncMode
import com.zlight106.nvvocab.data.SyncSettings
import com.zlight106.nvvocab.data.ThemeMode
import com.zlight106.nvvocab.data.WordEntry
import com.zlight106.nvvocab.data.WrongQuestionEntry
import com.zlight106.nvvocab.data.WordReviewPreferences
import com.zlight106.nvvocab.data.WordReviewResult
import com.zlight106.nvvocab.ui.screens.PracticeSessionRequest
import com.zlight106.nvvocab.data.repository.VocabularyRepository
import com.zlight106.nvvocab.domain.WordTextParser
import com.zlight106.nvvocab.domain.AttemptAnalytics
import com.zlight106.nvvocab.domain.ParaphrasePracticeGenerator
import com.zlight106.nvvocab.domain.ParaphraseSeedBatchParser
import com.zlight106.nvvocab.sync.SyncScheduler
import com.zlight106.nvvocab.sync.SyncRuntimeStatus
import com.zlight106.nvvocab.sync.SyncStateMonitor
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppUiState(
    val administratorMode: Boolean,
    val aiSettings: AiSettings,
    val aiTesting: Boolean = false,
    val automaticSync: Boolean,
    val contrastPracticePresets: ContrastPracticePresets,
    val contrastReviewPreferences: ContrastReviewPreferences,
    val dailyProgressSettings: DailyProgressSettings,
    val dailyMemoSettings: DailyMemoSettings,
    val dailyReviewTarget: Int,
    val dynamicColor: Boolean,
    val message: String? = null,
    val mixedReviewPreferences: MixedReviewPreferences,
    val reminderSettings: ReminderSettings,
    val reviewCategory: ReviewCategory,
    val quizReviewPreferences: QuizReviewPreferences,
    val session: AuthSession?,
    val supabaseConfig: SupabaseConfig,
    val syncSettings: SyncSettings,
    val syncing: Boolean = false,
    val themeMode: ThemeMode,
    val themePresetId: String?,
    val wordReviewPreferences: WordReviewPreferences,
)

class MainViewModel(private val application: NvvocabApplication) : ViewModel() {
    private val preferences = application.preferences
    private val repository: VocabularyRepository = application.repository
    private val mutableUiState = MutableStateFlow(readUiState())
    private val mutableContrastGenerationProgress = MutableStateFlow(0f)
    private val mutableMixedGenerationProgress = MutableStateFlow(0f)
    private val mutableActivePracticeSession = MutableStateFlow<PracticeSessionRequest?>(null)
    private val mutableActivePracticeSessionId = MutableStateFlow<String?>(null)
    private val mutablePracticeSessionRuntime = MutableStateFlow<PracticeSessionRuntime?>(null)
    private val mutableAnalyzingWrongQuestionId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AppUiState> = mutableUiState.asStateFlow()
    val words = repository.words
    val bookTags = repository.bookTags
    val reviewLogs = repository.reviewLogs
    val quizBanks = repository.quizBanks
    val contrastPracticeSessions = repository.contrastPracticeSessions
    val dailyPracticeProgress = repository.dailyPracticeProgress
    val studyTimeProgress = application.studyTimeTracker.progress
    val contrastGenerationProgress: StateFlow<Float> = mutableContrastGenerationProgress.asStateFlow()
    val mixedGenerationProgress: StateFlow<Float> = mutableMixedGenerationProgress.asStateFlow()
    val localDataLoaded = repository.localDataLoaded
    val wrongQuestions = repository.wrongQuestions
    val activePracticeSession: StateFlow<PracticeSessionRequest?> = mutableActivePracticeSession.asStateFlow()
    val activePracticeSessionId: StateFlow<String?> = mutableActivePracticeSessionId.asStateFlow()
    val practiceSessionRuntime: StateFlow<PracticeSessionRuntime?> = mutablePracticeSessionRuntime.asStateFlow()
    val analyzingWrongQuestionId: StateFlow<String?> = mutableAnalyzingWrongQuestionId.asStateFlow()
    val practiceAttempts = repository.practiceAttempts
    val paraphraseSeeds = repository.paraphraseSeeds
    val maturitySnapshots = repository.practiceAttempts
        .map(AttemptAnalytics::maturitySnapshots)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    private val timerLock = Any()
    private val accumulatedQuestionTime = mutableMapOf<String, Long>()
    private var activeQuestionKey: String? = null
    private var activeQuestionStartedAt: Long? = null
    private var practiceLifecycleActive = false

    init {
        viewModelScope.launch { repository.refreshLocal() }
    }

    fun importText(text: String, bookTag: String, onComplete: (Int) -> Unit) {
        val parsed = WordTextParser.parse(text)
        viewModelScope.launch {
            runCatching { repository.importWords(parsed, bookTag) }
                .onSuccess {
                    showMessage("已导入 $it 个单词")
                    onComplete(it)
                }
                .onFailure { showMessage(it.message ?: "导入失败") }
        }
    }

    fun updateWordTag(wordId: String, bookTag: String) {
        viewModelScope.launch {
            runCatching { repository.updateWordTag(wordId, bookTag) }
                .onSuccess { showMessage("单词分类已更新") }
                .onFailure { showMessage(it.message ?: "分类更新失败") }
        }
    }

    fun buildQueue(
        mode: DictationMode,
        bookTag: String?,
        sort: QueueSort,
        limit: Int?,
    ): List<WordEntry> = repository.buildQueue(mode, bookTag, sort, limit)

    fun recordReview(word: WordEntry, quality: Int, onComplete: () -> Unit) {
        viewModelScope.launch {
            runCatching { repository.recordReview(word, quality) }
                .onSuccess { onComplete() }
                .onFailure { showMessage(it.message ?: "本地记录失败") }
        }
    }

    fun recordReviewSession(
        results: List<com.zlight106.nvvocab.data.WordReviewResult>,
        attempts: List<PracticeAttempt> = emptyList(),
        onComplete: () -> Unit,
        onFailure: () -> Unit = {},
    ) {
        viewModelScope.launch {
            runCatching {
                repository.recordReviewSession(results)
                repository.recordPracticeAttempts(attempts)
            }
                .onSuccess { onComplete() }
                .onFailure {
                    onFailure()
                    showMessage(it.message ?: "本轮复习结算失败")
                }
        }
    }

    fun importQuizXml(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val resolver = application.contentResolver
                val displayName = resolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: "本地题库.xml"
                resolver.openInputStream(uri)?.use { stream ->
                    repository.importQuizBank(displayName, stream)
                } ?: error("无法读取所选 XML 文件。")
            }.onSuccess { bank ->
                showMessage("已导入 ${bank.name}，共 ${bank.questionCount} 题")
            }.onFailure {
                showMessage(it.message ?: "题库导入失败")
            }
        }
    }

    fun loadQuizQuestions(bankId: String, onComplete: (List<QuizQuestion>) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.getQuizQuestions(bankId) }
                .onSuccess(onComplete)
                .onFailure { showMessage(it.message ?: "题库读取失败") }
        }
    }

    fun recordQuizAnswer(question: QuizQuestion, selectedAnswers: Set<String>) {
        val correct = selectedAnswers == question.answers
        viewModelScope.launch {
            runCatching {
                repository.recordQuizAttempt(
                    QuizAttempt(
                        id = UUID.randomUUID().toString(),
                        bankId = question.bankId,
                        questionId = question.id,
                        answeredAt = System.currentTimeMillis(),
                        selectedAnswers = selectedAnswers,
                        correct = correct,
                        scoreGained = if (correct) question.score else 0,
                    ),
                )
            }.onFailure { showMessage(it.message ?: "答题记录保存失败") }
        }
    }

    fun recordQuizSession(
        answers: List<com.zlight106.nvvocab.data.QuizSessionAnswer>,
        attempts: List<PracticeAttempt> = emptyList(),
        onComplete: () -> Unit,
        onFailure: () -> Unit = {},
    ) {
        viewModelScope.launch {
            runCatching {
                repository.recordQuizSession(answers)
                repository.recordPracticeAttempts(attempts)
            }
                .onSuccess { onComplete() }
                .onFailure {
                    onFailure()
                    showMessage(it.message ?: "本轮答题结算失败")
                }
        }
    }

    fun evaluateFillBlankAnswer(
        question: QuizQuestion,
        userAnswer: String,
        ignoreCase: Boolean,
        onComplete: (FillBlankEvaluation) -> Unit,
    ) {
        viewModelScope.launch {
            val evaluation = repository.evaluateFillBlankAnswer(question, userAnswer, ignoreCase)
            onComplete(evaluation)
        }
    }

    fun saveParaphraseSeed(
        id: String?,
        sourceText: String,
        targetText: String,
        contextText: String?,
        sourceReference: String?,
        notes: String?,
        onComplete: () -> Unit = {},
    ) {
        viewModelScope.launch {
            runCatching {
                repository.saveParaphraseSeed(
                    id = id,
                    sourceText = sourceText,
                    targetText = targetText,
                    contextText = contextText,
                    sourceReference = sourceReference,
                    notes = notes,
                )
            }.onSuccess {
                showMessage("语义压缩种子已保存")
                onComplete()
            }.onFailure { showMessage(it.message ?: "语义压缩种子保存失败") }
        }
    }

    fun importParaphraseSeedText(text: String, onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                val entries = ParaphraseSeedBatchParser.parse(text, uiState.value.session?.userId)
                require(entries.isNotEmpty()) {
                    "未解析到有效种子，请使用 原表达 => 等效表达 | 上下文 | 来源 | 备注"
                }
                repository.importParaphraseSeeds(entries)
            }.onSuccess { count ->
                showMessage("已导入 $count 条语义压缩种子")
                onComplete(count)
            }.onFailure { showMessage(it.message ?: "语义压缩种子导入失败") }
        }
    }

    fun deleteParaphraseSeed(id: String) {
        viewModelScope.launch {
            runCatching { repository.deleteParaphraseSeed(id) }
                .onSuccess { showMessage("语义压缩种子已删除") }
                .onFailure { showMessage(it.message ?: "语义压缩种子删除失败") }
        }
    }

    fun exportQuizBank(bankId: String, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                application.contentResolver.openOutputStream(uri, "w")?.use { output ->
                    repository.exportQuizBank(bankId, output)
                } ?: error("无法写入所选文件。")
            }.onSuccess {
                showMessage("题库 XML 已导出")
            }.onFailure {
                showMessage(it.message ?: "题库导出失败")
            }
        }
    }

    fun recordWrongQuestionSession(
        answers: List<com.zlight106.nvvocab.data.QuizSessionAnswer>,
        attempts: List<PracticeAttempt> = emptyList(),
        onComplete: () -> Unit,
        onFailure: () -> Unit = {},
    ) {
        viewModelScope.launch {
            runCatching {
                repository.recordWrongQuestionSession(answers)
                repository.recordPracticeAttempts(attempts)
            }
                .onSuccess { onComplete() }
                .onFailure {
                    onFailure()
                    showMessage(it.message ?: "错题复习结算失败")
                }
        }
    }

    fun deleteQuizBank(bankId: String) {
        viewModelScope.launch {
            runCatching { repository.deleteQuizBank(bankId) }
                .onSuccess { showMessage("题库已删除") }
                .onFailure { showMessage(it.message ?: "题库删除失败") }
        }
    }

    fun renameQuizBank(bankId: String, name: String) {
        viewModelScope.launch {
            runCatching { repository.renameQuizBank(bankId, name) }
                .onSuccess { showMessage("题库名称已更新") }
                .onFailure { showMessage(it.message ?: "题库重命名失败") }
        }
    }

    fun generateContrastQuestions(
        targets: List<WordEntry>,
        distractorPool: List<WordEntry>,
        type: ContrastPracticeType,
        optionCount: Int,
        difficulty: PracticeDifficulty,
        onComplete: (Result<List<ContrastQuestion>>) -> Unit,
    ) {
        mutableContrastGenerationProgress.value = 0f
        viewModelScope.launch {
            val result = runCatching {
                repository.generateContrastQuestions(
                    targets = targets,
                    distractorPool = distractorPool,
                    type = type,
                    optionCount = optionCount,
                    difficulty = difficulty,
                    onProgress = { progress -> mutableContrastGenerationProgress.value = progress },
                )
            }
            result.exceptionOrNull()?.let { showMessage(it.message ?: "对照题生成失败") }
            onComplete(result)
        }
    }

    fun recordContrastPracticeSession(
        session: ContrastPracticeSession,
        results: List<ContrastQuestionResult> = emptyList(),
        attempts: List<PracticeAttempt> = emptyList(),
        onComplete: () -> Unit = {},
        onFailure: () -> Unit = {},
    ) {
        viewModelScope.launch {
            runCatching {
                repository.recordContrastPracticeSession(session)
                repository.recordContrastQuestionResults(results, session.practiceType)
                repository.recordPracticeAttempts(attempts)
            }
                .onSuccess { onComplete() }
                .onFailure {
                    onFailure()
                    showMessage(it.message ?: "练习结果保存失败")
                }
        }
    }

    fun setWrongQuestionFavorite(entry: WrongQuestionEntry) {
        viewModelScope.launch {
            runCatching { repository.setWrongQuestionFavorite(entry.id, !entry.favorite) }
                .onFailure { showMessage(it.message ?: "收藏状态更新失败") }
        }
    }

    fun generateMixedReview(
        assignments: List<MixedReviewItem>,
        distractorPool: List<WordEntry>,
        paraphraseSeeds: List<ParaphraseSeed>,
        optionCount: Int,
        difficulty: PracticeDifficulty,
        onComplete: (Result<List<MixedReviewItem>>) -> Unit,
    ) {
        mutableMixedGenerationProgress.value = 0f
        viewModelScope.launch {
            val result = runCatching {
                val choiceAssignments = assignments.filter { it.mode != MixedReviewMode.DICTATION }
                if (choiceAssignments.isEmpty()) return@runCatching assignments

                val generatedByItemId = mutableMapOf<String, ContrastQuestion>()
                var completedChoices = 0
                MixedReviewMode.entries.filter { it != MixedReviewMode.DICTATION }.forEach { mode ->
                    val modeAssignments = choiceAssignments.filter { it.mode == mode }
                    if (modeAssignments.isEmpty()) return@forEach
                    val generated = if (mode == MixedReviewMode.ENGLISH_DEFINITION_TO_ENGLISH) {
                        modeAssignments.map { assignment ->
                            ParaphrasePracticeGenerator.generate(
                                seed = requireNotNull(assignment.paraphraseSeed),
                                candidates = paraphraseSeeds,
                                optionCount = optionCount,
                            )
                        }
                    } else {
                        repository.generateContrastQuestions(
                            targets = modeAssignments.map { requireNotNull(it.word) },
                            distractorPool = distractorPool,
                            type = mode.toContrastPracticeType(),
                            optionCount = optionCount,
                            difficulty = difficulty,
                            useMixedReviewPrompt = true,
                            onProgress = { partial ->
                                mutableMixedGenerationProgress.value = (
                                    completedChoices + modeAssignments.size * partial
                                ) / choiceAssignments.size
                            },
                        )
                    }
                    modeAssignments.zip(generated).forEach { (assignment, question) ->
                        generatedByItemId[assignment.itemId] = question
                    }
                    completedChoices += modeAssignments.size
                    mutableMixedGenerationProgress.value = completedChoices.toFloat() / choiceAssignments.size
                }
                assignments.map { assignment ->
                    if (assignment.mode == MixedReviewMode.DICTATION) assignment else {
                        assignment.copy(
                            contrastQuestion = generatedByItemId[assignment.itemId]
                                ?: error("混合复习题目生成不完整，请重试。"),
                        )
                    }
                }
            }
            result.exceptionOrNull()?.let { showMessage(it.message ?: "混合复习生成失败") }
            onComplete(result)
        }
    }

    fun recordMixedReviewSession(
        wordResults: List<WordReviewResult>,
        contrastResults: Map<MixedReviewMode, List<ContrastQuestionResult>>,
        difficulty: PracticeDifficulty,
        elapsedSeconds: Int,
        attempts: List<PracticeAttempt> = emptyList(),
        onComplete: () -> Unit,
        onFailure: () -> Unit = {},
    ) {
        viewModelScope.launch {
            runCatching {
                if (wordResults.isNotEmpty()) repository.recordReviewSession(wordResults)
                val totalContrastCount = contrastResults.values.sumOf(List<ContrastQuestionResult>::size)
                contrastResults.forEach { (mode, results) ->
                    if (results.isEmpty() || mode == MixedReviewMode.DICTATION) return@forEach
                    val practiceType = mode.toContrastPracticeType()
                    val allocatedSeconds = if (totalContrastCount == 0) 0 else {
                        (elapsedSeconds * results.size.toFloat() / totalContrastCount).toInt()
                    }
                    repository.recordContrastPracticeSession(
                        ContrastPracticeSession(
                            id = UUID.randomUUID().toString(),
                            completedAt = System.currentTimeMillis(),
                            practiceType = practiceType,
                            difficulty = difficulty,
                            questionCount = results.size,
                            correctCount = results.count(ContrastQuestionResult::correct),
                            elapsedSeconds = allocatedSeconds,
                            hintEnabled = false,
                        ),
                    )
                    repository.recordContrastQuestionResults(results, practiceType)
                }
                repository.recordPracticeAttempts(attempts)
            }.onSuccess { onComplete() }
                .onFailure {
                    onFailure()
                    showMessage(it.message ?: "混合复习结算失败")
                }
        }
    }

    fun analyzeWrongQuestion(entry: WrongQuestionEntry) {
        if (mutableAnalyzingWrongQuestionId.value != null) return
        mutableAnalyzingWrongQuestionId.value = entry.id
        viewModelScope.launch {
            runCatching { repository.analyzeWrongQuestion(entry) }
                .onSuccess { showMessage("AI 错题解析已生成") }
                .onFailure { showMessage(it.message ?: "AI 错题解析失败") }
            mutableAnalyzingWrongQuestionId.value = null
        }
    }

    fun startPracticeSession(request: PracticeSessionRequest) {
        synchronized(timerLock) {
            accumulatedQuestionTime.clear()
            activeQuestionKey = null
            activeQuestionStartedAt = null
        }
        val sessionId = UUID.randomUUID().toString()
        mutableActivePracticeSessionId.value = sessionId
        mutablePracticeSessionRuntime.value = PracticeSessionRuntime(sessionId = sessionId)
        mutableActivePracticeSession.value = request
    }

    fun closePracticeSession() {
        synchronized(timerLock) {
            pauseActiveQuestionTimerLocked()
            accumulatedQuestionTime.clear()
            activeQuestionKey = null
        }
        mutableActivePracticeSession.value = null
        mutableActivePracticeSessionId.value = null
        mutablePracticeSessionRuntime.value = null
    }

    fun setPracticeSessionIndex(index: Int) {
        mutablePracticeSessionRuntime.value = mutablePracticeSessionRuntime.value?.copy(
            currentIndex = index.coerceAtLeast(0),
        )
    }

    fun stagePracticeAttempt(attempt: PracticeAttempt) {
        val runtime = mutablePracticeSessionRuntime.value ?: return
        if (runtime.sessionId != attempt.sessionId) return
        mutablePracticeSessionRuntime.value = runtime.copy(
            attempts = runtime.attempts
                .filterNot { it.sequenceIndex == attempt.sequenceIndex }
                .plus(attempt)
                .sortedBy(PracticeAttempt::sequenceIndex),
        )
        viewModelScope.launch { repository.recordPracticeAttempts(listOf(attempt)) }
    }

    fun markPracticeSessionFinished() {
        mutablePracticeSessionRuntime.value = mutablePracticeSessionRuntime.value?.copy(finished = true)
    }

    fun setPracticeLifecycleActive(active: Boolean) {
        synchronized(timerLock) {
            practiceLifecycleActive = active
            if (active) {
                if (activeQuestionKey != null && activeQuestionStartedAt == null) {
                    activeQuestionStartedAt = SystemClock.elapsedRealtime()
                }
            } else {
                pauseActiveQuestionTimerLocked()
            }
        }
    }

    fun beginQuestionTiming(sessionId: String, sequenceIndex: Int) {
        val key = "$sessionId:$sequenceIndex"
        synchronized(timerLock) {
            if (activeQuestionKey != key) {
                pauseActiveQuestionTimerLocked()
                activeQuestionKey = key
            }
            accumulatedQuestionTime.putIfAbsent(key, 0L)
            if (practiceLifecycleActive && activeQuestionStartedAt == null) {
                activeQuestionStartedAt = SystemClock.elapsedRealtime()
            }
        }
    }

    fun snapshotQuestionTime(sessionId: String, sequenceIndex: Int): Long {
        val key = "$sessionId:$sequenceIndex"
        return synchronized(timerLock) {
            if (activeQuestionKey == key) pauseActiveQuestionTimerLocked()
            accumulatedQuestionTime[key].orZero()
        }
    }

    fun clearQuestionTimers(sessionId: String) {
        synchronized(timerLock) {
            pauseActiveQuestionTimerLocked()
            accumulatedQuestionTime.keys.removeAll { it.startsWith("$sessionId:") }
            activeQuestionKey = null
        }
    }

    private fun pauseActiveQuestionTimerLocked() {
        val key = activeQuestionKey
        val startedAt = activeQuestionStartedAt
        if (key != null && startedAt != null) {
            val segment = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
            accumulatedQuestionTime[key] = accumulatedQuestionTime[key].orZero() + segment
        }
        activeQuestionStartedAt = null
    }

    private fun Long?.orZero(): Long = this ?: 0L

    fun exportWrongAttemptSession(
        sessionId: String,
        attempts: List<PracticeAttempt>,
        uri: Uri,
        includeTiming: Boolean,
    ) {
        viewModelScope.launch {
            runCatching {
                application.contentResolver.openOutputStream(uri, "w")?.use { output ->
                    repository.exportWrongAttemptSession(sessionId, attempts, output, includeTiming)
                } ?: error("无法写入所选文件。")
            }.onSuccess {
                showMessage("本次错题 XML 已导出")
            }.onFailure {
                showMessage(it.message ?: "错题 XML 导出失败")
            }
        }
    }

    fun exportSessionTelemetry(
        sessionId: String,
        attempts: List<PracticeAttempt>,
        uri: Uri,
        includeTiming: Boolean,
    ) {
        viewModelScope.launch {
            runCatching {
                application.contentResolver.openOutputStream(uri, "w")?.use { output ->
                    repository.exportSessionTelemetry(sessionId, attempts, output, includeTiming)
                } ?: error("无法写入所选文件")
            }.onSuccess {
                showMessage("遥测数据 XML 已导出")
            }.onFailure {
                showMessage(it.message ?: "遥测数据导出失败")
            }
        }
    }

    fun exportDatabase(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                application.contentResolver.openOutputStream(uri, "w")?.use { output ->
                    repository.exportDatabase(output)
                } ?: error("无法写入所选文件。")
            }.onSuccess {
                showMessage("SQLite 数据库已导出")
            }.onFailure {
                showMessage(it.message ?: "数据库导出失败")
            }
        }
    }

    fun saveSupabaseConfig(url: String, key: String) {
        preferences.saveSupabaseConfig(SupabaseConfig(url, key))
        mutableUiState.value = readUiState(message = "连接信息已保存")
    }

    fun saveAiSettings(settings: AiSettings) {
        preferences.saveAiSettings(settings)
        mutableUiState.value = readUiState(message = "AI 设置已保存")
    }

    fun testAiSettings(settings: AiSettings) {
        if (mutableUiState.value.aiTesting) return
        mutableUiState.value = mutableUiState.value.copy(aiTesting = true, message = null)
        viewModelScope.launch {
            runCatching { repository.testAiSettings(settings) }
                .onSuccess { message -> mutableUiState.value = readUiState(message = message) }
                .onFailure { error ->
                    mutableUiState.value = readUiState(message = error.message ?: "AI 连接测试失败")
                }
        }
    }

    fun setAutomaticSync(enabled: Boolean) {
        saveSyncSettings(preferences.readSyncSettings().copy(enabled = enabled))
    }

    fun setSyncMode(mode: SyncMode) {
        saveSyncSettings(preferences.readSyncSettings().copy(mode = mode))
    }

    fun setSyncIntervalMinutes(intervalMinutes: Long) {
        saveSyncSettings(preferences.readSyncSettings().copy(intervalMinutes = intervalMinutes))
    }

    private fun saveSyncSettings(settings: SyncSettings) {
        preferences.saveSyncSettings(settings)
        SyncScheduler.configure(application, settings)
        mutableUiState.value = readUiState()
    }

    fun setDynamicColor(enabled: Boolean) {
        preferences.setDynamicColorEnabled(enabled)
        if (enabled) preferences.saveThemePresetId(null)
        mutableUiState.value = readUiState()
    }

    fun setAdministratorMode(enabled: Boolean) {
        preferences.setAdministratorModeEnabled(enabled)
        mutableUiState.value = readUiState()
    }

    fun setReviewCategory(category: ReviewCategory) {
        preferences.saveReviewCategory(category)
        mutableUiState.value = readUiState()
    }

    fun saveWordReviewPreferences(value: WordReviewPreferences) {
        preferences.saveWordReviewPreferences(value)
        mutableUiState.value = readUiState()
    }

    fun saveQuizReviewPreferences(value: QuizReviewPreferences) {
        preferences.saveQuizReviewPreferences(value)
        mutableUiState.value = readUiState()
    }

    fun saveContrastReviewPreferences(value: ContrastReviewPreferences) {
        preferences.saveContrastReviewPreferences(value)
        mutableUiState.value = readUiState()
    }

    fun saveMixedReviewPreferences(value: MixedReviewPreferences) {
        preferences.saveMixedReviewPreferences(value)
        mutableUiState.value = readUiState()
    }

    fun setThemePreset(id: String?) {
        preferences.saveThemePresetId(id)
        preferences.setDynamicColorEnabled(id == null)
        mutableUiState.value = readUiState()
    }

    fun saveReminderConfiguration(settings: ReminderSettings, dailyReviewTarget: Int) {
        preferences.saveReminderSettings(settings)
        preferences.saveDailyReviewTarget(dailyReviewTarget)
        com.zlight106.nvvocab.sync.ReviewNotificationScheduler.configure(application, settings)
        mutableUiState.value = readUiState(message = "通知计划已更新")
    }

    fun saveDailyProgressConfiguration(reference: DailyProgressReference, target: Int) {
        preferences.saveDailyProgressSettings(reference, target)
        com.zlight106.nvvocab.widget.DailyMemoWidgetUpdater.updateAll(application)
        mutableUiState.value = readUiState()
    }

    fun saveDailyMemoSettings(settings: DailyMemoSettings) {
        preferences.saveDailyMemoSettings(settings)
        com.zlight106.nvvocab.widget.DailyMemoWidgetUpdater.updateAll(application)
        mutableUiState.value = readUiState(message = "每日备忘已更新")
    }

    fun saveStudyTimeGoal(minutes: Int) {
        application.studyTimeTracker.setGoalMinutes(minutes)
        showMessage("学习时间目标已更新")
    }

    fun startStudyTimeTracking() {
        application.studyTimeTracker.start()
    }

    fun stopStudyTimeTracking() {
        application.studyTimeTracker.stop()
    }

    fun saveContrastPracticePreset(difficulty: PracticeDifficulty, preset: ContrastPracticePreset) {
        preferences.saveContrastPracticePreset(difficulty, preset)
        mutableUiState.value = readUiState(message = "${difficulty.displayName()}预设已保存")
    }

    fun setThemeMode(mode: ThemeMode) {
        preferences.saveThemeMode(mode)
        mutableUiState.value = readUiState()
    }

    fun signIn(email: String, password: String) {
        runAccountAction { repository.signIn(email, password).message }
    }

    fun signUp(email: String, password: String) {
        runAccountAction { repository.signUp(email, password).message }
    }

    fun signOut() {
        repository.signOut()
        mutableUiState.value = readUiState(message = "已退出账户")
    }

    fun synchronize() {
        if (mutableUiState.value.syncing) return
        mutableUiState.value = mutableUiState.value.copy(syncing = true, message = null)
        SyncStateMonitor.update(SyncRuntimeStatus.RUNNING)
        viewModelScope.launch {
            runCatching { repository.synchronize() }
                .onSuccess { report ->
                    SyncStateMonitor.update(SyncRuntimeStatus.SUCCESS)
                    val attemptStatus = if (report.pendingAttempts > 0) {
                        "，${report.pendingAttempts} 条答题流水等待数据库迁移后同步"
                    } else {
                        "、${report.uploadedAttempts} 条答题流水"
                    }
                    mutableUiState.value = readUiState(
                        message = "同步完成：上传 ${report.uploadedWords} 个单词、${report.uploadedLogs} 条记录、" +
                            "${report.uploadedTitleLists} 个题库和 ${report.uploadedWrongQuestions} 道错题$attemptStatus",
                    )
                }
                .onFailure {
                    SyncStateMonitor.update(SyncRuntimeStatus.FAILED)
                    mutableUiState.value = readUiState(message = it.message ?: "同步失败")
                }
        }
    }

    fun clearMessage() {
        mutableUiState.value = mutableUiState.value.copy(message = null)
    }

    fun notifyUser(message: String) {
        showMessage(message)
    }

    private fun runAccountAction(action: suspend () -> String) {
        mutableUiState.value = mutableUiState.value.copy(syncing = true, message = null)
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { mutableUiState.value = readUiState(message = it) }
                .onFailure { mutableUiState.value = readUiState(message = it.message ?: "账户请求失败") }
        }
    }

    private fun showMessage(message: String) {
        mutableUiState.value = mutableUiState.value.copy(message = message)
    }

    private fun readUiState(message: String? = null): AppUiState = AppUiState(
        administratorMode = preferences.isAdministratorModeEnabled(),
        aiSettings = preferences.readAiSettings(),
        aiTesting = false,
        automaticSync = preferences.isAutomaticSyncEnabled(),
        contrastPracticePresets = preferences.readContrastPracticePresets(),
        contrastReviewPreferences = preferences.readContrastReviewPreferences(),
        dailyProgressSettings = preferences.readDailyProgressSettings(),
        dailyMemoSettings = preferences.readDailyMemoSettings(),
        dailyReviewTarget = preferences.readDailyReviewTarget(),
        dynamicColor = preferences.isDynamicColorEnabled(),
        message = message,
        mixedReviewPreferences = preferences.readMixedReviewPreferences(),
        reminderSettings = preferences.readReminderSettings(),
        reviewCategory = preferences.readReviewCategory(),
        quizReviewPreferences = preferences.readQuizReviewPreferences(),
        session = preferences.readSession(),
        supabaseConfig = preferences.readSupabaseConfig(),
        syncSettings = preferences.readSyncSettings(),
        syncing = false,
        themeMode = preferences.readThemeMode(),
        themePresetId = preferences.readThemePresetId(),
        wordReviewPreferences = preferences.readWordReviewPreferences(),
    )

    class Factory(private val application: NvvocabApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MainViewModel::class.java))
            return MainViewModel(application) as T
        }
    }
}

private fun MixedReviewMode.toContrastPracticeType(): ContrastPracticeType = when (this) {
    MixedReviewMode.CHINESE_TO_ENGLISH -> ContrastPracticeType.CHINESE_TO_ENGLISH
    MixedReviewMode.ENGLISH_TO_CHINESE -> ContrastPracticeType.ENGLISH_TO_CHINESE
    MixedReviewMode.ENGLISH_DEFINITION_TO_ENGLISH -> ContrastPracticeType.ENGLISH_DEFINITION_TO_ENGLISH
    MixedReviewMode.DICTATION -> error("默写模式不需要生成选择题。")
}

private fun PracticeDifficulty.displayName(): String = when (this) {
    PracticeDifficulty.EASY -> "简单"
    PracticeDifficulty.MEDIUM -> "中等"
    PracticeDifficulty.HARD -> "困难"
}
