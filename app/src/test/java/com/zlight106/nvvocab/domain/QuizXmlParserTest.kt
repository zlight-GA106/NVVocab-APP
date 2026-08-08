package com.zlight106.nvvocab.domain

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuizXmlParserTest {
    @Test
    fun parsesSingleAndMultipleChoiceQuestions() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <quiz>
                <password>sample</password>
                <question score="5">
                    <text>单选题</text>
                    <option id="A">选项一</option>
                    <option id="B">选项二</option>
                    <answer>B</answer>
                </question>
                <question score="10">
                    <text>多选题</text>
                    <option id="A">选项一</option>
                    <option id="B">选项二</option>
                    <option id="C">选项三</option>
                    <answer>AC</answer>
                </question>
            </quiz>
        """.trimIndent()

        val parsed = QuizXmlParser.parse(
            ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)),
            "示例题库.xml",
        )

        assertEquals("示例题库", parsed.name)
        assertEquals("sample", parsed.password)
        assertEquals(2, parsed.questions.size)
        assertEquals(setOf("B"), parsed.questions[0].answers)
        assertEquals(setOf("A", "C"), parsed.questions[1].answers)
        assertEquals(10, parsed.questions[1].score)
    }

    @Test
    fun parsesGeneratedQuizWithoutPassword() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <quiz>
                <question score="10">
                    <text>苹果</text>
                    <option id="A">pear</option>
                    <option id="B">apple</option>
                    <answer>B</answer>
                </question>
            </quiz>
        """.trimIndent()

        val parsed = QuizXmlParser.parse(
            ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)),
            "ai-generated.xml",
        )

        assertNull(parsed.password)
        assertEquals(1, parsed.questions.size)
        assertEquals("苹果", parsed.questions.single().text)
        assertEquals(setOf("B"), parsed.questions.single().answers)
    }
}
