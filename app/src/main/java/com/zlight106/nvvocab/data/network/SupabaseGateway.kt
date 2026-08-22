package com.zlight106.nvvocab.data.network

import com.zlight106.nvvocab.data.AuthSession
import com.zlight106.nvvocab.data.ContrastPracticeType
import com.zlight106.nvvocab.data.ParaphraseSeed
import com.zlight106.nvvocab.data.PracticeDifficulty
import com.zlight106.nvvocab.data.PracticeAttempt
import com.zlight106.nvvocab.data.PracticeAttemptMode
import com.zlight106.nvvocab.data.QuizOption
import com.zlight106.nvvocab.data.QuizQuestion
import com.zlight106.nvvocab.data.QuizQuestionType
import com.zlight106.nvvocab.data.QuizSource
import com.zlight106.nvvocab.data.ReviewLogEntry
import com.zlight106.nvvocab.data.SupabaseConfig
import com.zlight106.nvvocab.data.TitleListEntry
import com.zlight106.nvvocab.data.WordEntry
import com.zlight106.nvvocab.data.WrongQuestionEntry
import com.zlight106.nvvocab.data.WrongQuestionSource
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class AuthOutcome(
    val message: String,
    val session: AuthSession?,
)

class SupabaseGateway {
    suspend fun signIn(config: SupabaseConfig, email: String, password: String): AuthOutcome =
        withContext(Dispatchers.IO) {
            validateConfig(config)
            val response = request(
                config = config,
                path = "/auth/v1/token?grant_type=password",
                method = "POST",
                body = JSONObject().put("email", email.trim()).put("password", password).toString(),
            )
            val session = parseSession(JSONObject(response), email.trim())
                ?: error("登录响应中没有有效会话。")
            AuthOutcome(message = "登录成功", session = session)
        }

    suspend fun signUp(config: SupabaseConfig, email: String, password: String): AuthOutcome =
        withContext(Dispatchers.IO) {
            validateConfig(config)
            val response = request(
                config = config,
                path = "/auth/v1/signup",
                method = "POST",
                body = JSONObject().put("email", email.trim()).put("password", password).toString(),
            )
            val session = parseSession(JSONObject(response), email.trim())
            AuthOutcome(
                message = if (session == null) "注册成功，请验证邮箱后登录" else "注册并登录成功",
                session = session,
            )
        }

    suspend fun refreshSession(config: SupabaseConfig, session: AuthSession): AuthSession =
        withContext(Dispatchers.IO) {
            require(session.refreshToken.isNotBlank()) { "当前会话无法刷新，请重新登录。" }
            val response = request(
                config = config,
                path = "/auth/v1/token?grant_type=refresh_token",
                method = "POST",
                body = JSONObject().put("refresh_token", session.refreshToken).toString(),
            )
            parseSession(JSONObject(response), session.email) ?: error("会话刷新失败。")
        }

    suspend fun upsertWords(
        config: SupabaseConfig,
        session: AuthSession,
        words: List<WordEntry>,
    ) = withContext(Dispatchers.IO) {
        if (words.isEmpty()) return@withContext
        val payload = JSONArray()
        words.forEach { word ->
            payload.put(
                JSONObject()
                    .put("id", word.id)
                    .put("user_id", session.userId)
                    .put("words", word.spelling)
                    .put("phonetic", word.phonetic ?: JSONObject.NULL)
                    .put("translate", word.translation)
                    .put("book_tag", word.bookTag)
                    .put("introtime", Instant.ofEpochMilli(word.introTime).toString())
                    .put("repetitions", word.repetitions)
                    .put("interval", word.intervalDays)
                    .put("easiness", word.easiness)
                    .put("next_review_at", Instant.ofEpochMilli(word.nextReviewAt).toString())
                    .put("wrong_count", word.wrongCount),
            )
        }
        request(
            config = config,
            path = "/rest/v1/wordbase?on_conflict=id",
            method = "POST",
            body = payload.toString(),
            accessToken = session.accessToken,
            prefer = "resolution=merge-duplicates,return=minimal",
        )
    }

