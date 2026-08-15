package com.zlight106.nvvocab.data.network

import com.zlight106.nvvocab.data.AuthSession
import com.zlight106.nvvocab.data.ContrastPracticeType
import com.zlight106.nvvocab.data.PracticeDifficulty
import com.zlight106.nvvocab.data.QuizOption
import com.zlight106.nvvocab.data.QuizQuestion
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
        val payload = JSONArray().apply {
            entries.forEach { entry ->
                put(
                    JSONObject()
                        .put("id", entry.id)
                        .put("user_id", session.userId)
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
                        ),
                )
            }
        }
        request(
            config = config,
            path = "/rest/v1/wrong_questions?on_conflict=user_id,source,question_key",
            method = "POST",
            body = payload.toString(),
            accessToken = session.accessToken,
            prefer = "resolution=merge-duplicates,return=minimal",
        )
    }

    suspend fun fetchWrongQuestions(
        config: SupabaseConfig,
        session: AuthSession,
    ): List<WrongQuestionEntry> = withContext(Dispatchers.IO) {
        val response = request(
            config = config,
            path = "/rest/v1/wrong_questions?select=id,user_id,source,bank_id,bank_name,question_key,question_text,options,correct_answers,wrong_count,correct_count,favorite,ai_analysis,last_wrong_at,last_reviewed_at&order=last_wrong_at.desc",
            method = "GET",
            accessToken = session.accessToken,
        )
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
