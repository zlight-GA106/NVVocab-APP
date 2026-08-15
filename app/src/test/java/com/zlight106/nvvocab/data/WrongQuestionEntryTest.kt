package com.zlight106.nvvocab.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WrongQuestionEntryTest {
    @Test
    fun proficiencyUsesCorrectAndWrongAttempts() {
        val entry = sampleEntry(correctCount = 7, wrongCount = 3)

        assertEquals(10, entry.attemptCount)
        assertEquals(70, entry.proficiencyPercent)
    }

    @Test
    fun proficiencyIsZeroWithoutAttempts() {
        assertEquals(0, sampleEntry(correctCount = 0, wrongCount = 0).proficiencyPercent)
    }

    private fun sampleEntry(correctCount: Int, wrongCount: Int) = WrongQuestionEntry(
        id = "entry",
        userId = "user",
        source = WrongQuestionSource.QUIZ,
        bankId = "bank",
        bankName = "bank",
        questionKey = "question",
        questionText = "question",
        options = listOf(QuizOption("A", "answer")),
        correctAnswers = setOf("A"),
        wrongCount = wrongCount,
        correctCount = correctCount,
        favorite = false,
        aiAnalysis = null,
        lastWrongAt = 1L,
        lastReviewedAt = null,
        dirty = true,
    )
}