    suspend fun upsertReviewLogs(
        config: SupabaseConfig,
        session: AuthSession,
        logs: List<ReviewLogEntry>,
    ) = withContext(Dispatchers.IO) {
        if (logs.isEmpty()) return@withContext
        val payload = JSONArray()
        logs.forEach { log ->
            payload.put(
                JSONObject()
                    .put("id", log.id)
                    .put("user_id", session.userId)
                    .put("word_id", log.wordId)
                    .put("reviewed_at", Instant.ofEpochMilli(log.reviewedAt).toString())
                    .put("quality", log.quality),
            )
        }
        request(
            config = config,
            path = "/rest/v1/review_logs?on_conflict=id",
            method = "POST",
            body = payload.toString(),
            accessToken = session.accessToken,
            prefer = "resolution=merge-duplicates,return=minimal",
        )
    }

    suspend fun fetchWords(config: SupabaseConfig, session: AuthSession): List<WordEntry> =
        withContext(Dispatchers.IO) {
            val response = request(
                config = config,
                path = "/rest/v1/wordbase?select=id,user_id,words,phonetic,translate,book_tag,introtime,repetitions,interval,easiness,next_review_at,wrong_count&order=introtime.asc",
                method = "GET",
                accessToken = session.accessToken,
            )
            val payload = JSONArray(response)
            buildList {
                for (index in 0 until payload.length()) {
                    val item = payload.getJSONObject(index)
                    add(
                        WordEntry(
                            id = item.getString("id"),
                            userId = item.optString("user_id").ifBlank { session.userId },
                            spelling = item.getString("words"),
                            phonetic = item.optNullableString("phonetic"),
                            translation = item.getString("translate"),
                            bookTag = item.optString("book_tag", "未分类"),
                            introTime = parseInstant(item.optString("introtime")),
                            repetitions = item.optInt("repetitions", 0),
                            intervalDays = item.optInt("interval", 1),
                            easiness = item.optDouble("easiness", 2.5),
                            nextReviewAt = parseInstant(item.optString("next_review_at")),
                            wrongCount = item.optInt("wrong_count", 0),
                            dirty = false,
                        ),
                    )
                }
            }
        }

    suspend fun fetchReviewLogs(
        config: SupabaseConfig,
        session: AuthSession,
    ): List<ReviewLogEntry> = withContext(Dispatchers.IO) {
        val response = request(
            config = config,
            path = "/rest/v1/review_logs?select=id,user_id,word_id,reviewed_at,quality&order=reviewed_at.asc",
            method = "GET",
            accessToken = session.accessToken,
        )
        val payload = JSONArray(response)
        buildList {
            for (index in 0 until payload.length()) {
                val item = payload.getJSONObject(index)
                add(
                    ReviewLogEntry(
                        id = item.getString("id"),
                        userId = item.optString("user_id").ifBlank { session.userId },
                        wordId = item.getString("word_id"),
                        reviewedAt = parseInstant(item.optString("reviewed_at")),
                        quality = item.optInt("quality", 0).coerceIn(0, 5),
                        dirty = false,
                    ),
                )
            }
        }
    }

