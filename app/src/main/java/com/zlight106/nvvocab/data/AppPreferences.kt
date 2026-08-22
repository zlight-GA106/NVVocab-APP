package com.zlight106.nvvocab.data

import android.content.Context
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("nvvocab_preferences", Context.MODE_PRIVATE)

    fun readSupabaseConfig(): SupabaseConfig = SupabaseConfig(
        url = preferences.getString(KEY_SUPABASE_URL, "").orEmpty().trim().trimEnd('/'),
        publishableKey = preferences.getString(KEY_SUPABASE_KEY, "").orEmpty().trim(),
    )

    fun saveSupabaseConfig(config: SupabaseConfig) {
        preferences.edit()
            .putString(KEY_SUPABASE_URL, config.url.trim().trimEnd('/'))
            .putString(KEY_SUPABASE_KEY, config.publishableKey.trim())
            .apply()
    }

    fun readAiSettings(): AiSettings {
        val provider = runCatching {
            AiProvider.valueOf(
                preferences.getString(KEY_AI_PROVIDER, AiProvider.DEEPSEEK.name).orEmpty(),
            )
        }.getOrDefault(AiProvider.DEEPSEEK)
        val storedPrompt = preferences.getString(KEY_AI_SYSTEM_PROMPT, DEFAULT_AI_PROMPT)
            .orEmpty().trim()
        val resolvedPrompt = when {
            storedPrompt.isBlank() -> DEFAULT_AI_PROMPT
            storedPrompt == LEGACY_DEFAULT_AI_PROMPT -> DEFAULT_AI_PROMPT
            else -> storedPrompt
        }
        val storedAnalysisPrompt = preferences.getString(
            KEY_AI_ANALYSIS_PROMPT,
            DEFAULT_WRONG_QUESTION_ANALYSIS_PROMPT,
        ).orEmpty().trim()
        val resolvedAnalysisPrompt = when {
            storedAnalysisPrompt.isBlank() -> DEFAULT_WRONG_QUESTION_ANALYSIS_PROMPT
            storedAnalysisPrompt == LEGACY_DEFAULT_WRONG_QUESTION_ANALYSIS_PROMPT -> {
                DEFAULT_WRONG_QUESTION_ANALYSIS_PROMPT
            }
            else -> storedAnalysisPrompt
        }
        val mixedReviewPrompt = preferences.getString(
            KEY_AI_MIXED_REVIEW_PROMPT,
            DEFAULT_MIXED_REVIEW_PROMPT,
        ).orEmpty().trim().ifBlank { DEFAULT_MIXED_REVIEW_PROMPT }
        val fillBlankEvaluationPrompt = preferences.getString(
            KEY_AI_FILL_BLANK_EVALUATION_PROMPT,
            DEFAULT_FILL_BLANK_EVALUATION_PROMPT,
        ).orEmpty().trim().ifBlank { DEFAULT_FILL_BLANK_EVALUATION_PROMPT }
        return AiSettings(
            provider = provider,
            baseUrl = preferences.getString(KEY_AI_BASE_URL, "https://api.deepseek.com")
                .orEmpty().trim().trimEnd('/'),
            apiKey = preferences.getString(KEY_AI_API_KEY, "").orEmpty().trim(),
            model = preferences.getString(KEY_AI_MODEL, "deepseek-v4-flash").orEmpty().trim(),
            systemPrompt = resolvedPrompt,
            mixedReviewPrompt = mixedReviewPrompt,
            fillBlankEvaluationPrompt = fillBlankEvaluationPrompt,
            analysisPrompt = resolvedAnalysisPrompt,
        )
    }

    fun saveAiSettings(settings: AiSettings) {
        preferences.edit()
            .putString(KEY_AI_PROVIDER, settings.provider.name)
            .putString(KEY_AI_BASE_URL, settings.baseUrl.trim().trimEnd('/'))
            .putString(KEY_AI_API_KEY, settings.apiKey.trim())
            .putString(KEY_AI_MODEL, settings.model.trim())
            .putString(KEY_AI_SYSTEM_PROMPT, settings.systemPrompt.trim())
            .putString(KEY_AI_MIXED_REVIEW_PROMPT, settings.mixedReviewPrompt.trim())
            .putString(KEY_AI_FILL_BLANK_EVALUATION_PROMPT, settings.fillBlankEvaluationPrompt.trim())
            .putString(KEY_AI_ANALYSIS_PROMPT, settings.analysisPrompt.trim())
            .apply()
    }

    fun readSession(): AuthSession? {
        val accessToken = preferences.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val refreshToken = preferences.getString(KEY_REFRESH_TOKEN, null) ?: return null
        val userId = preferences.getString(KEY_USER_ID, null) ?: return null

        return AuthSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = userId,
            email = preferences.getString(KEY_EMAIL, "").orEmpty(),
            expiresAtEpochSeconds = preferences.getLong(KEY_EXPIRES_AT, 0L),
        )
    }

    fun saveSession(session: AuthSession) {
        preferences.edit()
            .putString(KEY_ACCESS_TOKEN, session.accessToken)
            .putString(KEY_REFRESH_TOKEN, session.refreshToken)
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_EMAIL, session.email)
            .putLong(KEY_EXPIRES_AT, session.expiresAtEpochSeconds)
            .apply()
    }

    fun clearSession() {
        preferences.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_EMAIL)
            .remove(KEY_EXPIRES_AT)
            .apply()
    }

    fun isAutomaticSyncEnabled(): Boolean = preferences.getBoolean(KEY_AUTO_SYNC, true)

    fun setAutomaticSyncEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_AUTO_SYNC, enabled).apply()
    }

    fun readSyncSettings(): SyncSettings {
        val mode = runCatching {
            SyncMode.valueOf(
                preferences.getString(KEY_SYNC_MODE, SyncMode.PERIODIC.name).orEmpty(),
            )
        }.getOrDefault(SyncMode.PERIODIC)
        return SyncSettings(
            enabled = isAutomaticSyncEnabled(),
            mode = mode,
            intervalMinutes = preferences.getLong(KEY_SYNC_INTERVAL_MINUTES, 15L)
                .coerceIn(15L, 1_440L),
        )
    }

    fun saveSyncSettings(settings: SyncSettings) {
        preferences.edit()
            .putBoolean(KEY_AUTO_SYNC, settings.enabled)
            .putString(KEY_SYNC_MODE, settings.mode.name)
            .putLong(KEY_SYNC_INTERVAL_MINUTES, settings.intervalMinutes.coerceIn(15L, 1_440L))
            .apply()
    }

    fun isDynamicColorEnabled(): Boolean = preferences.getBoolean(KEY_DYNAMIC_COLOR, true)

    fun setDynamicColorEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
    }

    fun isAdministratorModeEnabled(): Boolean =
        preferences.getBoolean(KEY_ADMINISTRATOR_MODE, false)

    fun setAdministratorModeEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ADMINISTRATOR_MODE, enabled).apply()
    }

    fun readThemeMode(): ThemeMode = runCatching {
        ThemeMode.valueOf(preferences.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name).orEmpty())
    }.getOrDefault(ThemeMode.SYSTEM)

    fun saveThemeMode(mode: ThemeMode) {
        preferences.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun readThemePresetId(): String? = preferences.getString(KEY_THEME_PRESET, null)

    fun saveThemePresetId(id: String?) {
        preferences.edit().putString(KEY_THEME_PRESET, id).apply()
    }

    fun readReviewCategory(): ReviewCategory = readEnum(
        key = KEY_REVIEW_CATEGORY,
        fallback = ReviewCategory.WORDS,
    )

    fun saveReviewCategory(category: ReviewCategory) {
        preferences.edit().putString(KEY_REVIEW_CATEGORY, category.name).apply()
    }

    fun readWordReviewPreferences(): WordReviewPreferences = WordReviewPreferences(
        mode = readEnum(KEY_WORD_REVIEW_MODE, DictationMode.REVIEW),
        selectedTag = preferences.getString(KEY_WORD_REVIEW_TAG, null),
        sort = readEnum(KEY_WORD_REVIEW_SORT, QueueSort.PROFICIENCY_LOW),
        limitText = preferences.getString(KEY_WORD_REVIEW_LIMIT, "").orEmpty(),
    )

    fun saveWordReviewPreferences(value: WordReviewPreferences) {
        preferences.edit()
            .putString(KEY_WORD_REVIEW_MODE, value.mode.name)
            .putString(KEY_WORD_REVIEW_TAG, value.selectedTag)
            .putString(KEY_WORD_REVIEW_SORT, value.sort.name)
            .putString(KEY_WORD_REVIEW_LIMIT, value.limitText)
            .apply()
    }

    fun readQuizReviewPreferences(): QuizReviewPreferences = QuizReviewPreferences(
        selectedBankId = preferences.getString(KEY_QUIZ_REVIEW_BANK, null),
        queueMode = readEnum(KEY_QUIZ_REVIEW_MODE, QuizQueueMode.SEQUENTIAL),
        rangeStart = preferences.getString(KEY_QUIZ_REVIEW_START, "1").orEmpty(),
        rangeEnd = preferences.getString(KEY_QUIZ_REVIEW_END, "").orEmpty(),
        randomCount = preferences.getString(KEY_QUIZ_REVIEW_RANDOM_COUNT, "20").orEmpty(),
        randomizeOptions = preferences.getBoolean(KEY_QUIZ_REVIEW_RANDOM_OPTIONS, false),
        unifiedSettlement = preferences.getBoolean(KEY_QUIZ_REVIEW_UNIFIED_SETTLEMENT, false),
        questionTypeFilter = readEnum(KEY_QUIZ_REVIEW_QUESTION_TYPE, QuizQuestionTypeFilter.MULTIPLE_CHOICE),
        ignoreFillBlankCase = preferences.getBoolean(KEY_QUIZ_REVIEW_IGNORE_CASE, true),
        timeLimitEnabled = preferences.getBoolean(KEY_QUIZ_REVIEW_TIME_ENABLED, false),
        timeLimitText = preferences.getString(KEY_QUIZ_REVIEW_TIME, "30").orEmpty(),
        includeTimingInXml = preferences.getBoolean(KEY_QUIZ_REVIEW_INCLUDE_TIMING_XML, true),
    )

    fun saveQuizReviewPreferences(value: QuizReviewPreferences) {
        preferences.edit()
            .putString(KEY_QUIZ_REVIEW_BANK, value.selectedBankId)
            .putString(KEY_QUIZ_REVIEW_MODE, value.queueMode.name)
            .putString(KEY_QUIZ_REVIEW_START, value.rangeStart)
            .putString(KEY_QUIZ_REVIEW_END, value.rangeEnd)
            .putString(KEY_QUIZ_REVIEW_RANDOM_COUNT, value.randomCount)
            .putBoolean(KEY_QUIZ_REVIEW_RANDOM_OPTIONS, value.randomizeOptions)
            .putBoolean(KEY_QUIZ_REVIEW_UNIFIED_SETTLEMENT, value.unifiedSettlement)
            .putString(KEY_QUIZ_REVIEW_QUESTION_TYPE, value.questionTypeFilter.name)
            .putBoolean(KEY_QUIZ_REVIEW_IGNORE_CASE, value.ignoreFillBlankCase)
            .putBoolean(KEY_QUIZ_REVIEW_TIME_ENABLED, value.timeLimitEnabled)
            .putString(KEY_QUIZ_REVIEW_TIME, value.timeLimitText)
            .putBoolean(KEY_QUIZ_REVIEW_INCLUDE_TIMING_XML, value.includeTimingInXml)
            .apply()
    }

    fun readContrastReviewPreferences(): ContrastReviewPreferences = ContrastReviewPreferences(
        type = readEnum(KEY_CONTRAST_REVIEW_TYPE, ContrastPracticeType.CHINESE_TO_ENGLISH),
        difficulty = readEnum(KEY_CONTRAST_REVIEW_DIFFICULTY, PracticeDifficulty.EASY),
        rangeMode = readEnum(KEY_CONTRAST_REVIEW_RANGE, PracticeRangeMode.ALL),
        selectedTag = preferences.getString(KEY_CONTRAST_REVIEW_TAG, null),
        proficiencyBand = readEnum(KEY_CONTRAST_REVIEW_PROFICIENCY, ProficiencyBand.LOW),
        selectedWordIds = preferences.getStringSet(KEY_CONTRAST_REVIEW_WORDS, emptySet()).orEmpty(),
        sort = readEnum(KEY_CONTRAST_REVIEW_SORT, QueueSort.LATEST),
        optionCountText = preferences.getString(KEY_CONTRAST_REVIEW_OPTIONS, "4").orEmpty(),
        questionCountText = preferences.getString(KEY_CONTRAST_REVIEW_QUESTIONS, "0").orEmpty(),
        timeLimitText = preferences.getString(KEY_CONTRAST_REVIEW_TIME, "30").orEmpty(),
        hintEnabled = preferences.getBoolean(KEY_CONTRAST_REVIEW_HINT, false),
        selectedQuizBankId = preferences.getString(KEY_CONTRAST_REVIEW_QUIZ_BANK, null),
        includeTimingInXml = preferences.getBoolean(KEY_CONTRAST_REVIEW_INCLUDE_TIMING_XML, true),
    )

    fun saveContrastReviewPreferences(value: ContrastReviewPreferences) {
        preferences.edit()
            .putString(KEY_CONTRAST_REVIEW_TYPE, value.type.name)
            .putString(KEY_CONTRAST_REVIEW_DIFFICULTY, value.difficulty.name)
            .putString(KEY_CONTRAST_REVIEW_RANGE, value.rangeMode.name)
            .putString(KEY_CONTRAST_REVIEW_TAG, value.selectedTag)
            .putString(KEY_CONTRAST_REVIEW_PROFICIENCY, value.proficiencyBand.name)
            .putStringSet(KEY_CONTRAST_REVIEW_WORDS, value.selectedWordIds)
            .putString(KEY_CONTRAST_REVIEW_SORT, value.sort.name)
            .putString(KEY_CONTRAST_REVIEW_OPTIONS, value.optionCountText)
            .putString(KEY_CONTRAST_REVIEW_QUESTIONS, value.questionCountText)
            .putString(KEY_CONTRAST_REVIEW_TIME, value.timeLimitText)
            .putBoolean(KEY_CONTRAST_REVIEW_HINT, value.hintEnabled)
            .putString(KEY_CONTRAST_REVIEW_QUIZ_BANK, value.selectedQuizBankId)
            .putBoolean(KEY_CONTRAST_REVIEW_INCLUDE_TIMING_XML, value.includeTimingInXml)
            .apply()
    }

    fun readMixedReviewPreferences(): MixedReviewPreferences {
        val enabledModes = preferences.getStringSet(
            KEY_MIXED_REVIEW_ENABLED_MODES,
            MixedReviewMode.entries.map(MixedReviewMode::name).toSet(),
        ).orEmpty().mapNotNull { name ->
            runCatching { MixedReviewMode.valueOf(name) }.getOrNull()
        }.toSet().ifEmpty { MixedReviewMode.entries.toSet() }
        val defaults = defaultMixedModePercentages()
        val percentages = MixedReviewMode.entries.associateWith { mode ->
            preferences.getInt(mixedModePercentageKey(mode), defaults.getValue(mode)).coerceIn(0, 100)
        }
        return MixedReviewPreferences(
            selectedTags = preferences.getStringSet(KEY_MIXED_REVIEW_TAGS, emptySet()).orEmpty().toSet(),
            questionCountText = preferences.getString(KEY_MIXED_REVIEW_QUESTION_COUNT, "50").orEmpty(),
            enabledModes = enabledModes,
            modePercentages = percentages,
            difficulty = readEnum(KEY_MIXED_REVIEW_DIFFICULTY, PracticeDifficulty.EASY),
            optionCountText = preferences.getString(KEY_MIXED_REVIEW_OPTION_COUNT, "4").orEmpty(),
            timeLimitText = preferences.getString(KEY_MIXED_REVIEW_TIME_LIMIT, "30").orEmpty(),
            includeTimingInXml = preferences.getBoolean(KEY_MIXED_REVIEW_INCLUDE_TIMING_XML, true),
        )
    }

    fun saveMixedReviewPreferences(value: MixedReviewPreferences) {
        val editor = preferences.edit()
            .putStringSet(KEY_MIXED_REVIEW_TAGS, value.selectedTags)
            .putString(KEY_MIXED_REVIEW_QUESTION_COUNT, value.questionCountText)
            .putStringSet(KEY_MIXED_REVIEW_ENABLED_MODES, value.enabledModes.map(MixedReviewMode::name).toSet())
            .putString(KEY_MIXED_REVIEW_DIFFICULTY, value.difficulty.name)
            .putString(KEY_MIXED_REVIEW_OPTION_COUNT, value.optionCountText)
            .putString(KEY_MIXED_REVIEW_TIME_LIMIT, value.timeLimitText)
            .putBoolean(KEY_MIXED_REVIEW_INCLUDE_TIMING_XML, value.includeTimingInXml)
        MixedReviewMode.entries.forEach { mode ->
            editor.putInt(
                mixedModePercentageKey(mode),
                value.modePercentages[mode]?.coerceIn(0, 100) ?: 0,
            )
        }
        editor.apply()
    }

    private fun mixedModePercentageKey(mode: MixedReviewMode): String =
        "${KEY_MIXED_REVIEW_PERCENTAGE}_${mode.name.lowercase()}"

    private inline fun <reified T : Enum<T>> readEnum(key: String, fallback: T): T = runCatching {
        enumValueOf<T>(preferences.getString(key, fallback.name).orEmpty())
    }.getOrDefault(fallback)

    fun readReminderSettings(): ReminderSettings = ReminderSettings(
        matchingEnabled = preferences.getBoolean(KEY_MATCHING_REMINDER, false),
        reviewEnabled = preferences.getBoolean(
            KEY_REVIEW_REMINDER,
            preferences.getBoolean(KEY_REVIEW_NOTIFICATION_LEGACY, false),
        ),
        questionEnabled = preferences.getBoolean(KEY_QUESTION_REMINDER, false),
        matchingQuestionTarget = preferences.getInt(KEY_MATCHING_TARGET, 20).coerceIn(1, 999),
        questionGroupCount = preferences.getInt(KEY_QUESTION_GROUPS, 3).coerceIn(1, 99),
        questionsPerGroup = preferences.getInt(KEY_QUESTIONS_PER_GROUP, 10).coerceIn(1, 999),
        reminderHour = preferences.getInt(KEY_REMINDER_HOUR, 8).coerceIn(0, 23),
    )

    fun saveReminderSettings(settings: ReminderSettings) {
        preferences.edit()
            .putBoolean(KEY_MATCHING_REMINDER, settings.matchingEnabled)
            .putBoolean(KEY_REVIEW_REMINDER, settings.reviewEnabled)
            .putBoolean(KEY_QUESTION_REMINDER, settings.questionEnabled)
            .putInt(KEY_MATCHING_TARGET, settings.matchingQuestionTarget.coerceIn(1, 999))
            .putInt(KEY_QUESTION_GROUPS, settings.questionGroupCount.coerceIn(1, 99))
            .putInt(KEY_QUESTIONS_PER_GROUP, settings.questionsPerGroup.coerceIn(1, 999))
            .putInt(KEY_REMINDER_HOUR, settings.reminderHour.coerceIn(0, 23))
            .remove(KEY_REVIEW_NOTIFICATION_LEGACY)
            .apply()
    }

    fun readDailyReviewTarget(): Int = preferences.getInt(KEY_DAILY_REVIEW_TARGET, 50).coerceAtLeast(1)

    fun saveDailyReviewTarget(target: Int) {
        preferences.edit().putInt(KEY_DAILY_REVIEW_TARGET, target.coerceAtLeast(1)).apply()
    }

    fun readStudyTimeGoalMinutes(): Int =
        preferences.getInt(KEY_STUDY_TIME_GOAL_MINUTES, 30).coerceIn(1, 720)

    fun saveStudyTimeGoalMinutes(minutes: Int) {
        preferences.edit()
            .putInt(KEY_STUDY_TIME_GOAL_MINUTES, minutes.coerceIn(1, 720))
            .apply()
    }

    @Synchronized
    fun readStudyTimeTodayMillis(today: LocalDate = LocalDate.now()): Long {
        val dailyKey = studyTimeDayKey(today)
        if (preferences.contains(dailyKey)) {
            return preferences.getLong(dailyKey, 0L).coerceAtLeast(0L)
        }
        if (preferences.getString(KEY_STUDY_TIME_DATE, null) != today.toString()) return 0L
        val legacyMillis = preferences.getLong(KEY_STUDY_TIME_MILLIS, 0L).coerceAtLeast(0L)
        preferences.edit().putLong(dailyKey, legacyMillis).apply()
        return legacyMillis
    }

    @Synchronized
    fun saveStudyTimeTodayMillis(today: LocalDate, elapsedMillis: Long) {
        val normalized = elapsedMillis.coerceAtLeast(0L)
        preferences.edit()
            .putString(KEY_STUDY_TIME_DATE, today.toString())
            .putLong(KEY_STUDY_TIME_MILLIS, normalized)
            .putLong(studyTimeDayKey(today), normalized)
            .apply()
    }

    fun readStudyTimeHistory(endDate: LocalDate = LocalDate.now(), dayCount: Int = 370): Map<LocalDate, Long> {
        val normalizedDayCount = dayCount.coerceAtLeast(1)
        return buildMap {
            repeat(normalizedDayCount) { offset ->
                val date = endDate.minusDays(offset.toLong())
                val elapsed = readStudyTimeTodayMillis(date)
                if (elapsed > 0L) put(date, elapsed)
            }
        }
    }

    private fun studyTimeDayKey(date: LocalDate): String = "$KEY_STUDY_TIME_DAY_PREFIX$date"

    fun readDailyProgressSettings(): DailyProgressSettings {
        val reminders = readReminderSettings()
        val reference = runCatching {
            DailyProgressReference.valueOf(
                preferences.getString(
                    KEY_DAILY_PROGRESS_REFERENCE,
                    DailyProgressReference.DICTATION.name,
                ).orEmpty(),
            )
        }.getOrDefault(DailyProgressReference.DICTATION)
        return DailyProgressSettings(
            reference = reference,
            dictationTarget = readDailyReviewTarget(),
            contrastTarget = preferences.getInt(
                KEY_DAILY_CONTRAST_TARGET,
                reminders.matchingQuestionTarget,
            ).coerceAtLeast(1),
            customQuizTarget = preferences.getInt(
                KEY_DAILY_CUSTOM_QUIZ_TARGET,
                reminders.questionGroupCount * reminders.questionsPerGroup,
            ).coerceAtLeast(1),
        )
    }

    fun saveDailyProgressSettings(reference: DailyProgressReference, target: Int) {
        val normalizedTarget = target.coerceAtLeast(1)
        val editor = preferences.edit().putString(KEY_DAILY_PROGRESS_REFERENCE, reference.name)
        when (reference) {
            DailyProgressReference.DICTATION -> editor.putInt(KEY_DAILY_REVIEW_TARGET, normalizedTarget)
            DailyProgressReference.CONTRAST -> editor.putInt(KEY_DAILY_CONTRAST_TARGET, normalizedTarget)
            DailyProgressReference.CUSTOM_QUIZ -> editor.putInt(KEY_DAILY_CUSTOM_QUIZ_TARGET, normalizedTarget)
        }
        editor.apply()
    }

    fun readDailyMemoSettings(): DailyMemoSettings {
        val storedItems = preferences.getString(KEY_MEMO_ITEMS, null)?.let(::parseDailyMemoItems)
        val targets = readDailyProgressSettings()
        val items = storedItems ?: listOf(
            DailyMemoItem(
                id = "memo-dictation",
                action = DailyMemoAction.REVIEW,
                target = DailyMemoTarget.DICTATION,
                amount = targets.dictationTarget,
            ),
        )
        return DailyMemoSettings(
            items = items.take(3),
            restDays = preferences.getStringSet(KEY_MEMO_REST_DAYS, emptySet())
            .orEmpty()
            .mapNotNull(String::toIntOrNull)
            .filter { it in 1..7 }
            .toSet(),
        )
    }

    fun saveDailyMemoSettings(settings: DailyMemoSettings) {
        preferences.edit()
            .putString(KEY_MEMO_ITEMS, serializeDailyMemoItems(settings.items.take(3)))
            .putStringSet(KEY_MEMO_REST_DAYS, settings.restDays.map(Int::toString).toSet())
            .apply()
    }

    private fun parseDailyMemoItems(raw: String): List<DailyMemoItem>? = runCatching {
        val array = JSONArray(raw)
        buildList {
            repeat(array.length()) { index ->
                val value = array.getJSONObject(index)
                add(
                    DailyMemoItem(
                        id = value.getString("id"),
                        action = DailyMemoAction.valueOf(value.getString("action")),
                        target = DailyMemoTarget.valueOf(value.getString("target")),
                        quizBankId = if (value.isNull("quizBankId")) {
                            null
                        } else {
                            value.optString("quizBankId").takeIf(String::isNotBlank)
                        },
                        quizBankName = if (value.isNull("quizBankName")) {
                            null
                        } else {
                            value.optString("quizBankName").takeIf(String::isNotBlank)
                        },
                        amount = if (value.isNull("amount")) null else value.getInt("amount").coerceAtLeast(1),
                    ),
                )
            }
        }
    }.getOrNull()

    private fun serializeDailyMemoItems(items: List<DailyMemoItem>): String = JSONArray().apply {
        items.forEach { item ->
            put(
                JSONObject().apply {
                    put("id", item.id)
                    put("action", item.action.name)
                    put("target", item.target.name)
                    put("quizBankId", item.quizBankId ?: JSONObject.NULL)
                    put("quizBankName", item.quizBankName ?: JSONObject.NULL)
                    put("amount", item.amount ?: JSONObject.NULL)
                },
            )
        }
    }.toString()

    fun readContrastPracticePresets(): ContrastPracticePresets = ContrastPracticePresets(
        easy = readContrastPracticePreset(
            prefix = "easy",
            fallback = ContrastPracticePreset(4, 0, 30),
        ),
        medium = readContrastPracticePreset(
            prefix = "medium",
            fallback = ContrastPracticePreset(6, 0, 20),
        ),
        hard = readContrastPracticePreset(
            prefix = "hard",
            fallback = ContrastPracticePreset(8, 0, 12),
        ),
    )

    fun saveContrastPracticePreset(difficulty: PracticeDifficulty, preset: ContrastPracticePreset) {
        val prefix = difficulty.name.lowercase()
        preferences.edit()
            .putInt("${KEY_CONTRAST_PRESET}_${prefix}_options", preset.optionCount.coerceIn(2, 8))
            .putInt(contrastPresetQuestionKey(prefix), preset.questionCount.coerceAtLeast(0))
            .putInt("${KEY_CONTRAST_PRESET}_${prefix}_seconds", preset.timeLimitSeconds.coerceIn(5, 300))
            .apply()
    }

    private fun readContrastPracticePreset(
        prefix: String,
        fallback: ContrastPracticePreset,
    ): ContrastPracticePreset = ContrastPracticePreset(
        optionCount = preferences.getInt(
            "${KEY_CONTRAST_PRESET}_${prefix}_options",
            fallback.optionCount,
        ).coerceIn(2, 8),
        questionCount = preferences.getInt(
            contrastPresetQuestionKey(prefix),
            fallback.questionCount,
        ).coerceAtLeast(0),
        timeLimitSeconds = preferences.getInt(
            "${KEY_CONTRAST_PRESET}_${prefix}_seconds",
            fallback.timeLimitSeconds,
        ).coerceIn(5, 300),
    )

    private fun contrastPresetQuestionKey(prefix: String): String =
        "${KEY_CONTRAST_PRESET}_${prefix}_max_questions_v2"

    private companion object {
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_AI_API_KEY = "ai_api_key"
        const val KEY_AI_ANALYSIS_PROMPT = "ai_analysis_prompt"
        const val KEY_AI_BASE_URL = "ai_base_url"
        const val KEY_AI_FILL_BLANK_EVALUATION_PROMPT = "ai_fill_blank_evaluation_prompt"
        const val KEY_AI_MIXED_REVIEW_PROMPT = "ai_mixed_review_prompt"
        const val KEY_AI_MODEL = "ai_model"
        const val KEY_AI_PROVIDER = "ai_provider"
        const val KEY_AI_SYSTEM_PROMPT = "ai_system_prompt"
        const val KEY_ADMINISTRATOR_MODE = "administrator_mode"
        const val KEY_AUTO_SYNC = "automatic_sync"
        const val KEY_CONTRAST_PRESET = "contrast_practice_preset"
        const val KEY_CONTRAST_REVIEW_DIFFICULTY = "contrast_review_difficulty"
        const val KEY_CONTRAST_REVIEW_HINT = "contrast_review_hint"
        const val KEY_CONTRAST_REVIEW_INCLUDE_TIMING_XML = "contrast_review_include_timing_xml"
        const val KEY_CONTRAST_REVIEW_OPTIONS = "contrast_review_options"
        const val KEY_CONTRAST_REVIEW_PROFICIENCY = "contrast_review_proficiency"
        const val KEY_CONTRAST_REVIEW_QUIZ_BANK = "contrast_review_quiz_bank"
        const val KEY_CONTRAST_REVIEW_QUESTIONS = "contrast_review_max_questions_v2"
        const val KEY_CONTRAST_REVIEW_RANGE = "contrast_review_range"
        const val KEY_CONTRAST_REVIEW_SORT = "contrast_review_sort"
        const val KEY_CONTRAST_REVIEW_TAG = "contrast_review_tag"
        const val KEY_CONTRAST_REVIEW_TIME = "contrast_review_time"
        const val KEY_CONTRAST_REVIEW_TYPE = "contrast_review_type"
        const val KEY_CONTRAST_REVIEW_WORDS = "contrast_review_words"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_DAILY_CONTRAST_TARGET = "daily_contrast_target"
        const val KEY_DAILY_CUSTOM_QUIZ_TARGET = "daily_custom_quiz_target"
        const val KEY_DAILY_PROGRESS_REFERENCE = "daily_progress_reference"
        const val KEY_DAILY_REVIEW_TARGET = "daily_review_target"
        const val KEY_EMAIL = "email"
        const val KEY_EXPIRES_AT = "expires_at"
        const val KEY_MATCHING_REMINDER = "matching_reminder"
        const val KEY_MATCHING_TARGET = "matching_question_target"
        const val KEY_MIXED_REVIEW_DIFFICULTY = "mixed_review_difficulty"
        const val KEY_MIXED_REVIEW_ENABLED_MODES = "mixed_review_enabled_modes"
        const val KEY_MIXED_REVIEW_INCLUDE_TIMING_XML = "mixed_review_include_timing_xml"
        const val KEY_MIXED_REVIEW_OPTION_COUNT = "mixed_review_option_count"
        const val KEY_MIXED_REVIEW_PERCENTAGE = "mixed_review_percentage"
        const val KEY_MIXED_REVIEW_QUESTION_COUNT = "mixed_review_question_count"
        const val KEY_MIXED_REVIEW_TAGS = "mixed_review_tags"
        const val KEY_MIXED_REVIEW_TIME_LIMIT = "mixed_review_time_limit"
        const val KEY_MEMO_CONTRAST = "daily_memo_contrast"
        const val KEY_MEMO_CUSTOM_QUIZ = "daily_memo_custom_quiz"
        const val KEY_MEMO_DICTATION = "daily_memo_dictation"
        const val KEY_MEMO_ITEMS = "daily_memo_items"
        const val KEY_MEMO_REST_DAYS = "daily_memo_rest_days"
        const val KEY_QUESTIONS_PER_GROUP = "questions_per_group"
        const val KEY_QUESTION_GROUPS = "question_group_count"
        const val KEY_QUESTION_REMINDER = "question_reminder"
        const val KEY_QUIZ_REVIEW_BANK = "quiz_review_bank"
        const val KEY_QUIZ_REVIEW_END = "quiz_review_end"
        const val KEY_QUIZ_REVIEW_MODE = "quiz_review_mode"
        const val KEY_QUIZ_REVIEW_RANDOM_COUNT = "quiz_review_random_count"
        const val KEY_QUIZ_REVIEW_RANDOM_OPTIONS = "quiz_review_random_options"
        const val KEY_QUIZ_REVIEW_QUESTION_TYPE = "quiz_review_question_type"
        const val KEY_QUIZ_REVIEW_IGNORE_CASE = "quiz_review_ignore_case"
        const val KEY_QUIZ_REVIEW_INCLUDE_TIMING_XML = "quiz_review_include_timing_xml"
        const val KEY_QUIZ_REVIEW_START = "quiz_review_start"
        const val KEY_QUIZ_REVIEW_TIME = "quiz_review_time"
        const val KEY_QUIZ_REVIEW_TIME_ENABLED = "quiz_review_time_enabled"
        const val KEY_QUIZ_REVIEW_UNIFIED_SETTLEMENT = "quiz_review_unified_settlement"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_REMINDER_HOUR = "reminder_hour"
        const val KEY_REVIEW_CATEGORY = "review_category"
        const val KEY_REVIEW_NOTIFICATION_LEGACY = "review_notification"
        const val KEY_REVIEW_REMINDER = "review_reminder"
        const val KEY_SUPABASE_KEY = "supabase_publishable_key"
        const val KEY_SUPABASE_URL = "supabase_url"
        const val KEY_SYNC_INTERVAL_MINUTES = "sync_interval_minutes"
        const val KEY_SYNC_MODE = "sync_mode"
        const val KEY_STUDY_TIME_DATE = "study_time_date"
        const val KEY_STUDY_TIME_DAY_PREFIX = "study_time_day_"
        const val KEY_STUDY_TIME_GOAL_MINUTES = "study_time_goal_minutes"
        const val KEY_STUDY_TIME_MILLIS = "study_time_millis"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_THEME_PRESET = "theme_preset"
        const val KEY_USER_ID = "user_id"
        const val KEY_WORD_REVIEW_LIMIT = "word_review_limit"
        const val KEY_WORD_REVIEW_MODE = "word_review_mode"
        const val KEY_WORD_REVIEW_SORT = "word_review_sort"
        const val KEY_WORD_REVIEW_TAG = "word_review_tag"
    }
}
