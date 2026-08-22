package com.zlight106.nvvocab.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zlight106.nvvocab.data.DailyMemoAction
import com.zlight106.nvvocab.data.DailyMemoItem
import com.zlight106.nvvocab.data.DailyMemoSettings
import com.zlight106.nvvocab.data.DailyMemoTarget
import com.zlight106.nvvocab.data.DailyPracticeProgress
import com.zlight106.nvvocab.data.DailyProgressReference
import com.zlight106.nvvocab.data.DailyProgressSettings
import com.zlight106.nvvocab.data.QuizBank
import com.zlight106.nvvocab.data.PracticeAttempt
import com.zlight106.nvvocab.data.PracticeAttemptMode
import com.zlight106.nvvocab.data.StudyTimeProgress
import com.zlight106.nvvocab.data.WordEntry
import com.zlight106.nvvocab.ui.AppUiState
import com.zlight106.nvvocab.ui.components.DailyMemoEditor
import com.zlight106.nvvocab.ui.components.SectionCard
import com.zlight106.nvvocab.ui.icons.NvvIcons
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.text.KeyboardOptions

private enum class HeatmapRange { MONTH, WEEK }

@Composable
fun DashboardScreen(
    state: AppUiState,
    words: List<WordEntry>,
    quizBanks: List<QuizBank>,
    studyTimeProgress: StudyTimeProgress,
    practiceAttempts: List<PracticeAttempt>,
    onSaveProgressSettings: (DailyProgressReference, Int) -> Unit,
    onSaveStudyTimeGoal: (Int) -> Unit,
    onSaveDailyMemoSettings: (DailyMemoSettings) -> Unit,
) {
    var range by remember { mutableStateOf(HeatmapRange.WEEK) }
    var showProgressDialog by remember { mutableStateOf(false) }
    var showMemoDialog by remember { mutableStateOf(false) }
    var showStudyTimeDialog by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val attemptDailyMillis = remember(practiceAttempts, zone) {
        practiceAttempts.groupBy { attempt ->
            Instant.ofEpochMilli(attempt.timestamp).atZone(zone).toLocalDate()
        }.mapValues { (_, attempts) -> attempts.sumOf(PracticeAttempt::activeTimeMs) }
    }
    val attemptStudyTimeProgress = remember(attemptDailyMillis, studyTimeProgress.goalMinutes, today) {
        StudyTimeProgress(
            elapsedMillis = attemptDailyMillis[today] ?: 0L,
            goalMinutes = studyTimeProgress.goalMinutes,
            dailyMillis = attemptDailyMillis,
        )
    }
    val todayAttempts = remember(practiceAttempts, today, zone) {
        practiceAttempts.filter { attempt ->
            Instant.ofEpochMilli(attempt.timestamp).atZone(zone).toLocalDate() == today
        }
    }
    val attemptDailyProgress = remember(todayAttempts) {
        DailyPracticeProgress(
            dictationCompleted = todayAttempts.count {
                it.mode == PracticeAttemptMode.WORD_DICTATION || it.mode == PracticeAttemptMode.WORD_SPELLING
            },
            contrastCompleted = todayAttempts.count {
                it.mode == PracticeAttemptMode.CHINESE_TO_ENGLISH ||
                    it.mode == PracticeAttemptMode.ENGLISH_TO_CHINESE ||
                    it.mode == PracticeAttemptMode.ENGLISH_DEFINITION_TO_ENGLISH
            },
            customQuizCompleted = todayAttempts.count {
                it.mode == PracticeAttemptMode.QUIZ_CHOICE || it.mode == PracticeAttemptMode.QUIZ_FILL_BLANK
            },
            customQuizCompletedByBank = todayAttempts.asSequence()
                .filter { attempt ->
                    attempt.mode == PracticeAttemptMode.QUIZ_CHOICE ||
                        attempt.mode == PracticeAttemptMode.QUIZ_FILL_BLANK
                }
                .mapNotNull { attempt -> attempt.sourceId?.takeIf(String::isNotBlank) }
                .groupingBy { bankId -> bankId }
                .eachCount(),
        )
    }
    val progressSettings = state.dailyProgressSettings
    val progressReference = progressSettings.reference
    val todayCompleted = attemptDailyProgress.completedFor(progressReference)
    val target = progressSettings.targetFor().coerceAtLeast(1)
    val progress = if (target > 0) {
        (todayCompleted.toFloat() / target).coerceIn(0f, 1f)
    } else {
        0f
    }
    val progressUnit = if (progressReference == DailyProgressReference.DICTATION) "词" else "题"

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(remember { ScrollState(0) }).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("仪表板", style = MaterialTheme.typography.headlineMedium)
        Text("查看本机词库、每日复习进度与近期活动。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showProgressDialog = true
                    },
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("每日复习进度", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "$todayCompleted / $target $progressUnit",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            "${(progress * 100).toInt()}%",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer,
                    strokeCap = StrokeCap.Round,
                )
                Text(
                    "参照范围：${progressReference.label()}。轻触切换参照并修改题量。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricTile("词库总量", words.size.toString(), Modifier.weight(1f))
                    MetricTile("今日完成", todayCompleted.toString(), Modifier.weight(1f))
                }
            }
        }
        StudyTimeCard(
            progress = attemptStudyTimeProgress,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showStudyTimeDialog = true
            },
        )
        DailyMemoCard(
            settings = state.dailyMemoSettings,
            progress = attemptDailyProgress,
            wordCount = words.size,
            quizBanks = quizBanks,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showMemoDialog = true
            },
        )
        ContrastPracticeSummary(practiceAttempts)
        SectionCard {
            Column(
                modifier = Modifier.animateContentSize(tween(260)),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(NvvIcons.LayoutDashboard, null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            if (range == HeatmapRange.MONTH) "月学习热力图" else "周学习热力图",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    RangeSelector(range = range, onChange = { range = it })
                }
                AnimatedContent(
                    targetState = range,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
                    label = "heatmap-range",
                ) { selected ->
                    if (selected == HeatmapRange.MONTH) {
                        MonthHeatmap(today, attemptDailyMillis)
                    } else {
                        WeekHeatmap(today, attemptDailyMillis)
                    }
                }
            }
        }
    }
    if (showProgressDialog) {
        DailyProgressDialog(
            settings = progressSettings,
            onDismiss = { showProgressDialog = false },
            onConfirm = { reference, quantity ->
                onSaveProgressSettings(reference, quantity)
                showProgressDialog = false
            },
        )
    }
    if (showMemoDialog) {
        DailyMemoDialog(
            settings = state.dailyMemoSettings,
            quizBanks = quizBanks,
            onDismiss = { showMemoDialog = false },
            onConfirm = {
                onSaveDailyMemoSettings(it)
                showMemoDialog = false
            },
        )
    }
    if (showStudyTimeDialog) {
        StudyTimeGoalDialog(
            progress = attemptStudyTimeProgress,
            onDismiss = { showStudyTimeDialog = false },
            onConfirm = { minutes ->
                onSaveStudyTimeGoal(minutes)
                showStudyTimeDialog = false
            },
        )
    }
}

