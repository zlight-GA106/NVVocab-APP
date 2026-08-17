package com.zlight106.nvvocab.domain

import com.zlight106.nvvocab.data.PracticeRangeMode
import com.zlight106.nvvocab.data.ProficiencyBand
import com.zlight106.nvvocab.data.QueueSort
import com.zlight106.nvvocab.data.WordEntry

object ContrastPracticePlanner {
    fun selectWords(
        words: List<WordEntry>,
        rangeMode: PracticeRangeMode,
        selectedTag: String?,
        proficiencyBand: ProficiencyBand,
        selectedWordIds: Set<String>,
        sort: QueueSort = QueueSort.LATEST,
        now: Long = System.currentTimeMillis(),
    ): List<WordEntry> {
        val filtered = words.asSequence()
        .filter { word ->
            when (rangeMode) {
                PracticeRangeMode.ALL -> true
                PracticeRangeMode.CATEGORY -> selectedTag != null && word.bookTag == selectedTag
                PracticeRangeMode.PROFICIENCY -> {
                    val score = ProficiencyCalculator.calculate(word, now).score
                    when (proficiencyBand) {
                        ProficiencyBand.LOW -> score < 40
                        ProficiencyBand.MEDIUM -> score in 40..69
                        ProficiencyBand.HIGH -> score >= 70
                    }
                }
                PracticeRangeMode.CUSTOM -> word.id in selectedWordIds
            }
        }
        .toList()
        return when (sort) {
            QueueSort.EARLIEST -> filtered.sortedBy(WordEntry::introTime)
            QueueSort.LATEST -> filtered.sortedByDescending(WordEntry::introTime)
            QueueSort.PROFICIENCY_LOW -> filtered.sortedBy { ProficiencyCalculator.calculate(it, now).score }
            QueueSort.PROFICIENCY_HIGH -> filtered.sortedByDescending { ProficiencyCalculator.calculate(it, now).score }
            QueueSort.WRONG_COUNT -> filtered.sortedByDescending(WordEntry::wrongCount)
            QueueSort.RANDOM -> filtered.shuffled()
        }
    }
}
