package com.zlight106.nvvocab.domain

import com.zlight106.nvvocab.data.WrongQuestionEntry
import com.zlight106.nvvocab.data.WrongQuestionSource

data class WrongQuestionGroup(
    val key: String,
    val name: String,
    val source: WrongQuestionSource,
    val entries: List<WrongQuestionEntry>,
)

object WrongQuestionGrouping {
    fun group(entries: List<WrongQuestionEntry>): List<WrongQuestionGroup> {
        val grouped = linkedMapOf<String, MutableList<WrongQuestionEntry>>()
        entries.forEach { entry ->
            grouped.getOrPut(keyOf(entry)) { mutableListOf() }.add(entry)
        }
        return grouped.map { (key, questions) ->
            val first = questions.first()
            WrongQuestionGroup(
                key = key,
                name = first.bankName,
                source = first.source,
                entries = questions,
            )
        }
    }

    fun keyOf(entry: WrongQuestionEntry): String = when (entry.source) {
        WrongQuestionSource.QUIZ -> "quiz:${entry.bankId ?: entry.bankName}"
        WrongQuestionSource.CONTRAST -> "contrast:${entry.bankName}"
    }
}
