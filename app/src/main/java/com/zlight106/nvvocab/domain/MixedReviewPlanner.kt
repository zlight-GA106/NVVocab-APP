package com.zlight106.nvvocab.domain

import com.zlight106.nvvocab.data.MixedReviewItem
import com.zlight106.nvvocab.data.MixedReviewMode
import com.zlight106.nvvocab.data.ParaphraseSeed
import com.zlight106.nvvocab.data.WordEntry
import kotlin.math.floor
import kotlin.random.Random

object MixedReviewPlanner {
    fun plan(
        words: List<WordEntry>,
        selectedTags: Set<String>,
        requestedCount: Int,
        enabledModes: Set<MixedReviewMode>,
        percentages: Map<MixedReviewMode, Int>,
        random: Random = Random.Default,
        paraphraseSeeds: List<ParaphraseSeed> = emptyList(),
    ): List<MixedReviewItem> {
        require(requestedCount > 0) { "本次复习数量必须大于 0" }
        require(selectedTags.isNotEmpty()) { "请至少选择一个词库分类" }
        require(enabledModes.isNotEmpty()) { "请至少启用一种复习模式" }
        require(enabledModes.sumOf { percentages[it] ?: 0 } == 100) {
            "已启用模式的比例总和必须为 100%"
        }
        require(enabledModes.all { (percentages[it] ?: 0) > 0 }) {
            "已启用模式的比例必须大于 0%"
        }

        val selectedWords = words.asSequence()
            .filter { it.bookTag in selectedTags }
            .distinctBy(WordEntry::id)
            .toList()
            .shuffled(random)
        val selectedSeeds = paraphraseSeeds.asSequence()
            .filter { it.sourceReference.isNullOrBlank() || it.sourceReference in selectedTags }
            .distinctBy(ParaphraseSeed::id)
            .toList()
            .shuffled(random)
        val semanticMode = MixedReviewMode.ENGLISH_DEFINITION_TO_ENGLISH
        val semanticEnabled = semanticMode in enabledModes
        if (semanticEnabled) require(selectedSeeds.size >= 2) {
            "语义压缩练习至少需要两条可用种子"
        }

        val totalAvailable = selectedWords.size + if (semanticEnabled) selectedSeeds.size else 0
        val actualCount = requestedCount.coerceAtMost(totalAvailable)
        require(actualCount > 0) { "所选范围内没有可复习内容" }
        val modeCounts = allocateCounts(actualCount, enabledModes, percentages).toMutableMap()

        val semanticRequested = modeCounts[semanticMode] ?: 0
        val semanticCount = semanticRequested.coerceAtMost(selectedSeeds.size)
        modeCounts[semanticMode] = semanticCount
        var wordCount = modeCounts.filterKeys { it != semanticMode }.values.sum()
        val missing = actualCount - semanticCount - wordCount
        if (missing > 0) {
            val fallbackModes = MixedReviewMode.entries.filter { it in enabledModes && it != semanticMode }
            require(fallbackModes.isNotEmpty() && selectedWords.size >= wordCount + missing) {
                "当前词库或语义压缩种子不足以生成指定题量"
            }
            repeat(missing) { offset ->
                val mode = fallbackModes[offset % fallbackModes.size]
                modeCounts[mode] = modeCounts.getOrDefault(mode, 0) + 1
            }
            wordCount += missing
        }
        require(selectedWords.size >= wordCount) { "当前词库中的不同单词数量不足" }

        val wordModes = buildList {
            MixedReviewMode.entries.filter { it != semanticMode }.forEach { mode ->
                repeat(modeCounts[mode] ?: 0) { add(mode) }
            }
        }.shuffled(random)
        val items = buildList {
            selectedWords.take(wordModes.size).zip(wordModes).forEach { (word, mode) ->
                add(MixedReviewItem(word = word, mode = mode))
            }
            selectedSeeds.take(semanticCount).forEach { seed ->
                add(MixedReviewItem(paraphraseSeed = seed, mode = semanticMode))
            }
        }
        return items.shuffled(random)
    }

    fun allocateCounts(
        total: Int,
        enabledModes: Set<MixedReviewMode>,
        percentages: Map<MixedReviewMode, Int>,
    ): Map<MixedReviewMode, Int> {
        require(total >= 0) { "题量不能小于 0" }
        require(enabledModes.isNotEmpty()) { "请至少启用一种复习模式" }
        require(enabledModes.sumOf { percentages[it] ?: 0 } == 100) {
            "已启用模式的比例总和必须为 100%"
        }

        data class Share(val mode: MixedReviewMode, val base: Int, val remainder: Double)

        val shares = MixedReviewMode.entries
            .filter(enabledModes::contains)
            .map { mode ->
                val exact = total * (percentages[mode] ?: 0) / 100.0
                val base = floor(exact).toInt()
                Share(mode = mode, base = base, remainder = exact - base)
            }
        val result = shares.associate { it.mode to it.base }.toMutableMap()
        var remaining = total - shares.sumOf(Share::base)
        shares.sortedWith(
            compareByDescending<Share> { it.remainder }
                .thenBy { MixedReviewMode.entries.indexOf(it.mode) },
        ).forEach { share ->
            if (remaining > 0) {
                result[share.mode] = result.getValue(share.mode) + 1
                remaining -= 1
            }
        }
        return result
    }
}
