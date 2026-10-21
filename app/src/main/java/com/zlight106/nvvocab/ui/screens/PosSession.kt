package com.zlight106.nvvocab.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zlight106.nvvocab.data.*
import com.zlight106.nvvocab.domain.Cet4Training
import com.zlight106.nvvocab.domain.PosHeuristics
import com.zlight106.nvvocab.domain.PosPractice
import com.zlight106.nvvocab.domain.PosSlotRuleEngine
import com.zlight106.nvvocab.ui.MainViewModel
import com.zlight106.nvvocab.ui.components.SectionCard
import com.zlight106.nvvocab.ui.icons.NvvIcons
import kotlinx.coroutines.delay
import org.json.JSONObject

@Composable
internal fun PosSession(request: PracticeSessionRequest.Pos, runtime: PracticeSessionRuntime, viewModel: MainViewModel, speak: (String) -> Unit, onExit: () -> Unit) {
    var saving by remember(runtime.sessionId) { mutableStateOf(false) }
    val words by viewModel.words.collectAsState()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 980.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (runtime.finished) {
                val blanks = runtime.attempts.filter { it.mode == PracticeAttemptMode.CET4_WORD_BANK }
                val preclassified = runtime.attempts.filter { it.mode == PracticeAttemptMode.WORD_POS_CLASSIFICATION }
                val slotAttempts = runtime.attempts.filter { it.itemId.startsWith("cet4-slot-pos:") }
                val summary = if (blanks.isNotEmpty()) buildString {
                    append("填空正确 ${blanks.count { it.correct }} / ${blanks.size}；候选词预分类正确 ${preclassified.count { it.correct }} / ${preclassified.size}")
                    if (slotAttempts.isNotEmpty()) append("\n空位词性正确 ${slotAttempts.count { it.correct }} / ${slotAttempts.size}")
                } else "正确 ${runtime.attempts.count { it.correct }} / ${runtime.attempts.size}"
                SessionResult(if (request.foundation && request.followUpCet4 != null) "保底训练前两阶段已结算" else "词性训练已结算", summary, runtime.sessionId, runtime.attempts, viewModel, onExit = onExit) {
                    request.followUpCet4?.let { followUp ->
                        Text("可选第三阶段：完成一套 CET-4 选词填空，先判空位，再选单词。")
                        Button(onClick = {
                            viewModel.startPracticeSession(PracticeSessionRequest.Pos(listOf(followUp), slotFirst = request.slotFirst, foundation = true))
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("继续第三阶段：CET-4 模拟") }
                    }
                    runtime.attempts.forEach { a ->
                        Text("${a.sequenceIndex + 1}. ${if (a.correct) "✓" else "✗"} ${a.errorType?.label.orEmpty()}\n${a.question}\n你的答案：${a.finalAnswer.ifBlank { "未作答" }}\n正确：${a.referenceAnswer}\n${a.explanation.orEmpty()}")
                    }
                }
            } else if (request.queue.firstOrNull()?.mode == PosMode.CET4) {
                if (request.foundation) Text("第三阶段（可选）：CET-4 选词填空", color = MaterialTheme.colorScheme.primary)
                Cet4Question(request.queue.first(), runtime, viewModel, speak, request.focusBlanks, request.slotFirst)
            } else if (request.queue.isNotEmpty()) {
                val index = runtime.currentIndex.coerceIn(0, request.queue.lastIndex)
                val q = request.queue[index]
                val attempt = runtime.attempts.firstOrNull { it.sequenceIndex == index }
                LaunchedEffect(runtime.sessionId, index) { viewModel.beginQuestionTiming(runtime.sessionId, index) }
                if (request.foundation) {
                    val stageCount = request.queue.count { it.mode == q.mode }
                    val stageIndex = request.queue.take(index + 1).count { it.mode == q.mode }
                    Text("${if (q.mode == PosMode.DRILL) "第一阶段：词性速判" else "第二阶段：空位词性"} $stageIndex / $stageCount", color = MaterialTheme.colorScheme.primary)
                } else Text("${index + 1} / ${request.queue.size} · ${q.mode.label}", color = MaterialTheme.colorScheme.primary)
                PosQuestion(q, attempt, request.completeMode && q.mode == PosMode.DRILL, request.timeLimitSeconds,
                    { viewModel.elapsedQuestionTime(runtime.sessionId, index) }, speak, { selected ->
                        if (viewModel.practiceSessionRuntime.value?.attempts?.none { it.sequenceIndex == index } == true) {
                            viewModel.stagePracticeAttempt(PosPractice.attempt(q, runtime.sessionId, index, selected, request.completeMode && q.mode == PosMode.DRILL,
                                viewModel.snapshotQuestionTime(runtime.sessionId, index), attempt))
                        }
                    }, {
                        if (index + 1 < request.queue.size) viewModel.setPracticeSessionIndex(index + 1)
                        else if (!saving) {
                            saving = true
                            viewModel.recordPosSession(request.queue, runtime.attempts, {
                                saving = false; viewModel.clearQuestionTimers(runtime.sessionId); viewModel.markPracticeSessionFinished()
                            }, { saving = false })
                        }
                    }, !saving, words.firstOrNull { it.id == q.sourceWordId })
                if (q.mode == PosMode.DRILL && attempt != null) {
                    val word = words.firstOrNull { it.id == q.sourceWordId }
                    if (word != null && word.posExplanation.isBlank()) OutlinedButton(onClick = { viewModel.annotatePos(listOf(word), false, true) }) { Text("生成解释（DeepSeek）") }
                    if (word != null && word.posExplanation.isNotBlank()) Text(word.posExplanation)
                }
            }
        }
    }
}

