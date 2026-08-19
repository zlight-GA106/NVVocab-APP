package com.zlight106.nvvocab.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import com.zlight106.nvvocab.data.AiProvider
import com.zlight106.nvvocab.data.AiSettings
import com.zlight106.nvvocab.data.DEFAULT_WRONG_QUESTION_ANALYSIS_PROMPT
import com.zlight106.nvvocab.data.QuizBank
import com.zlight106.nvvocab.data.ThemeMode
import com.zlight106.nvvocab.data.ReminderSettings
import com.zlight106.nvvocab.data.SyncMode
import com.zlight106.nvvocab.ui.AppUiState
import com.zlight106.nvvocab.ui.MainViewModel
import com.zlight106.nvvocab.ui.components.DailyMemoEditor
import com.zlight106.nvvocab.ui.components.NvvDropdown
import com.zlight106.nvvocab.ui.components.SectionCard
import com.zlight106.nvvocab.ui.components.SelectionRow
import com.zlight106.nvvocab.ui.icons.NvvIcons
import com.zlight106.nvvocab.ui.theme.ThemePreset
import com.zlight106.nvvocab.ui.theme.themePresets

@Composable
fun SettingsScreen(viewModel: MainViewModel, state: AppUiState, quizBanks: List<QuizBank>) {
    var url by remember(state.supabaseConfig.url) { mutableStateOf(state.supabaseConfig.url) }
    var key by remember(state.supabaseConfig.publishableKey) { mutableStateOf(state.supabaseConfig.publishableKey) }
    var aiProvider by remember(state.aiSettings.provider) { mutableStateOf(state.aiSettings.provider) }
    var aiBaseUrl by remember(state.aiSettings.baseUrl) { mutableStateOf(state.aiSettings.baseUrl) }
    var aiApiKey by remember(state.aiSettings.apiKey) { mutableStateOf(state.aiSettings.apiKey) }
    var aiModel by remember(state.aiSettings.model) { mutableStateOf(state.aiSettings.model) }
    var aiPrompt by remember(state.aiSettings.systemPrompt) { mutableStateOf(state.aiSettings.systemPrompt) }
    var aiAnalysisPrompt by remember(state.aiSettings.analysisPrompt) {
        mutableStateOf(state.aiSettings.analysisPrompt)
    }
    var dailyTarget by remember(state.dailyReviewTarget) { mutableStateOf(state.dailyReviewTarget.toString()) }
    var matchingEnabled by remember(state.reminderSettings.matchingEnabled) {
        mutableStateOf(state.reminderSettings.matchingEnabled)
    }
    var reviewEnabled by remember(state.reminderSettings.reviewEnabled) {
        mutableStateOf(state.reminderSettings.reviewEnabled)
    }
    var questionEnabled by remember(state.reminderSettings.questionEnabled) {
        mutableStateOf(state.reminderSettings.questionEnabled)
    }
    var matchingTarget by remember(state.reminderSettings.matchingQuestionTarget) {
        mutableStateOf(state.reminderSettings.matchingQuestionTarget.toString())
    }
    var questionGroups by remember(state.reminderSettings.questionGroupCount) {
        mutableStateOf(state.reminderSettings.questionGroupCount.toString())
    }
    var questionsPerGroup by remember(state.reminderSettings.questionsPerGroup) {
        mutableStateOf(state.reminderSettings.questionsPerGroup.toString())
    }
    var reminderHour by remember(state.reminderSettings.reminderHour) {
        mutableStateOf(state.reminderSettings.reminderHour.toString())
    }
    val context = LocalContext.current
    var notificationGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationGranted = granted
    }
    val databaseExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.sqlite3"),
    ) { uri ->
        uri?.let(viewModel::exportDatabase)
    }

    fun requestNotificationPermission(enabled: Boolean) {
        if (enabled && Build.VERSION.SDK_INT >= 33 && !notificationGranted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(remember { ScrollState(0) }).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("系统设置", style = MaterialTheme.typography.headlineMedium)
        ExpandableSettingCard("Supabase 节点", NvvIcons.Database) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = url,
                onValueChange = { url = it },
                label = { Text("Project URL") },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = key,
                onValueChange = { key = it },
                label = { Text("Publishable Key") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )
            Button(
                onClick = { viewModel.saveSupabaseConfig(url, key) },
                shape = CircleShape,
                modifier = Modifier.align(Alignment.End),
            ) { Text("保存连接信息") }
        }
        ExpandableSettingCard("AI 与提示词", NvvIcons.Bot) {
            NvvDropdown(
                label = "AI 供应商",
                value = aiProvider,
                options = listOf(
                    AiProvider.DEEPSEEK to "DeepSeek",
                    AiProvider.OPENAI_COMPATIBLE to "OpenAI 兼容服务",
                ),
                icon = NvvIcons.Bot,
                onChange = { provider ->
                    aiProvider = provider
                    if (provider == AiProvider.DEEPSEEK) {
                        aiBaseUrl = "https://api.deepseek.com"
                        aiModel = "deepseek-v4-flash"
                    }
                },
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = aiBaseUrl,
                onValueChange = { aiBaseUrl = it },
                label = { Text("API Base URL") },
                supportingText = {
                    Text(
                        if (aiProvider == AiProvider.DEEPSEEK) {
                            "DeepSeek 官方 OpenAI 兼容入口。"
                        } else {
                            "填写兼容 Chat Completions 的服务地址。"
                        },
                    )
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = aiApiKey,
                onValueChange = { aiApiKey = it },
                label = { Text("API Key") },
                visualTransformation = PasswordVisualTransformation(),
                supportingText = { Text("密钥仅保存在当前设备的应用私有配置中。") },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = aiModel,
                onValueChange = { aiModel = it },
                label = { Text("模型") },
                supportingText = {
                    Text(if (aiProvider == AiProvider.DEEPSEEK) "默认使用 deepseek-v4-flash。" else "填写供应商模型标识。")
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                value = aiPrompt,
                onValueChange = { aiPrompt = it },
                label = { Text("系统提示词") },
                supportingText = { Text("AI 对照练习生成将使用此提示词。") },
                shape = MaterialTheme.shapes.extraLarge,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                value = aiAnalysisPrompt,
                onValueChange = { aiAnalysisPrompt = it },
                label = { Text("错题解析提示词") },
                supportingText = { Text("错题本中的 AI 解析会使用此提示词。") },
                shape = MaterialTheme.shapes.extraLarge,
            )
            OutlinedButton(
                onClick = { aiAnalysisPrompt = DEFAULT_WRONG_QUESTION_ANALYSIS_PROMPT },
                shape = CircleShape,
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(NvvIcons.RefreshCw, contentDescription = null)
                Text("恢复默认提示词", Modifier.padding(start = 8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.testAiSettings(
                            AiSettings(
                                provider = aiProvider,
                                baseUrl = aiBaseUrl,
                                apiKey = aiApiKey,
                                model = aiModel,
                                systemPrompt = aiPrompt,
                                analysisPrompt = aiAnalysisPrompt,
                            ),
                        )
                    },
                    enabled = aiBaseUrl.isNotBlank() && aiApiKey.isNotBlank() && aiModel.isNotBlank() && !state.aiTesting,
                    shape = CircleShape,
                ) {
                    Icon(NvvIcons.Bot, contentDescription = null)
                    Text(if (state.aiTesting) "测试中" else "测试", Modifier.padding(start = 8.dp))
                }
                Button(
                    onClick = {
                        viewModel.saveAiSettings(
                            AiSettings(
                                provider = aiProvider,
                                baseUrl = aiBaseUrl,
                                apiKey = aiApiKey,
                                model = aiModel,
                                systemPrompt = aiPrompt,
                                analysisPrompt = aiAnalysisPrompt,
                            ),
                        )
                    },
                    enabled = aiBaseUrl.isNotBlank() && aiModel.isNotBlank() &&
                        aiPrompt.isNotBlank() && aiAnalysisPrompt.isNotBlank(),
                    shape = CircleShape,
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("保存 AI 设置") }
            }
        }
        ExpandableSettingCard("账户", NvvIcons.UserRound) {
            AccountSettingsPanel(viewModel = viewModel, state = state)
        }
        ExpandableSettingCard("色彩预设", NvvIcons.Palette) {
            Text(
                "从六套完整角色调色板中切换全站颜色。选择预设后会暂停系统动态取色。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            themePresets.chunked(2).forEach { rowPresets ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowPresets.forEach { preset ->
                        ThemePresetCard(
                            preset = preset,
                            selected = state.themePresetId == preset.id,
                            onClick = { viewModel.setThemePreset(preset.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowPresets.size == 1) Surface(Modifier.weight(1f), color = Color.Transparent) {}
                }
            }
        }
        ExpandableSettingCard("显示与动态取色", NvvIcons.Sun) {
            SettingSwitch(
                title = "系统动态取色",
                description = null,
                checked = state.dynamicColor,
                onCheckedChange = viewModel::setDynamicColor,
            )
            Text("明暗模式", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        label = {
                            Text(
                                when (mode) {
                                    ThemeMode.SYSTEM -> "跟随系统"
                                    ThemeMode.LIGHT -> "浅色"
                                    ThemeMode.DARK -> "深色"
                                },
                            )
                        },
                    )
                }
            }
        }
        ExpandableSettingCard("通知与每日目标", NvvIcons.Bell) {
            if (!notificationGranted && Build.VERSION.SDK_INT >= 33) {
                Button(
                    onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    shape = CircleShape,
                ) { Text("请求通知权限") }
            }
            ReminderCheckRow(
                title = "单词匹配提醒",
                description = null,
                checked = matchingEnabled,
                onCheckedChange = {
                    matchingEnabled = it
                    requestNotificationPermission(it)
                },
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = matchingTarget,
                onValueChange = { matchingTarget = it.filter(Char::isDigit).take(3) },
                label = { Text("每日单词匹配题量") },
                suffix = { Text("题") },
                enabled = matchingEnabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ReminderCheckRow(
                title = "复习默写提醒",
                description = null,
                checked = reviewEnabled,
                onCheckedChange = {
                    reviewEnabled = it
                    requestNotificationPermission(it)
                },
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = dailyTarget,
                onValueChange = { dailyTarget = it.filter(Char::isDigit).take(9) },
                label = { Text("每日复习量目标") },
                suffix = { Text("词") },
                enabled = reviewEnabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ReminderCheckRow(
                title = "分组题目提醒",
                description = null,
                checked = questionEnabled,
                onCheckedChange = {
                    questionEnabled = it
                    requestNotificationPermission(it)
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = questionGroups,
                    onValueChange = { questionGroups = it.filter(Char::isDigit).take(2) },
                    label = { Text("分组数") },
                    suffix = { Text("组") },
                    enabled = questionEnabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                )
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = questionsPerGroup,
                    onValueChange = { questionsPerGroup = it.filter(Char::isDigit).take(3) },
                    label = { Text("每组题量") },
                    suffix = { Text("题") },
                    enabled = questionEnabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                )
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = reminderHour,
                onValueChange = { reminderHour = it.filter(Char::isDigit).take(2) },
                label = { Text("每天几点提醒我") },
                suffix = { Text("点") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )
            val parsedMatching = matchingTarget.toIntOrNull() ?: 0
            val parsedReview = dailyTarget.toIntOrNull() ?: 0
            val parsedGroups = questionGroups.toIntOrNull() ?: 0
            val parsedPerGroup = questionsPerGroup.toIntOrNull() ?: 0
            val parsedHour = reminderHour.toIntOrNull() ?: -1
            val formValid = parsedMatching in 1..999 &&
                parsedReview > 0 &&
                parsedGroups in 1..99 &&
                parsedPerGroup in 1..999 &&
                parsedHour in 0..23
            Button(
                onClick = {
                    viewModel.saveReminderConfiguration(
                        settings = ReminderSettings(
                            matchingEnabled = matchingEnabled,
                            reviewEnabled = reviewEnabled,
                            questionEnabled = questionEnabled,
                            matchingQuestionTarget = parsedMatching,
                            questionGroupCount = parsedGroups,
                            questionsPerGroup = parsedPerGroup,
                            reminderHour = parsedHour,
                        ),
                        dailyReviewTarget = parsedReview,
                    )
                },
                enabled = formValid,
                shape = CircleShape,
                modifier = Modifier.align(Alignment.End),
            ) { Text("保存通知计划") }
        }
        ExpandableSettingCard("桌面微件与每日备忘", NvvIcons.ListChecks) {
            DailyMemoEditor(
                settings = state.dailyMemoSettings,
                quizBanks = quizBanks,
                onSave = viewModel::saveDailyMemoSettings,
            )
        }
        ExpandableSettingCard("离线与同步", NvvIcons.Cloud) {
            SettingSwitch(
                title = "自动同步",
                description = "联网后按照选定策略同步 SQLite 与 Supabase。",
                checked = state.automaticSync,
                onCheckedChange = viewModel::setAutomaticSync,
            )
            if (state.automaticSync) {
                NvvDropdown(
                    label = "自动同步触发方式",
                    value = state.syncSettings.mode,
                    options = listOf(
                        SyncMode.ON_LOCAL_CHANGE to "每次本地数据库被修改后",
                        SyncMode.PERIODIC to "按时间间隔同步",
                    ),
                    icon = NvvIcons.RefreshCw,
                    onChange = viewModel::setSyncMode,
                )
                if (state.syncSettings.mode == SyncMode.PERIODIC) {
                    NvvDropdown(
                        label = "时间间隔",
                        value = state.syncSettings.intervalMinutes,
                        options = listOf(
                            15L to "每 15 分钟",
                            30L to "每 30 分钟",
                            60L to "每 1 小时",
                            180L to "每 3 小时",
                            360L to "每 6 小时",
                            720L to "每 12 小时",
                            1_440L to "每 24 小时",
                        ),
                        icon = NvvIcons.Timer,
                        onChange = viewModel::setSyncIntervalMinutes,
                    )
                } else {
                    Text(
                        "本地写入完成后会合并短时间内的连续修改，并在网络可用时自动同步。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Button(
                onClick = viewModel::synchronize,
                enabled = !state.syncing,
                shape = CircleShape,
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(NvvIcons.RefreshCw, null)
                Text(if (state.syncing) "正在同步" else "立即同步", Modifier.padding(start = 8.dp))
            }
        }
        ExpandableSettingCard("本地数据库", NvvIcons.Database) {
            Text("SQLite 是移动端数据源。导入与默写记录会先在本机提交，即使断网也可继续使用。")
            Button(
                onClick = { databaseExportLauncher.launch("nvvocab-backup.db") },
                shape = CircleShape,
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(NvvIcons.Download, null)
                Text("导出 SQLite 文件", Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun ThemePresetCard(
    preset: ThemePreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val targetColor = if (selected) preset.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val targetBorderColor = if (selected) preset.primary else MaterialTheme.colorScheme.outlineVariant
    val cardColor by animateColorAsState(targetColor, tween(420), label = "preset-card-color")
    val borderColor by animateColorAsState(targetBorderColor, tween(420), label = "preset-border-color")
    Surface(
        modifier = modifier.heightIn(min = 148.dp).animateContentSize(tween(320)).clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        color = cardColor,
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 2.dp else 1.dp,
            borderColor,
        ),
        tonalElevation = if (selected) 4.dp else 0.dp,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    preset.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) preset.onSurface else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (selected) Icon(NvvIcons.Check, null, tint = preset.primary)
            }
            Text(
                preset.description,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) preset.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(
                    preset.primary,
                    preset.primaryContainer,
                    preset.secondary,
                    preset.tertiary,
                    preset.surface,
                ).forEach { color ->
                    Surface(
                        modifier = Modifier.weight(1f).heightIn(min = 28.dp),
                        shape = CircleShape,
                        color = color,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.55f)),
                    ) {}
                }
            }
        }
    }
}

@Composable
private fun ReminderCheckRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SelectionRow(onClick = { onCheckedChange(!checked) }) {
        Checkbox(checked = checked, onCheckedChange = null)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ExpandableSettingCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SelectionRow(onClick = { expanded = !expanded }) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(NvvIcons.ChevronDown, null)
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(180)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SelectionRow(onClick = { onCheckedChange(!checked) }, spacing = 16.dp) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            description?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
