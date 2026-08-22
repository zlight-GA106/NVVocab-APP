package com.zlight106.nvvocab.domain

import com.zlight106.nvvocab.data.QuizQuestion
import com.zlight106.nvvocab.data.QuizQuestionType
import java.io.OutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

object QuizXmlWriter {
    fun write(questions: List<QuizQuestion>, outputStream: OutputStream) {
        require(questions.isNotEmpty()) { "题库中没有可导出的题目。" }
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .newDocument()
        val root = document.createElement("quiz")
        document.appendChild(root)

        questions.sortedBy { it.originalIndex }.forEach { question ->
            val questionElement = document.createElement("question").apply {
                setAttribute("score", question.score.toString())
                setAttribute(
                    "type",
                    if (question.type == QuizQuestionType.FILL_BLANK) "fill_blank" else "multiple_choice",
                )
            }
            questionElement.appendTextElement(document, "text", question.text)
            when (question.type) {
                QuizQuestionType.MULTIPLE_CHOICE -> {
                    question.options.forEach { option ->
                        questionElement.appendChild(document.createElement("option").apply {
                            setAttribute("id", option.id)
                            textContent = option.text
                        })
                    }
                    questionElement.appendTextElement(
                        document,
                        "answer",
                        question.answers.sorted().joinToString(","),
                    )
                }
                QuizQuestionType.FILL_BLANK -> {
                    questionElement.appendTextElement(
                        document,
                        "reference_answer",
                        question.referenceAnswer.orEmpty(),
                    )
                    question.acceptedAnswers.sorted().forEach { answer ->
                        questionElement.appendTextElement(document, "accepted_answer", answer)
                    }
                }
            }
            question.explanation?.let { questionElement.appendTextElement(document, "explanation", it) }
            question.category?.let { questionElement.appendTextElement(document, "category", it) }
            question.sourceReference?.let { questionElement.appendTextElement(document, "source", it) }
            root.appendChild(questionElement)
        }

        TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4")
        }.transform(DOMSource(document), StreamResult(outputStream))
    }

    private fun org.w3c.dom.Element.appendTextElement(
        document: org.w3c.dom.Document,
        name: String,
        value: String,
    ) {
        appendChild(document.createElement(name).apply { textContent = value })
    }
}
