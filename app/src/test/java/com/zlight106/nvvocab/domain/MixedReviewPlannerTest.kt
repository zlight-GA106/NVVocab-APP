package com.zlight106.nvvocab.domain

import com.zlight106.nvvocab.data.MixedReviewMode
import com.zlight106.nvvocab.data.ParaphraseSeed
import com.zlight106.nvvocab.data.WordEntry
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MixedReviewPlannerTest {
    @Test
    fun exactPercentagesProduceExpectedModeCounts() {
        val counts = MixedReviewPlanner.allocateCounts(
            total = 150,
            enabledModes = setOf(
                MixedReviewMode.CHINESE_TO_ENGLISH,
                MixedReviewMode.ENGLISH_TO_CHINESE,
                MixedReviewMode.ENGLISH_DEFINITION_TO_ENGLISH,
            ),
            percentages = mapOf(
                MixedReviewMode.CHINESE_TO_ENGLISH to 50,
                MixedReviewMode.ENGLISH_TO_CHINESE to 30,
                MixedReviewMode.ENGLISH_DEFINITION_TO_ENGLISH to 20,
            ),
        )

        assertEquals(75, counts[MixedReviewMode.CHINESE_TO_ENGLISH])
        assertEquals(45, counts[MixedReviewMode.ENGLISH_TO_CHINESE])
        assertEquals(30, counts[MixedReviewMode.ENGLISH_DEFINITION_TO_ENGLISH])
    }

    @Test
    fun largestRemainderAlwaysPreservesTotal() {
        val counts = MixedReviewPlanner.allocateCounts(
            total = 7,
            enabledModes = MixedReviewMode.entries.toSet(),
            percentages = MixedReviewMode.entries.associateWith { 25 },
        )

        assertEquals(7, counts.values.sum())
        assertEquals(listOf(2, 2, 2, 1), MixedReviewMode.entries.map { counts[it] })
    }

    @Test
    fun planDrawsDistinctWordsAcrossSelectedCategories() {
        val words = (1..9).flatMap { tag ->
            (1..20).map { index -> word(id = "$tag-$index", tag = tag.toString()) }
        }
        val seeds = (1..30).map { index -> seed(index) }

        val plan = MixedReviewPlanner.plan(
            words = words,
            selectedTags = (1..9).map(Int::toString).toSet(),
            requestedCount = 150,
            enabledModes = setOf(
                MixedReviewMode.CHINESE_TO_ENGLISH,
                MixedReviewMode.ENGLISH_TO_CHINESE,
                MixedReviewMode.ENGLISH_DEFINITION_TO_ENGLISH,
            ),
            percentages = mapOf(
                MixedReviewMode.CHINESE_TO_ENGLISH to 50,
                MixedReviewMode.ENGLISH_TO_CHINESE to 30,
                MixedReviewMode.ENGLISH_DEFINITION_TO_ENGLISH to 20,
            ),
            random = Random(42),
            paraphraseSeeds = seeds,
        )

        assertEquals(150, plan.size)
        assertEquals(150, plan.map { it.itemId }.distinct().size)
        assertTrue(plan.filter { it.word != null }.all { it.word?.bookTag in (1..9).map(Int::toString) })
        assertEquals(75, plan.count { it.mode == MixedReviewMode.CHINESE_TO_ENGLISH })
        assertEquals(45, plan.count { it.mode == MixedReviewMode.ENGLISH_TO_CHINESE })
        assertEquals(30, plan.count { it.mode == MixedReviewMode.ENGLISH_DEFINITION_TO_ENGLISH })
    }

    private fun word(id: String, tag: String): WordEntry = WordEntry(
        id = id,
        userId = null,
        spelling = id,
        phonetic = null,
        translation = "释义$id",
        bookTag = tag,
        introTime = 0L,
        repetitions = 0,
        intervalDays = 1,
        easiness = 2.5,
        nextReviewAt = 0L,
        wrongCount = 0,
        dirty = false,
    )

    private fun seed(index: Int): ParaphraseSeed = ParaphraseSeed(
        id = "seed-$index",
        userId = null,
        sourceText = "source $index",
        targetText = "target $index",
        contextText = null,
        sourceReference = null,
        notes = null,
        createdAt = index.toLong(),
        updatedAt = index.toLong(),
        dirty = false,
    )
}
