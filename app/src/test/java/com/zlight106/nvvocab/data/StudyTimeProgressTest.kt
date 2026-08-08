package com.zlight106.nvvocab.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyTimeProgressTest {
    @Test
    fun calculatesAndClampsProgress() {
        val halfway = StudyTimeProgress(elapsedMillis = 15 * 60_000L, goalMinutes = 30)
        assertEquals(15, halfway.elapsedMinutes)
        assertEquals(0.5f, halfway.progressFraction, 0.0001f)
        assertFalse(halfway.completed)

        val complete = StudyTimeProgress(elapsedMillis = 35 * 60_000L, goalMinutes = 30)
        assertEquals(1f, complete.progressFraction, 0.0001f)
        assertTrue(complete.completed)
    }
}
