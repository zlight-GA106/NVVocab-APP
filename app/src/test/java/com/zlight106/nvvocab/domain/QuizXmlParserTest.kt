package com.zlight106.nvvocab.domain

import com.zlight106.nvvocab.data.QuizQuestionType
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun parsesNcreFillBankMetadata() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ncre_linux_fill_bank title="NCRE Linux 填空题">
              <volume id="1">
                <question ordinal="1" source="fill/001.png">
                  <prompt>Shell 中上一条命令的退出状态由 ______ 表示。</prompt>
                  <correct_answer>${'$'}?</correct_answer>
                  <analysis>该特殊变量保存上一条命令的退出状态。</analysis>
                </question>
              </volume>
            </ncre_linux_fill_bank>
        """.trimIndent()

        val bank = QuizXmlParser.parse(ByteArrayInputStream(xml.toByteArray()), "fill.xml")
        val question = bank.questions.single()

        assertEquals("NCRE Linux 填空题", bank.name)
        assertEquals(QuizQuestionType.FILL_BLANK, question.type)
        assertEquals("\$?", question.referenceAnswer)
        assertTrue("\$?" in question.acceptedAnswers)
        assertEquals("第1卷", question.category)
        assertEquals("fill/001.png", question.sourceReference)
    }

    @Test
    fun keepsLegacyChoiceBankCompatible() {
        val xml = """
            <quiz>
              <question score="10">
                <text>测试题</text>
                <option id="A">正确</option>
                <option id="B">错误</option>
                <answer>A</answer>
              </question>
            </quiz>
        """.trimIndent()

        val question = QuizXmlParser.parse(ByteArrayInputStream(xml.toByteArray()), "choice.xml")
            .questions.single()

        assertEquals(QuizQuestionType.MULTIPLE_CHOICE, question.type)
        assertEquals(setOf("A"), question.answers)
    }
}
