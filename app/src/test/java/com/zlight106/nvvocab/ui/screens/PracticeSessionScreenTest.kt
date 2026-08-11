package com.zlight106.nvvocab.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class PracticeSessionScreenTest {
    @Test
    fun administratorAnswerText_sortsAndJoinsMultipleAnswers() {
        assertEquals("答案：A、C", administratorAnswerText(setOf("C", "A")))
    }
}