private fun Set<PartOfSpeech>.displayPos(): String = sortedBy { it.ordinal }.joinToString(" / ") {
    if (it == PartOfSpeech.UNKNOWN) "? 不确定" else it.shortLabel
}.ifBlank { "未作答" }

@Composable
internal fun PosQuestion(q: PosExercise, attempt: PracticeAttempt?, complete: Boolean, seconds: Int,
    time: () -> Long, speak: (String) -> Unit, answer: (Set<PartOfSpeech>) -> Unit, next: () -> Unit,
    enabled: Boolean = true, liveWord: WordEntry? = null) {
    var selected by remember(q.id) { mutableStateOf(emptySet<PartOfSpeech>()) }
    var remaining by remember(q.id) { mutableIntStateOf(seconds) }
    LaunchedEffect(q.id, attempt?.id, seconds) {
        if (seconds > 0 && attempt == null) {
            while (true) {
                remaining = (seconds - time() / 1000).toInt().coerceAtLeast(0)
                if (time() >= seconds * 1000L) { answer(if (complete) selected else emptySet()); break }
                delay(100)
            }
        }
    }
    SectionCard { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (seconds > 0 && attempt == null) Text("剩余 $remaining 秒", color = MaterialTheme.colorScheme.primary)
        val sentence = buildAnnotatedString {
            append(q.sentence)
            if (q.mode == PosMode.CONTEXT && q.targetWord.isNotBlank()) {
                Regex("(?i)(?<![a-z])" + Regex.escape(q.targetWord) + "(?![a-z])").findAll(q.sentence).forEach { match ->
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary), match.range.first, match.range.last + 1)
                }
            }
        }
        Text(sentence, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.headlineSmall)
        if (q.mode != PosMode.SLOT) IconButton(onClick = { speak(if (q.mode == PosMode.CONTEXT) q.sentence else q.targetWord) }) { Icon(NvvIcons.Volume2, "播放发音") }
        if (q.mode == PosMode.DRILL) Text(if (complete) "完整模式：必须选出全部常见词性" else "保底模式：选中任一真实常见词性即可，答后显示全部词性")
        else if (q.mode == PosMode.SLOT) Text("先判断这个空需要哪一种词性。")
        PartOfSpeech.core.forEach { p ->
            val colors = MaterialTheme.colorScheme
            val container = when {
                attempt != null && p in q.correctParts -> colors.tertiaryContainer
                attempt != null && p in attempt.selectedPos -> colors.errorContainer
                p in selected -> colors.primaryContainer
                else -> colors.surfaceContainer
            }
            OutlinedButton(onClick = {
                if (complete) selected = if (p in selected) selected - p else selected + p
                else answer(setOf(p))
            }, enabled = enabled && attempt == null, modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = container, disabledContainerColor = container,
                    disabledContentColor = if (attempt != null && p in attempt.selectedPos && p !in q.correctParts) colors.onErrorContainer else colors.onSurface)) {
                Text("${p.shortLabel} ${p.label}${if (attempt != null && p in q.correctParts) " ✓" else if (p in selected) " ☑" else ""}", style = MaterialTheme.typography.titleLarge)
            }
        }
        OutlinedButton(onClick = { answer(setOf(PartOfSpeech.UNKNOWN)) }, enabled = enabled && attempt == null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("? 不确定", style = MaterialTheme.typography.titleMedium) }
        if (attempt == null && complete) Button(onClick = { answer(selected) }, enabled = selected.isNotEmpty() && enabled, modifier = Modifier.fillMaxWidth()) { Text("提交全部词性") }
        if (attempt != null) {
            Text(if (attempt.correct) "✓ 正确" else "✗ ${attempt.errorType?.label ?: "错误"}", style = MaterialTheme.typography.titleLarge,
                color = if (attempt.correct) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error)
            val actualParts = if (q.mode == PosMode.DRILL) liveWord?.partsOfSpeech?.let(PosPractice::core)?.takeIf { it.isNotEmpty() } ?: q.correctParts else q.correctParts
            Text("你的选择：${attempt.selectedPos.displayPos()}\n正确词性：${actualParts.displayPos()}")
            if (q.mode == PosMode.DRILL) {
                liveWord?.translation?.takeIf { it.isNotBlank() }?.let { Text("中文释义：$it") }
                PosHeuristics.hints(liveWord?.spelling ?: q.lemma.ifBlank { q.targetWord }).forEach { Text(it) }
                if (PosPractice.core(actualParts).size > 1) Text("注意：该词常见 ${actualParts.displayPos()} 多词性")
                Text("词尾只用于初筛，不等于最终答案", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (q.mode == PosMode.SLOT) {
                PosSlotRuleEngine.hint(q.sentence)?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                if (q.grammarClue.isNotBlank()) Text("补充依据：${q.grammarClue}")
            } else if (q.grammarClue.isNotBlank()) Text(q.grammarClue)
            if (q.explanation.isNotBlank()) Text(q.explanation)
            if (q.mode == PosMode.SLOT && q.targetWord.isNotBlank()) IconButton(onClick = { speak(q.sentence.replace("______", q.targetWord)) }) { Icon(NvvIcons.Volume2, "播放完整例句") }
            Button(onClick = next, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("下一题 / 结算") }
        }
    } }
}

