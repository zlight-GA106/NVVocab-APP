package com.zlight106.nvvocab.ui.screens

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zlight106.nvvocab.data.WordEntry
import com.zlight106.nvvocab.domain.ProficiencyCalculator
import com.zlight106.nvvocab.ui.MainViewModel
import com.zlight106.nvvocab.ui.components.NvvDropdown
import com.zlight106.nvvocab.ui.components.SectionCard
import com.zlight106.nvvocab.ui.components.WebPronunciationHost
import com.zlight106.nvvocab.ui.components.rememberWebPronunciationPlayer
import com.zlight106.nvvocab.ui.icons.NvvIcons
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class LexiconSort {
    EARLIEST,
    LATEST,
    PROFICIENCY_LOW,
    PROFICIENCY_HIGH,
}

@Composable
fun LexiconScreen(
    viewModel: MainViewModel,
    words: List<WordEntry>,
    tags: List<String>,
) {
    val localDataLoaded by viewModel.localDataLoaded.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf("") }
    var selectedLetter by remember { mutableStateOf<Char?>(null) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(LexiconSort.EARLIEST) }
    var editingWord by remember { mutableStateOf<WordEntry?>(null) }
    val chronologicalNumbers = remember(words) {
        words.sortedWith(compareBy(WordEntry::introTime, WordEntry::id))
            .mapIndexed { index, word -> word.id to index + 1 }
            .toMap()
    }
    val visibleWords = remember(words, search, selectedLetter, selectedTag, sort) {
        val filtered = words.asSequence()
            .filter { word ->
                search.isBlank() || word.spelling.contains(search.trim(), ignoreCase = true) ||
                    word.translation.contains(search.trim(), ignoreCase = true)
            }
            .filter { word ->
                selectedLetter == null || word.spelling.firstOrNull()?.uppercaseChar() == selectedLetter
            }
            .filter { word -> selectedTag == null || word.bookTag == selectedTag }
            .toList()
        when (sort) {
            LexiconSort.EARLIEST -> filtered.sortedWith(compareBy(WordEntry::introTime, WordEntry::id))
            LexiconSort.LATEST -> filtered.sortedWith(compareByDescending<WordEntry> { it.introTime }.thenBy { it.id })
            LexiconSort.PROFICIENCY_LOW -> filtered.sortedBy { ProficiencyCalculator.calculate(it).score }
            LexiconSort.PROFICIENCY_HIGH -> filtered.sortedByDescending { ProficiencyCalculator.calculate(it).score }
        }
    }
    val context = LocalContext.current
    var ttsReady by remember { mutableStateOf(false) }
    var ttsInitializationStatus by remember { mutableStateOf<Int?>(null) }
    val textToSpeech = remember(context) {
        TextToSpeech(context.applicationContext) { status ->
            ttsInitializationStatus = status
        }
    }

    LaunchedEffect(ttsInitializationStatus, textToSpeech) {
        if (ttsInitializationStatus == TextToSpeech.SUCCESS) {
            val languageResult = textToSpeech.setLanguage(Locale.US)
            textToSpeech.setSpeechRate(0.9f)
            ttsReady = languageResult >= TextToSpeech.LANG_AVAILABLE
        } else if (ttsInitializationStatus != null) {
            ttsReady = false
        }
    }
    val webPronunciationPlayer = rememberWebPronunciationPlayer { word ->
        if (ttsReady) {
            textToSpeech.speak(word, TextToSpeech.QUEUE_FLUSH, null, "fallback-$word")
        } else {
            viewModel.notifyUser("暂时无法获取在线发音，请检查网络后重试")
        }
    }
    DisposableEffect(textToSpeech) {
        onDispose {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        WebPronunciationHost(webPronunciationPlayer)
        val columnCount = if (maxWidth >= 720.dp) 2 else 1
        val rows = visibleWords.chunked(columnCount)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("词库一览", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "按首字母、分类和熟练度浏览本地词库。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                SectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                            Icon(
                                NvvIcons.BookOpen,
                                null,
                                modifier = Modifier.padding(12.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text("当前词库", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "共 ${words.size} 词，当前显示 ${visibleWords.size} 词",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                SectionCard {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = search,
                            onValueChange = { search = it },
                            label = { Text("搜索单词或释义") },
                            leadingIcon = { Icon(NvvIcons.Search, null) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.large,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedLetter == null,
                                    onClick = { selectedLetter = null },
                                    label = { Text("全部") },
                                )
                            }
                            items(('A'..'Z').toList(), key = Char::code) { letter ->
                                FilterChip(
                                    selected = selectedLetter == letter,
                                    onClick = { selectedLetter = letter },
                                    label = { Text(letter.toString()) },
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            LexiconTagDropdown(selectedTag, tags, { selectedTag = it }, Modifier.weight(1f))
                            LexiconSortDropdown(sort, { sort = it }, Modifier.weight(1f))
                        }
                    }
                }
            }
            when {
                !localDataLoaded -> item {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }
                visibleWords.isEmpty() -> item {
                    SectionCard {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(NvvIcons.Search, null, tint = MaterialTheme.colorScheme.primary)
                            Text("暂无匹配词汇", style = MaterialTheme.typography.titleLarge)
                            Text("调整搜索、首字母或分类条件后再查看。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                else -> items(rows, key = { row -> row.joinToString("|") { it.id } }) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        row.forEach { word ->
                            WordPreviewCard(
                                modifier = Modifier.weight(1f),
                                word = word,
                                number = chronologicalNumbers[word.id] ?: 0,
                                onSpeak = { webPronunciationPlayer.speak(word.spelling) },
                                onEditTag = { editingWord = word },
                            )
                        }
                        repeat(columnCount - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }

    editingWord?.let { word ->
        EditTagDialog(
            word = word,
            tags = tags,
            onDismiss = { editingWord = null },
            onSave = { newTag ->
                viewModel.updateWordTag(word.id, newTag)
                editingWord = null
            },
        )
    }
}

@Composable
private fun LexiconTagDropdown(
    value: String?,
    tags: List<String>,
    onChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options: List<Pair<String?, String>> =
        listOf(null to "所有分类") + tags.map { tag -> tag to tag }
    NvvDropdown(
        label = "词库分类",
        value = value,
        options = options,
        icon = NvvIcons.Tags,
        onChange = onChange,
        modifier = modifier,
        compact = true,
    )
}

@Composable
private fun LexiconSortDropdown(
    value: LexiconSort,
    onChange: (LexiconSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    NvvDropdown(
        label = "排序方式",
        value = value,
        options = listOf(
            LexiconSort.EARLIEST to "最早导入优先",
            LexiconSort.LATEST to "最近导入优先",
            LexiconSort.PROFICIENCY_LOW to "熟练度从低到高",
            LexiconSort.PROFICIENCY_HIGH to "熟练度从高到低",
        ),
        icon = NvvIcons.RefreshCw,
        onChange = onChange,
        modifier = modifier,
        compact = true,
    )
}

@Composable
private fun WordPreviewCard(
    word: WordEntry,
    number: Int,
    onSpeak: () -> Unit,
    onEditTag: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val proficiency = remember(word) { ProficiencyCalculator.calculate(word) }
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(ZoneId.systemDefault())
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            number.toString(),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        word.spelling,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    word.phonetic?.takeIf(String::isNotBlank)?.let {
                        Text("[$it]", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                IconButton(onClick = onSpeak, enabled = word.spelling.isNotBlank()) {
                    Icon(NvvIcons.Volume2, "朗读 ${word.spelling}")
                }
                IconButton(onClick = onEditTag) {
                    Icon(NvvIcons.Pencil, "修改分类")
                }
            }
            Text(word.translation, style = MaterialTheme.typography.bodyLarge)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    word.bookTag,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("熟练度 ${proficiency.label}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${proficiency.score}", color = MaterialTheme.colorScheme.primary)
                }
                LinearProgressIndicator(
                    progress = { proficiency.score / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer,
                    strokeCap = StrokeCap.Round,
                )
            }
            Text(
                "导入 ${dateFormatter.format(Instant.ofEpochMilli(word.introTime))}  ·  ${word.repetitions} 次复习",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EditTagDialog(
    word: WordEntry,
    tags: List<String>,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var tag by remember(word.id) { mutableStateOf(word.bookTag) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改分类标签") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(word.spelling, style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = tag,
                    onValueChange = { tag = it },
                    label = { Text("分类标签") },
                    leadingIcon = { Icon(NvvIcons.Tags, null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                )
                if (tags.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(tags, key = { it }) { existingTag ->
                            FilterChip(
                                selected = tag == existingTag,
                                onClick = { tag = existingTag },
                                label = { Text(existingTag, maxLines = 1) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(tag) }, enabled = tag.isNotBlank(), shape = CircleShape) {
                Text("保存")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = CircleShape) { Text("取消") }
        },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    )
}
