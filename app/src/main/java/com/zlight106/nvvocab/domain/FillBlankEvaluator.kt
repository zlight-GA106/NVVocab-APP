package com.zlight106.nvvocab.domain

import com.zlight106.nvvocab.data.AnswerEvaluationResult
import com.zlight106.nvvocab.data.FillBlankEvaluation
import com.zlight106.nvvocab.data.QuizQuestion
import com.zlight106.nvvocab.data.QuizQuestionType
import java.util.Locale

object FillBlankEvaluator {
    fun evaluateLocally(
        question: QuizQuestion,
        userAnswer: String,
        ignoreCase: Boolean,
    ): FillBlankEvaluation {
        require(question.type == QuizQuestionType.FILL_BLANK) { "仅填空题支持文本答案判定。" }
        val normalizedUserAnswer = normalize(userAnswer, ignoreCase)
        if (normalizedUserAnswer.isBlank()) {
            return FillBlankEvaluation(
                result = AnswerEvaluationResult.INCORRECT,
                reason = "答案为空",
                confidence = 1.0,
                evaluatedByAi = false,
            )
        }
        val accepted = buildSet {
            question.referenceAnswer?.takeIf(String::isNotBlank)?.let(::add)
            addAll(question.acceptedAnswers.filter(String::isNotBlank))
        }.mapTo(linkedSetOf()) { normalize(it, ignoreCase) }
        return if (normalizedUserAnswer in accepted) {
            FillBlankEvaluation(
                result = AnswerEvaluationResult.CORRECT,
                reason = "与参考答案精确匹配",
                confidence = 1.0,
                evaluatedByAi = false,
            )
        } else {
            FillBlankEvaluation(
                result = AnswerEvaluationResult.REVIEW,
                reason = "本地答案未精确匹配",
                confidence = 0.0,
                evaluatedByAi = false,
            )
        }
    }

    fun normalize(value: String, ignoreCase: Boolean): String {
        var normalized = value.trim().replace(WHITESPACE, " ")
        while (normalized.firstOrNull() in TRIMMABLE_SYMBOLS) normalized = normalized.drop(1).trimStart()
        while (normalized.lastOrNull() in TRIMMABLE_SYMBOLS) normalized = normalized.dropLast(1).trimEnd()
        return if (ignoreCase) normalized.lowercase(Locale.ROOT) else normalized
    }

    private val WHITESPACE = Regex("\\s+")
    private val TRIMMABLE_SYMBOLS = setOf(
        '。', '，', ',', '；', ';', '：', ':', '！', '!',
        '“', '”', '‘', '’', '"', '\'', '（', '）', '(', ')',
        '【', '】', '[', ']', '《', '》',
    )
}
