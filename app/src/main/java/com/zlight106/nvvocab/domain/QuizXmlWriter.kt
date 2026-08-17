package com.zlight106.nvvocab.domain

import com.zlight106.nvvocab.data.QuizQuestion
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
            }
            questionElement.appendChild(document.createElement("text").apply {
                textContent = question.text
            })
            question.options.forEach { option ->
                questionElement.appendChild(document.createElement("option").apply {
                    setAttribute("id", option.id)
                    textContent = option.text
                })
            }
            questionElement.appendChild(document.createElement("answer").apply {
                textContent = question.answers.sorted().joinToString(",")
            })
            root.appendChild(questionElement)
        }

        TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4")
        }.transform(DOMSource(document), StreamResult(outputStream))
    }
}
