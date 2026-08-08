package com.zlight106.nvvocab.domain

import com.zlight106.nvvocab.data.PracticeRangeMode
import com.zlight106.nvvocab.data.ProficiencyBand
import com.zlight106.nvvocab.data.WordEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class ContrastPracticePlannerTest {
    @Test
    fun categoryRangeUsesLatestImportOrder() {
        val words = listOf(
            word("old", "A", 100L),
            word("other", "B", 300L),
            word("new", "A", 200L),
        )

        val result = ContrastPracticePlanner.selectWords(
            words = words,
            rangeMode = PracticeRangeMode.CATEGORY,
            selectedTag = "A",
            proficiencyBand = ProficiencyBand.LOW,
            selectedWordIds = emptySet(),
            now = 1_000L,
        )

        assertEquals(listOf("new", "old"), result.map(WordEntry::id))
    }

    @Test
    fun customRangeOnlyKeepsSelectedIds() {
        val words = listOf(word("one", "A", 100L), word("two", "A", 200L))

        val result = ContrastPracticePlanner.selectWords(
            words = words,
            rangeMode = PracticeRangeMode.CUSTOM,
            selectedTag = null,
            proficiencyBand = ProficiencyBand.LOW,
            selectedWordIds = setOf("one"),
            now = 1_000L,
        )

        assertEquals(listOf("one"), result.map(WordEntry::id))
    }

    private fun word(id: String, tag: String, introTime: Long): WordEntry = WordEntry(
        id = id,
        userId = null,
        spelling = id,
        phonetic = null,
        translation = "释义$id",
        bookTag = tag,
        introTime = introTime,
        repetitions = 0,
        intervalDays = 1,
        easiness = 2.5,
        nextReviewAt = 0L,
        wrongCount = 0,
        dirty = false,
    )
}
