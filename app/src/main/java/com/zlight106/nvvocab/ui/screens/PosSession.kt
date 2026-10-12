package com.zlight106.nvvocab.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zlight106.nvvocab.data.*
import com.zlight106.nvvocab.domain.PosPractice
import com.zlight106.nvvocab.ui.MainViewModel
import com.zlight106.nvvocab.ui.components.SectionCard
import com.zlight106.nvvocab.ui.icons.NvvIcons
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.util.UUID

@Composable
internal fun PosSession(request: PracticeSessionRequest.Pos, runtime: PracticeSessionRuntime, viewModel: MainViewModel, speak: (String) -> Unit, onExit: () -> Unit) {
    var saving by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.widthIn(max = 980.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (runtime.finished) {
            val blanks = runtime.attempts.filter { it.mode == PracticeAttemptMode.CET4_WORD_BANK }
            val summary = if (blanks.isNotEmpty()) "填空正确 ${blanks.count { it.correct }} / ${blanks.size}；预分类正确 ${runtime.attempts.count { it.mode == PracticeAttemptMode.WORD_POS_CLASSIFICATION && it.correct }} / 15"
                else "正确 ${runtime.attempts.count { it.correct }} / ${runtime.attempts.size}"
            SessionResult("词性训练已结算", summary, runtime.sessionId, runtime.attempts, viewModel, onExit = onExit) {
                runtime.attempts.forEach { a ->
                    Text("${a.sequenceIndex + 1}. ${if (a.correct) "✓" else "✗"} ${a.errorType?.label.orEmpty()}\n${a.question}\n你的答案：${a.finalAnswer.ifBlank { "未作答" }}\n正确：${a.referenceAnswer}\n${a.explanation.orEmpty()}")
                }
            }
        } else if (request.queue.firstOrNull()?.mode == PosMode.CET4) {
            Cet4Question(request.queue.first(), runtime, viewModel, speak, request.focusBlanks)
        } else {
            val index = runtime.currentIndex.coerceIn(0, request.queue.lastIndex)
            val q = request.queue[index]
            val attempt = runtime.attempts.firstOrNull { it.sequenceIndex == index }
            LaunchedEffect(runtime.sessionId, index) { viewModel.beginQuestionTiming(runtime.sessionId, index) }
            Text("${index + 1} / ${request.queue.size} · ${q.mode.label}", color = MaterialTheme.colorScheme.primary)
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
                }, !saving)
            if (q.mode == PosMode.DRILL && attempt != null && q.sourceWordId != null) {
                val words by viewModel.words.collectAsState()
                val word = words.firstOrNull { it.id == q.sourceWordId }
                if (word != null && word.posExplanation.isBlank()) OutlinedButton(onClick = { viewModel.annotatePos(listOf(word), false, true) }) { Text("生成解释（DeepSeek）") }
                if (word != null && word.posExplanation.isNotBlank()) Text(word.posExplanation)
            }
        }
    }
    }
}

@Composable
internal fun PosQuestion(q: PosExercise, attempt: PracticeAttempt?, complete: Boolean, seconds: Int,
    time: () -> Long, speak: (String) -> Unit, answer: (Set<PartOfSpeech>) -> Unit, next: () -> Unit, enabled: Boolean = true) {
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
        if (complete) Text("请选出所有正确词性")
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
        if (attempt == null && complete) Button(onClick = { answer(selected) }, enabled = selected.isNotEmpty() && enabled, modifier = Modifier.fillMaxWidth()) { Text("提交全部词性") }
        if (attempt != null) {
            Text(if (attempt.correct) "✓ 正确" else "✗ ${attempt.errorType?.label ?: "错误"}", style = MaterialTheme.typography.titleLarge,
                color = if (attempt.correct) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error)
            Text("你的选择：${attempt.selectedPos.joinToString { it.shortLabel }.ifBlank { "未作答" }}\n正确词性：${q.correctParts.joinToString { it.shortLabel }}")
            Text(q.grammarClue); Text(q.explanation)
            if (q.mode == PosMode.SLOT) IconButton(onClick = { speak(q.sentence.replace("______", q.targetWord)) }) { Icon(NvvIcons.Volume2, "播放完整例句") }
            Button(onClick = next, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("下一题 / 结算") }
        }
    } }
}

