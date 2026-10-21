package com.zlight106.nvvocab.domain

import com.zlight106.nvvocab.data.PartOfSpeech
import com.zlight106.nvvocab.data.PosBlank
import com.zlight106.nvvocab.data.PosExercise
import com.zlight106.nvvocab.data.PosMode

data class Cet4ClassificationProgress(
    val classified: Int,
    val uncertain: Int,
    val unprocessed: Int,
    val total: Int,
) {
    // A rehearsal must remain usable when the learner does not recognise every word.
    val canContinue: Boolean get() = true
}

/** Local study helpers. Candidate filtering deliberately has no access to answer metadata. */
object Cet4Training {
    fun classificationProgress(
        candidateIds: List<String>,
        classified: Map<String, Set<PartOfSpeech>>,
    ): Cet4ClassificationProgress {
        val selections = candidateIds.distinct().map { classified[it].orEmpty() }
        val uncertain = selections.count { PartOfSpeech.UNKNOWN in it }
        val known = selections.count { PartOfSpeech.UNKNOWN !in it && PosPractice.core(it).isNotEmpty() }
        return Cet4ClassificationProgress(known, uncertain, selections.size - known - uncertain, selections.size)
    }

    fun filterCandidates(
        candidateIds: List<String>,
        classified: Map<String, Set<PartOfSpeech>>,
        filter: PartOfSpeech? = null,
    ): List<String> = candidateIds.filter { id ->
        val userParts = classified[id].orEmpty()
        when (filter) {
            null -> true
            PartOfSpeech.UNKNOWN -> PartOfSpeech.UNKNOWN in userParts || PosPractice.core(userParts).isEmpty()
            in PartOfSpeech.core -> PartOfSpeech.UNKNOWN !in userParts && filter in userParts
            else -> false
        }
    }

    fun slotItemId(exerciseId: String, blankIndex: Int): String = "cet4-slot-pos:$exerciseId:$blankIndex"

    /** Keep every other blank hidden: using its correct candidate would disclose an answer. */
    fun slotExercise(exercise: PosExercise, blank: PosBlank): PosExercise {
        require(exercise.mode == PosMode.CET4 && exercise.blanks.any { it.index == blank.index })
        val sentence = Regex("\\{\\{(\\d+)}}").replace(exercise.sentence) { match ->
            if (match.groupValues[1].toInt() == blank.index) "______" else "〔第${match.groupValues[1]}空〕"
        }
        return PosExercise(
            id = slotItemId(exercise.id, blank.index),
            mode = PosMode.SLOT,
            sourceWordId = exercise.candidates.firstOrNull { it.id == blank.answerId }?.wordId,
            sourceTag = exercise.sourceTag,
            sentence = sentence,
            correctParts = setOf(blank.correctPos),
            grammarClue = blank.grammarClue,
            explanation = blank.explanation,
            difficulty = exercise.difficulty,
        )
    }
}
