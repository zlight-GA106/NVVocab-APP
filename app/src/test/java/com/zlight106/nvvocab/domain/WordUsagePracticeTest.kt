package com.zlight106.nvvocab.domain

import com.zlight106.nvvocab.data.WordEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordUsagePracticeTest {
    private val target = WordEntry("word", null, "rely", null, "依赖", "学习", 0L, 0, 0, 2.5, 0L, 0, false)
    private fun xml(source: String = "rely", example: String = "例句：We rely on our friends."): String =
        "<quiz>" + (1..2).joinToString("") {
            """<question score="10"><text>补全搭配：We ____ our friends.</text>
                <option id="A">rely on</option><option id="B">rely at</option><option id="C">rely for</option><option id="D">rely of</option>
                <answer>A</answer><explanation>rely on 依赖；rely upon 信赖。
                $example</explanation><source>$source</source></question>"""
        } + "</quiz>"

    @Test
    fun mapsEveryQuestionToItsWordAndKeepsCorrectPhraseAfterRedistribution() {
        val questions = WordUsagePractice.parseXml(xml(), listOf(target))
        assertEquals(2, questions.size)
        questions.forEachIndexed { index, question ->
            assertEquals(index, question.originalIndex)
            assertEquals(WordUsagePractice.CATEGORY, question.category)
            assertEquals(target.spelling, question.sourceReference)
            assertEquals("rely on", question.options.single { it.id in question.answers }.text)
            assertEquals("We rely on our friends.", WordUsagePractice.exampleText(question.explanation))
        }
        assertTrue(questions.map { it.answers.single() }.distinct().size > 1)
    }

    @Test
    fun rejectsUnrelatedWordsAndMissingExamplesInsteadOfSavingIncompleteBanks() {
        listOf(xml(source = "other"), xml(example = "没有例句")).forEach { content ->
            val result = runCatching { WordUsagePractice.parseXml(content, listOf(target)) }
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        }
    }
}
