package com.zlight106.nvvocab.ui

import android.net.Uri
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
import com.zlight106.nvvocab.data.DailyProgressReference
import com.zlight106.nvvocab.data.DailyProgressSettings
import com.zlight106.nvvocab.data.DailyMemoSettings
import com.zlight106.nvvocab.data.DictationMode
import com.zlight106.nvvocab.data.PracticeDifficulty
import com.zlight106.nvvocab.data.QueueSort
import com.zlight106.nvvocab.data.QuizAttempt
import com.zlight106.nvvocab.data.QuizQuestion
import com.zlight106.nvvocab.data.ReminderSettings
import com.zlight106.nvvocab.data.SupabaseConfig
import com.zlight106.nvvocab.data.SyncMode
import com.zlight106.nvvocab.data.SyncSettings
import com.zlight106.nvvocab.data.ThemeMode
import com.zlight106.nvvocab.data.WordEntry
import com.zlight106.nvvocab.data.WrongQuestionEntry
import com.zlight106.nvvocab.ui.screens.PracticeSessionRequest
import com.zlight106.nvvocab.data.repository.VocabularyRepository
import com.zlight106.nvvocab.domain.WordTextParser
import com.zlight106.nvvocab.sync.SyncScheduler
import com.zlight106.nvvocab.sync.SyncRuntimeStatus
import com.zlight106.nvvocab.sync.SyncStateMonitor
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppUiState(
    val administratorMode: Boolean,
    val aiSettings: AiSettings,
    val aiTesting: Boolean = false,
    val automaticSync: Boolean,
    val contrastPracticePresets: ContrastPracticePresets,
    val dailyProgressSettings: DailyProgressSettings,
    val dailyMemoSettings: DailyMemoSettings,
    val dailyReviewTarget: Int,
    val dynamicColor: Boolean,
    val message: String? = null,
    val reminderSettings: ReminderSettings,
    val session: AuthSession?,
    val supabaseConfig: SupabaseConfig,
    val syncSettings: SyncSettings,
    val syncing: Boolean = false,
    val themeMode: ThemeMode,
    val themePresetId: String?,
)

class MainViewModel(private val application: NvvocabApplication) : ViewModel() {
    private val preferences = application.preferences
    private val repository: VocabularyRepository = application.repository
    private val mutableUiState = MutableStateFlow(readUiState())
    private val mutableContrastGenerationProgress = MutableStateFlow(0f)
    private val mutableActivePracticeSession = MutableStateFlow<PracticeSessionRequest?>(null)
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
    val localDataLoaded = repository.localDataLoaded
    val wrongQuestions = repository.wrongQuestions
    val activePracticeSession: StateFlow<PracticeSessionRequest?> = mutableActivePracticeSession.asStateFlow()
    val analyzingWrongQuestionId: StateFlow<String?> = mutableAnalyzingWrongQuestionId.asStateFlow()

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
        onComplete: () -> Unit,
        onFailure: () -> Unit = {},
    ) {
        viewModelScope.launch {
            runCatching { repository.recordReviewSession(results) }
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
        onComplete: () -> Unit,
        onFailure: () -> Unit = {},
    ) {
        viewModelScope.launch {
            runCatching { repository.recordQuizSession(answers) }
                .onSuccess { onComplete() }
                .onFailure {
                    onFailure()
                    showMessage(it.message ?: "本轮答题结算失败")
                }
        }
    }

    fun recordWrongQuestionSession(
        answers: List<com.zlight106.nvvocab.data.QuizSessionAnswer>,
        onComplete: () -> Unit,
        onFailure: () -> Unit = {},
    ) {
        viewModelScope.launch {
            runCatching { repository.recordWrongQuestionSession(answers) }
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
        onComplete: () -> Unit = {},
        onFailure: () -> Unit = {},
    ) {
        viewModelScope.launch {
            runCatching {
                repository.recordContrastPracticeSession(session)
                repository.recordContrastQuestionResults(results, session.practiceType)
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
        mutableActivePracticeSession.value = request
    }

    fun closePracticeSession() {
        mutableActivePracticeSession.value = null
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
                    mutableUiState.value = readUiState(
                        message = "同步完成：上传 ${report.uploadedWords} 个单词、${report.uploadedLogs} 条记录、${report.uploadedTitleLists} 个题库和 ${report.uploadedWrongQuestions} 道错题",
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
        dailyProgressSettings = preferences.readDailyProgressSettings(),
        dailyMemoSettings = preferences.readDailyMemoSettings(),
        dailyReviewTarget = preferences.readDailyReviewTarget(),
        dynamicColor = preferences.isDynamicColorEnabled(),
        message = message,
        reminderSettings = preferences.readReminderSettings(),
        session = preferences.readSession(),
        supabaseConfig = preferences.readSupabaseConfig(),
        syncSettings = preferences.readSyncSettings(),
        syncing = false,
        themeMode = preferences.readThemeMode(),
        themePresetId = preferences.readThemePresetId(),
    )

    class Factory(private val application: NvvocabApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MainViewModel::class.java))
            return MainViewModel(application) as T
        }
    }
}

private fun PracticeDifficulty.displayName(): String = when (this) {
    PracticeDifficulty.EASY -> "简单"
    PracticeDifficulty.MEDIUM -> "中等"
    PracticeDifficulty.HARD -> "困难"
}
