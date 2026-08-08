package com.zlight106.nvvocab.domain

import kotlin.random.Random

object AnswerPositionPlanner {
    fun distributed(
        questionCount: Int,
        optionCount: Int,
        random: Random = Random.Default,
    ): List<Int> {
        require(questionCount >= 0) { "题目数量不能小于 0。" }
        require(optionCount >= 2) { "选项数量不能小于 2。" }

        return buildList(questionCount) {
            while (size < questionCount) {
                val remaining = questionCount - size
                addAll((0 until optionCount).shuffled(random).take(remaining))
            }
        }
    }
}
