package com.zlight106.nvvocab.domain

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerPositionPlannerTest {
    @Test
    fun distributedUsesEveryPositionBeforeRepeatingWithinAFullBlock() {
        val positions = AnswerPositionPlanner.distributed(
            questionCount = 10,
            optionCount = 4,
            random = Random(17),
        )

        assertEquals(10, positions.size)
        assertEquals(setOf(0, 1, 2, 3), positions.take(4).toSet())
        assertEquals(setOf(0, 1, 2, 3), positions.drop(4).take(4).toSet())
        assertTrue(positions.all { it in 0..3 })
    }

    @Test
    fun distributedHandlesPartialFinalBlock() {
        val positions = AnswerPositionPlanner.distributed(
            questionCount = 3,
            optionCount = 8,
            random = Random(23),
        )

        assertEquals(3, positions.size)
        assertEquals(3, positions.toSet().size)
        assertTrue(positions.all { it in 0..7 })
    }
}
