package com.zlight106.nvvocab.domain

import com.zlight106.nvvocab.data.ParsedWord

object WordTextParser {
    private val phoneticPattern = Regex(
        "^([A-Za-z][A-Za-z'’-]*(?:\\s+[A-Za-z][A-Za-z'’-]*)*)\\s*(?:\\[([^]]+)]|/([^/]+)/)\\s+(.+)$",
    )
    private val plainPattern = Regex("^([A-Za-z][A-Za-z'’-]*)\\s+(.+)$")

    fun parse(text: String): List<ParsedWord> = text
        .lineSequence()
        .mapNotNull(::parseLine)
        .toList()

    private fun parseLine(line: String): ParsedWord? {
        val normalized = line.trim()
        if (normalized.isEmpty()) return null

        phoneticPattern.matchEntire(normalized)?.let { match ->
            return ParsedWord(
                spelling = match.groupValues[1].trim(),
                phonetic = (match.groupValues[2].ifBlank { match.groupValues[3] }).trim(),
                translation = match.groupValues[4].trim(),
            )
        }

        plainPattern.matchEntire(normalized)?.let { match ->
            return ParsedWord(
                spelling = match.groupValues[1].trim(),
                phonetic = "",
                translation = match.groupValues[2].trim(),
            )
        }

        val parts = normalized.split(Regex("\\s+"), limit = 2)
        if (parts.size < 2) return null

        return ParsedWord(
            spelling = parts[0],
            phonetic = "",
            translation = parts[1],
        )
    }
}