@Composable
private fun StudyTimeCard(
    progress: StudyTimeProgress,
    onClick: () -> Unit,
) {
    val percent = (progress.progressFraction * 100).toInt()
    SectionCard {
        Column(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(NvvIcons.Timer, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text("学习时间目标", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "今天已学习 ${progress.elapsedMinutes} / ${progress.goalMinutes} 分钟",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        "$percent%",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progress.progressFraction },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer,
                strokeCap = StrokeCap.Round,
            )
            Text(
                "轻触设置每日学习时长；仅在练习会话中累计，并在每天 0 点切换到新记录。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StudyTimeGoalDialog(
    progress: StudyTimeProgress,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var minutesText by remember(progress.goalMinutes) {
        mutableStateOf(progress.goalMinutes.toString())
    }
    val minutes = minutesText.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("每日学习时间目标") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("今天已累计 ${progress.elapsedMinutes} 分钟。目标会同步显示到桌面微件。")
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { input -> minutesText = input.filter(Char::isDigit).take(3) },
                    label = { Text("目标时长") },
                    suffix = { Text("分钟") },
                    isError = minutes == null || minutes !in 1..720,
                    supportingText = {
                        if (minutes == null || minutes !in 1..720) Text("请输入 1 到 720 分钟")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                )
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = CircleShape) { Text("取消") }
        },
        confirmButton = {
            Button(
                onClick = { minutes?.let(onConfirm) },
                enabled = minutes != null && minutes in 1..720,
                shape = CircleShape,
            ) { Text("保存") }
        },
    )
}

