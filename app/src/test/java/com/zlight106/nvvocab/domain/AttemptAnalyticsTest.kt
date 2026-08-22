package com.zlight106.nvvocab.domain

import com.zlight106.nvvocab.data.ItemMaturity
import com.zlight106.nvvocab.data.PracticeAttempt
import com.zlight106.nvvocab.data.PracticeAttemptMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttemptAnalyticsTest {
    @Test
    fun `maturity advances through learning and familiar`() {
        val first = attempts(count = 1)
        val second = attempts(count = 2)
        val third = attempts(count = 3)

        assertEquals(ItemMaturity.NEW, snapshot(first).maturity)
        assertEquals(ItemMaturity.LEARNING, snapshot(second).maturity)
        assertEquals(ItemMaturity.FAMILIAR, snapshot(third).maturity)
    }

    @Test
    fun `clean fast answers become mature and mastered`() {
        assertEquals(ItemMaturity.MATURE, snapshot(attempts(count = 5)).maturity)
        assertEquals(ItemMaturity.MASTERED, snapshot(attempts(count = 8, sessionModulo = 3)).maturity)
    }

    @Test
    fun `hinted answers cannot upgrade maturity`() {
        val history = attempts(count = 3) + attempts(
            count = 2,
            startIndex = 3,
            hinted = true,
        )

        assertEquals(ItemMaturity.FAMILIAR, snapshot(history).maturity)
    }

    @Test
    fun `one error only drops mastered item by one level`() {
        val history = attempts(count = 8, sessionModulo = 3) + attempt(
            index = 8,
            correct = false,
        )

        assertEquals(ItemMaturity.MATURE, snapshot(history).maturity)
    }

    @Test
    fun `time summaries use median and mode share`() {
        val attempts = listOf(
            attempt(index = 0, activeTimeMs = 1_000L),
            attempt(index = 1, activeTimeMs = 3_000L),
            attempt(index = 2, activeTimeMs = 6_000L, mode = PracticeAttemptMode.QUIZ_FILL_BLANK),
        )

        val summaries = AttemptAnalytics.modeTimeSummaries(attempts)
        val dictation = summaries.first { it.mode == PracticeAttemptMode.WORD_DICTATION }
        val fillBlank = summaries.first { it.mode == PracticeAttemptMode.QUIZ_FILL_BLANK }
        assertEquals(2_000L, dictation.averageTimeMs)
        assertEquals(2_000L, dictation.medianTimeMs)
        assertEquals(0.4, dictation.timeShare, 0.0001)
        assertEquals(0.6, fillBlank.timeShare, 0.0001)
    }

    @Test
    fun `stale history raises priority without lowering maturity`() {
        val history = attempts(count = 8, sessionModulo = 3)
        val recent = snapshot(history, now = 20_000L)
        val stale = snapshot(history, now = 20_000L + 30L * 86_400_000L)

        assertEquals(recent.maturity, stale.maturity)
        assertTrue(stale.reviewPriority > recent.reviewPriority)
    }

    private fun snapshot(
        attempts: List<PracticeAttempt>,
        now: Long = 20_000L,
    ) = AttemptAnalytics.maturitySnapshot(
        itemId = ITEM_ID,
        mode = PracticeAttemptMode.WORD_DICTATION,
        itemAttempts = attempts,
        baselineMs = 1_000L,
        now = now,
    )

    private fun attempts(
        count: Int,
        startIndex: Int = 0,
        hinted: Boolean = false,
        sessionModulo: Int = 1,
    ): List<PracticeAttempt> = List(count) { offset ->
        val index = startIndex + offset
        attempt(
            index = index,
            hinted = hinted,
            sessionId = "session-${index % sessionModulo}",
        )
    }

    private fun attempt(
        index: Int,
        correct: Boolean = true,
        hinted: Boolean = false,
        activeTimeMs: Long = 1_000L,
        mode: PracticeAttemptMode = PracticeAttemptMode.WORD_DICTATION,
        sessionId: String = "session-0",
    ): PracticeAttempt = PracticeAttempt(
        id = "attempt-$mode-$index",
        userId = "user",
        sessionId = sessionId,
        itemId = ITEM_ID,
        mode = mode,
        sequenceIndex = index,
        question = "question $index",
        options = emptyList(),
        firstAnswer = if (correct) "answer" else "wrong",
        finalAnswer = if (correct) "answer" else "wrong",
        referenceAnswer = "answer",
        acceptedAnswers = setOf("answer"),
        explanation = null,
        correct = correct,
        firstAnswerCorrect = correct,
        activeTimeMs = activeTimeMs,
        hintUsed = hinted,
        timestamp = 1_000L + index,
        dirty = true,
    )

    private companion object {
        const val ITEM_ID = "item"
    }
}
