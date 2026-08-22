package com.zlight106.nvvocab.domain

import com.zlight106.nvvocab.data.ItemMaturity
import com.zlight106.nvvocab.data.ItemMaturitySnapshot
import com.zlight106.nvvocab.data.PracticeAttempt
import com.zlight106.nvvocab.data.PracticeAttemptMode
import kotlin.math.max

data class AttemptModeTimeSummary(
    val mode: PracticeAttemptMode,
    val attemptCount: Int,
    val totalTimeMs: Long,
    val averageTimeMs: Long,
    val medianTimeMs: Long,
    val timeShare: Double,
)

object AttemptAnalytics {
    fun modeTimeSummaries(attempts: List<PracticeAttempt>): List<AttemptModeTimeSummary> {
        val totalTime = attempts.sumOf { it.activeTimeMs.coerceAtLeast(0L) }.coerceAtLeast(1L)
        return attempts.groupBy(PracticeAttempt::mode).map { (mode, modeAttempts) ->
            val times = modeAttempts.map { it.activeTimeMs.coerceAtLeast(0L) }
            val modeTotal = times.sum()
            AttemptModeTimeSummary(
                mode = mode,
                attemptCount = modeAttempts.size,
                totalTimeMs = modeTotal,
                averageTimeMs = if (times.isEmpty()) 0L else modeTotal / times.size,
                medianTimeMs = median(times),
                timeShare = modeTotal.toDouble() / totalTime,
            )
        }.sortedByDescending(AttemptModeTimeSummary::totalTimeMs)
    }

    fun maturitySnapshots(
        attempts: List<PracticeAttempt>,
        now: Long = System.currentTimeMillis(),
    ): List<ItemMaturitySnapshot> {
        val baselines = attempts.groupBy(PracticeAttempt::mode).mapValues { (_, modeAttempts) ->
            val independentCorrectTimes = modeAttempts
                .filter { it.correct && it.firstAnswerCorrect && !it.hintUsed && it.activeTimeMs > 0L }
                .map(PracticeAttempt::activeTimeMs)
            median(independentCorrectTimes.ifEmpty {
                modeAttempts.filter { it.activeTimeMs > 0L }.map(PracticeAttempt::activeTimeMs)
            })
        }
        return attempts.groupBy { it.itemId to it.mode }.map { (key, itemAttempts) ->
            maturitySnapshot(
                itemId = key.first,
                mode = key.second,
                itemAttempts = itemAttempts,
                baselineMs = baselines[key.second] ?: 0L,
                now = now,
            )
        }
    }

    fun maturitySnapshot(
        itemId: String,
        mode: PracticeAttemptMode,
        itemAttempts: List<PracticeAttempt>,
        baselineMs: Long,
        now: Long = System.currentTimeMillis(),
    ): ItemMaturitySnapshot {
        val chronological = itemAttempts
            .filter { it.itemId == itemId && it.mode == mode }
            .sortedWith(compareBy(PracticeAttempt::timestamp, PracticeAttempt::sequenceIndex, PracticeAttempt::id))
        var maturity = ItemMaturity.NEW
        val history = mutableListOf<PracticeAttempt>()
        chronological.forEach { attempt ->
            history += attempt
            if (!attempt.correct) {
                val previousMaturity = maturity
                maturity = maturity.previous()
                if (history.size >= 2 && previousMaturity == ItemMaturity.NEW) {
                    maturity = ItemMaturity.LEARNING
                }
            } else if (!attempt.hintUsed) {
                val candidate = candidateMaturity(history, baselineMs)
                if (candidate.ordinal > maturity.ordinal) maturity = candidate
            }
        }
        val valid = chronological
        val recent = valid.takeLast(8)
        val accuracy = if (recent.isEmpty()) 0.0 else recent.count(PracticeAttempt::correct).toDouble() / recent.size
        val lastTimestamp = chronological.lastOrNull()?.timestamp ?: now
        val ageDays = max(0L, now - lastTimestamp).toDouble() / DAY_MS
        val errorWeight = chronological.takeLast(5).count { !it.correct } * 0.5
        return ItemMaturitySnapshot(
            itemId = itemId,
            mode = mode,
            maturity = maturity,
            validAttemptCount = valid.size,
            sessionCount = valid.map(PracticeAttempt::sessionId).distinct().size,
            recentAccuracy = accuracy,
            medianActiveTimeMs = median(valid.map(PracticeAttempt::activeTimeMs).filter { it > 0L }),
            baselineActiveTimeMs = baselineMs,
            reviewPriority = ageDays / (maturity.ordinal + 1.0) + errorWeight,
        )
    }

    private fun candidateMaturity(history: List<PracticeAttempt>, baselineMs: Long): ItemMaturity {
        val valid = history
        if (valid.size < 2) return ItemMaturity.NEW
        val recent = valid.takeLast(8)
        val recentAccuracy = recent.count(PracticeAttempt::correct).toDouble() / recent.size
        if (recentAccuracy < 0.8 || valid.takeLast(3).count { !it.correct } >= 2) {
            return ItemMaturity.LEARNING
        }

        if (valid.size < 3) return ItemMaturity.LEARNING

        val medianTime = median(valid.takeLast(8).map(PracticeAttempt::activeTimeMs).filter { it > 0L })
        val hasBaseline = baselineMs > 0L
        val lastFive = valid.takeLast(5)
        val lastEight = valid.takeLast(8)
        val mastered = valid.size >= 8 &&
            valid.map(PracticeAttempt::sessionId).distinct().size >= 3 &&
            firstAnswerAccuracy(lastEight) >= 0.95 &&
            lastFive.size == 5 && lastFive.none { !it.correct || it.hintUsed } &&
            hasBaseline && medianTime <= baselineMs
        if (mastered) return ItemMaturity.MASTERED

        val lastThree = valid.takeLast(3)
        val mature = valid.size >= 5 &&
            lastFive.size == 5 && firstAnswerAccuracy(lastFive) >= 0.9 &&
            lastThree.size == 3 && lastThree.none { !it.correct || it.hintUsed } &&
            hasBaseline && medianTime <= (baselineMs * 1.25).toLong()
        if (mature) return ItemMaturity.MATURE
        return ItemMaturity.FAMILIAR
    }

    private fun firstAnswerAccuracy(attempts: List<PracticeAttempt>): Double =
        if (attempts.isEmpty()) 0.0 else attempts.count(PracticeAttempt::firstAnswerCorrect).toDouble() / attempts.size

    internal fun median(values: List<Long>): Long {
        if (values.isEmpty()) return 0L
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2L
    }

    private fun ItemMaturity.previous(): ItemMaturity = when (this) {
        ItemMaturity.NEW -> ItemMaturity.NEW
        ItemMaturity.LEARNING -> ItemMaturity.NEW
        ItemMaturity.FAMILIAR -> ItemMaturity.LEARNING
        ItemMaturity.MATURE -> ItemMaturity.FAMILIAR
        ItemMaturity.MASTERED -> ItemMaturity.MATURE
    }

    private const val DAY_MS = 86_400_000.0
}
