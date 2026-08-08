package com.zlight106.nvvocab.domain

import com.zlight106.nvvocab.data.ParsedQuizBank
import com.zlight106.nvvocab.data.ParsedQuizQuestion
import com.zlight106.nvvocab.data.QuizOption
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
            name = fileName.substringBeforeLast('.').trim().ifBlank { "未命名题库" },
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

        private var activeTag: String? = null
        private val textBuffer = StringBuilder()
        private var score = 0
        private var questionText = ""
        private var answerText = ""
        private var optionId = ""
        private val options = mutableListOf<QuizOption>()

        override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
            activeTag = qName.lowercase()
            textBuffer.clear()
            if (activeTag == "question") {
                score = attributes.getValue("score")?.toIntOrNull() ?: 0
                questionText = ""
                answerText = ""
                options.clear()
            } else if (activeTag == "option") {
                optionId = attributes.getValue("id")?.trim().orEmpty()
            }
        }

        override fun characters(characters: CharArray, start: Int, length: Int) {
            if (activeTag != null) textBuffer.append(characters, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String) {
            val value = textBuffer.toString().trim()
            when (qName.lowercase()) {
                "password" -> password = value
                "text" -> questionText = value
                "option" -> if (optionId.isNotBlank() && value.isNotBlank()) {
                    options += QuizOption(optionId, value)
                }
                "answer" -> answerText = value
                "question" -> appendQuestion()
            }
            activeTag = null
            textBuffer.clear()
        }

        private fun appendQuestion() {
            require(questionText.isNotBlank()) { "题目正文不能为空。" }
            require(options.size >= 2) { "题目至少需要两个选项。" }
            val answers = parseAnswers(answerText, options.map(QuizOption::id))
            require(answers.isNotEmpty()) { "题目缺少有效答案。" }
            questions += ParsedQuizQuestion(
                originalIndex = questions.size,
                score = score,
                text = questionText,
                options = options.toList(),
                answers = answers,
            )
        }

        private fun parseAnswers(rawAnswer: String, optionIds: List<String>): Set<String> {
            val normalizedIds = optionIds.associateBy(String::uppercase)
            val tokens = rawAnswer.uppercase().split(Regex("[^A-Z0-9]+"))
                .filter(String::isNotBlank)
            val directMatches = tokens.mapNotNull(normalizedIds::get)
            if (directMatches.isNotEmpty() && directMatches.size == tokens.size) {
                return directMatches.toSet()
            }
            return rawAnswer.uppercase().map(Char::toString).mapNotNull(normalizedIds::get).toSet()
        }
    }
}
