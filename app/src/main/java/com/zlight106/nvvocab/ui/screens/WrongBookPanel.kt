package com.zlight106.nvvocab.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zlight106.nvvocab.data.WrongQuestionEntry
import com.zlight106.nvvocab.data.WrongQuestionSort
import com.zlight106.nvvocab.data.formatOptionAnswers
import com.zlight106.nvvocab.ui.MainViewModel
import com.zlight106.nvvocab.ui.components.NvvDropdown
import com.zlight106.nvvocab.ui.components.QuestionOptionDetails
import com.zlight106.nvvocab.ui.components.SectionCard
import com.zlight106.nvvocab.ui.components.SegmentedRow
import com.zlight106.nvvocab.ui.icons.NvvIcons

private enum class WrongBookView {
    ERRORS,
    FAVORITES,
}

@Composable
fun WrongBookPanel(
    viewModel: MainViewModel,
    onStartSession: (PracticeSessionRequest) -> Unit,
) {
    val entries by viewModel.wrongQuestions.collectAsStateWithLifecycle()
    val analyzingId by viewModel.analyzingWrongQuestionId.collectAsStateWithLifecycle()
    var view by remember { mutableStateOf(WrongBookView.ERRORS) }
    var sort by remember { mutableStateOf(WrongQuestionSort.WRONG_COUNT) }
    val visible = remember(entries, view, sort) {
        entries.asSequence()
            .filter { view == WrongBookView.ERRORS || it.favorite }
            .let { sequence ->
                when (sort) {
                    WrongQuestionSort.LATEST -> sequence.sortedByDescending(WrongQuestionEntry::lastWrongAt)
                    WrongQuestionSort.WRONG_COUNT -> sequence.sortedByDescending(WrongQuestionEntry::wrongCount)
                    WrongQuestionSort.PROFICIENCY_LOW -> sequence.sortedBy(WrongQuestionEntry::proficiencyPercent)
                    WrongQuestionSort.PROFICIENCY_HIGH -> sequence.sortedByDescending(WrongQuestionEntry::proficiencyPercent)
                }
            }
            .toList()
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("错题本", style = MaterialTheme.typography.titleLarge)
                Text(
                    "按题目累计正确与错误次数，收藏题目可形成独立复习清单。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SegmentedRow(Modifier.fillMaxWidth()) {
                    WrongBookSegment(
                        modifier = Modifier.weight(1f),
                        selected = view == WrongBookView.ERRORS,
                        label = "错误（${entries.size}）",
                        onClick = { view = WrongBookView.ERRORS },
                    )
                    WrongBookSegment(
                        modifier = Modifier.weight(1f),
                        selected = view == WrongBookView.FAVORITES,
                        label = "收藏（${entries.count(WrongQuestionEntry::favorite)}）",
                        onClick = { view = WrongBookView.FAVORITES },
                    )
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth < 520.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            WrongSortDropdown(sort, { sort = it })
                            Button(
                                onClick = { onStartSession(PracticeSessionRequest.WrongBook(visible)) },
                                enabled = visible.isNotEmpty(),
                                shape = CircleShape,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(NvvIcons.Play, null)
                                Text("复习当前清单", Modifier.padding(start = 8.dp))
                            }
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            WrongSortDropdown(sort, { sort = it }, Modifier.weight(1f))
                            Button(
                                onClick = { onStartSession(PracticeSessionRequest.WrongBook(visible)) },
                                enabled = visible.isNotEmpty(),
                                shape = CircleShape,
                            ) {
                                Icon(NvvIcons.Play, null)
                                Text("复习当前清单", Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
        }

        if (visible.isEmpty()) {
            SectionCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(NvvIcons.Check, null, tint = MaterialTheme.colorScheme.primary)
                    Text(if (view == WrongBookView.FAVORITES) "暂无收藏题目" else "暂无错题记录")
                }
            }
        } else {
            Column(
                modifier = Modifier.heightIn(max = 680.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                visible.forEach { entry ->
                    WrongQuestionCard(
                        entry = entry,
                        analyzing = analyzingId == entry.id,
                        onFavorite = { viewModel.setWrongQuestionFavorite(entry) },
                        onAnalyze = { viewModel.analyzeWrongQuestion(entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WrongSortDropdown(
    value: WrongQuestionSort,
    onChange: (WrongQuestionSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    NvvDropdown(
        label = "排序方式",
        value = value,
        options = listOf(
            WrongQuestionSort.WRONG_COUNT to "根据错误次数排序",
            WrongQuestionSort.LATEST to "最近错误优先",
        ),
        icon = NvvIcons.RefreshCw,
        onChange = onChange,
        modifier = modifier,
        compact = true,
    )
}

@Composable
private fun WrongBookSegment(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
    }
}

@Composable
private fun WrongQuestionCard(
    entry: WrongQuestionEntry,
    analyzing: Boolean,
    onFavorite: () -> Unit,
    onAnalyze: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(entry.bankName, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    Text(entry.questionText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = onFavorite) {
                    Icon(
                        NvvIcons.Bookmark,
                        contentDescription = if (entry.favorite) "取消收藏" else "收藏错题",
                        tint = if (entry.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Text(
                        "错误次数 ${entry.wrongCount}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    "正确 ${entry.correctCount} 次",
                    modifier = Modifier.align(Alignment.CenterVertically),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            QuestionOptionDetails(
                options = entry.options,
                correctAnswers = entry.correctAnswers,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "正确答案：${formatOptionAnswers(entry.options, entry.correctAnswers)}",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            entry.aiAnalysis?.takeIf(String::isNotBlank)?.let { analysis ->
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                ) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "AI 错题解析",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(analysis, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            OutlinedButton(
                onClick = onAnalyze,
                enabled = !analyzing,
                shape = CircleShape,
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(NvvIcons.Bot, null)
                Text(if (analyzing) "正在分析" else if (entry.aiAnalysis.isNullOrBlank()) "AI 分析" else "重新分析", Modifier.padding(start = 8.dp))
            }
        }
    }
}
