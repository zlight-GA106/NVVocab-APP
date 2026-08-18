package com.zlight106.nvvocab.domain

import com.zlight106.nvvocab.data.QuizOption
import com.zlight106.nvvocab.data.WrongQuestionEntry
import com.zlight106.nvvocab.data.WrongQuestionSource
import org.junit.Assert.assertEquals
import org.junit.Test

class WrongQuestionGroupingTest {
    @Test
    fun groupsQuizQuestionsByBankIdAndContrastQuestionsByType() {
        val entries = listOf(
            entry("1", WrongQuestionSource.QUIZ, "bank-a", "Linux 题库"),
            entry("2", WrongQuestionSource.QUIZ, "bank-a", "Linux 题库"),
            entry("3", WrongQuestionSource.QUIZ, "bank-b", "网络题库"),
            entry("4", WrongQuestionSource.CONTRAST, null, "对照练习：中文翻译英文"),
        )

        val groups = WrongQuestionGrouping.group(entries)

        assertEquals(3, groups.size)
        assertEquals(listOf("1", "2"), groups[0].entries.map(WrongQuestionEntry::id))
        assertEquals("quiz:bank-b", groups[1].key)
        assertEquals("contrast:对照练习：中文翻译英文", groups[2].key)
    }

    private fun entry(
        id: String,
        source: WrongQuestionSource,
        bankId: String?,
        bankName: String,
    ) = WrongQuestionEntry(
        id = id,
        userId = null,
        source = source,
        bankId = bankId,
        bankName = bankName,
        questionKey = id,
        questionText = "题目 $id",
        options = listOf(QuizOption("A", "选项")),
        correctAnswers = setOf("A"),
        wrongCount = 1,
        correctCount = 0,
        favorite = false,
        aiAnalysis = null,
        lastWrongAt = 1L,
        lastReviewedAt = null,
        dirty = false,
    )
}