    suspend fun upsertTitleLists(
        config: SupabaseConfig,
        session: AuthSession,
        entries: List<TitleListEntry>,
    ) = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) return@withContext
        val payload = JSONArray()
        entries.forEach { entry ->
            payload.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("user_id", session.userId)
                    .put("name", entry.name)
                    .put("source", entry.source.name)
                    .put("practice_type", entry.practiceType?.name ?: JSONObject.NULL)
                    .put("difficulty", entry.difficulty?.name ?: JSONObject.NULL)
                    .put("questions", entry.questions.toJson())
                    .put("imported_at", Instant.ofEpochMilli(entry.importedAt).toString()),
            )
        }
        request(
            config = config,
            path = "/rest/v1/titlelist?on_conflict=id",
            method = "POST",
            body = payload.toString(),
            accessToken = session.accessToken,
            prefer = "resolution=merge-duplicates,return=minimal",
        )
    }

    suspend fun fetchTitleLists(config: SupabaseConfig, session: AuthSession): List<TitleListEntry> =
        withContext(Dispatchers.IO) {
            val response = request(
                config = config,
                path = "/rest/v1/titlelist?select=id,user_id,name,source,practice_type,difficulty,questions,imported_at&order=imported_at.asc",
                method = "GET",
                accessToken = session.accessToken,
            )
            val payload = JSONArray(response)
            buildList {
                for (index in 0 until payload.length()) {
                    val item = payload.getJSONObject(index)
                    val bankId = item.getString("id")
                    add(
                        TitleListEntry(
                            id = bankId,
                            userId = item.optString("user_id").ifBlank { session.userId },
                            name = item.optString("name", "未命名题库"),
                            source = item.optEnum("source", QuizSource.XML),
                            practiceType = item.optEnumOrNull<ContrastPracticeType>("practice_type"),
                            difficulty = item.optEnumOrNull<PracticeDifficulty>("difficulty"),
                            importedAt = parseInstant(item.optString("imported_at")),
                            questions = item.optJSONArray("questions").toQuizQuestions(bankId),
                            dirty = false,
                        ),
                    )
                }
            }
        }

    suspend fun deleteTitleLists(
        config: SupabaseConfig,
        session: AuthSession,
        ids: List<String>,
    ) = withContext(Dispatchers.IO) {
        ids.forEach { id ->
            val encodedId = URLEncoder.encode(id, StandardCharsets.UTF_8.toString())
            request(
                config = config,
                path = "/rest/v1/titlelist?id=eq.$encodedId",
                method = "DELETE",
                accessToken = session.accessToken,
                prefer = "return=minimal",
            )
        }
    }

    suspend fun upsertWrongQuestions(
        config: SupabaseConfig,
        session: AuthSession,
        entries: List<WrongQuestionEntry>,
    ) = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) return@withContext
        val send: (Boolean) -> Unit = { includeMetadata ->
            request(
                config = config,
                path = "/rest/v1/wrong_questions?on_conflict=user_id,source,question_key",
                method = "POST",
                body = wrongQuestionPayload(entries, session.userId, includeMetadata).toString(),
                accessToken = session.accessToken,
                prefer = "resolution=merge-duplicates,return=minimal",
            )
            Unit
        }
        runCatching { send(true) }.getOrElse { send(false) }
    }

    suspend fun fetchWrongQuestions(
        config: SupabaseConfig,
        session: AuthSession,
    ): List<WrongQuestionEntry> = withContext(Dispatchers.IO) {
        val baseColumns = "id,user_id,source,bank_id,bank_name,question_key,question_text,options," +
            "correct_answers,wrong_count,correct_count,favorite,ai_analysis,last_wrong_at,last_reviewed_at"
        val metadataColumns = ",question_type,reference_answer,accepted_answers,explanation,category," +
            "source_reference,last_user_answer,hint_used_count"
        val response = runCatching {
            request(
                config = config,
                path = "/rest/v1/wrong_questions?select=$baseColumns$metadataColumns&order=last_wrong_at.desc",
                method = "GET",
                accessToken = session.accessToken,
            )
        }.getOrElse {
            request(
                config = config,
                path = "/rest/v1/wrong_questions?select=$baseColumns&order=last_wrong_at.desc",
                method = "GET",
                accessToken = session.accessToken,
            )
        }
        val payload = JSONArray(response)
        buildList {
            for (index in 0 until payload.length()) {
                val item = payload.getJSONObject(index)
                val optionsPayload = item.optJSONArray("options") ?: JSONArray()
                val options = buildList {
                    for (optionIndex in 0 until optionsPayload.length()) {
                        val option = optionsPayload.optJSONObject(optionIndex) ?: continue
                        add(QuizOption(option.optString("id"), option.optString("text")))
                    }
                }
                val answersPayload = item.optJSONArray("correct_answers") ?: JSONArray()
                val answers = buildSet {
                    for (answerIndex in 0 until answersPayload.length()) {
                        answersPayload.optString(answerIndex).takeIf(String::isNotBlank)?.let(::add)
                    }
                }
                add(
                    WrongQuestionEntry(
                        id = item.getString("id"),
                        userId = item.optString("user_id").ifBlank { session.userId },
                        source = item.optEnum("source", WrongQuestionSource.QUIZ),
                        bankId = item.optNullableString("bank_id"),
                        bankName = item.optString("bank_name", "未命名题库"),
                        questionKey = item.getString("question_key"),
                        questionText = item.getString("question_text"),
                        options = options,
                        correctAnswers = answers,
                        wrongCount = item.optInt("wrong_count", 1),
                        correctCount = item.optInt("correct_count", 0),
                        favorite = item.optBoolean("favorite", false),
                        aiAnalysis = item.optNullableString("ai_analysis"),
                        lastWrongAt = parseInstant(item.optString("last_wrong_at")),
                        lastReviewedAt = item.optNullableString("last_reviewed_at")?.let(::parseInstant),
                        dirty = false,
                        questionType = item.optEnum("question_type", QuizQuestionType.MULTIPLE_CHOICE),
                        referenceAnswer = item.optNullableString("reference_answer"),
                        acceptedAnswers = item.optJSONArray("accepted_answers").toStringSet(),
                        explanation = item.optNullableString("explanation"),
                        category = item.optNullableString("category"),
                        sourceReference = item.optNullableString("source_reference"),
                        lastUserAnswer = item.optNullableString("last_user_answer"),
                        hintUsedCount = item.optInt("hint_used_count", 0),
                    ),
                )
            }
        }
    }

    suspend fun upsertParaphraseSeeds(
        config: SupabaseConfig,
        session: AuthSession,
        entries: List<ParaphraseSeed>,
    ) = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) return@withContext
        val payload = JSONArray().apply {
            entries.forEach { entry ->
                put(
                    JSONObject()
                        .put("id", entry.id)
                        .put("user_id", session.userId)
                        .put("source_text", entry.sourceText)
                        .put("target_text", entry.targetText)
                        .put("context_text", entry.contextText ?: JSONObject.NULL)
                        .put("source_reference", entry.sourceReference ?: JSONObject.NULL)
                        .put("notes", entry.notes ?: JSONObject.NULL)
                        .put("created_at", Instant.ofEpochMilli(entry.createdAt).toString())
                        .put("updated_at", Instant.ofEpochMilli(entry.updatedAt).toString()),
                )
            }
        }
        request(
            config = config,
            path = "/rest/v1/paraphrase_seeds?on_conflict=id",
            method = "POST",
            body = payload.toString(),
            accessToken = session.accessToken,
            prefer = "resolution=merge-duplicates,return=minimal",
        )
    }

    suspend fun fetchParaphraseSeeds(
        config: SupabaseConfig,
        session: AuthSession,
    ): List<ParaphraseSeed> = withContext(Dispatchers.IO) {
        val columns = "id,user_id,source_text,target_text,context_text,source_reference,notes,created_at,updated_at"
        val response = request(
            config = config,
            path = "/rest/v1/paraphrase_seeds?select=$columns&order=created_at.asc,id.asc",
            method = "GET",
            accessToken = session.accessToken,
        )
        val payload = JSONArray(response)
        buildList {
            for (index in 0 until payload.length()) {
                val item = payload.getJSONObject(index)
                add(
                    ParaphraseSeed(
                        id = item.getString("id"),
                        userId = item.optString("user_id").ifBlank { session.userId },
                        sourceText = item.getString("source_text"),
                        targetText = item.getString("target_text"),
                        contextText = item.optNullableString("context_text"),
                        sourceReference = item.optNullableString("source_reference"),
                        notes = item.optNullableString("notes"),
                        createdAt = parseInstant(item.getString("created_at")),
                        updatedAt = parseInstant(item.getString("updated_at")),
                        dirty = false,
                    ),
                )
            }
        }
    }

    suspend fun deleteParaphraseSeeds(
        config: SupabaseConfig,
        session: AuthSession,
        ids: List<String>,
    ) = withContext(Dispatchers.IO) {
        ids.forEach { id ->
            val encodedId = URLEncoder.encode(id, StandardCharsets.UTF_8.toString())
            request(
                config = config,
                path = "/rest/v1/paraphrase_seeds?id=eq.$encodedId",
                method = "DELETE",
                accessToken = session.accessToken,
                prefer = "return=minimal",
            )
        }
    }

    suspend fun upsertPracticeAttempts(
        config: SupabaseConfig,
        session: AuthSession,
        attempts: List<PracticeAttempt>,
    ) = withContext(Dispatchers.IO) {
        if (attempts.isEmpty()) return@withContext
        val payload = JSONArray()
        attempts.forEach { attempt ->
            payload.put(
                JSONObject()
                    .put("id", attempt.id)
                    .put("user_id", session.userId)
                    .put("session_id", attempt.sessionId)
                    .put("item_id", attempt.itemId)
                    .put("source_id", attempt.sourceId ?: JSONObject.NULL)
                    .put("mode", attempt.mode.name)
                    .put("sequence_index", attempt.sequenceIndex)
                    .put("question", attempt.question)
                    .put(
                        "options",
                        JSONArray().apply {
                            attempt.options.forEach { option ->
                                put(JSONObject().put("id", option.id).put("text", option.text))
                            }
                        },
                    )
                    .put("first_answer", attempt.firstAnswer)
                    .put("final_answer", attempt.finalAnswer)
                    .put("reference_answer", attempt.referenceAnswer)
                    .put("accepted_answers", JSONArray(attempt.acceptedAnswers.sorted()))
                    .put("explanation", attempt.explanation ?: JSONObject.NULL)
                    .put("correct", attempt.correct)
                    .put("first_answer_correct", attempt.firstAnswerCorrect)
                    .put("active_time_ms", attempt.activeTimeMs)
                    .put("hint_used", attempt.hintUsed)
                    .put("answered_at", Instant.ofEpochMilli(attempt.timestamp).toString()),
            )
        }
        request(
            config = config,
            path = "/rest/v1/practice_attempts?on_conflict=id",
            method = "POST",
            body = payload.toString(),
            accessToken = session.accessToken,
            prefer = "resolution=merge-duplicates,return=minimal",
        )
    }

    suspend fun fetchPracticeAttempts(
        config: SupabaseConfig,
        session: AuthSession,
    ): List<PracticeAttempt> = withContext(Dispatchers.IO) {
        val columns = "id,user_id,session_id,item_id,source_id,mode,sequence_index,question,options,first_answer," +
            "final_answer,reference_answer,accepted_answers,explanation,correct,first_answer_correct," +
            "active_time_ms,hint_used,answered_at"
        val response = request(
            config = config,
            path = "/rest/v1/practice_attempts?select=$columns&order=answered_at.asc,sequence_index.asc",
            method = "GET",
            accessToken = session.accessToken,
        )
        val payload = JSONArray(response)
        buildList {
            for (index in 0 until payload.length()) {
                val item = payload.getJSONObject(index)
                val optionPayload = item.optJSONArray("options") ?: JSONArray()
                val options = buildList {
                    for (optionIndex in 0 until optionPayload.length()) {
                        val option = optionPayload.optJSONObject(optionIndex) ?: continue
                        add(QuizOption(option.optString("id"), option.optString("text")))
                    }
                }
                add(
                    PracticeAttempt(
                        id = item.getString("id"),
                        userId = item.optString("user_id").ifBlank { session.userId },
                        sessionId = item.getString("session_id"),
                        itemId = item.getString("item_id"),
                        sourceId = item.optNullableString("source_id"),
                        mode = item.optEnum("mode", PracticeAttemptMode.QUIZ_CHOICE),
                        sequenceIndex = item.optInt("sequence_index"),
                        question = item.optString("question"),
                        options = options,
                        firstAnswer = item.optString("first_answer"),
                        finalAnswer = item.optString("final_answer"),
                        referenceAnswer = item.optString("reference_answer"),
                        acceptedAnswers = item.optJSONArray("accepted_answers").toStringSet(),
                        explanation = item.optNullableString("explanation"),
                        correct = item.optBoolean("correct"),
                        firstAnswerCorrect = item.optBoolean("first_answer_correct"),
                        activeTimeMs = item.optLong("active_time_ms").coerceAtLeast(0L),
                        hintUsed = item.optBoolean("hint_used"),
                        timestamp = parseInstant(item.optString("answered_at")),
                        dirty = false,
                    ),
                )
            }
        }
    }

    private fun List<QuizQuestion>.toJson(): JSONArray = JSONArray().apply {
        forEach { question ->
            put(
                JSONObject()
                    .put("id", question.id)
                    .put("original_index", question.originalIndex)
                    .put("score", question.score)
                    .put("text", question.text)
                    .put("question_type", question.type.name)
                    .put("reference_answer", question.referenceAnswer ?: JSONObject.NULL)
                    .put("accepted_answers", JSONArray(question.acceptedAnswers.sorted()))
                    .put("explanation", question.explanation ?: JSONObject.NULL)
                    .put("category", question.category ?: JSONObject.NULL)
                    .put("source_reference", question.sourceReference ?: JSONObject.NULL)
                    .put(
                        "options",
                        JSONArray().apply {
                            question.options.forEach { option ->
                                put(JSONObject().put("id", option.id).put("text", option.text))
                            }
                        },
                    )
                    .put("answers", JSONArray(question.answers.sorted())),
            )
        }
    }

    private fun JSONArray?.toQuizQuestions(bankId: String): List<QuizQuestion> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val question = optJSONObject(index) ?: continue
                val optionsPayload = question.optJSONArray("options") ?: JSONArray()
                val options = buildList {
                    for (optionIndex in 0 until optionsPayload.length()) {
                        val option = optionsPayload.optJSONObject(optionIndex) ?: continue
                        val id = option.optString("id").trim()
                        val text = option.optString("text").trim()
                        if (id.isNotBlank() && text.isNotBlank()) add(QuizOption(id, text))
                    }
                }
                val answersPayload = question.optJSONArray("answers") ?: JSONArray()
                val answers = buildSet {
                    for (answerIndex in 0 until answersPayload.length()) {
                        answersPayload.optString(answerIndex).trim().takeIf(String::isNotBlank)?.let(::add)
                    }
                }
                add(
                    QuizQuestion(
                        id = question.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                        bankId = bankId,
                        originalIndex = question.optInt("original_index", index),
                        score = question.optInt("score", 10),
                        text = question.optString("text"),
                        options = options,
                        answers = answers,
                        type = question.optEnum("question_type", QuizQuestionType.MULTIPLE_CHOICE),
                        referenceAnswer = question.optNullableString("reference_answer"),
                        acceptedAnswers = question.optJSONArray("accepted_answers").toStringSet(),
                        explanation = question.optNullableString("explanation"),
                        category = question.optNullableString("category"),
                        sourceReference = question.optNullableString("source_reference"),
                    ),
                )
            }
        }
    }

    private inline fun <reified T : Enum<T>> JSONObject.optEnumOrNull(key: String): T? =
        optNullableString(key)?.let { value -> runCatching { enumValueOf<T>(value) }.getOrNull() }

    private inline fun <reified T : Enum<T>> JSONObject.optEnum(key: String, fallback: T): T =
        optEnumOrNull<T>(key) ?: fallback

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else optString(key).trim().takeIf(String::isNotEmpty)

    private fun JSONArray?.toStringSet(): Set<String> {
        if (this == null) return emptySet()
        return buildSet {
            for (index in 0 until length()) {
                optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }

    private fun wrongQuestionPayload(
        entries: List<WrongQuestionEntry>,
        userId: String,
        includeMetadata: Boolean,
    ): JSONArray = JSONArray().apply {
        entries.forEach { entry ->
            put(
                JSONObject()
                    .put("id", entry.id)
                    .put("user_id", userId)
                    .put("source", entry.source.name)
                    .put("bank_id", entry.bankId ?: JSONObject.NULL)
                    .put("bank_name", entry.bankName)
                    .put("question_key", entry.questionKey)
                    .put("question_text", entry.questionText)
                    .put(
                        "options",
                        JSONArray().apply {
                            entry.options.forEach { option ->
                                put(JSONObject().put("id", option.id).put("text", option.text))
                            }
                        },
                    )
                    .put("correct_answers", JSONArray(entry.correctAnswers.sorted()))
                    .put("wrong_count", entry.wrongCount)
                    .put("correct_count", entry.correctCount)
                    .put("favorite", entry.favorite)
                    .put("ai_analysis", entry.aiAnalysis ?: JSONObject.NULL)
                    .put("last_wrong_at", Instant.ofEpochMilli(entry.lastWrongAt).toString())
                    .put(
                        "last_reviewed_at",
                        entry.lastReviewedAt?.let { Instant.ofEpochMilli(it).toString() } ?: JSONObject.NULL,
                    )
                    .apply {
                        if (includeMetadata) {
                            put("question_type", entry.questionType.name)
                            put("reference_answer", entry.referenceAnswer ?: JSONObject.NULL)
                            put("accepted_answers", JSONArray(entry.acceptedAnswers.sorted()))
                            put("explanation", entry.explanation ?: JSONObject.NULL)
                            put("category", entry.category ?: JSONObject.NULL)
                            put("source_reference", entry.sourceReference ?: JSONObject.NULL)
                            put("last_user_answer", entry.lastUserAnswer ?: JSONObject.NULL)
                            put("hint_used_count", entry.hintUsedCount)
                        }
                    },
            )
        }
    }

    private fun parseSession(payload: JSONObject, fallbackEmail: String): AuthSession? {
        val accessToken = payload.optString("access_token")
        if (accessToken.isBlank()) return null
        val user = payload.optJSONObject("user") ?: return null
        val expiresIn = payload.optLong("expires_in", 3600L)
        return AuthSession(
            accessToken = accessToken,
            refreshToken = payload.optString("refresh_token"),
            userId = user.getString("id"),
            email = user.optString("email", fallbackEmail),
            expiresAtEpochSeconds = Instant.now().epochSecond + expiresIn,
        )
    }

    private fun parseInstant(value: String): Long = runCatching {
        Instant.parse(value).toEpochMilli()
    }.getOrDefault(System.currentTimeMillis())

    private fun validateConfig(config: SupabaseConfig) {
        require(config.url.startsWith("http://") || config.url.startsWith("https://")) {
            "Supabase Project URL 无效。"
        }
        require(config.publishableKey.isNotBlank()) { "Supabase Publishable Key 不能为空。" }
        require(config.publishableKey.all { it.code in 33..126 }) {
            "Supabase Key 包含不支持的字符。"
        }
    }

    private fun request(
        config: SupabaseConfig,
        path: String,
        method: String,
        body: String? = null,
        accessToken: String? = null,
        prefer: String? = null,
    ): String {
        val connection = URL(config.url.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 25_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("apikey", config.publishableKey)
            connection.setRequestProperty("Authorization", "Bearer ${accessToken ?: config.publishableKey}")
            prefer?.let { connection.setRequestProperty("Prefer", it) }
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            }
            val success = connection.responseCode in 200..299
            val stream = if (success) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (!success) {
                val message = runCatching {
                    val payload = JSONObject(response)
                    payload.optString("message")
                        .ifBlank { payload.optString("msg") }
                        .ifBlank { payload.optString("error_description") }
                        .ifBlank { payload.optString("error") }
                }.getOrNull().orEmpty().ifBlank { "Supabase 请求失败，HTTP ${connection.responseCode}。" }
                error(message)
            }
            return response
        } finally {
            connection.disconnect()
        }
    }
}
