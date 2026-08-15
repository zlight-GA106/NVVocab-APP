package com.zlight106.nvvocab.data.network

import com.zlight106.nvvocab.data.AiSettings
import com.zlight106.nvvocab.data.AiProvider
import com.zlight106.nvvocab.data.ContrastPracticeType
import com.zlight106.nvvocab.data.ContrastQuestion
import com.zlight106.nvvocab.data.ParsedQuizQuestion
import com.zlight106.nvvocab.data.PracticeDifficulty
import com.zlight106.nvvocab.data.WordEntry
import com.zlight106.nvvocab.data.WrongQuestionEntry
import com.zlight106.nvvocab.domain.AnswerPositionPlanner
import com.zlight106.nvvocab.domain.QuizXmlParser
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class AiPracticeGateway {
    suspend fun testConnection(settings: AiSettings): String = withContext(Dispatchers.IO) {
        validateSettings(settings)
        val body = JSONObject()
            .put("model", settings.model)
            .put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", "Reply with OK only."),
                ),
            )
            .put("temperature", 0)
            .put("max_tokens", 4)
            .put("stream", false)
            .apply {
                if (settings.provider == AiProvider.DEEPSEEK) {
                    put("thinking", JSONObject().put("type", "disabled"))
                }
            }
        val response = JSONObject(executeRequest(settings, body))
        val content = response.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            .orEmpty()
        require(content.isNotBlank()) { "AI 服务返回了空响应。" }
        "AI 连接测试成功"
    }

    suspend fun generateQuestions(
        settings: AiSettings,
        targets: List<WordEntry>,
        distractorPool: List<WordEntry>,
        type: ContrastPracticeType,
        optionCount: Int,
        difficulty: PracticeDifficulty,
        onProgress: (Float) -> Unit,
    ): List<ContrastQuestion> = withContext(Dispatchers.IO) {
        validateSettings(settings)
        require(targets.isNotEmpty()) { "当前范围没有可生成题目的单词。" }
        val safeOptionCount = optionCount.coerceIn(2, 8)
        val batches = targets.chunked(BATCH_SIZE)
        val correctPositions = AnswerPositionPlanner.distributed(targets.size, safeOptionCount)
        var positionOffset = 0
        val generated = buildList {
            batches.forEachIndexed { index, batch ->
                val batchPositions = correctPositions.subList(positionOffset, positionOffset + batch.size)
                addAll(
                    generateBatch(
                        settings = settings,
                        targets = batch,
                        distractorPool = distractorPool,
                        type = type,
                        optionCount = safeOptionCount,
                        difficulty = difficulty,
                        correctPositions = batchPositions,
                    ),
                )
                positionOffset += batch.size
                onProgress((index + 1f) / batches.size)
            }
        }
        generated
    }

    suspend fun analyzeWrongQuestion(
        settings: AiSettings,
        entry: WrongQuestionEntry,
    ): String = withContext(Dispatchers.IO) {
        validateSettings(settings)
        require(settings.analysisPrompt.isNotBlank()) { "请先配置错题解析提示词。" }
        val options = entry.options.joinToString("\n") { "${it.id}. ${it.text}" }
        val questionContext = """
            题库：${entry.bankName}
            题目：${entry.questionText}
            选项：
            $options
            正确答案：${entry.correctAnswers.sorted().joinToString("、")}
            历史错误次数：${entry.wrongCount}
            历史正确次数：${entry.correctCount}
        """.trimIndent()
        val body = JSONObject()
            .put("model", settings.model)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", settings.analysisPrompt))
                    .put(JSONObject().put("role", "user").put("content", questionContext)),
            )
            .put("temperature", 0.2)
            .put("max_tokens", 900)
            .put("stream", false)
            .apply {
                if (settings.provider == AiProvider.DEEPSEEK) {
                    put("thinking", JSONObject().put("type", "disabled"))
                }
            }
        val response = JSONObject(executeRequest(settings, body))
        val content = response.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            .orEmpty()
            .trim()
        require(content.isNotBlank()) { "AI 未返回有效的错题解析。" }
        content
    }

    private fun generateBatch(
        settings: AiSettings,
        targets: List<WordEntry>,
        distractorPool: List<WordEntry>,
        type: ContrastPracticeType,
        optionCount: Int,
        difficulty: PracticeDifficulty,
        correctPositions: List<Int>,
    ): List<ContrastQuestion> {
        val body = buildRequestBody(settings, targets, distractorPool, type, optionCount, difficulty)
        return parseResponse(
            responseText = executeRequest(settings, body),
            targets = targets,
            distractorPool = distractorPool,
            type = type,
            optionCount = optionCount,
            correctPositions = correctPositions,
        )
    }

    private fun executeRequest(settings: AiSettings, body: JSONObject): String {
        val endpoint = "${settings.baseUrl.trimEnd('/')}/chat/completions"
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20_000
            connection.readTimeout = 120_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
            connection.outputStream.use { output ->
                output.write(body.toString().toByteArray(StandardCharsets.UTF_8))
            }
            val status = connection.responseCode
            val responseText = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(StandardCharsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            if (status !in 200..299) {
                val message = runCatching {
                    JSONObject(responseText).optJSONObject("error")?.optString("message")
                }.getOrNull().orEmpty()
                error(message.ifBlank { "AI 请求失败，HTTP $status。" })
            }
            responseText
        } finally {
            connection.disconnect()
        }
    }

    private fun validateSettings(settings: AiSettings) {
        require(settings.baseUrl.startsWith("http://") || settings.baseUrl.startsWith("https://")) {
            "AI Base URL 无效。"
        }
        require(settings.apiKey.isNotBlank()) { "请先在设置中填写 AI API Key。" }
        require(settings.apiKey.all { it.code in 33..126 }) { "AI API Key 包含无效字符，请重新填写。" }
        require(settings.model.isNotBlank()) { "请先配置 AI 模型。" }
    }

    private fun buildRequestBody(
        settings: AiSettings,
        targets: List<WordEntry>,
        distractorPool: List<WordEntry>,
        type: ContrastPracticeType,
        optionCount: Int,
        difficulty: PracticeDifficulty,
    ): JSONObject {
        val schemaInstruction = """
            只输出 XML 原文，不得输出 Markdown 或解释。根元素必须是 quiz，不得生成 password。
            每个目标单词按输入顺序生成一道 question，question 的 score 固定为 10。
            每题必须有一个 text、恰好 $optionCount 个不重复的 option，以及一个 answer。
            option id 从 A 开始连续排列，answer 的文本必须是正确 option 的 id。
            正确选项必须逐字使用输入词库中的单词或翻译。
        """.trimIndent()
        val typeInstruction = when (type) {
            ContrastPracticeType.CHINESE_TO_ENGLISH ->
                "题干使用目标单词的中文翻译，选项全部使用英文单词，正确答案为目标英文单词。"
            ContrastPracticeType.ENGLISH_DEFINITION_TO_ENGLISH ->
                "题干使用简洁、准确且不直接出现目标单词的英文释义，选项全部使用英文单词，正确答案为目标英文单词。"
            ContrastPracticeType.ENGLISH_TO_CHINESE ->
                "题干使用目标英文单词，选项全部使用中文翻译，正确答案为输入中的目标中文翻译。"
        }
        val difficultyInstruction = when (difficulty) {
            PracticeDifficulty.EASY -> "干扰项应与正确答案差异明显。"
            PracticeDifficulty.MEDIUM -> "干扰项应属于相近主题，但仍可通过词义判断。"
            PracticeDifficulty.HARD -> "干扰项应在拼写或语义上接近正确答案，但不得产生多个正确答案。"
        }
        val targetArray = JSONArray().apply {
            targets.forEach { word ->
                put(
                    JSONObject()
                        .put("word_id", word.id)
                        .put("word", word.spelling)
                        .put("translation", word.translation),
                )
            }
        }
        val poolArray = JSONArray().apply {
            distractorPool.asSequence().distinctBy(WordEntry::id).take(MAX_POOL_SIZE).forEach { word ->
                put(
                    JSONObject()
                        .put("word", word.spelling)
                        .put("translation", word.translation),
                )
            }
        }
        val userContent = JSONObject()
            .put("task", type.name)
            .put("targets", targetArray)
            .put("distractor_pool", poolArray)
            .toString()
        return JSONObject()
            .put("model", settings.model)
            .put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "system")
                            .put("content", "${settings.systemPrompt}\n\n$schemaInstruction\n$typeInstruction\n$difficultyInstruction"),
                    )
                    .put(JSONObject().put("role", "user").put("content", userContent)),
            )
            .put("temperature", 0.35)
            .put("max_tokens", 4096)
            .put("stream", false)
            .apply {
                if (settings.provider == AiProvider.DEEPSEEK) {
                    put("thinking", JSONObject().put("type", "disabled"))
                }
            }
    }

    private fun parseResponse(
        responseText: String,
        targets: List<WordEntry>,
        distractorPool: List<WordEntry>,
        type: ContrastPracticeType,
        optionCount: Int,
        correctPositions: List<Int>,
    ): List<ContrastQuestion> {
        val response = JSONObject(responseText)
        val choices = response.optJSONArray("choices") ?: error("AI 返回中缺少 choices。")
        val content = choices.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            .orEmpty()
        require(content.isNotBlank()) { "AI 返回了空内容，请重试。" }
        val xml = extractQuizXml(content)
        val parsedQuestions = ByteArrayInputStream(xml.toByteArray(StandardCharsets.UTF_8)).use { input ->
            QuizXmlParser.parse(input, "ai-generated.xml").questions
        }
        require(parsedQuestions.size == targets.size) {
            "AI 返回了 ${parsedQuestions.size} 道题，但当前批次需要 ${targets.size} 道题。"
        }
        require(correctPositions.size == targets.size) { "答案位置规划与题目数量不一致。" }
        return targets.indices.map { index ->
            normalizeQuestion(
                source = parsedQuestions[index],
                target = targets[index],
                distractorPool = distractorPool,
                type = type,
                optionCount = optionCount,
                correctPosition = correctPositions[index],
            )
        }
    }

    private fun normalizeQuestion(
        source: ParsedQuizQuestion,
        target: WordEntry,
        distractorPool: List<WordEntry>,
        type: ContrastPracticeType,
        optionCount: Int,
        correctPosition: Int,
    ): ContrastQuestion {
        val expectedAnswer = when (type) {
            ContrastPracticeType.CHINESE_TO_ENGLISH,
            ContrastPracticeType.ENGLISH_DEFINITION_TO_ENGLISH,
            -> target.spelling.trim()
            ContrastPracticeType.ENGLISH_TO_CHINESE -> target.translation.trim()
        }
        val prompt = when (type) {
            ContrastPracticeType.CHINESE_TO_ENGLISH -> target.translation.trim()
            ContrastPracticeType.ENGLISH_TO_CHINESE -> target.spelling.trim()
            ContrastPracticeType.ENGLISH_DEFINITION_TO_ENGLISH -> source.text.trim()
        }
        require(prompt.isNotBlank()) { "AI 返回了空题干。" }
        require(source.options.size == optionCount) {
            "AI 返回了 ${source.options.size} 个选项，但当前设置需要 $optionCount 个选项。"
        }
        require(source.answers.size == 1) { "AI 返回的题目必须只有一个正确答案。" }
        val answerId = source.answers.single()
        val rawAnswerIndex = source.options.indexOfFirst { option -> option.id.equals(answerId, true) }
        require(rawAnswerIndex >= 0) { "AI 返回的答案未对应任何选项。" }
        val allowedOptions = distractorPool.mapTo(mutableSetOf()) { word ->
            when (type) {
                ContrastPracticeType.CHINESE_TO_ENGLISH,
                ContrastPracticeType.ENGLISH_DEFINITION_TO_ENGLISH,
                -> word.spelling.trim().lowercase()
                ContrastPracticeType.ENGLISH_TO_CHINESE -> word.translation.trim().lowercase()
            }
        }
        val candidateOptions = buildList {
            source.options.mapTo(this) { option -> option.text.trim() }
            distractorPool.forEach { word ->
                add(
                    when (type) {
                        ContrastPracticeType.CHINESE_TO_ENGLISH,
                        ContrastPracticeType.ENGLISH_DEFINITION_TO_ENGLISH,
                        -> word.spelling.trim()
                        ContrastPracticeType.ENGLISH_TO_CHINESE -> word.translation.trim()
                    },
                )
            }
        }.filter { it.lowercase() in allowedOptions }
            .distinctBy(String::lowercase)
            .filterNot { it.equals(expectedAnswer, ignoreCase = true) }
        require(candidateOptions.size >= optionCount - 1) { "词库中的可用干扰项不足，请减少选项数量。" }
        val options = candidateOptions.take(optionCount - 1).toMutableList()
        options.add(correctPosition.coerceIn(0, optionCount - 1), expectedAnswer)
        return ContrastQuestion(
            id = UUID.randomUUID().toString(),
            wordId = target.id,
            prompt = prompt,
            options = options,
            correctIndex = options.indexOf(expectedAnswer),
        )
    }

    private fun extractQuizXml(content: String): String {
        val withoutFence = content.trim()
            .removePrefix("```xml")
            .removePrefix("```XML")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = withoutFence.indexOf("<quiz")
        val endTag = "</quiz>"
        val end = withoutFence.lastIndexOf(endTag)
        require(start >= 0 && end >= start) { "AI 返回内容不是有效的 quiz XML。" }
        return withoutFence.substring(start, end + endTag.length)
    }

    private companion object {
        const val BATCH_SIZE = 6
        const val MAX_POOL_SIZE = 48
    }
}