@Composable
private fun Cet4Question(q: PosExercise, runtime: PracticeSessionRuntime, viewModel: MainViewModel, speak: (String) -> Unit,
    focusBlanks: Set<Int>, slotFirst: Boolean) {
    var draft by remember(runtime.sessionId) { mutableStateOf(runCatching { JSONObject(viewModel.readPosDraft(runtime.sessionId)) }.getOrElse { JSONObject() }.toString()) }
    var saving by remember(runtime.sessionId) { mutableStateOf(false) }
    var candidateFilter by remember(runtime.sessionId) { mutableStateOf<PartOfSpeech?>(null) }
    val state = JSONObject(draft)
    val classified = q.candidates.associate { it.id to state.optString("pos_${it.id}").takeIf(String::isNotBlank)?.let(PartOfSpeech::decode).orEmpty() }
    val answers = (1..10).mapNotNull { i -> state.optString("answer_$i").takeIf(String::isNotBlank)?.let { i to it } }.toMap()
    val stage2 = state.optBoolean("stage2")
    val activeBlanks = q.blanks.filter { focusBlanks.isEmpty() || it.index in focusBlanks }
    if (activeBlanks.isEmpty()) return
    val indexes = activeBlanks.map { it.index - 1 }
    val runtimeBlankIndex = if (runtime.currentIndex in 25..34) runtime.currentIndex - 25 else runtime.currentIndex
    val index = runtimeBlankIndex.takeIf { it in indexes } ?: indexes.first()
    val classificationIndex = runtime.currentIndex.takeIf { it in 10..24 } ?: 10
    val currentBlank = index + 1
    val blank = q.blanks.first { it.index == currentBlank }
    val slotSelected = state.optString("slot_$currentBlank").takeIf(String::isNotBlank)?.let(PartOfSpeech::decode)
    val choosingSlot = slotFirst && (slotSelected == null || state.optBoolean("slotOpen_$currentBlank"))
    val timingIndex = if (!stage2) classificationIndex else if (choosingSlot) 25 + index else index
    fun save(updated: JSONObject) {
        draft = updated.toString()
        viewModel.savePosDraft(runtime.sessionId, draft)
    }
    fun update(key: String, value: Any) = save(JSONObject(draft).put(key, value))
    fun latestClassification(candidateId: String): Set<PartOfSpeech> = JSONObject(draft).optString("pos_$candidateId")
        .takeIf(String::isNotBlank)?.let(PartOfSpeech::decode).orEmpty()
    fun previous(sequence: Int) = viewModel.practiceSessionRuntime.value?.takeIf { it.sessionId == runtime.sessionId }?.attempts?.firstOrNull { it.sequenceIndex == sequence }
    fun stageCandidate(candidate: PosCandidate, parts: Set<PartOfSpeech>, updated: JSONObject) {
        val sequence = 10 + q.candidates.indexOf(candidate)
        val exercise = PosExercise("pos-word:${candidate.wordId ?: candidate.id}", PosMode.DRILL, candidate.wordId, q.sourceTag, candidate.word, candidate.word, candidate.word,
            PosPractice.core(candidate.parts), explanation = "CET-4 候选词预分类：${candidate.parts.displayPos()}")
        val old = previous(sequence)
        viewModel.stagePracticeAttempt(PosPractice.attempt(exercise, runtime.sessionId, sequence, parts, true,
            maxOf(viewModel.elapsedQuestionTime(runtime.sessionId, sequence), updated.optLong("classificationTime_$sequence"), old?.activeTimeMs ?: 0), old))
    }
    fun classify(candidate: PosCandidate, parts: Set<PartOfSpeech>) {
        val sequence = 10 + q.candidates.indexOf(candidate)
        val updated = JSONObject(draft)
        val timeKey = "classificationTime_$classificationIndex"
        updated.put(timeKey, maxOf(updated.optLong(timeKey), viewModel.elapsedQuestionTime(runtime.sessionId, classificationIndex)))
        updated.put("pos_${candidate.id}", PartOfSpeech.encode(parts))
        save(updated)
        stageCandidate(candidate, parts, updated)
        q.blanks.filter { updated.optString("answer_${it.index}") == candidate.id }.forEach { b ->
            val old = previous(b.index - 1)
            val slotAnswer = updated.optString("slot_${b.index}").takeIf(String::isNotBlank)?.let(PartOfSpeech::decode)
            viewModel.stagePracticeAttempt(PosPractice.cetAttempt(q, b, runtime.sessionId, candidate.id, parts,
                maxOf(old?.activeTimeMs ?: 0, updated.optLong("time_${b.index}")), old, slotAnswer))
        }
        viewModel.setPracticeSessionIndex(sequence)
        viewModel.beginQuestionTiming(runtime.sessionId, sequence)
    }
    LaunchedEffect(runtime.sessionId, timingIndex, stage2) {
        if (runtime.currentIndex != timingIndex) viewModel.setPracticeSessionIndex(timingIndex)
        viewModel.beginQuestionTiming(runtime.sessionId, timingIndex)
    }
    if (!stage2) {
        val progress = Cet4Training.classificationProgress(q.candidates.map { it.id }, classified)
        Text("第一阶段：候选词预分类", style = MaterialTheme.typography.titleLarge)
        Text("已分类：${progress.classified}/${progress.total} · 不确定：${progress.uncertain} · 未处理：${progress.unprocessed}")
        Text("认识的词可多选，不认识可留空或选 ?；可以直接进入填空。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        q.candidates.forEach { c ->
            SectionCard { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${c.id}. ${c.word}", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { speak(c.word) }) { Icon(NvvIcons.Volume2, "发音") }
                }
                PartOfSpeech.core.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { pair.forEach { p ->
                        val current = classified.getValue(c.id)
                        FilterChip(p in current, {
                            val known = latestClassification(c.id) - PartOfSpeech.UNKNOWN
                            classify(c, if (p in known) known - p else known + p)
                        }, label = { Text("${p.shortLabel} ${p.label}") }, modifier = Modifier.weight(1f).heightIn(min = 48.dp))
                    } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(PartOfSpeech.UNKNOWN in classified.getValue(c.id), {
                        classify(c, if (PartOfSpeech.UNKNOWN in latestClassification(c.id)) emptySet() else setOf(PartOfSpeech.UNKNOWN))
                    }, label = { Text("? 不确定") }, modifier = Modifier.weight(1f))
                    TextButton(onClick = { classify(c, emptySet()) }, modifier = Modifier.weight(1f)) { Text("留空") }
                }
            } }
        }
        Button(onClick = {
            val updated = JSONObject(draft)
            val timeKey = "classificationTime_$classificationIndex"
            updated.put(timeKey, maxOf(updated.optLong(timeKey), viewModel.elapsedQuestionTime(runtime.sessionId, classificationIndex)))
            q.candidates.forEach { c -> stageCandidate(c, classified.getValue(c.id), updated) }
            save(updated.put("stage2", true))
            viewModel.setPracticeSessionIndex(indexes.first())
        }, enabled = progress.canContinue && !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("直接进入选词填空") }
    } else {
        Text("第二阶段：${activeBlanks.size}个空，每个候选词最多使用一次", style = MaterialTheme.typography.titleLarge)
        val annotated = buildAnnotatedString {
            var offset = 0
            Regex("\\{\\{(\\d+)}}").findAll(q.sentence).forEach { match ->
                append(q.sentence.substring(offset, match.range.first))
                val slot = match.groupValues[1].toInt()
                val answerId = if (slot - 1 in indexes) answers[slot] else q.blanks.first { it.index == slot }.answerId
                val word = q.candidates.firstOrNull { it.id == answerId }?.word ?: "______"
                pushStringAnnotation("blank", slot.toString())
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) { append("[$slot $word]") }
                pop(); offset = match.range.last + 1
            }
            append(q.sentence.substring(offset))
        }
        @Suppress("DEPRECATION")
        ClickableText(annotated, style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface), onClick = { offset ->
            annotated.getStringAnnotations("blank", offset, offset).firstOrNull()?.let { if (it.item.toInt() - 1 in indexes) viewModel.setPracticeSessionIndex(it.item.toInt() - 1) }
        })
        Text("当前第 $currentBlank 空 · 已填 ${answers.count { it.key - 1 in indexes }}/${activeBlanks.size}", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { viewModel.setPracticeSessionIndex(indexes[(indexes.indexOf(index) + indexes.size - 1) % indexes.size]) }) { Text("上一空") }
            OutlinedButton(onClick = { viewModel.setPracticeSessionIndex(indexes[(indexes.indexOf(index) + 1) % indexes.size]) }) { Text("下一空") }
            TextButton(onClick = {
                update("answer_$currentBlank", "")
                previous(index)?.let { old ->
                    viewModel.stagePracticeAttempt(PosPractice.cetAttempt(q, blank, runtime.sessionId, null, emptySet(),
                        maxOf(viewModel.elapsedQuestionTime(runtime.sessionId, index), old.activeTimeMs), old, slotSelected))
                }
            }) { Text("清除") }
        }
        if (choosingSlot) {
            SectionCard { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Step 1：第 $currentBlank 空需要什么词性？", style = MaterialTheme.typography.titleLarge)
                Text("先判断 N / V / A / Adv，再挑单词。正确答案统一在结算后显示。")
                (PartOfSpeech.core + PartOfSpeech.UNKNOWN).forEach { p ->
                    OutlinedButton(onClick = {
                        val selected = setOf(p)
                        val slotSequence = 25 + index
                        val slotExercise = Cet4Training.slotExercise(q, blank).let { exercise ->
                            exercise.copy(explanation = listOfNotNull(PosSlotRuleEngine.hint(q.sentence, currentBlank),
                                exercise.grammarClue.takeIf { it.isNotBlank() }?.let { "补充依据：$it" }, exercise.explanation)
                                .joinToString("\n"))
                        }
                        viewModel.stagePracticeAttempt(PosPractice.attempt(slotExercise, runtime.sessionId, slotSequence,
                            selected, false, viewModel.snapshotQuestionTime(runtime.sessionId, slotSequence), previous(slotSequence)))
                        save(JSONObject(draft).put("slot_$currentBlank", PartOfSpeech.encode(selected)).put("slotOpen_$currentBlank", false))
                        answers[currentBlank]?.let { chosen ->
                            viewModel.stagePracticeAttempt(PosPractice.cetAttempt(q, blank, runtime.sessionId, chosen, classified[chosen].orEmpty(),
                                viewModel.elapsedQuestionTime(runtime.sessionId, index), previous(index), selected))
                        }
                    }, enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text(if (p == PartOfSpeech.UNKNOWN) "? 不确定" else "${p.shortLabel} ${p.label}", style = MaterialTheme.typography.titleLarge)
                    }
                }
            } }
        } else {
            if (slotFirst) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("空位判断：${slotSelected.orEmpty().displayPos()}", Modifier.weight(1f))
                    TextButton(onClick = { update("slotOpen_$currentBlank", true) }) { Text("修改空位判断") }
                }
            }
            Text(if (slotFirst) "Step 2：选择候选词" else "选择候选词", style = MaterialTheme.typography.titleLarge)
            Text("按你自己的预分类筛选，不确定与未分类词都在 ? 中。", style = MaterialTheme.typography.bodySmall)
            val filters = listOf<PartOfSpeech?>(null) + PartOfSpeech.core + PartOfSpeech.UNKNOWN
            filters.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { p ->
                    FilterChip(candidateFilter == p, { candidateFilter = p }, label = { Text(p?.shortLabel ?: "全部") }, modifier = Modifier.weight(1f))
                } }
            }
            val visibleIds = Cet4Training.filterCandidates(q.candidates.map { it.id }, classified, candidateFilter).toSet()
            if (visibleIds.isEmpty()) Text("当前筛选没有候选词，可以切换“全部”或“?”。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            q.candidates.filter { it.id in visibleIds }.forEach { c ->
                val used = answers.any { (slot, id) -> slot != currentBlank && id == c.id } || q.blanks.any { it.index - 1 !in indexes && it.answerId == c.id }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        PosPractice.assign(answers, currentBlank, c.id)
                        val current = JSONObject(draft)
                        if (!current.has("first_$currentBlank")) current.put("first_$currentBlank", c.id)
                        current.put("answer_$currentBlank", c.id)
                        current.put("time_$currentBlank", viewModel.elapsedQuestionTime(runtime.sessionId, index))
                        save(current)
                        viewModel.stagePracticeAttempt(PosPractice.cetAttempt(q, blank, runtime.sessionId, c.id, classified.getValue(c.id),
                            viewModel.elapsedQuestionTime(runtime.sessionId, index), previous(index), slotSelected))
                    }, enabled = !used && !saving, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                        val userPos = classified.getValue(c.id).takeIf { it.isNotEmpty() }?.displayPos() ?: "? 未分类"
                        Text("${c.id}. ${c.word} · $userPos${if (used) "（已使用）" else if (answers[currentBlank] == c.id) " ✓" else ""}")
                    }
                    IconButton(onClick = { speak(c.word) }) { Icon(NvvIcons.Volume2, "发音") }
                }
            }
        }
        TextButton(onClick = { viewModel.setPracticeSessionIndex(10); update("stage2", false) }) { Text("修改候选词预分类") }
        Button(onClick = {
            if (!saving) {
                saving = true
                val latest = viewModel.practiceSessionRuntime.value?.takeIf { it.sessionId == runtime.sessionId }?.attempts ?: runtime.attempts
                val completed = latest.filter { it.sequenceIndex >= 10 } + activeBlanks.map { b ->
                    val chosen = answers[b.index]
                    val slotAnswer = state.optString("slot_${b.index}").takeIf(String::isNotBlank)?.let(PartOfSpeech::decode)
                    PosPractice.cetAttempt(q, b, runtime.sessionId, chosen, classified[chosen].orEmpty(),
                        maxOf(viewModel.elapsedQuestionTime(runtime.sessionId, b.index - 1), state.optLong("time_${b.index}")),
                        latest.firstOrNull { it.sequenceIndex == b.index - 1 }, slotAnswer)
                }
                completed.forEach(viewModel::stagePracticeAttempt)
                viewModel.recordPosSession(listOf(q), completed, { saving = false; viewModel.clearQuestionTimers(runtime.sessionId); viewModel.markPracticeSessionFinished() }, { saving = false })
            }
        }, enabled = !saving && !choosingSlot && activeBlanks.all { it.index in answers } && answers.values.distinct().size == answers.size,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(if (saving) "正在结算" else "提交并统一结算") }
    }
}
