package com.zlight106.nvvocab.data.repository

import com.zlight106.nvvocab.data.AppPreferences
import com.zlight106.nvvocab.data.AuthSession
import com.zlight106.nvvocab.data.ContrastPracticeSession
import com.zlight106.nvvocab.data.ContrastPracticeType
import com.zlight106.nvvocab.data.ContrastQuestion
import com.zlight106.nvvocab.data.DailyPracticeProgress
import com.zlight106.nvvocab.data.DictationMode
import com.zlight106.nvvocab.data.ParsedWord
import com.zlight106.nvvocab.data.ParsedQuizBank
import com.zlight106.nvvocab.data.ParsedQuizQuestion
import com.zlight106.nvvocab.data.PracticeDifficulty
import com.zlight106.nvvocab.data.QuizAttempt
import com.zlight106.nvvocab.data.QuizBank
import com.zlight106.nvvocab.data.QuizOption
import com.zlight106.nvvocab.data.QuizQuestion
import com.zlight106.nvvocab.data.QuizSource
import com.zlight106.nvvocab.data.QueueSort
import com.zlight106.nvvocab.data.SupabaseConfig
import com.zlight106.nvvocab.data.SyncReport
import com.zlight106.nvvocab.data.WordEntry
import com.zlight106.nvvocab.data.ContrastQuestionResult
import com.zlight106.nvvocab.data.WrongQuestionEntry
import com.zlight106.nvvocab.data.WrongQuestionResult
import com.zlight106.nvvocab.data.WrongQuestionSource
import com.zlight106.nvvocab.data.local.NvvocabDatabase
import com.zlight106.nvvocab.data.network.AiPracticeGateway
import com.zlight106.nvvocab.data.network.AuthOutcome
import com.zlight106.nvvocab.data.network.SupabaseGateway
import com.zlight106.nvvocab.domain.ProficiencyCalculator
import com.zlight106.nvvocab.domain.QuizXmlParser
import com.zlight106.nvvocab.domain.ReviewCadence
import java.io.OutputStream
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class VocabularyRepository(
    private val database: NvvocabDatabase,
    private val preferences: AppPreferences,
    private val gateway: SupabaseGateway,
    private val aiPracticeGateway: AiPracticeGateway,
    private val onLocalDataChanged: () -> Unit,
) {
    private val mutableWords = MutableStateFlow<List<WordEntry>>(emptyList())
    private val mutableBookTags = MutableStateFlow<List<String>>(emptyList())
    private val mutableReviewLogs = MutableStateFlow<List<com.zlight106.nvvocab.data.ReviewLogEntry>>(emptyList())
    private val mutableQuizBanks = MutableStateFlow<List<QuizBank>>(emptyList())
    private val mutableContrastPracticeSessions = MutableStateFlow<List<ContrastPracticeSession>>(emptyList())
    private val mutableDailyPracticeProgress = MutableStateFlow(DailyPracticeProgress())
    private val mutableWrongQuestions = MutableStateFlow<List<WrongQuestionEntry>>(emptyList())
    private val mutableLocalDataLoaded = MutableStateFlow(false)

    val words: StateFlow<List<WordEntry>> = mutableWords.asStateFlow()
    val bookTags: StateFlow<List<String>> = mutableBookTags.asStateFlow()
    val reviewLogs: StateFlow<List<com.zlight106.nvvocab.data.ReviewLogEntry>> = mutableReviewLogs.asStateFlow()
    val quizBanks: StateFlow<List<QuizBank>> = mutableQuizBanks.asStateFlow()
    val contrastPracticeSessions: StateFlow<List<ContrastPracticeSession>> =
        mutableContrastPracticeSessions.asStateFlow()
    val dailyPracticeProgress: StateFlow<DailyPracticeProgress> = mutableDailyPracticeProgress.asStateFlow()
    val wrongQuestions: StateFlow<List<WrongQuestionEntry>> = mutableWrongQuestions.asStateFlow()
    val localDataLoaded: StateFlow<Boolean> = mutableLocalDataLoaded.asStateFlow()

    suspend fun refreshLocal() = withContext(Dispatchers.IO) {
        mutableWords.value = database.getWords()
        mutableBookTags.value = database.getBookTags()
        mutableReviewLogs.value = database.getReviewLogs()
        mutableQuizBanks.value = database.getQuizBanks()
        mutableContrastPracticeSessions.value = database.getRecentContrastPracticeSessions()
        mutableDailyPracticeProgress.value = database.getDailyPracticeProgress(startOfTodayMillis())
        mutableWrongQuestions.value = database.getWrongQuestions()
        mutableLocalDataLoaded.value = true
    }

    suspend fun importWords(parsedWords: List<ParsedWord>, bookTag: String): Int = withContext(Dispatchers.IO) {
        if (parsedWords.isEmpty()) return@withContext 0
        val now = System.currentTimeMillis()
        val session = preferences.readSession()
        val normalizedTag = bookTag.trim().ifBlank { "未分类" }
        val entries = parsedWords.mapIndexed { index, word ->
            WordEntry(
                id = UUID.randomUUID().toString(),
                userId = session?.userId,
                spelling = word.spelling,
                phonetic = word.phonetic.ifBlank { null },
                translation = word.translation,
                bookTag = normalizedTag,
                introTime = now + index,
                repetitions = 0,
                intervalDays = 1,
                easiness = 2.5,
                nextReviewAt = now,
                wrongCount = 0,
                dirty = true,
            )
        }
        database.insertWords(entries)
        refreshLocal()
        onLocalDataChanged()
        entries.size
    }

    suspend fun updateWordTag(wordId: String, bookTag: String) = withContext(Dispatchers.IO) {
        database.updateWordTag(wordId, bookTag.trim().ifBlank { "未分类" })
        refreshLocal()
        onLocalDataChanged()
    }

    fun buildQueue(
        mode: DictationMode,
        bookTag: String?,
        sort: QueueSort,
        limit: Int?,
        now: Long = System.currentTimeMillis(),
    ): List<WordEntry> {
        val filtered = mutableWords.value.asSequence()
            .filter { mode == DictationMode.PRACTICE || it.nextReviewAt <= now }
            .filter { bookTag.isNullOrBlank() || it.bookTag == bookTag }
            .toList()
        val sorted = when (sort) {
            QueueSort.EARLIEST -> filtered.sortedBy(WordEntry::introTime)
            QueueSort.LATEST -> filtered.sortedByDescending(WordEntry::introTime)
            QueueSort.PROFICIENCY_LOW -> filtered.sortedBy { ProficiencyCalculator.calculate(it, now).score }
            QueueSort.PROFICIENCY_HIGH -> filtered.sortedByDescending { ProficiencyCalculator.calculate(it, now).score }
            QueueSort.RANDOM -> filtered.shuffled()
        }
        return sorted.take(limit?.coerceIn(1, 500) ?: 100)
    }

    suspend fun recordReview(word: WordEntry, quality: Int) = withContext(Dispatchers.IO) {
        val reviewedAt = System.currentTimeMillis()
        database.applyReview(
            word = word,
            state = ReviewCadence.next(word, quality.coerceIn(0, 5), reviewedAt),
            quality = quality.coerceIn(0, 5),
            userId = preferences.readSession()?.userId,
            reviewedAt = reviewedAt,
        )
        refreshLocal()
        onLocalDataChanged()
    }

    suspend fun recordReviewSession(results: List<com.zlight106.nvvocab.data.WordReviewResult>) =
        withContext(Dispatchers.IO) {
            val userId = preferences.readSession()?.userId
            val reviewedAt = System.currentTimeMillis()
            results.forEachIndexed { index, result ->
                val quality = result.quality.coerceIn(0, 5)
                val timestamp = reviewedAt + index
                database.applyReview(
                    word = result.word,
                    state = ReviewCadence.next(result.word, quality, timestamp),
                    quality = quality,
                    userId = userId,
                    reviewedAt = timestamp,
                )
            }
            refreshLocal()
            onLocalDataChanged()
        }

    suspend fun importQuizBank(fileName: String, inputStream: InputStream): QuizBank = withContext(Dispatchers.IO) {
        val bank = database.replaceQuizBank(
            parsedBank = QuizXmlParser.parse(inputStream, fileName),
            userId = preferences.readSession()?.userId,
            source = QuizSource.XML,
        )
        mutableQuizBanks.value = database.getQuizBanks()
        onLocalDataChanged()
        bank
    }

    suspend fun getQuizQuestions(bankId: String): List<QuizQuestion> = withContext(Dispatchers.IO) {
        database.getQuizQuestions(bankId)
    }

    suspend fun recordQuizAttempt(attempt: QuizAttempt) = withContext(Dispatchers.IO) {
        database.insertQuizAttempt(attempt)
        mutableDailyPracticeProgress.value = database.getDailyPracticeProgress(startOfTodayMillis())
        onLocalDataChanged()
    }

    suspend fun recordQuizSession(answers: List<com.zlight106.nvvocab.data.QuizSessionAnswer>) =
        withContext(Dispatchers.IO) {
            val answeredAt = System.currentTimeMillis()
            val bankNames = database.getQuizBanks().associate { it.id to it.name }
            answers.forEachIndexed { index, answer ->
                val correct = answer.selectedAnswers == answer.question.answers
                database.insertQuizAttempt(
                    QuizAttempt(
                        id = UUID.randomUUID().toString(),
                        bankId = answer.question.bankId,
                        questionId = answer.question.id,
                        answeredAt = answeredAt + index,
                        selectedAnswers = answer.selectedAnswers,
                        correct = correct,
                        scoreGained = if (correct) answer.question.score else 0,
                    ),
                )
            }
            database.recordWrongQuestionResults(
                answers.map { answer ->
                    WrongQuestionResult(
                        source = WrongQuestionSource.QUIZ,
                        bankId = answer.question.bankId,
                        bankName = bankNames[answer.question.bankId] ?: "未命名题库",
                        questionKey = answer.question.id,
                        questionText = answer.question.text,
                        options = answer.question.options,
                        correctAnswers = answer.question.answers,
                        correct = answer.selectedAnswers == answer.question.answers,
                    )
                },
                preferences.readSession()?.userId,
            )
            mutableWrongQuestions.value = database.getWrongQuestions()
            mutableDailyPracticeProgress.value = database.getDailyPracticeProgress(startOfTodayMillis())
            onLocalDataChanged()
        }

    suspend fun recordWrongQuestionSession(answers: List<com.zlight106.nvvocab.data.QuizSessionAnswer>) =
        withContext(Dispatchers.IO) {
            val existingByKey = database.getWrongQuestions().associateBy(WrongQuestionEntry::questionKey)
            database.recordWrongQuestionResults(
                answers.map { answer ->
                    val existing = existingByKey[answer.question.id]
                    WrongQuestionResult(
                        source = existing?.source ?: WrongQuestionSource.QUIZ,
                        bankId = existing?.bankId ?: answer.question.bankId,
                        bankName = existing?.bankName ?: "错题复习",
                        questionKey = answer.question.id,
                        questionText = answer.question.text,
                        options = answer.question.options,
                        correctAnswers = answer.question.answers,
                        correct = answer.selectedAnswers == answer.question.answers,
                    )
                },
                preferences.readSession()?.userId,
            )
            mutableWrongQuestions.value = database.getWrongQuestions()
            onLocalDataChanged()
        }

    suspend fun recordContrastQuestionResults(
        results: List<ContrastQuestionResult>,
        practiceType: ContrastPracticeType,
    ) = withContext(Dispatchers.IO) {
        val bankName = when (practiceType) {
            ContrastPracticeType.CHINESE_TO_ENGLISH -> "对照练习：中文翻译英文"
            ContrastPracticeType.ENGLISH_DEFINITION_TO_ENGLISH -> "对照练习：英文释义选词"
            ContrastPracticeType.ENGLISH_TO_CHINESE -> "对照练习：英文翻译中文"
        }
        database.recordWrongQuestionResults(
            results.map { result ->
                WrongQuestionResult(
                    source = WrongQuestionSource.CONTRAST,
                    bankId = null,
                    bankName = bankName,
                    questionKey = result.question.id,
                    questionText = result.question.prompt,
                    options = result.question.options.mapIndexed { index, text ->
                        QuizOption(('A'.code + index).toChar().toString(), text)
                    },
                    correctAnswers = setOf(('A'.code + result.question.correctIndex).toChar().toString()),
                    correct = result.correct,
                )
            },
            preferences.readSession()?.userId,
        )
        mutableWrongQuestions.value = database.getWrongQuestions()
        onLocalDataChanged()
    }

    suspend fun setWrongQuestionFavorite(id: String, favorite: Boolean) = withContext(Dispatchers.IO) {
        database.setWrongQuestionFavorite(id, favorite)
        mutableWrongQuestions.value = database.getWrongQuestions()
        onLocalDataChanged()
    }

    suspend fun analyzeWrongQuestion(entry: WrongQuestionEntry): String = withContext(Dispatchers.IO) {
        val analysis = aiPracticeGateway.analyzeWrongQuestion(preferences.readAiSettings(), entry)
        database.saveWrongQuestionAnalysis(entry.id, analysis)
        mutableWrongQuestions.value = database.getWrongQuestions()
        onLocalDataChanged()
        analysis
    }

    suspend fun renameQuizBank(bankId: String, name: String) = withContext(Dispatchers.IO) {
        database.renameQuizBank(bankId, name)
        mutableQuizBanks.value = database.getQuizBanks()
        onLocalDataChanged()
    }

    suspend fun deleteQuizBank(bankId: String) = withContext(Dispatchers.IO) {
        database.deleteQuizBank(bankId, preferences.readSession()?.userId)
        mutableQuizBanks.value = database.getQuizBanks()
        onLocalDataChanged()
    }

    suspend fun generateContrastQuestions(
        targets: List<WordEntry>,
        distractorPool: List<WordEntry>,
        type: ContrastPracticeType,
        optionCount: Int,
        difficulty: PracticeDifficulty,
        onProgress: (Float) -> Unit,
    ): List<ContrastQuestion> = withContext(Dispatchers.IO) {
        val generated = aiPracticeGateway.generateQuestions(
            settings = preferences.readAiSettings(),
            targets = targets,
            distractorPool = distractorPool,
            type = type,
            optionCount = optionCount,
            difficulty = difficulty,
            onProgress = onProgress,
        )
        val now = System.currentTimeMillis()
        val bankName = "AI 对照练习 ${AI_BANK_TIME_FORMAT.format(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()))}-${now % 1_000}"
        val parsedQuestions = generated.mapIndexed { index, question ->
            val options = question.options.mapIndexed { optionIndex, option ->
                QuizOption(id = ('A'.code + optionIndex).toChar().toString(), text = option)
            }
            val answer = options.getOrNull(question.correctIndex)?.id
                ?: error("AI 题目的正确答案索引无效。")
            ParsedQuizQuestion(
                originalIndex = index,
                score = 10,
                text = question.prompt,
                options = options,
                answers = setOf(answer),
            )
        }
        database.replaceQuizBank(
            parsedBank = ParsedQuizBank(name = bankName, password = null, questions = parsedQuestions),
            userId = preferences.readSession()?.userId,
            source = QuizSource.AI,
            practiceType = type,
            difficulty = difficulty,
        )
        mutableQuizBanks.value = database.getQuizBanks()
        onLocalDataChanged()
        generated
    }

    suspend fun testAiSettings(settings: com.zlight106.nvvocab.data.AiSettings): String =
        aiPracticeGateway.testConnection(settings)

    suspend fun recordContrastPracticeSession(session: ContrastPracticeSession) = withContext(Dispatchers.IO) {
        database.insertContrastPracticeSession(session)
        mutableContrastPracticeSessions.value = database.getRecentContrastPracticeSessions()
        mutableDailyPracticeProgress.value = database.getDailyPracticeProgress(startOfTodayMillis())
        onLocalDataChanged()
    }

    suspend fun exportDatabase(output: OutputStream) = withContext(Dispatchers.IO) {
        database.exportDatabase(output)
    }

    suspend fun signIn(email: String, password: String): AuthOutcome {
        val outcome = gateway.signIn(preferences.readSupabaseConfig(), email, password)
        outcome.session?.let(preferences::saveSession)
        return outcome
    }

    suspend fun signUp(email: String, password: String): AuthOutcome {
        val outcome = gateway.signUp(preferences.readSupabaseConfig(), email, password)
        outcome.session?.let(preferences::saveSession)
        return outcome
    }

    fun signOut() = preferences.clearSession()

    suspend fun synchronize(): SyncReport = withContext(Dispatchers.IO) {
        val config = preferences.readSupabaseConfig()
        var session = preferences.readSession() ?: error("请先登录账户。")
        if (!config.isValid()) error("请先在设置中填写 Supabase 连接信息。")
        if (session.expiresAtEpochSeconds <= Instant.now().epochSecond + 60) {
            session = gateway.refreshSession(config, session)
            preferences.saveSession(session)
        }
        database.assignUnsyncedRowsToUser(session.userId)

        // Pull first. Remote rows are merged without replacing locally dirty rows or
        // resurrecting locally deleted title lists, so offline edits remain authoritative
        // until the upload phase below.
        val remoteWords = gateway.fetchWords(config, session)
        database.upsertRemoteWords(remoteWords)
        val remoteLogs = gateway.fetchReviewLogs(config, session)
        database.upsertRemoteReviewLogs(remoteLogs)
        val remoteTitleLists = gateway.fetchTitleLists(config, session)
        database.upsertRemoteTitleLists(remoteTitleLists)
        val remoteWrongQuestions = gateway.fetchWrongQuestions(config, session)
        database.upsertRemoteWrongQuestions(remoteWrongQuestions)

        val deletedTitleListIds = database.getDeletedTitleListIds()
        val dirtyWords = database.getDirtyWords()
        val dirtyLogs = database.getDirtyReviewLogs()
        val dirtyTitleLists = database.getDirtyTitleLists()
        val dirtyWrongQuestions = database.getDirtyWrongQuestions()
        gateway.deleteTitleLists(config, session, deletedTitleListIds)
        database.clearDeletedTitleLists(deletedTitleListIds)
        gateway.upsertWords(config, session, dirtyWords)
        gateway.upsertReviewLogs(config, session, dirtyLogs)
        gateway.upsertTitleLists(config, session, dirtyTitleLists)
        gateway.upsertWrongQuestions(config, session, dirtyWrongQuestions)
        database.markWordsClean(dirtyWords.map(WordEntry::id))
        database.markReviewLogsClean(dirtyLogs.map { it.id })
        database.markTitleListsClean(dirtyTitleLists.map { it.id })
        database.markWrongQuestionsClean(dirtyWrongQuestions.map { it.id })
        refreshLocal()
        SyncReport(
            downloadedLogs = remoteLogs.size,
            downloadedTitleLists = remoteTitleLists.size,
            downloadedWrongQuestions = remoteWrongQuestions.size,
            downloadedWords = remoteWords.size,
            uploadedLogs = dirtyLogs.size,
            uploadedTitleLists = dirtyTitleLists.size,
            uploadedWrongQuestions = dirtyWrongQuestions.size,
            uploadedWords = dirtyWords.size,
        )
    }

    private fun SupabaseConfig.isValid(): Boolean =
        url.isNotBlank() && publishableKey.isNotBlank()

    private fun startOfTodayMillis(): Long {
        val zone = ZoneId.systemDefault()
        return LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    private companion object {
        val AI_BANK_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
