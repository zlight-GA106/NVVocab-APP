package com.zlight106.nvvocab.domain

import com.zlight106.nvvocab.data.*
import org.junit.Assert.*
import org.junit.Test

class PosFoundationPlannerTest {
    private fun word(id: String, parts: Set<PartOfSpeech> = setOf(PartOfSpeech.NOUN)) = WordEntry(
        id, null, id, null, "释义", "CET4", 0, 0, 1, 2.5, 0, 0, false, parts)
    private fun slot(word: WordEntry) = PosExercise("slot:${word.id}", PosMode.SLOT, word.id, word.bookTag,
        "The rapid ______ of technology continues.", correctParts = setOf(PartOfSpeech.NOUN), explanation = "名词")

    @Test fun foundationHasTenWordQuestionsThenTenCachedSlotQuestions() {
        val words = (1..12).map { word("word$it") }
        val plan = PosFoundationPlanner.plan(words, words.map(::slot), emptyList())
        assertTrue(plan.ready)
        assertEquals(20, plan.queue.size)
        assertTrue(plan.queue.take(10).all { it.mode == PosMode.DRILL })
        assertTrue(plan.queue.drop(10).all { it.mode == PosMode.SLOT })
        assertEquals(20, plan.queue.map { it.id }.distinct().size)
    }

    @Test fun missingCacheDoesNotInventQuestionsOrUseUnknownAsAnswers() {
        val words = (1..10).map { word("word$it") } + word("unknown", setOf(PartOfSpeech.UNKNOWN))
        val plan = PosFoundationPlanner.plan(words, emptyList(), emptyList())
        assertFalse(plan.ready)
        assertEquals(10, plan.drills.size)
        assertTrue(plan.slots.isEmpty())
        assertFalse(plan.drills.any { PartOfSpeech.UNKNOWN in it.correctParts })
    }

    @Test fun priorPosErrorsAndMultiplePartsHavePriority() {
        val plain = word("a")
        val multi = word("impact", setOf(PartOfSpeech.NOUN, PartOfSpeech.VERB))
        val wrong = word("risk", setOf(PartOfSpeech.NOUN, PartOfSpeech.VERB))
        val attempt = PosPractice.attempt(PosPractice.drill(wrong), "s", 0, setOf(PartOfSpeech.ADVERB), false, 500)
        assertEquals(listOf("risk", "impact", "a"), PosFoundationPlanner.prioritizedWords(listOf(plain, multi, wrong), emptyList(), listOf(attempt)).map { it.id })
    }
}