@Composable
private fun DailyMemoCard(
    settings: DailyMemoSettings,
    progress: DailyPracticeProgress,
    wordCount: Int,
    quizBanks: List<QuizBank>,
    onClick: () -> Unit,
) {
    val isRestDay = settings.isRestDay(LocalDate.now().dayOfWeek.value)
    SectionCard {
        Column(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(NvvIcons.ListChecks, null, tint = MaterialTheme.colorScheme.primary)
                Text("每日备忘", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
            if (isRestDay) {
                Text("今日宜休", style = MaterialTheme.typography.titleLarge)
            } else {
                settings.items.forEach { item ->
                    val task = item.toTaskProgress(progress, wordCount, quizBanks)
                    MemoTaskRow(task.label, task.completed, task.target, task.unit)
                }
                if (settings.items.isEmpty()) {
                    Text("尚未添加备忘任务。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MemoTaskRow(label: String, completed: Int, target: Int, unit: String) {
    val finished = target > 0 && completed >= target
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            textDecoration = if (finished) TextDecoration.LineThrough else TextDecoration.None,
            color = if (finished) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "$completed / $target $unit",
            textDecoration = if (finished) TextDecoration.LineThrough else TextDecoration.None,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class MemoTaskProgress(
    val label: String,
    val completed: Int,
    val target: Int,
    val unit: String,
)

private fun DailyMemoItem.toTaskProgress(
    progress: DailyPracticeProgress,
    wordCount: Int,
    quizBanks: List<QuizBank>,
): MemoTaskProgress {
    val actionLabel = if (action == DailyMemoAction.COMPLETE) "完成" else "复习"
    val bank = quizBankId?.let { id -> quizBanks.firstOrNull { it.id == id } }
    val targetLabel = when (target) {
        DailyMemoTarget.DICTATION -> "默写"
        DailyMemoTarget.CONTRAST -> "对照复习"
        DailyMemoTarget.QUIZ_BANK -> "题库 ${bank?.name ?: quizBankName ?: "全部题库"}"
    }
    val completedCount = when (target) {
        DailyMemoTarget.DICTATION -> progress.dictationCompleted
        DailyMemoTarget.CONTRAST -> progress.contrastCompleted
        DailyMemoTarget.QUIZ_BANK -> quizBankId?.let { progress.customQuizCompletedByBank[it] }
            ?: progress.customQuizCompleted
    }
    val availableCount = when (target) {
        DailyMemoTarget.DICTATION, DailyMemoTarget.CONTRAST -> wordCount
        DailyMemoTarget.QUIZ_BANK -> bank?.questionCount ?: quizBanks.sumOf(QuizBank::questionCount)
    }
    return MemoTaskProgress(
        label = "$actionLabel $targetLabel",
        completed = completedCount,
        target = amount ?: availableCount,
        unit = if (target == DailyMemoTarget.DICTATION) "词" else "题",
    )
}

@Composable
private fun DailyMemoDialog(
    settings: DailyMemoSettings,
    quizBanks: List<QuizBank>,
    onDismiss: () -> Unit,
    onConfirm: (DailyMemoSettings) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 760.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.verticalScroll(remember { ScrollState(0) }).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("每日备忘", style = MaterialTheme.typography.headlineSmall)
                DailyMemoEditor(
                    settings = settings,
                    quizBanks = quizBanks,
                    onSave = onConfirm,
                    onCancel = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun DailyProgressDialog(
    settings: DailyProgressSettings,
    onDismiss: () -> Unit,
    onConfirm: (DailyProgressReference, Int) -> Unit,
) {
    var selectedReference by remember(settings) { mutableStateOf(settings.reference) }
    var quantityText by remember(settings) {
        mutableStateOf(settings.targetFor().coerceAtLeast(1).toString())
    }
    val quantity = quantityText.toIntOrNull()
    val validQuantity = quantity != null && quantity > 0
    val haptic = LocalHapticFeedback.current

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("每日进度参照") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DailyProgressReference.entries.forEach { reference ->
                    ProgressReferenceRow(
                        reference = reference,
                        selected = selectedReference == reference,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedReference = reference
                            quantityText = settings.targetFor(reference).coerceAtLeast(1).toString()
                        },
                    )
                }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = quantityText,
                    onValueChange = { quantityText = it.filter(Char::isDigit).take(9) },
                    label = { Text("每日目标题量") },
                    supportingText = {
                        Text("请输入任意大于 0 的${selectedReference.unit()}数。")
                    },
                    suffix = { Text(selectedReference.unit()) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = quantityText.isNotEmpty() && !validQuantity,
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                )
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = CircleShape) {
                Text("取消")
            }
        },
        confirmButton = {
            Button(
                onClick = { quantity?.let { onConfirm(selectedReference, it) } },
                enabled = validQuantity,
                shape = CircleShape,
            ) {
                Text("保存")
            }
        },
    )
}

@Composable
private fun ProgressReferenceRow(
    reference: DailyProgressReference,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(reference.icon(), null)
            Column(Modifier.weight(1f)) {
                Text(reference.label(), fontWeight = FontWeight.Medium)
                Text(
                    reference.description(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}

private fun DailyProgressReference.label(): String = when (this) {
    DailyProgressReference.DICTATION -> "默写复习"
    DailyProgressReference.CONTRAST -> "对照复习"
    DailyProgressReference.CUSTOM_QUIZ -> "自定义题库"
}

private fun DailyProgressReference.description(): String = when (this) {
    DailyProgressReference.DICTATION -> "统计今日拼写正确并完成结算的单词。"
    DailyProgressReference.CONTRAST -> "统计今日完成的 AI 对照选择题。"
    DailyProgressReference.CUSTOM_QUIZ -> "统计今日完成的本地 XML 题库题目。"
}

private fun DailyProgressReference.unit(): String =
    if (this == DailyProgressReference.DICTATION) "词" else "题"

private fun DailyProgressReference.icon() = when (this) {
    DailyProgressReference.DICTATION -> NvvIcons.BrainCircuit
    DailyProgressReference.CONTRAST -> NvvIcons.Sparkles
    DailyProgressReference.CUSTOM_QUIZ -> NvvIcons.FileQuestion
}

private data class AttemptSessionSummary(
    val correctCount: Int,
    val questionCount: Int,
) {
    val accuracyPercent: Int
        get() = if (questionCount == 0) 0 else correctCount * 100 / questionCount
}

@Composable
private fun ContrastPracticeSummary(attempts: List<PracticeAttempt>) {
    val recent = remember(attempts) {
        attempts.filter { attempt ->
            attempt.mode == PracticeAttemptMode.CHINESE_TO_ENGLISH ||
                attempt.mode == PracticeAttemptMode.ENGLISH_TO_CHINESE ||
                attempt.mode == PracticeAttemptMode.ENGLISH_DEFINITION_TO_ENGLISH
        }.groupBy(PracticeAttempt::sessionId)
            .values
            .sortedByDescending { sessionAttempts -> sessionAttempts.maxOf(PracticeAttempt::timestamp) }
            .take(5)
            .map { sessionAttempts ->
                AttemptSessionSummary(
                    correctCount = sessionAttempts.count(PracticeAttempt::correct),
                    questionCount = sessionAttempts.size,
                )
            }
    }
    val latest = recent.firstOrNull()
    val recentCorrectCount = recent.sumOf(AttemptSessionSummary::correctCount)
    val recentQuestionCount = recent.sumOf(AttemptSessionSummary::questionCount)
    val recentAccuracy = if (recentQuestionCount == 0) {
        0
    } else {
        (recentCorrectCount * 100f / recentQuestionCount).toInt()
    }
    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(NvvIcons.Sparkles, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text("对照练习成绩", style = MaterialTheme.typography.titleMedium)
                    Text("最近一次与最近五次本地结算统计。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (latest == null) {
                Text("暂无对照练习记录。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth >= 520.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ContrastMetricTiles(
                                latest = latest,
                                recentAccuracy = recentAccuracy,
                                recentCorrectCount = recentCorrectCount,
                                recentQuestionCount = recentQuestionCount,
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                MetricTile("最近一次正确率", "${latest.accuracyPercent}%", Modifier.weight(1f))
                                MetricTile(
                                    "最近一次正确数量",
                                    "${latest.correctCount} / ${latest.questionCount}",
                                    Modifier.weight(1f),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                MetricTile("最近五次正确率", "$recentAccuracy%", Modifier.weight(1f))
                                MetricTile(
                                    "最近五次正确数量",
                                    "$recentCorrectCount / $recentQuestionCount",
                                    Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ContrastMetricTiles(
    latest: AttemptSessionSummary,
    recentAccuracy: Int,
    recentCorrectCount: Int,
    recentQuestionCount: Int,
) {
    MetricTile("最近一次正确率", "${latest.accuracyPercent}%", Modifier.weight(1f))
    MetricTile(
        "最近一次正确数量",
        "${latest.correctCount} / ${latest.questionCount}",
        Modifier.weight(1f),
    )
    MetricTile("最近五次正确率", "$recentAccuracy%", Modifier.weight(1f))
    MetricTile(
        "最近五次正确数量",
        "$recentCorrectCount / $recentQuestionCount",
        Modifier.weight(1f),
    )
}

@Composable
private fun RangeSelector(range: HeatmapRange, onChange: (HeatmapRange) -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            RangeSegment("月", range == HeatmapRange.MONTH) { onChange(HeatmapRange.MONTH) }
            RangeSegment("周", range == HeatmapRange.WEEK) { onChange(HeatmapRange.WEEK) }
        }
    }
}

@Composable
private fun RangeSegment(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MonthHeatmap(today: LocalDate, dailyMillis: Map<LocalDate, Long>) {
    val month = YearMonth.from(today)
    val firstDate = month.atDay(1)
    val leadingEmptyCells = firstDate.dayOfWeek.value - 1
    val dates = (1..month.lengthOfMonth()).map(month::atDay)
    val cells = buildList<LocalDate?> {
        repeat(leadingEmptyCells) { add(null) }
        addAll(dates)
        while (size % 7 != 0) add(null)
    }
    val maxDuration = dates.maxOfOrNull { dailyMillis[it] ?: 0L }?.coerceAtLeast(1L) ?: 1L

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            month.atDay(1).format(DateTimeFormatter.ofPattern("yyyy年M月")),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                week.forEach { date ->
                    if (date == null) {
                        Surface(Modifier.weight(1f).aspectRatio(1f), color = androidx.compose.ui.graphics.Color.Transparent) {}
                    } else {
                        HeatCell(
                            durationMillis = dailyMillis[date] ?: 0L,
                            maxDurationMillis = maxDuration,
                            modifier = Modifier.weight(1f).aspectRatio(1f),
                            label = date.dayOfMonth.toString(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekHeatmap(today: LocalDate, dailyMillis: Map<LocalDate, Long>) {
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val durations = days.map { date -> dailyMillis[date] ?: 0L }
    val maxDuration = durations.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            days.forEach { date ->
                Text(
                    text = date.format(DateTimeFormatter.ofPattern("E")),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            durations.forEach { duration ->
                HeatCell(duration, maxDuration, Modifier.weight(1f).aspectRatio(1f))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            days.forEach { date ->
                Text(
                    text = date.format(DateTimeFormatter.ofPattern("M/d")),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HeatCell(
    durationMillis: Long,
    maxDurationMillis: Long,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val intensity = if (durationMillis == 0L) {
        0.08f
    } else {
        0.2f + 0.8f * (durationMillis.toFloat() / maxDurationMillis)
    }
    val normalizedIntensity = intensity.coerceIn(0.08f, 1f)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = normalizedIntensity),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            when {
                label != null -> Text(
                    label,
                    color = if (normalizedIntensity >= 0.62f) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
                durationMillis > 0L -> Text(
                    (durationMillis / 60_000L).coerceAtLeast(1L).toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
