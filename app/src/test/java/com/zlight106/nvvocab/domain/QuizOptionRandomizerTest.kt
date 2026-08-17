package com.zlight106.nvvocab.domain

import com.zlight106.nvvocab.data.QuizOption
import com.zlight106.nvvocab.data.QuizQuestion
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizOptionRandomizerTest {
    @Test
    fun randomize_relabelsOptionsAndKeepsCorrectAnswerMapping() {
        val original = QuizQuestion(
            id = "question-1",
            bankId = "bank-1",
            originalIndex = 0,
            score = 10,
            text = "题目",
            options = listOf(
                QuizOption("A", "错误一"),
                QuizOption("B", "正确内容"),
                QuizOption("C", "错误二"),
                QuizOption("D", "错误三"),
            ),
            answers = setOf("B"),
        )

        val randomized = QuizOptionRandomizer.randomize(original, Random(17))

        assertEquals(listOf("A", "B", "C", "D"), randomized.options.map(QuizOption::id))
        assertEquals(original.options.map(QuizOption::text).toSet(), randomized.options.map(QuizOption::text).toSet())
        val correctOption = randomized.options.single { it.id in randomized.answers }
        assertEquals("正确内容", correctOption.text)
        assertTrue(original.answers == setOf("B"))
    }
}
