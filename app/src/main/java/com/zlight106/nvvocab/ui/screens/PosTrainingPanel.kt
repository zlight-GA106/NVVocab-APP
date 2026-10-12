package com.zlight106.nvvocab.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zlight106.nvvocab.data.*
import com.zlight106.nvvocab.domain.*
import com.zlight106.nvvocab.ui.MainViewModel
import com.zlight106.nvvocab.ui.components.NvvDropdown
import com.zlight106.nvvocab.ui.components.SectionCard
import com.zlight106.nvvocab.ui.icons.NvvIcons
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun PosTrainingPanel(viewModel: MainViewModel, words: List<WordEntry>, tags: List<String>, onStart: (PracticeSessionRequest) -> Unit) {
    val cache by viewModel.posExercises.collectAsStateWithLifecycle()
    val attempts by viewModel.practiceAttempts.collectAsStateWithLifecycle()
    val busy by viewModel.posBusy.collectAsStateWithLifecycle()
    val progress by viewModel.posProgress.collectAsStateWithLifecycle()
    val loaded by viewModel.localDataLoaded.collectAsStateWithLifecycle()
    var settings by remember { mutableStateOf(viewModel.posSettings) }
    var mode by remember { mutableStateOf(PosMode.DRILL) }
    var tag by remember { mutableStateOf<String?>(null) }
    var source by remember { mutableStateOf("全部词库") }
    var onlyUnknown by remember { mutableStateOf(true) }
    var pair by remember { mutableStateOf("全部词性") }
    var onlyPosErrors by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var selecting by remember { mutableStateOf(false) }
    var editingWord by remember { mutableStateOf<WordEntry?>(null) }
    var showAnalysis by remember { mutableStateOf(false) }
    var showWrong by remember { mutableStateOf(false) }
    val scoped = words.filter { tag == null || it.bookTag == tag }
    val latestErrors = attempts.filter { it.mode in PosPractice.modes }.groupBy { it.itemId }.mapValues { (_, rows) -> rows.maxBy { it.timestamp } }
        .values.filter { !it.correct && (!onlyPosErrors || it.errorType in setOf(PosErrorType.POS_WRONG, PosErrorType.MULTI_POS_CONFUSION)) }
    val wrongWordIds = latestErrors.mapNotNull { a ->
        if (a.itemId.startsWith("pos-word:")) a.itemId.removePrefix("pos-word:")
        else cache.firstOrNull { it.id == a.itemId }?.sourceWordId
            ?: cache.firstNotNullOfOrNull { q -> q.blanks.firstOrNull { "${q.id}:${it.index}" == a.itemId }?.let { b -> q.candidates.first { it.id == b.answerId }.wordId } }
    }.toSet()
    val targets = scoped.filter { when (source) {
        "熟练度低" -> ProficiencyCalculator.calculate(it).score < 60
        "词性错题" -> it.id in wrongWordIds
        "当前选中单词" -> it.id in selectedIds
        else -> true
    } }
    val wanted = when (pair) { "只练 Adj / Adv" -> setOf(PartOfSpeech.ADJECTIVE, PartOfSpeech.ADVERB)
        "只练 N / V" -> setOf(PartOfSpeech.NOUN, PartOfSpeech.VERB)
        else -> PartOfSpeech.core.toSet() }
    val eligible = targets.filter { PosPractice.eligible(it) && it.partsOfSpeech.any { p -> p in wanted } }
    val questions = if (mode == PosMode.DRILL) eligible.map(PosPractice::drill) else cache.filter { q ->
        q.mode == mode && (tag == null || q.sourceWordId in scoped.map { it.id } || (q.mode == PosMode.CET4 && q.candidates.all { c -> c.wordId in scoped.map { it.id } })) &&
            (q.mode == PosMode.CET4 || q.sourceWordId in targets.map { it.id }) &&
            (q.mode == PosMode.CET4 || q.correctParts.any { it in wanted }) &&
            (source != "词性错题" || latestErrors.any { a -> a.itemId == q.id || a.itemId.startsWith(q.id + ":") })
    }
    LaunchedEffect(tag, loaded) {
        if (loaded && settings.autoUnknown && scoped.any { it.partsOfSpeech == setOf(PartOfSpeech.UNKNOWN) }) viewModel.annotatePos(scoped, true)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("词性专项 / CET-4", style = MaterialTheme.typography.titleLarge)
                NvvDropdown("训练模式", mode, PosMode.entries.map { it to it.label }, NvvIcons.BookOpen, { mode = it })
                NvvDropdown("词库分类", tag, listOf(null to "全部词库") + tags.map { it to it }, NvvIcons.BookOpen, { tag = it })
                NvvDropdown("题目来源", source, listOf("全部词库", "当前词库", "熟练度低", "词性错题", "当前选中单词").map { it to it }, NvvIcons.BookOpen, { source = it })
                if (source == "当前词库" && tag == null) Text("请在上方选择词库分类", color = MaterialTheme.colorScheme.error)
                if (source == "当前选中单词") OutlinedButton(onClick = { selecting = true }, modifier = Modifier.fillMaxWidth()) { Text("选择单词（${selectedIds.size}）") }
                NvvDropdown("词性范围", pair, listOf("全部词性", "只练 Adj / Adv", "只练 N / V").map { it to it }, NvvIcons.BookOpen, { pair = it })
                PosToggle("仅识别 UNKNOWN / 待复核", onlyUnknown) { onlyUnknown = it }
                if (source == "词性错题") PosToggle("只练词性判断错误", onlyPosErrors) { onlyPosErrors = it }
                Button(onClick = { viewModel.annotatePos(targets, onlyUnknown) }, enabled = !busy && targets.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("自动识别词性（批量 DeepSeek）") }
                Text("范围 ${targets.size} 词 · 可训练 ${eligible.size} 词 · 未识别 ${targets.count { PartOfSpeech.UNKNOWN in it.partsOfSpeech }} · 待复核 ${targets.count { it.posNeedsReview }}")
                if (mode != PosMode.CET4) {
                    OutlinedTextField(settings.questionCount.toString(), { text -> text.toIntOrNull()?.takeIf { it in 1..200 }?.let { settings = settings.copy(questionCount = it); viewModel.savePosSettings(settings) } }, label = { Text("本次题量") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    NvvDropdown("单题限时", settings.timeLimitSeconds, listOf(0 to "不限时", 3 to "3 秒", 5 to "5 秒"), NvvIcons.Clock, { settings = settings.copy(timeLimitSeconds = it); viewModel.savePosSettings(settings) })
                }
                if (mode == PosMode.DRILL) PosToggle("完整模式：选出所有正确词性（默认宽松）", settings.completeMode) { settings = settings.copy(completeMode = it); viewModel.savePosSettings(settings) }
                if (mode != PosMode.DRILL) OutlinedButton(onClick = { viewModel.generatePos(mode, eligible, settings.questionCount) }, enabled = !busy && eligible.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(if (questions.isEmpty()) "生成并缓存题目" else "重新生成（保留旧题与记录）") }
                Text(if (mode == PosMode.DRILL) "选择后立即本地判题；其他可能词性也会列出。" else "已有 ${questions.size} ${if (mode == PosMode.CET4) "套" else "道"}缓存，答题无需 AI 或网络。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { onStart(PracticeSessionRequest.Pos(questions.shuffled().take(if (mode == PosMode.CET4) 1 else settings.questionCount), settings.completeMode, settings.timeLimitSeconds)) }, enabled = !busy && questions.isNotEmpty() && (source != "当前词库" || tag != null), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("开始离线训练") }
                if (busy) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { showAnalysis = !showAnalysis }, modifier = Modifier.fillMaxWidth()) { Text("词性能力分析") }
                OutlinedButton(onClick = { showWrong = !showWrong }, modifier = Modifier.fillMaxWidth()) { Text("词性错题（${latestErrors.size}）") }
            }
        }
        if (showWrong) SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("词性错题", style = MaterialTheme.typography.titleLarge)
                latestErrors.filter { tag == null || tag in it.sourceId.orEmpty().split(" / ") }.forEach { a ->
                    Text("${PosMode.entries.first { it.attemptMode == a.mode }.label} · ${a.errorType?.label.orEmpty()}\n${a.question}\n正确：${a.referenceAnswer} · 你的答案：${a.finalAnswer}")
                }
                Button(onClick = { source = "词性错题" }, modifier = Modifier.fillMaxWidth()) { Text("重练当前模式的词性错题") }
            }
        }
        if (showAnalysis) PosAnalysisPanel(attempts, cache, words, tag) { shortcut -> pair = shortcut; source = "词性错题"; mode = PosMode.DRILL }
        val pending = scoped.filter { it.posNeedsReview }
        if (pending.isNotEmpty()) SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("待复核词条（${pending.size}）")
                pending.take(30).forEach { word -> OutlinedButton(onClick = { editingWord = word }, modifier = Modifier.fillMaxWidth()) { Text("${word.spelling} · ${word.partsOfSpeech.joinToString { it.shortLabel }}") } }
                OutlinedButton(onClick = { viewModel.annotatePos(pending, false, true) }, enabled = !busy) { Text("DeepSeek 复核歧义词") }
            }
        }
    }
    if (selecting) {
        var query by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { selecting = false }, title = { Text("选择单词") }, text = {
            Column { OutlinedTextField(query, { query = it }, label = { Text("搜索单词 / 释义") })
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(scoped.filter { it.spelling.contains(query, true) || it.translation.contains(query, true) }, key = { it.id }) { word ->
                        PosToggle(word.spelling, word.id in selectedIds) { yes -> selectedIds = if (yes) selectedIds + word.id else selectedIds - word.id }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { selecting = false }) { Text("完成") } })
    }
    editingWord?.let { word ->
        var parts by remember(word.id) { mutableStateOf(PosPractice.core(word.partsOfSpeech)) }
        AlertDialog(onDismissRequest = { editingWord = null }, title = { Text(word.spelling) }, text = {
            Column { Text(word.translation); PartOfSpeech.core.forEach { p -> PosToggle("${p.shortLabel} ${p.label}", p in parts) { parts = if (it) parts + p else parts - p } } }
        }, confirmButton = { TextButton(onClick = { viewModel.confirmWordPos(word, parts); editingWord = null }, enabled = parts.isNotEmpty()) { Text("确认词性") } })
    }
}

