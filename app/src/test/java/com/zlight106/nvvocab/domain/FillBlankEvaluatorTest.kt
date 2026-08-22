package com.zlight106.nvvocab.domain

import com.zlight106.nvvocab.data.AnswerEvaluationResult
import com.zlight106.nvvocab.data.QuizQuestion
import com.zlight106.nvvocab.data.QuizQuestionType
import org.junit.Assert.assertEquals
import org.junit.Test

class FillBlankEvaluatorTest {
    @Test
    fun normalizesCaseSpacesAndWrappingPunctuation() {
        val result = FillBlankEvaluator.evaluateLocally(
            question = question("umount", setOf("umount")),
            userAnswer = "  UMOUNT； ",
            ignoreCase = true,
        )

        assertEquals(AnswerEvaluationResult.CORRECT, result.result)
    }

    @Test
    fun preservesShellQuestionMark() {
        val result = FillBlankEvaluator.evaluateLocally(
            question = question("\$?", setOf("\$?")),
            userAnswer = "\$?",
            ignoreCase = true,
        )

        assertEquals(AnswerEvaluationResult.CORRECT, result.result)
    }

    @Test
    fun unmatchedAnswerRequiresSecondLayerReview() {
        val result = FillBlankEvaluator.evaluateLocally(
            question = question("父目录", setOf("父目录", "上一级目录", "上级目录")),
            userAnswer = "父级目录",
            ignoreCase = true,
        )

        assertEquals(AnswerEvaluationResult.REVIEW, result.result)
    }

    private fun question(reference: String, accepted: Set<String>) = QuizQuestion(
        id = "question",
        bankId = "bank",
        originalIndex = 0,
        score = 10,
        text = "题目",
        options = emptyList(),
        answers = emptySet(),
        type = QuizQuestionType.FILL_BLANK,
        referenceAnswer = reference,
        acceptedAnswers = accepted,
    )
}
