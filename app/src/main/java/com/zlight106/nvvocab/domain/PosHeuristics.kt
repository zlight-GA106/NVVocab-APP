package com.zlight106.nvvocab.domain

import java.util.Locale

/** Teaching hints only. This class deliberately has no answer or PartOfSpeech API. */
object PosHeuristics {
    const val DISCLAIMER = "词尾只用于初筛，不等于最终答案"

    private val nounSuffixes = listOf(
        "tion", "sion", "ment", "ness", "ity", "ance", "ence", "ship", "ism", "ist", "age", "ure", "er", "or",
    ).sortedByDescending(String::length)
    private val adjectiveSuffixes = listOf(
        "al", "ful", "less", "ous", "ive", "able", "ible", "ic", "ical", "ary", "ent", "ant", "y",
    ).sortedByDescending(String::length)
    private val verbSuffixes = listOf("ize", "ise", "ify", "fy", "en", "ate").sortedByDescending(String::length)
    private val commonNounVerbs = setOf("impact", "risk", "change", "increase", "influence", "support", "result", "type")
    private val adjectiveLyExceptions = setOf("friendly", "lovely", "likely", "lively", "lonely", "elderly", "costly", "silly", "ugly", "holy")
    private val otherLyExceptions = mapOf(
        "family" to "family 常见 N 名词",
        "assembly" to "assembly 常见 N 名词",
        "apply" to "apply 常见 V 动词",
        "comply" to "comply 常见 V 动词",
        "imply" to "imply 常见 V 动词",
        "multiply" to "multiply 常见 V 动词",
        "reply" to "reply 常见 N / V 多词性",
        "supply" to "supply 常见 N / V 多词性",
        "early" to "early 常见 A / Adj 与 Adv 多词性",
        "daily" to "daily 可有 A / Adj、Adv 等多种用法",
        "weekly" to "weekly 可有 A / Adj、Adv 等多种用法",
        "monthly" to "monthly 可有 A / Adj、Adv 等多种用法",
        "yearly" to "yearly 可有 A / Adj、Adv 等多种用法",
    )

    fun hints(word: String): List<String> {
        val value = word.trim().lowercase(Locale.ROOT).replace('’', '\'')
            .trim { !it.isLetter() && it != '\'' && it != '-' }
        if (!value.matches(Regex("[a-z]+(?:['-][a-z]+)*"))) return emptyList()

        val ending = value.substringAfterLast('-')
        if (ending in adjectiveLyExceptions) {
            return listOf("例外提示：$ending 常作 A / Adj 形容词，不能仅凭 -ly 当作副词。")
        }
        otherLyExceptions[ending]?.let {
            return listOf("例外提示：$it，不能仅凭 -ly 当作副词。")
        }

        val result = mutableListOf<String>()
        val commonBase = when {
            value in commonNounVerbs -> value
            value.endsWith("s") && value.dropLast(1) in commonNounVerbs -> value.dropLast(1)
            value.endsWith("es") && value.dropLast(2) in commonNounVerbs -> value.dropLast(2)
            else -> null
        }
        if (commonBase != null) result += "注意：$commonBase 常见 N / V 多词性，具体用法仍需结合语境。"

        // Only normalize a plural when its base has a listed noun ending. Do not stem arbitrary words.
        val nounBase = when {
            nounSuffixes.any { ending.length > it.length && ending.endsWith(it) } -> ending
            ending.endsWith("s") && !ending.endsWith("ss") && nounSuffixes.any {
                ending.dropLast(1).length > it.length && ending.dropLast(1).endsWith(it)
            } -> ending.dropLast(1)
            ending.endsWith("ies") && nounSuffixes.any {
                (ending.dropLast(3) + "y").length > it.length && (ending.dropLast(3) + "y").endsWith(it)
            } -> ending.dropLast(3) + "y"
            else -> null
        }
        val nounSuffix = nounBase?.let { base -> nounSuffixes.firstOrNull { base.length > it.length && base.endsWith(it) } }
        when {
            nounSuffix != null -> {
                val pluralNote = if (nounBase != ending) "（先去掉常见复数词尾）" else ""
                result += "机械提示：-$nounSuffix 常见 N 名词$pluralNote。"
            }
            ending.length > 3 && ending.endsWith("ly") -> result += "机械提示：-ly 常见 Adv 副词，需注意形容词等例外。"
            else -> {
                // Pick the longest ending across categories; e.g. -ment before -ent and -ify before -y.
                val adjective = adjectiveSuffixes.firstOrNull { ending.length > it.length && ending.endsWith(it) }
                val verb = verbSuffixes.firstOrNull { ending.length > it.length && ending.endsWith(it) }
                if (verb != null && (adjective == null || verb.length > adjective.length)) {
                    result += "机械提示：-$verb 常见 V 动词。"
                } else if (adjective != null) {
                    result += "机械提示：-$adjective 常见 A / Adj 形容词。"
                }
            }
        }
        return result
    }
}
