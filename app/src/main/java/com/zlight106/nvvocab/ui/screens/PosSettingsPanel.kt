package com.zlight106.nvvocab.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zlight106.nvvocab.data.*
import com.zlight106.nvvocab.ui.MainViewModel
import com.zlight106.nvvocab.ui.components.NvvDropdown
import com.zlight106.nvvocab.ui.icons.NvvIcons

@Composable
internal fun PosSettingsPanel(viewModel: MainViewModel) {
    var settings by remember { mutableStateOf(viewModel.posSettings) }
    var count by remember { mutableStateOf(settings.questionCount.toString()) }
    var batch by remember { mutableStateOf(settings.batchSize.toString()) }
    var prompts by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(count, { count = it.filter(Char::isDigit).take(3) }, label = { Text("默认题量（1–200）") }, modifier = Modifier.fillMaxWidth())
        NvvDropdown("默认限时", settings.timeLimitSeconds, listOf(0 to "不限时", 3 to "3秒", 5 to "5秒"), NvvIcons.Clock, { settings = settings.copy(timeLimitSeconds = it) })
        PosToggle("速判完整模式（默认宽松）", settings.completeMode) { settings = settings.copy(completeMode = it) }
        OutlinedTextField(batch, { batch = it.filter(Char::isDigit).take(3) }, label = { Text("AI 每批标注数量（30–100）") }, modifier = Modifier.fillMaxWidth())
        Text("语境 / 空位出题每次最多10词，避免截断；标注按上方批量数量处理。", style = MaterialTheme.typography.bodySmall)
        PosToggle("标注时自动生成词性解释", settings.autoExplanation) { settings = settings.copy(autoExplanation = it) }
        PosToggle("允许将词性判断加入混合复习", settings.mixedEnabled) { settings = settings.copy(mixedEnabled = it) }
        PosToggle("进入词性专项时自动分析 UNKNOWN", settings.autoUnknown) { settings = settings.copy(autoUnknown = it) }
        OutlinedButton(onClick = { prompts = !prompts }, modifier = Modifier.fillMaxWidth()) { Text("查看 / 修改独立 POS AI Prompt") }
        if (prompts) {
            OutlinedTextField(settings.annotationPrompt, { settings = settings.copy(annotationPrompt = it) }, label = { Text("词性标注 Prompt") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(settings.contextPrompt, { settings = settings.copy(contextPrompt = it) }, label = { Text("语境词性 Prompt") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(settings.slotPrompt, { settings = settings.copy(slotPrompt = it) }, label = { Text("空位词性 Prompt") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(settings.cet4Prompt, { settings = settings.copy(cet4Prompt = it) }, label = { Text("CET-4 Section A Prompt") }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { settings = settings.copy(annotationPrompt = POS_ANNOTATION_PROMPT, contextPrompt = POS_CONTEXT_PROMPT, slotPrompt = POS_SLOT_PROMPT, cet4Prompt = POS_CET4_PROMPT) }) { Text("恢复默认 Prompt") }
        }
        Button(onClick = {
            settings = settings.copy(questionCount = count.toInt(), batchSize = batch.toInt())
            viewModel.savePosSettings(settings); viewModel.notifyUser("词性专项设置已保存")
        }, enabled = count.toIntOrNull() in 1..200 && batch.toIntOrNull() in 30..100 &&
            listOf(settings.annotationPrompt, settings.contextPrompt, settings.slotPrompt, settings.cet4Prompt).all { it.isNotBlank() }, modifier = Modifier.fillMaxWidth()) { Text("保存词性专项设置") }
    }
}
