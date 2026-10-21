package com.zlight106.nvvocab.domain

import org.junit.Assert.*
import org.junit.Test

class PosSlotRuleEngineTest {
    private fun contains(sentence: String, text: String) {
        assertTrue("$sentence -> ${PosSlotRuleEngine.hint(sentence)}", PosSlotRuleEngine.hint(sentence)?.contains(text) == true)
    }

    @Test fun modalAndInfinitiveToSuggestBaseVerbs() {
        listOf("can", "may", "must", "should", "will", "could").forEach { modal ->
            contains("They $modal ___ the system.", "V 动词原形")
        }
        contains("They cannot ___ the system.", "V 动词原形")
        contains("They should not ___ the system.", "V 动词原形")
        contains("to ___", "V 动词原形")
        contains("We want to ______ our skills.", "V 动词原形")
    }

    @Test fun prepositionalToDoesNotMasqueradeAsInfinitive() {
        listOf("We look forward to ______.", "I am used to ______.", "They are not used to ______.",
            "He is accustomed to ______.", "In addition to ______, we need time.",
            "They object to ______.", "They went to ______.", "According to ______, it works.").forEach { sentence ->
            assertNull(sentence, PosSlotRuleEngine.hint(sentence))
        }
    }

    @Test fun perfectAuxiliaryExplainsVerbFormAndAbstainsFromPossessiveAmbiguity() {
        listOf("have", "has", "had").forEach { have -> contains("They $have ___ the project.", "过去分词") }
        assertNull(PosSlotRuleEngine.hint("They have ______ books."))
    }

    @Test fun demonstrativesAndPossessivesExplainNounNumber() {
        contains("these ___", "复数 N 名词")
        contains("Despite these ______, the project continued.", "复数 N 名词")
        contains("those ______", "复数 N 名词")
        contains("this ______", "单数 N 名词")
        contains("that ______", "单数 N 名词")
        contains("our ______", "N 名词")
        assertNull(PosSlotRuleEngine.hint("I think that ______."))
    }

    @Test fun degreeWordsSuggestAdjectiveWhenContextSupportsIt() {
        contains("very ___", "A / Adj 形容词")
        listOf("extremely", "highly", "quite", "somewhat").forEach { degree ->
            contains("The result is $degree ______.", "A / Adj 形容词")
        }
        assertNull(PosSlotRuleEngine.hint("He spoke very ______."))
    }

    @Test fun articleAdjectiveHeadNounAndArticleBeforeNounAreDifferent() {
        contains("a rapid ___", "N 名词")
        contains("an important ___", "N 名词")
        contains("a useful ______", "N 名词")
        contains("The rapid ______ of technology changed our lives.", "the/a/an + adjective + ___ + of")
        contains("a ______", "N 名词")
        contains("the ______ students", "A / Adj 形容词")
        contains("these ______ books", "A / Adj 形容词")
        contains("______ technology", "A / Adj 形容词")
        contains("lower ______ of death", "N 名词")
    }

    @Test fun prepositionAndSimpleSubjectObjectRulesRemainTeachingOnly() {
        listOf("in", "on", "of", "for", "with", "by", "from").forEach { preposition ->
            contains("$preposition ______", "N 名词 / V-ing")
        }
        contains("They ______ the system.", "主语 + ___ + 宾语 → V")
        contains("The researchers ______ the report.", "主语 + ___ + 宾语 → V")
        assertNull(PosSlotRuleEngine.hint("Amazingly ______ tomorrow."))
    }

    @Test fun cetPassageUsesOnlyChosenBlankAndNeverOtherAnswers() {
        val passage = "They can {{1}} the project. Despite these {{2}}, the study continued. An unclear {{3}} tomorrow."
        assertNull(PosSlotRuleEngine.hint(passage))
        assertTrue(PosSlotRuleEngine.hint(passage, 1)?.contains("V 动词原形") == true)
        assertTrue(PosSlotRuleEngine.hint(passage, 2)?.contains("复数 N 名词") == true)
        assertNull(PosSlotRuleEngine.hint(passage, 99))
        assertNull(PosSlotRuleEngine.hint("{{1}} and {{1}}", 1))
        assertNull(PosSlotRuleEngine.hint("No blank appears here."))
    }

    @Test fun neighborBlankBoundsCannotLeakIntoCurrentRule() {
        assertNull(PosSlotRuleEngine.hint("They can {{1}} {{2}}.", 2))
        assertTrue(PosSlotRuleEngine.hint("They can {{1}} {{2}}.", 1)?.contains("V 动词原形") == true)
        assertNull(PosSlotRuleEngine.hint("can. ______", 1))
    }
}
