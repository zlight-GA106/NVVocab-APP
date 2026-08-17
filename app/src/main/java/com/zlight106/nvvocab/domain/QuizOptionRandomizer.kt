package com.zlight106.nvvocab.domain

import com.zlight106.nvvocab.data.QuizOption
import com.zlight106.nvvocab.data.QuizQuestion
import kotlin.random.Random

object QuizOptionRandomizer {
    fun randomize(
        question: QuizQuestion,
        random: Random = Random.Default,
    ): QuizQuestion {
        if (question.options.size < 2) return question
        require(question.options.size <= 26) { "单题选项数量不能超过 26。" }

        val shuffled = question.options.shuffled(random)
        val remappedAnswers = buildSet {
            shuffled.forEachIndexed { index, option ->
                if (option.id in question.answers) add(optionId(index))
            }
        }
        val remappedOptions = shuffled.mapIndexed { index, option ->
            QuizOption(id = optionId(index), text = option.text)
        }
        return question.copy(
            options = remappedOptions,
            answers = remappedAnswers,
        )
    }

    private fun optionId(index: Int): String = ('A'.code + index).toChar().toString()
}
