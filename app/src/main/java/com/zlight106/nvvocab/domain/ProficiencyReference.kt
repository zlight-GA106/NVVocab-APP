package com.zlight106.nvvocab.domain

import com.zlight106.nvvocab.data.WordEntry
import kotlin.math.ln
import kotlin.math.roundToInt

data class ProficiencyReference(
    val label: String,
    val score: Int,
)

object ProficiencyCalculator {
    fun calculate(word: WordEntry, now: Long = System.currentTimeMillis()): ProficiencyReference {
        val repetitionScore = (word.repetitions.coerceAtLeast(0) * 12).coerceAtMost(36)
        val intervalScore = (
            ln(word.intervalDays.coerceAtLeast(0) + 1.0) / ln(2.0) * 9.0
        ).roundToInt().coerceAtMost(28)
        val easinessScore = (((word.easiness.coerceIn(1.3, 3.0) - 1.3) / 1.7) * 26).roundToInt()
        val errorPenalty = (word.wrongCount.coerceAtLeast(0) * 5).coerceAtMost(30)
        val overduePenalty = if (word.nextReviewAt < now) 8 else 0
        val score = (repetitionScore + intervalScore + easinessScore - errorPenalty - overduePenalty)
            .coerceIn(0, 100)

        val label = when (score) {
            in 0..24 -> "薄弱"
            in 25..49 -> "学习中"
            in 50..74 -> "熟悉"
            else -> "掌握"
        }

        return ProficiencyReference(label = label, score = score)
    }
}

data class ReviewCadenceState(
    val intervalDays: Int,
    val nextReviewAt: Long,
    val repetitions: Int,
    val wrongCount: Int,
)

object ReviewCadence {
    private const val DAY_MILLIS = 86_400_000L

    fun next(word: WordEntry, quality: Int, reviewedAt: Long = System.currentTimeMillis()): ReviewCadenceState {
        val successful = quality >= 3
        val intervalDays = when (quality) {
            in 5..Int.MAX_VALUE -> 7
            in 3..4 -> 3
            else -> 1
        }

        return ReviewCadenceState(
            intervalDays = intervalDays,
            nextReviewAt = reviewedAt + intervalDays * DAY_MILLIS,
            repetitions = if (successful) word.repetitions + 1 else 0,
            wrongCount = word.wrongCount + if (successful) 0 else 1,
        )
    }
}
