package com.zlight106.nvvocab.ui.screens

import com.zlight106.nvvocab.data.QuizOption
import org.junit.Assert.assertEquals
import org.junit.Test

class PracticeSessionScreenTest {
    @Test
    fun administratorAnswerText_includesOptionContent() {
        val options = listOf(
            QuizOption("A", "chief"),
            QuizOption("B", "severe"),
            QuizOption("C", "primary"),
        )
        assertEquals(
            "答案：A. chief；C. primary",
            administratorAnswerText(options, setOf("C", "A")),
        )
    }
}
