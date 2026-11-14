package com.zlight106.nvvocab.data

enum class PartOfSpeech {
    NOUN, VERB, ADJECTIVE, ADVERB, OTHER, UNKNOWN;

    companion object {
        fun encode(values: Set<PartOfSpeech>): String = values.sortedBy { it.ordinal }.joinToString(",") { it.name }

        fun decode(value: String?): Set<PartOfSpeech> = value.orEmpty().split(',')
            .mapNotNull { token -> entries.firstOrNull { it.name == token.trim() } }.toSet()
            .ifEmpty { setOf(UNKNOWN) }
    }
}

enum class PosErrorType {
    POS_WRONG, MEANING_WRONG, COLLOCATION_WRONG, CONTEXT_WRONG, MULTI_POS_CONFUSION, OTHER,
}