@Composable
private fun Cet4Question(q: PosExercise, runtime: PracticeSessionRuntime, viewModel: MainViewModel, speak: (String) -> Unit, focusBlanks: Set<Int>) {
    var draft by remember(runtime.sessionId) { mutableStateOf(runCatching { JSONObject(viewModel.readPosDraft(runtime.sessionId)) }.getOrElse { JSONObject() }.toString()) }
    val state = JSONObject(draft)
    val classified = q.candidates.associate { it.id to state.optString("pos_${it.id}").takeIf(String::isNotBlank)?.let(PartOfSpeech::decode).orEmpty() }
    val answers = (1..10).mapNotNull { i -> state.optString("answer_$i").takeIf(String::isNotBlank)?.let { i to it } }.toMap()
    val stage2 = state.optBoolean("stage2")
    val activeBlanks = q.blanks.filter { focusBlanks.isEmpty() || it.index in focusBlanks }
    val indexes = activeBlanks.map { it.index - 1 }
    val index = runtime.currentIndex.takeIf { it in indexes } ?: indexes.first()
    val classificationIndex = runtime.currentIndex.takeIf { it in 10..24 } ?: 10
    val currentBlank = index + 1
    var saving by remember { mutableStateOf(false) }
    fun update(key: String, value: Any) {
        val updated = JSONObject(draft).put(key, value).toString()
        draft = updated; viewModel.savePosDraft(runtime.sessionId, updated)
    }
    LaunchedEffect(stage2, index, classificationIndex) {
        val timingIndex = if (stage2) index else classificationIndex
        if (runtime.currentIndex != timingIndex) viewModel.setPracticeSessionIndex(timingIndex)
        viewModel.beginQuestionTiming(runtime.sessionId, timingIndex)
    }
    if (!stage2) {
        Text("第一阶段：给15个候选词预分类（可多选）", style = MaterialTheme.typography.titleLarge)
        q.candidates.forEach { c ->
            SectionCard { Column {
                Row { Text("${c.id}. ${c.word}", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { speak(c.word) }) { Icon(NvvIcons.Volume2, "发音") } }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { PartOfSpeech.core.forEach { p ->
                    FilterChip(p in classified.getValue(c.id), {
                        val nextIndex = 10 + q.candidates.indexOf(c)
                        val updated = JSONObject(draft)
                        val timeKey = "classificationTime_$classificationIndex"
                        updated.put(timeKey, maxOf(updated.optLong(timeKey), viewModel.elapsedQuestionTime(runtime.sessionId, classificationIndex)))
                        val current = classified.getValue(c.id)
                        updated.put("pos_${c.id}", PartOfSpeech.encode(if (p in current) current - p else current + p))
                        draft = updated.toString(); viewModel.savePosDraft(runtime.sessionId, draft)
                        viewModel.setPracticeSessionIndex(nextIndex)
                        viewModel.beginQuestionTiming(runtime.sessionId, nextIndex)
                    }, label = { Text(p.shortLabel) })
                } }
            } }
        }
        Button(onClick = {
            q.candidates.forEachIndexed { candidateIndex, c ->
                val sequence = 10 + candidateIndex
                val exercise = PosExercise("pos-word:${c.wordId}", PosMode.DRILL, c.wordId, q.sourceTag, c.word, c.word, c.word,
                    PosPractice.core(c.parts), explanation = "CET-4 候选词预分类：${c.parts.joinToString { it.shortLabel }}")
                val previous = runtime.attempts.firstOrNull { it.sequenceIndex == sequence }
                viewModel.stagePracticeAttempt(PosPractice.attempt(exercise, runtime.sessionId, sequence, classified.getValue(c.id), true,
                    maxOf(viewModel.elapsedQuestionTime(runtime.sessionId, sequence), state.optLong("classificationTime_$sequence"), previous?.activeTimeMs ?: 0), previous))
            }
            viewModel.setPracticeSessionIndex(indexes.first())
            update("stage2", true)
        }, enabled = classified.values.all { it.isNotEmpty() }, modifier = Modifier.fillMaxWidth()) { Text("进入正式选词填空") }
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
                val blank = q.blanks.first { it.index == currentBlank }
                viewModel.stagePracticeAttempt(PosPractice.cetAttempt(q, blank, runtime.sessionId, null, emptySet(),
                    viewModel.elapsedQuestionTime(runtime.sessionId, index), runtime.attempts.firstOrNull { it.sequenceIndex == index }))
            }) { Text("清除") }
        }
        q.candidates.forEach { c ->
            val used = answers.any { (slot, id) -> slot != currentBlank && id == c.id } || q.blanks.any { it.index - 1 !in indexes && it.answerId == c.id }
            Row(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = {
                    PosPractice.assign(answers, currentBlank, c.id)
                    val current = JSONObject(draft)
                    if (!current.has("first_$currentBlank")) current.put("first_$currentBlank", c.id)
                    current.put("answer_$currentBlank", c.id)
                    current.put("time_$currentBlank", viewModel.elapsedQuestionTime(runtime.sessionId, index))
                    draft = current.toString(); viewModel.savePosDraft(runtime.sessionId, draft)
                    val blank = q.blanks.first { it.index == currentBlank }
                    viewModel.stagePracticeAttempt(PosPractice.cetAttempt(q, blank, runtime.sessionId, c.id, classified.getValue(c.id),
                        viewModel.elapsedQuestionTime(runtime.sessionId, index), runtime.attempts.firstOrNull { it.sequenceIndex == index }))
                }, enabled = !used && !saving, modifier = Modifier.weight(1f)) {
                    Text("${c.id}. ${c.word} · ${classified.getValue(c.id).joinToString { it.shortLabel }}${if (used) "（已使用）" else if (answers[currentBlank] == c.id) " ✓" else ""}")
                }
                IconButton(onClick = { speak(c.word) }) { Icon(NvvIcons.Volume2, "发音") }
            }
        }
        TextButton(onClick = { viewModel.setPracticeSessionIndex(10); update("stage2", false) }) { Text("修改预分类") }
        Button(onClick = {
            if (!saving) {
                saving = true
                val completed = runtime.attempts.filter { it.sequenceIndex >= 10 } + activeBlanks.map { blank ->
                    val chosen = answers[blank.index]
                    PosPractice.cetAttempt(q, blank, runtime.sessionId, chosen, classified[chosen].orEmpty(),
                        maxOf(viewModel.elapsedQuestionTime(runtime.sessionId, blank.index - 1), state.optLong("time_${blank.index}")),
                        runtime.attempts.firstOrNull { it.sequenceIndex == blank.index - 1 })
                }
                completed.forEach(viewModel::stagePracticeAttempt)
                viewModel.recordPosSession(listOf(q), completed, { saving = false; viewModel.clearQuestionTimers(runtime.sessionId); viewModel.markPracticeSessionFinished() }, { saving = false })
            }
        }, enabled = !saving && activeBlanks.all { it.index in answers } && answers.values.distinct().size == answers.size, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(if (saving) "正在结算" else "提交并结算") }
    }
}
