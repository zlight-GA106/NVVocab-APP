package com.zlight106.nvvocab.data.repository

import com.zlight106.nvvocab.data.AppPreferences
import com.zlight106.nvvocab.data.AnswerEvaluationResult
import com.zlight106.nvvocab.data.AuthSession
import com.zlight106.nvvocab.data.ContrastPracticeSession
import com.zlight106.nvvocab.data.ContrastPracticeType
import com.zlight106.nvvocab.data.ContrastQuestion
import com.zlight106.nvvocab.data.DailyPracticeProgress
import com.zlight106.nvvocab.data.DictationMode
import com.zlight106.nvvocab.data.FillBlankEvaluation
import com.zlight106.nvvocab.data.ParaphraseSeed
import com.zlight106.nvvocab.data.ParsedWord
import com.zlight106.nvvocab.data.ParsedQuizBank
import com.zlight106.nvvocab.data.ParsedQuizQuestion
import com.zlight106.nvvocab.data.PracticeDifficulty
import com.zlight106.nvvocab.data.PracticeAttempt
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
import com.zlight106.nvvocab.domain.FillBlankEvaluator
import com.zlight106.nvvocab.domain.QuizXmlParser
import com.zlight106.nvvocab.domain.QuizXmlWriter
import com.zlight106.nvvocab.domain.ReviewCadence
import com.zlight106.nvvocab.domain.WrongAttemptXmlWriter
import com.zlight106.nvvocab.domain.SessionTelemetryXmlWriter
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val mutablePracticeAttempts = MutableStateFlow<List<PracticeAttempt>>(emptyList())
    private val mutableParaphraseSeeds = MutableStateFlow<List<ParaphraseSeed>>(emptyList())
    private val mutableLocalDataLoaded = MutableStateFlow(false)
    private val practiceAttemptWriteMutex = Mutex()

    val words: StateFlow<List<WordEntry>> = mutableWords.asStateFlow()
    val bookTags: StateFlow<List<String>> = mutableBookTags.asStateFlow()
    val reviewLogs: StateFlow<List<com.zlight106.nvvocab.data.ReviewLogEntry>> = mutableReviewLogs.asStateFlow()
    val quizBanks: StateFlow<List<QuizBank>> = mutableQuizBanks.asStateFlow()
    val contrastPracticeSessions: StateFlow<List<ContrastPracticeSession>> =
        mutableContrastPracticeSessions.asStateFlow()
    val dailyPracticeProgress: StateFlow<DailyPracticeProgress> = mutableDailyPracticeProgress.asStateFlow()
    val wrongQuestions: StateFlow<List<WrongQuestionEntry>> = mutableWrongQuestions.asStateFlow()
    val practiceAttempts: StateFlow<List<PracticeAttempt>> = mutablePracticeAttempts.asStateFlow()
    val paraphraseSeeds: StateFlow<List<ParaphraseSeed>> = mutableParaphraseSeeds.asStateFlow()
    val localDataLoaded: StateFlow<Boolean> = mutableLocalDataLoaded.asStateFlow()

    suspend fun refreshLocal() = withContext(Dispatchers.IO) {
        mutableWords.value = database.getWords()
        mutableBookTags.value = database.getBookTags()
        mutableReviewLogs.value = database.getReviewLogs()
        mutableQuizBanks.value = database.getQuizBanks()
        mutableContrastPracticeSessions.value = database.getRecentContrastPracticeSessions()
        mutableDailyPracticeProgress.value = database.getDailyPracticeProgress(startOfTodayMillis())
        mutableWrongQuestions.value = database.getWrongQuestions()
        mutablePracticeAttempts.value = database.getPracticeAttempts()
        mutableParaphraseSeeds.value = database.getParaphraseSeeds()
        mutableLocalDataLoaded.value = true
    }

    suspend fun recordPracticeAttempts(attempts: List<PracticeAttempt>) = withContext(Dispatchers.IO) {
        if (attempts.isEmpty()) return@withContext
        practiceAttemptWriteMutex.withLock {
            val userId = preferences.readSession()?.userId
            database.insertPracticeAttempts(attempts.map { it.copy(userId = it.userId ?: userId, dirty = true) })
            mutablePracticeAttempts.value = database.getPracticeAttempts()
            onLocalDataChanged()
        }
    }

    suspend fun exportWrongAttemptSession(
        sessionId: String,
        attempts: List<PracticeAttempt>,
        output: OutputStream,
        includeTiming: Boolean = true,
    ) = withContext(Dispatchers.IO) {
        WrongAttemptXmlWriter.write(sessionId, attempts, output, includeTiming)
    }

    suspend fun exportSessionTelemetry(
        sessionId: String,
        attempts: List<PracticeAttempt>,
        output: OutputStream,
        includeTiming: Boolean,
    ) = withContext(Dispatchers.IO) {
        SessionTelemetryXmlWriter.write(sessionId, attempts, output, includeTiming)
    }

    suspend fun saveParaphraseSeed(
        id: String?,
        sourceText: String,
        targetText: String,
        contextText: String?,
        sourceReference: String?,
        notes: String?,
    ): ParaphraseSeed = withContext(Dispatchers.IO) {
        require(sourceText.isNotBlank()) { "原表达不能为空" }
        require(targetText.isNotBlank()) { "等效表达不能为空" }
        val now = System.currentTimeMillis()
        val existing = id?.let { candidate -> mutableParaphraseSeeds.value.firstOrNull { it.id == candidate } }
        val entry = ParaphraseSeed(
            id = existing?.id ?: UUID.randomUUID().toString(),
            userId = existing?.userId ?: preferences.readSession()?.userId,
            sourceText = sourceText.trim(),
            targetText = targetText.trim(),
            contextText = contextText?.trim()?.takeIf(String::isNotBlank),
            sourceReference = sourceReference?.trim()?.takeIf(String::isNotBlank),
            notes = notes?.trim()?.takeIf(String::isNotBlank),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            dirty = true,
        )
        database.upsertParaphraseSeeds(listOf(entry))
        mutableParaphraseSeeds.value = database.getParaphraseSeeds()
        onLocalDataChanged()
        entry
    }

    suspend fun importParaphraseSeeds(entries: List<ParaphraseSeed>): Int = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) return@withContext 0
        val now = System.currentTimeMillis()
        val userId = preferences.readSession()?.userId
        val normalized = entries.mapIndexed { index, entry ->
            entry.copy(
                id = entry.id.ifBlank { UUID.randomUUID().toString() },
                userId = entry.userId ?: userId,
                sourceText = entry.sourceText.trim(),
                targetText = entry.targetText.trim(),
                createdAt = entry.createdAt.takeIf { it > 0L } ?: now + index,
                updatedAt = now + index,
                dirty = true,
            )
        }.filter { it.sourceText.isNotBlank() && it.targetText.isNotBlank() }
        database.upsertParaphraseSeeds(normalized)
        mutableParaphraseSeeds.value = database.getParaphraseSeeds()
        onLocalDataChanged()
        normalized.size
    }

    suspend fun deleteParaphraseSeed(id: String) = withContext(Dispatchers.IO) {
        database.deleteParaphraseSeed(id, preferences.readSession()?.userId, System.currentTimeMillis())
        mutableParaphraseSeeds.value = database.getParaphraseSeeds()
        onLocalDataChanged()
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
            QueueSort.WRONG_COUNT -> filtered.sortedByDescending(WordEntry::wrongCount)
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

    suspend fun evaluateFillBlankAnswer(
        question: QuizQuestion,
        userAnswer: String,
        ignoreCase: Boolean,
    ): FillBlankEvaluation = withContext(Dispatchers.IO) {
        val local = FillBlankEvaluator.evaluateLocally(question, userAnswer, ignoreCase)
        if (local.result != AnswerEvaluationResult.REVIEW) return@withContext local
        val settings = preferences.readAiSettings()
        if (settings.apiKey.isBlank()) return@withContext local.copy(
            reason = "本地未匹配且尚未配置 AI，已标记为待复核",
        )
        runCatching {
            aiPracticeGateway.evaluateFillBlankAnswer(settings, question, userAnswer)
        }.getOrElse {
            local.copy(reason = "AI 复核不可用，已标记为待复核")
        }
    }

    suspend fun exportQuizBank(bankId: String, output: OutputStream) = withContext(Dispatchers.IO) {
        QuizXmlWriter.write(database.getQuizQuestions(bankId), output)
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
                val correct = answer.correct
                database.insertQuizAttempt(
                    QuizAttempt(
                        id = UUID.randomUUID().toString(),
                        bankId = answer.question.bankId,
                        questionId = answer.question.id,
                        answeredAt = answeredAt + index,
                        selectedAnswers = answer.selectedAnswers,
                        correct = correct,
                        scoreGained = if (correct) answer.question.score else 0,
                        userAnswer = answer.userAnswer,
                        hintUsed = answer.hintUsed,
                        evaluationResult = answer.evaluation?.result ?: if (correct) {
                            AnswerEvaluationResult.CORRECT
                        } else {
                            AnswerEvaluationResult.INCORRECT
                        },
                    ),
                )
            }
            database.recordWrongQuestionResults(
                answers.filterNot { it.evaluation?.result == AnswerEvaluationResult.REVIEW }.map { answer ->
                    WrongQuestionResult(
                        source = WrongQuestionSource.QUIZ,
                        bankId = answer.question.bankId,
                        bankName = bankNames[answer.question.bankId] ?: "未命名题库",
                        questionKey = answer.question.id,
                        questionText = answer.question.text,
                        options = answer.question.options,
                        correctAnswers = answer.question.answers,
                        correct = answer.correct,
                        questionType = answer.question.type,
                        referenceAnswer = answer.question.referenceAnswer,
                        acceptedAnswers = answer.question.acceptedAnswers,
                        explanation = answer.question.explanation,
                        category = answer.question.category,
                        sourceReference = answer.question.sourceReference,
                        userAnswer = answer.userAnswer,
                        hintUsed = answer.hintUsed,
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
                answers.filterNot { it.evaluation?.result == AnswerEvaluationResult.REVIEW }.map { answer ->
                    val existing = existingByKey[answer.question.id]
                    WrongQuestionResult(
                        source = existing?.source ?: WrongQuestionSource.QUIZ,
                        bankId = existing?.bankId ?: answer.question.bankId,
                        bankName = existing?.bankName ?: "错题复习",
                        questionKey = answer.question.id,
                        questionText = answer.question.text,
                        options = answer.question.options,
                        correctAnswers = answer.question.answers,
                        correct = answer.correct,
                        questionType = answer.question.type,
                        referenceAnswer = answer.question.referenceAnswer,
                        acceptedAnswers = answer.question.acceptedAnswers,
                        explanation = answer.question.explanation,
                        category = answer.question.category,
                        sourceReference = answer.question.sourceReference,
                        userAnswer = answer.userAnswer,
                        hintUsed = answer.hintUsed,
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
            ContrastPracticeType.ENGLISH_DEFINITION_TO_ENGLISH -> "对照练习：语义压缩"
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
        useMixedReviewPrompt: Boolean = false,
        persistGeneratedBank: Boolean = true,
        onProgress: (Float) -> Unit,
    ): List<ContrastQuestion> = withContext(Dispatchers.IO) {
        val storedSettings = preferences.readAiSettings()
        val generated = aiPracticeGateway.generateQuestions(
            settings = if (useMixedReviewPrompt) {
                storedSettings.copy(systemPrompt = storedSettings.mixedReviewPrompt)
            } else {
                storedSettings
            },
            targets = targets,
            distractorPool = distractorPool,
            type = type,
            optionCount = optionCount,
            difficulty = difficulty,
            onProgress = onProgress,
        )
        if (persistGeneratedBank) {
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
        }
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
        val remotePracticeAttempts = runCatching {
            gateway.fetchPracticeAttempts(config, session)
        }.getOrDefault(emptyList())
        database.upsertRemotePracticeAttempts(remotePracticeAttempts)
        val remoteParaphraseSeeds = runCatching {
            gateway.fetchParaphraseSeeds(config, session)
        }.getOrDefault(emptyList())
        database.upsertRemoteParaphraseSeeds(remoteParaphraseSeeds)

        val deletedTitleListIds = database.getDeletedTitleListIds()
        val deletedParaphraseSeedIds = database.getDeletedParaphraseSeedIds()
        val dirtyWords = database.getDirtyWords()
        val dirtyLogs = database.getDirtyReviewLogs()
        val dirtyTitleLists = database.getDirtyTitleLists()
        val dirtyWrongQuestions = database.getDirtyWrongQuestions()
        val dirtyPracticeAttempts = database.getDirtyPracticeAttempts()
        val dirtyParaphraseSeeds = database.getDirtyParaphraseSeeds()
        gateway.deleteTitleLists(config, session, deletedTitleListIds)
        database.clearDeletedTitleLists(deletedTitleListIds)
        val paraphraseDeletesUploaded = runCatching {
            gateway.deleteParaphraseSeeds(config, session, deletedParaphraseSeedIds)
        }.isSuccess
        if (paraphraseDeletesUploaded) database.clearDeletedParaphraseSeeds(deletedParaphraseSeedIds)
        gateway.upsertWords(config, session, dirtyWords)
        gateway.upsertReviewLogs(config, session, dirtyLogs)
        gateway.upsertTitleLists(config, session, dirtyTitleLists)
        gateway.upsertWrongQuestions(config, session, dirtyWrongQuestions)
        val attemptsUploaded = runCatching {
            gateway.upsertPracticeAttempts(config, session, dirtyPracticeAttempts)
        }.isSuccess
        val paraphraseSeedsUploaded = runCatching {
            gateway.upsertParaphraseSeeds(config, session, dirtyParaphraseSeeds)
        }.isSuccess
        database.markWordsClean(dirtyWords.map(WordEntry::id))
        database.markReviewLogsClean(dirtyLogs.map { it.id })
        database.markTitleListsClean(dirtyTitleLists.map { it.id })
        database.markWrongQuestionsClean(dirtyWrongQuestions.map { it.id })
        if (attemptsUploaded) {
            database.markPracticeAttemptsClean(dirtyPracticeAttempts.map(PracticeAttempt::id))
        }
        if (paraphraseSeedsUploaded) {
            database.markParaphraseSeedsClean(dirtyParaphraseSeeds.map(ParaphraseSeed::id))
        }
        refreshLocal()
        SyncReport(
            downloadedParaphraseSeeds = remoteParaphraseSeeds.size,
            downloadedAttempts = remotePracticeAttempts.size,
            downloadedLogs = remoteLogs.size,
            downloadedTitleLists = remoteTitleLists.size,
            downloadedWrongQuestions = remoteWrongQuestions.size,
            downloadedWords = remoteWords.size,
            uploadedAttempts = if (attemptsUploaded) dirtyPracticeAttempts.size else 0,
            uploadedLogs = dirtyLogs.size,
            uploadedTitleLists = dirtyTitleLists.size,
            uploadedWrongQuestions = dirtyWrongQuestions.size,
            uploadedWords = dirtyWords.size,
            uploadedParaphraseSeeds = if (paraphraseSeedsUploaded) dirtyParaphraseSeeds.size else 0,
            pendingAttempts = if (attemptsUploaded) 0 else dirtyPracticeAttempts.size,
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
