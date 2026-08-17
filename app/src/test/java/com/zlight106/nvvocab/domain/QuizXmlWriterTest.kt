package com.zlight106.nvvocab.domain

import com.zlight106.nvvocab.data.QuizOption
import com.zlight106.nvvocab.data.QuizQuestion
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuizXmlWriterTest {
    @Test
    fun exportedQuestionsCanBeImportedAgain() {
        val questions = listOf(
            QuizQuestion(
                id = "question-1",
                bankId = "bank-1",
                originalIndex = 0,
                score = 10,
                text = "包含 <符号> 与引号的题目",
                options = listOf(
                    QuizOption("A", "错误选项"),
                    QuizOption("B", "正确 & 选项"),
                ),
                answers = setOf("B"),
            ),
        )
        val output = ByteArrayOutputStream()

        QuizXmlWriter.write(questions, output)
        val parsed = QuizXmlParser.parse(
            ByteArrayInputStream(output.toByteArray()),
            "导出题库.xml",
        )

        assertEquals("导出题库", parsed.name)
        assertNull(parsed.password)
        assertEquals(questions.single().text, parsed.questions.single().text)
        assertEquals(questions.single().options, parsed.questions.single().options)
        assertEquals(setOf("B"), parsed.questions.single().answers)
    }
}
