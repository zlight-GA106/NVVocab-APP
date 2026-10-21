package com.zlight106.nvvocab.domain

import org.junit.Assert.*
import org.junit.Test

class PosHeuristicsTest {
    @Test fun commonBareWordsExplainNounVerbPossibilitiesWithoutChoosingAnAnswer() {
        listOf("impact", "risk", "change", "increase", "influence", "support", "result", "type").forEach { word ->
            assertTrue(word, PosHeuristics.hints(word).any { it.contains("N / V 多词性") })
        }
        assertTrue(PosHeuristics.hints("  IMPACT  ").any { it.contains("impact") })
        assertTrue(PosHeuristics.hints("risks").any { it.contains("risk 常见 N / V") })
    }

    @Test fun limitationsKeepsTheTionHintAfterConservativePluralNormalization() {
        val hints = PosHeuristics.hints("limitations")
        assertTrue(hints.any { it.contains("-tion") && it.contains("N 名词") })
        assertTrue(hints.any { it.contains("复数") })
        assertTrue(PosHeuristics.hints("possibilities").any { it.contains("-ity") })
        assertTrue(PosHeuristics.hints("happiness").any { it.contains("-ness") })
    }

    @Test fun seeminglyShowsAdverbHintButLyAdjectiveExceptionsDoNot() {
        assertTrue(PosHeuristics.hints("seemingly").any { it.contains("-ly") && it.contains("Adv 副词") })
        listOf("friendly", "lovely", "likely", "lively", "lonely").forEach { word ->
            val hints = PosHeuristics.hints(word)
            assertTrue(word, hints.any { it.contains("例外") && it.contains("A / Adj") })
            assertFalse(word, hints.any { it.contains("-ly 常见 Adv") })
        }
        assertFalse(PosHeuristics.hints("family").any { it.contains("-ly 常见 Adv") })
        assertFalse(PosHeuristics.hints("supply").any { it.contains("-ly 常见 Adv") })
    }

    @Test fun allRequestedNounSuffixesHaveTeachingHints() {
        mapOf("tion" to "action", "sion" to "decision", "ment" to "development", "ness" to "happiness",
            "ity" to "ability", "ance" to "performance", "ence" to "independence", "ship" to "friendship",
            "ism" to "socialism", "ist" to "artist", "age" to "passage", "ure" to "culture",
            "er" to "worker", "or" to "actor").forEach { (suffix, word) ->
            assertTrue(word, PosHeuristics.hints(word).any { it.contains("-$suffix 常见 N 名词") })
        }
    }

    @Test fun allRequestedAdjectiveSuffixesHaveTeachingHints() {
        mapOf("al" to "national", "ful" to "helpful", "less" to "hopeless", "ous" to "dangerous",
            "ive" to "active", "able" to "suitable", "ible" to "visible", "ic" to "basic",
            "ical" to "logical", "ary" to "ordinary", "ent" to "different", "ant" to "important",
            "y" to "cloudy").forEach { (suffix, word) ->
            assertTrue(word, PosHeuristics.hints(word).any { it.contains("-$suffix 常见 A / Adj") })
        }
    }

    @Test fun verbSuffixesWinOverTheShortAdjectiveYEnding() {
        mapOf("ize" to "realize", "ise" to "modernise", "ify" to "clarify", "fy" to "satisfy",
            "en" to "strengthen", "ate" to "create").forEach { (suffix, word) ->
            val hints = PosHeuristics.hints(word)
            assertTrue(word, hints.any { it.contains("-$suffix 常见 V 动词") })
            assertFalse(word, hints.any { it.contains("-y 常见 A / Adj") })
        }
    }

    @Test fun noSuffixOrNonWordInputDoesNotInventAHint() {
        assertTrue(PosHeuristics.hints("book").isEmpty())
        assertTrue(PosHeuristics.hints("two words").isEmpty())
        assertTrue(PosHeuristics.hints("").isEmpty())
        assertEquals("词尾只用于初筛，不等于最终答案", PosHeuristics.DISCLAIMER)
    }
}