@Composable
internal fun PosToggle(label: String, value: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Checkbox(value, change); Text(label, Modifier.weight(1f))
    }
}

@Composable
private fun PosAnalysisPanel(attempts: List<PracticeAttempt>, cache: List<PosExercise>, words: List<WordEntry>, tag: String?, shortcut: (String) -> Unit) {
    var days by remember { mutableStateOf(7) }
    val since = if (days == 0) 0L else LocalDate.now().minusDays(days.toLong() - 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    fun wordName(id: String): String = cache.firstOrNull { it.id == id }?.lemma?.ifBlank { null }
        ?: words.firstOrNull { "pos-word:${it.id}" == id }?.spelling
        ?: cache.firstNotNullOfOrNull { q -> q.blanks.firstOrNull { "${q.id}:${it.index}" == id }?.let { b -> q.candidates.first { it.id == b.answerId }.word } }
        ?: attempts.firstOrNull { it.itemId == id }?.question.orEmpty()
    val statistics = PosAnalytics.calculate(attempts.filter { tag == null || tag in it.sourceId.orEmpty().split(" / ") }, since, ::wordName)
    SectionCard { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("词性能力分析", style = MaterialTheme.typography.titleLarge)
        NvvDropdown("时间范围", days, listOf(7 to "最近7天", 30 to "最近30天", 0 to "全部历史"), NvvIcons.Clock, { days = it })
        Text("3秒 / 5秒正确率：在相应时限内作答且未用提示的记录中计算。")
        statistics.rates.forEach { (pos, rate) -> Text("${pos.shortLabel} ${pos.label}：${(rate.accuracy * 100).toInt()}%（${rate.correct}/${rate.count}）\n平均 ${"%.1f".format(rate.averageMs / 1000.0)} 秒 · 3秒 ${(rate.within3 * 100).toInt()}% · 5秒 ${(rate.within5 * 100).toInt()}%") }
        Text("误判矩阵（次数）", style = MaterialTheme.typography.titleMedium)
        PartOfSpeech.core.forEach { actual -> Text(PartOfSpeech.core.filter { it != actual }.joinToString("   ") { selected -> "${actual.shortLabel}→${selected.shortLabel}: ${statistics.confusion[actual to selected] ?: 0}" }) }
        Text("最常误判的20个单词")
        statistics.worstWords.forEach { (name, count) ->
            Text("$name · $count 次")
        }
        listOf("只练 Adj / Adv", "只练 N / V", "全部词性").forEach { label -> OutlinedButton(onClick = { shortcut(label) }, modifier = Modifier.fillMaxWidth()) { Text(if (label == "全部词性") "重练全部词性错题" else label) } }
    } }
}
