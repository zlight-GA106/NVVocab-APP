package com.zlight106.nvvocab.domain

import com.zlight106.nvvocab.data.QuizBank
import com.zlight106.nvvocab.data.QuizOption
import com.zlight106.nvvocab.data.QuizQuestion
import com.zlight106.nvvocab.data.QuizQuestionType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class QuizBankZipWriterTest {
    @Test
    fun exportsEveryBankWithUniqueSafeNamesAndReimportableContents() {
        val banks = listOf(QuizBank("one", "搭配/练习", null, 0L, 1), QuizBank("two", "搭配\\练习", null, 0L, 1))
        val choice = QuizQuestion("q1", "one", 0, 10, "We ____ our friends.",
            listOf(QuizOption("A", "rely on"), QuizOption("B", "rely of")), setOf("A"),
            explanation = "搭配解析\n例句：We rely on our friends.", category = WordUsagePractice.CATEGORY, sourceReference = "rely")
        val fill = QuizQuestion("q2", "two", 0, 5, "填写单词", emptyList(), emptySet(),
            type = QuizQuestionType.FILL_BLANK, referenceAnswer = "rely", acceptedAnswers = setOf("rely", "RELY"))
        val output = ByteArrayOutputStream()
        QuizBankZipWriter.write(banks, output) { if (it == "one") listOf(choice) else listOf(fill) }
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            banks.forEachIndexed { index, bank ->
                val entry = requireNotNull(zip.nextEntry)
                names += entry.name
                assertFalse(entry.name.contains('/'))
                assertFalse(entry.name.contains('\\'))
                val imported = QuizXmlParser.parse(ByteArrayInputStream(zip.readBytes()), entry.name)
                assertEquals(bank.name, imported.name)
                val question = imported.questions.single()
                if (index == 0) {
                    assertEquals(choice.options, question.options)
                    assertEquals(choice.answers, question.answers)
                    assertEquals(choice.explanation, question.explanation)
                    assertEquals(choice.category, question.category)
                    assertEquals(choice.sourceReference, question.sourceReference)
                } else {
                    assertEquals(fill.type, question.type)
                    assertEquals(fill.referenceAnswer, question.referenceAnswer)
                    assertEquals(fill.acceptedAnswers, question.acceptedAnswers)
                }
                zip.closeEntry()
            }
            assertEquals(null, zip.nextEntry)
        }
        assertEquals(2, names.distinct().size)
    }
}
