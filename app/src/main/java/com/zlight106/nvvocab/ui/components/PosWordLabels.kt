package com.zlight106.nvvocab.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import com.zlight106.nvvocab.data.*
import com.zlight106.nvvocab.ui.MainViewModel
import com.zlight106.nvvocab.domain.PosPractice
import com.zlight106.nvvocab.domain.PosHeuristics

@Composable
fun PosWordLabels(original: WordEntry, viewModel: MainViewModel) {
    val words by viewModel.words.collectAsState()
    val word = words.firstOrNull { it.id == original.id } ?: original
    var inspecting by remember(word.id) { mutableStateOf(false) }
    val parts = PosPractice.core(word.partsOfSpeech)
    if (parts.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        parts.forEach { p -> SuggestionChip(onClick = { inspecting = true }, label = { Text(p.shortLabel) }) }
        if (word.posNeedsReview) Text("待复核", color = MaterialTheme.colorScheme.error)
    }
    if (inspecting) AlertDialog(onDismissRequest = { inspecting = false }, title = { Text("${word.spelling} · ${parts.joinToString { it.shortLabel }}") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(word.translation); Text(word.posExplanation.ifBlank { "尚无缓存的用法 / 例句解释。" })
            PosHeuristics.hints(word.spelling).forEach { Text(it) }
            Text("词尾只用于初筛，不等于最终答案", style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton(onClick = { inspecting = false }) { Text("关闭") } },
        dismissButton = { TextButton(onClick = { viewModel.annotatePos(listOf(word), false, true) }) { Text(if (word.posExplanation.isBlank()) "生成 AI 解释" else "重新生成解释") } })
}
