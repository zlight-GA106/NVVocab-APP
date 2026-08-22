package com.zlight106.nvvocab.domain

import com.zlight106.nvvocab.data.ContrastQuestion
import com.zlight106.nvvocab.data.ParaphraseSeed
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.random.Random

object ParaphrasePracticeGenerator {
    fun generate(
        seed: ParaphraseSeed,
        candidates: List<ParaphraseSeed>,
        optionCount: Int,
        random: Random = Random.Default,
    ): ContrastQuestion {
        val normalizedCount = optionCount.coerceIn(2, 8)
        val distractors = candidates.asSequence()
            .filter { it.id != seed.id }
            .map(ParaphraseSeed::targetText)
            .filter { it.isNotBlank() && !it.equals(seed.targetText, ignoreCase = true) }
            .distinctBy(String::lowercase)
            .toList()
            .shuffled(random)
            .take(normalizedCount - 1)
        require(distractors.isNotEmpty()) { "语义压缩练习至少需要一条不同的等效表达" }
        val options = (distractors + seed.targetText).shuffled(random)
        return ContrastQuestion(
            id = UUID.randomUUID().toString(),
            wordId = seed.id,
            prompt = seed.sourceText,
            options = options,
            correctIndex = options.indexOf(seed.targetText),
        )
    }
}

object ParaphraseSeedBatchParser {
    fun parse(text: String, userId: String? = null, now: Long = System.currentTimeMillis()): List<ParaphraseSeed> =
        text.lineSequence().mapIndexedNotNull { index, rawLine ->
            val line = rawLine.trim()
            if (line.isBlank()) return@mapIndexedNotNull null
            val sections = line.split('|').map(String::trim)
            val pair = sections.first().split("=>", limit = 2).map(String::trim)
            if (pair.size != 2 || pair.any(String::isBlank)) return@mapIndexedNotNull null
            ParaphraseSeed(
                id = UUID.randomUUID().toString(),
                userId = userId,
                sourceText = pair[0],
                targetText = pair[1],
                contextText = sections.getOrNull(1)?.takeIf(String::isNotBlank),
                sourceReference = sections.getOrNull(2)?.takeIf(String::isNotBlank),
                notes = sections.getOrNull(3)?.takeIf(String::isNotBlank),
                createdAt = now + index,
                updatedAt = now + index,
                dirty = true,
            )
        }.toList()
}

interface ParaphraseAiExpansionGateway {
    suspend fun expand(seeds: List<ParaphraseSeed>): List<ParaphraseSeed>
}

interface ParaphraseDistractorProvider {
    suspend fun generate(seed: ParaphraseSeed, count: Int): List<String>
}

interface ParaphraseSeedXmlCodec {
    fun read(input: InputStream): List<ParaphraseSeed>
    fun write(entries: List<ParaphraseSeed>, output: OutputStream)
}
