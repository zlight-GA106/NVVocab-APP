package com.zlight106.nvvocab.domain

import com.zlight106.nvvocab.data.PracticeRangeMode
import com.zlight106.nvvocab.data.ProficiencyBand
import com.zlight106.nvvocab.data.WordEntry

object ContrastPracticePlanner {
    fun selectWords(
        words: List<WordEntry>,
        rangeMode: PracticeRangeMode,
        selectedTag: String?,
        proficiencyBand: ProficiencyBand,
        selectedWordIds: Set<String>,
        now: Long = System.currentTimeMillis(),
    ): List<WordEntry> = words.asSequence()
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
        .sortedWith(compareByDescending<WordEntry> { it.introTime }.thenByDescending { it.id })
        .toList()
}
