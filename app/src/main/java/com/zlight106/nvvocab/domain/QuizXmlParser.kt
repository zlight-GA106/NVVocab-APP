package com.zlight106.nvvocab.domain

import com.zlight106.nvvocab.data.ParsedQuizBank
import com.zlight106.nvvocab.data.ParsedQuizQuestion
import com.zlight106.nvvocab.data.QuizOption
import com.zlight106.nvvocab.data.QuizQuestionType
import java.io.InputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

object QuizXmlParser {
    fun parse(inputStream: InputStream, fileName: String): ParsedQuizBank {
        val handler = QuizHandler()
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
            isValidating = false
            setFeatureSafely("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeatureSafely("http://xml.org/sax/features/external-general-entities", false)
            setFeatureSafely("http://xml.org/sax/features/external-parameter-entities", false)
        }
        factory.newSAXParser().parse(inputStream, handler)
        require(handler.questions.isNotEmpty()) { "XML 中没有可导入的题目。" }
        return ParsedQuizBank(
            name = handler.bankTitle?.trim()?.takeIf(String::isNotBlank)
                ?: fileName.substringBeforeLast('.').trim().ifBlank { "未命名题库" },
            password = handler.password?.takeIf(String::isNotBlank),
            questions = handler.questions,
        )
    }

    private fun SAXParserFactory.setFeatureSafely(name: String, enabled: Boolean) {
        runCatching { setFeature(name, enabled) }
    }

    private class QuizHandler : DefaultHandler() {
        val questions = mutableListOf<ParsedQuizQuestion>()
        var password: String? = null
        var bankTitle: String? = null

        private var rootTag = ""
        private var activeTag: String? = null
        private val textBuffer = StringBuilder()
        private var score = 10
        private var questionType = QuizQuestionType.MULTIPLE_CHOICE
        private var questionText = ""
        private var answerText = ""
        private var referenceAnswer = ""
        private var explanation = ""
        private var category = ""
        private var sourceReference = ""
        private var optionId = ""
        private val options = mutableListOf<QuizOption>()
        private val acceptedAnswers = linkedSetOf<String>()

        override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
            val tag = qName.lowercase()
            if (rootTag.isBlank()) {
                rootTag = tag
                bankTitle = attributes.getValue("title")
            }
            activeTag = tag
            textBuffer.clear()
            when (tag) {
                "volume" -> category = attributes.getValue("id")?.trim()?.let { "第${it}卷" }.orEmpty()
                "question" -> {
                    score = attributes.getValue("score")?.toIntOrNull() ?: 10
                    questionType = when {
                        rootTag == "ncre_linux_fill_bank" -> QuizQuestionType.FILL_BLANK
                        attributes.getValue("type")?.equals("fill_blank", ignoreCase = true) == true -> {
                            QuizQuestionType.FILL_BLANK
                        }
                        else -> QuizQuestionType.MULTIPLE_CHOICE
                    }
                    questionText = ""
                    answerText = ""
                    referenceAnswer = ""
                    explanation = ""
                    sourceReference = attributes.getValue("source")?.trim().orEmpty()
                    options.clear()
                    acceptedAnswers.clear()
                }
                "option" -> optionId = attributes.getValue("id")?.trim().orEmpty()
            }
        }

        override fun characters(characters: CharArray, start: Int, length: Int) {
            if (activeTag != null) textBuffer.append(characters, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String) {
            val tag = qName.lowercase()
            val value = textBuffer.toString().trim()
            when (tag) {
                "password" -> password = value
                "text", "prompt" -> questionText = value
                "option" -> if (optionId.isNotBlank() && value.isNotBlank()) {
                    options += QuizOption(optionId, value)
                }
                "answer" -> answerText = value
                "reference_answer", "correct_answer" -> referenceAnswer = value
                "accepted_answer" -> value.takeIf(String::isNotBlank)?.let(acceptedAnswers::add)
                "explanation", "analysis" -> explanation = value
                "category" -> category = value
                "source" -> sourceReference = value
                "question" -> appendQuestion()
            }
            activeTag = null
            textBuffer.clear()
        }

        private fun appendQuestion() {
            require(questionText.isNotBlank()) { "题目正文不能为空。" }
            when (questionType) {
                QuizQuestionType.MULTIPLE_CHOICE -> appendChoiceQuestion()
                QuizQuestionType.FILL_BLANK -> appendFillBlankQuestion()
            }
        }

        private fun appendChoiceQuestion() {
            require(options.size >= 2) { "选择题至少需要两个选项。" }
            val answers = parseChoiceAnswers(answerText, options.map(QuizOption::id))
            require(answers.isNotEmpty()) { "选择题缺少有效答案。" }
            questions += ParsedQuizQuestion(
                originalIndex = questions.size,
                score = score,
                text = questionText,
                options = options.toList(),
                answers = answers,
                type = QuizQuestionType.MULTIPLE_CHOICE,
                explanation = explanation.takeIf(String::isNotBlank),
                category = category.takeIf(String::isNotBlank),
                sourceReference = sourceReference.takeIf(String::isNotBlank),
            )
        }

        private fun appendFillBlankQuestion() {
            require(referenceAnswer.isNotBlank()) { "填空题缺少参考答案。" }
            val accepted = linkedSetOf<String>().apply {
                add(referenceAnswer.trim())
                addAll(acceptedAnswers)
                addAll(parseAcceptedAnswerVariants(referenceAnswer))
            }.filterTo(linkedSetOf(), String::isNotBlank)
            questions += ParsedQuizQuestion(
                originalIndex = questions.size,
                score = score,
                text = questionText,
                options = emptyList(),
                answers = emptySet(),
                type = QuizQuestionType.FILL_BLANK,
                referenceAnswer = referenceAnswer.trim(),
                acceptedAnswers = accepted,
                explanation = explanation.takeIf(String::isNotBlank),
                category = category.takeIf(String::isNotBlank),
                sourceReference = sourceReference.takeIf(String::isNotBlank),
            )
        }

        private fun parseChoiceAnswers(rawAnswer: String, optionIds: List<String>): Set<String> {
            val normalizedIds = optionIds.associateBy(String::uppercase)
            val tokens = rawAnswer.uppercase().split(Regex("[^A-Z0-9]+"))
                .filter(String::isNotBlank)
            val directMatches = tokens.mapNotNull(normalizedIds::get)
            if (directMatches.isNotEmpty() && directMatches.size == tokens.size) {
                return directMatches.toSet()
            }
            return rawAnswer.uppercase().map(Char::toString).mapNotNull(normalizedIds::get).toSet()
        }

        private fun parseAcceptedAnswerVariants(value: String): Set<String> = value
            .split(Regex("\\s+/\\s+|\\s*[；;]\\s*|\\s+或\\s+"))
            .map(String::trim)
            .filter(String::isNotBlank)
            .toSet()
    }
}
