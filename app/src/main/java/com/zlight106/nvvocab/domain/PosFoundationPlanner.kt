package com.zlight106.nvvocab.domain

import com.zlight106.nvvocab.data.*

data class PosFoundationPlan(
    val drills: List<PosExercise>,
    val slots: List<PosExercise>,
    val cet4: PosExercise?,
) {
    val ready: Boolean get() = drills.size == 10 && slots.size == 10
    val queue: List<PosExercise> get() = drills + slots
}

/** Selects existing confirmed words and cached questions. Never generates answers or calls AI. */
object PosFoundationPlanner {
    fun prioritizedWords(words: List<WordEntry>, cache: List<PosExercise>, attempts: List<PracticeAttempt>): List<WordEntry> {
        val wrongIds = attempts.asSequence().filter {
            it.mode in PosPractice.modes && !it.correct &&
                it.errorType in setOf(PosErrorType.POS_WRONG, PosErrorType.MULTI_POS_CONFUSION)
        }.mapNotNull { wordId(it.itemId, cache) }.toSet()
        fun priority(word: WordEntry): Int {
            val parts = PosPractice.core(word.partsOfSpeech)
            return (if (word.id in wrongIds) 8 else 0) +
                (if (ProficiencyCalculator.calculate(word).score < 60) 4 else 0) +
                (if (parts.containsAll(setOf(PartOfSpeech.NOUN, PartOfSpeech.VERB))) 2 else 0) +
                (if (parts.any { it in setOf(PartOfSpeech.ADJECTIVE, PartOfSpeech.ADVERB) }) 2 else 0)
        }
        return words.filter(PosPractice::eligible).distinctBy { it.id }
            .sortedWith(compareByDescending<WordEntry>(::priority)
                .thenBy { ProficiencyCalculator.calculate(it).score }.thenBy { it.id })
    }

    fun plan(words: List<WordEntry>, cache: List<PosExercise>, attempts: List<PracticeAttempt>): PosFoundationPlan {
        val ranked = prioritizedWords(words, cache, attempts)
        val wordRank = ranked.mapIndexed { index, word -> word.id to index }.toMap()
        val wordIds = words.map { it.id }.toSet()
        val slotErrors = attempts.filter { it.mode == PracticeAttemptMode.SLOT_POS_CLASSIFICATION && !it.correct }.map { it.itemId }.toSet()
        val cachedSlots = cache.filter { it.mode == PosMode.SLOT } + cache.filter { it.mode == PosMode.CET4 }
            .flatMap { q -> q.blanks.map { Cet4Training.slotExercise(q, it) } }
        val slots = cachedSlots.filter { it.sourceWordId in wordIds }
            .distinctBy { it.id }
            .sortedWith(compareByDescending<PosExercise> { it.id in slotErrors }
                .thenBy { wordRank[it.sourceWordId] ?: Int.MAX_VALUE }.thenBy { it.id }).take(10)
        val cet4 = cache.firstOrNull { q ->
            q.mode == PosMode.CET4 && q.candidates.isNotEmpty() && q.candidates.all { it.wordId in wordIds }
        }
        return PosFoundationPlan(ranked.take(10).map(PosPractice::drill), slots, cet4)
    }

    fun wordId(itemId: String, cache: List<PosExercise>): String? = when {
        itemId.startsWith("pos-word:") -> itemId.removePrefix("pos-word:")
        else -> cache.firstOrNull { it.id == itemId }?.sourceWordId ?: cache.firstNotNullOfOrNull { q ->
            q.blanks.firstOrNull { b -> itemId == "${q.id}:${b.index}" || itemId == Cet4Training.slotItemId(q.id, b.index) }
                ?.let { b -> q.candidates.firstOrNull { it.id == b.answerId }?.wordId }
        }
    }
}
