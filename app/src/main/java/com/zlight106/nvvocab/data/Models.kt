package com.zlight106.nvvocab.data

data class ParsedWord(
    val spelling: String,
    val phonetic: String,
    val translation: String,
)

data class WordEntry(
    val id: String,
    val userId: String?,
    val spelling: String,
    val phonetic: String?,
    val translation: String,
    val bookTag: String,
    val introTime: Long,
    val repetitions: Int,
    val intervalDays: Int,
    val easiness: Double,
    val nextReviewAt: Long,
    val wrongCount: Int,
    val dirty: Boolean,
)

data class ReviewLogEntry(
    val id: String,
    val userId: String?,
    val wordId: String,
    val reviewedAt: Long,
    val quality: Int,
    val dirty: Boolean,
)

data class QuizBank(
    val id: String,
    val name: String,
    val password: String?,
    val importedAt: Long,
    val questionCount: Int,
)

data class QuizOption(
    val id: String,
    val text: String,
)

data class QuizQuestion(
    val id: String,
    val bankId: String,
    val originalIndex: Int,
    val score: Int,
    val text: String,
    val options: List<QuizOption>,
    val answers: Set<String>,
)

enum class QuizSource {
    XML,
    AI,
}

data class TitleListEntry(
    val id: String,
    val userId: String?,
    val name: String,
    val source: QuizSource,
    val practiceType: ContrastPracticeType?,
    val difficulty: PracticeDifficulty?,
    val importedAt: Long,
    val questions: List<QuizQuestion>,
    val dirty: Boolean,
)

data class ParsedQuizBank(
    val name: String,
    val password: String?,
    val questions: List<ParsedQuizQuestion>,
)

data class ParsedQuizQuestion(
    val originalIndex: Int,
    val score: Int,
    val text: String,
    val options: List<QuizOption>,
    val answers: Set<String>,
)

data class QuizAttempt(
    val id: String,
    val bankId: String,
    val questionId: String,
    val answeredAt: Long,
    val selectedAnswers: Set<String>,
    val correct: Boolean,
    val scoreGained: Int,
)

data class WordReviewResult(
    val word: WordEntry,
    val quality: Int,
)

data class QuizSessionAnswer(
    val question: QuizQuestion,
    val selectedAnswers: Set<String>,
)

enum class WrongQuestionSource {
    QUIZ,
    CONTRAST,
}

data class WrongQuestionEntry(
    val id: String,
    val userId: String?,
    val source: WrongQuestionSource,
    val bankId: String?,
    val bankName: String,
    val questionKey: String,
    val questionText: String,
    val options: List<QuizOption>,
    val correctAnswers: Set<String>,
    val wrongCount: Int,
    val correctCount: Int,
    val favorite: Boolean,
    val aiAnalysis: String?,
    val lastWrongAt: Long,
    val lastReviewedAt: Long?,
    val dirty: Boolean,
) {
    val attemptCount: Int
        get() = wrongCount + correctCount

    val proficiencyPercent: Int
        get() = if (attemptCount == 0) 0 else (correctCount * 100f / attemptCount).toInt().coerceIn(0, 100)
}

data class WrongQuestionResult(
    val source: WrongQuestionSource,
    val bankId: String?,
    val bankName: String,
    val questionKey: String,
    val questionText: String,
    val options: List<QuizOption>,
    val correctAnswers: Set<String>,
    val correct: Boolean,
)

data class ContrastQuestionResult(
    val question: ContrastQuestion,
    val selectedIndex: Int?,
) {
    val correct: Boolean
        get() = selectedIndex == question.correctIndex
}

enum class WrongQuestionSort {
    LATEST,
    WRONG_COUNT,
    PROFICIENCY_LOW,
    PROFICIENCY_HIGH,
}

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String,
    val expiresAtEpochSeconds: Long,
)

data class SupabaseConfig(
    val url: String,
    val publishableKey: String,
)

enum class AiProvider {
    DEEPSEEK,
    OPENAI_COMPATIBLE,
}

data class AiSettings(
    val provider: AiProvider = AiProvider.DEEPSEEK,
    val baseUrl: String = "https://api.deepseek.com",
    val apiKey: String = "",
    val model: String = "deepseek-v4-flash",
    val systemPrompt: String = DEFAULT_AI_PROMPT,
    val analysisPrompt: String = DEFAULT_WRONG_QUESTION_ANALYSIS_PROMPT,
)

const val LEGACY_DEFAULT_AI_PROMPT = """你是单词速记应用中的英语学习助手。请使用简洁、准确的中文解释词义、常见搭配和易错点；需要生成练习时，必须基于用户提供的词库内容，不虚构单词数据。"""

const val DEFAULT_AI_PROMPT = """你是单词速记应用中的英语对照练习出题器。你只能使用用户提供的目标词和候选词生成单选题，不得虚构词条，不得修改目标词顺序。

响应必须是结构完整、可直接解析的 XML 原文。禁止输出 Markdown 代码块、解释、前言、结语或 JSON。XML 必须严格采用以下结构：
<?xml version="1.0" encoding="UTF-8"?>
<quiz>
    <question score="10">
        <text>题干</text>
        <option id="A">选项一</option>
        <option id="B">选项二</option>
        <answer>A</answer>
    </question>
</quiz>

每个目标词必须按输入顺序生成且只生成一道 question。每题只能有一个正确答案。option 的 id 必须从 A 开始连续排列，answer 必须填写正确选项的 id。选项数量必须符合用户指令并且不得重复。score 固定为 10。不要生成 password 字段。文本中的小于号、大于号、与号、引号和撇号必须按 XML 规则转义。"""

const val DEFAULT_WRONG_QUESTION_ANALYSIS_PROMPT = """请分析下面这道英语学习错题。输出中文纯文本，依次说明正确答案、核心知识点、常见误区和一条便于记忆的建议。保持简洁，不要使用 Markdown 表格。"""

enum class ContrastPracticeType {
    CHINESE_TO_ENGLISH,
    ENGLISH_DEFINITION_TO_ENGLISH,
    ENGLISH_TO_CHINESE,
}

enum class PracticeDifficulty {
    EASY,
    MEDIUM,
    HARD,
}

data class ContrastPracticePreset(
    val optionCount: Int,
    val questionCount: Int,
    val timeLimitSeconds: Int,
)

data class ContrastPracticePresets(
    val easy: ContrastPracticePreset = ContrastPracticePreset(4, 10, 30),
    val medium: ContrastPracticePreset = ContrastPracticePreset(6, 10, 20),
    val hard: ContrastPracticePreset = ContrastPracticePreset(8, 10, 12),
) {
    fun forDifficulty(difficulty: PracticeDifficulty): ContrastPracticePreset = when (difficulty) {
        PracticeDifficulty.EASY -> easy
        PracticeDifficulty.MEDIUM -> medium
        PracticeDifficulty.HARD -> hard
    }
}

enum class PracticeRangeMode {
    ALL,
    CATEGORY,
    PROFICIENCY,
    CUSTOM,
}

enum class ProficiencyBand {
    LOW,
    MEDIUM,
    HIGH,
}

data class ContrastQuestion(
    val id: String,
    val wordId: String,
    val prompt: String,
    val options: List<String>,
    val correctIndex: Int,
)

data class ContrastPracticeSession(
    val id: String,
    val completedAt: Long,
    val practiceType: ContrastPracticeType,
    val difficulty: PracticeDifficulty,
    val questionCount: Int,
    val correctCount: Int,
    val elapsedSeconds: Int,
    val hintEnabled: Boolean,
) {
    val accuracyPercent: Int
        get() = if (questionCount == 0) 0 else (correctCount * 100f / questionCount).toInt()
}

enum class DailyProgressReference {
    DICTATION,
    CONTRAST,
    CUSTOM_QUIZ,
}

data class DailyProgressSettings(
    val reference: DailyProgressReference = DailyProgressReference.DICTATION,
    val dictationTarget: Int = 50,
    val contrastTarget: Int = 20,
    val customQuizTarget: Int = 30,
) {
    fun targetFor(reference: DailyProgressReference = this.reference): Int = when (reference) {
        DailyProgressReference.DICTATION -> dictationTarget
        DailyProgressReference.CONTRAST -> contrastTarget
        DailyProgressReference.CUSTOM_QUIZ -> customQuizTarget
    }
}

data class DailyPracticeProgress(
    val dictationCompleted: Int = 0,
    val contrastCompleted: Int = 0,
    val customQuizCompleted: Int = 0,
    val customQuizCompletedByBank: Map<String, Int> = emptyMap(),
) {
    fun completedFor(reference: DailyProgressReference): Int = when (reference) {
        DailyProgressReference.DICTATION -> dictationCompleted
        DailyProgressReference.CONTRAST -> contrastCompleted
        DailyProgressReference.CUSTOM_QUIZ -> customQuizCompleted
    }
}

data class StudyTimeProgress(
    val elapsedMillis: Long = 0L,
    val goalMinutes: Int = 30,
) {
    val elapsedMinutes: Int
        get() = (elapsedMillis / 60_000L).toInt()

    val progressFraction: Float
        get() = if (goalMinutes <= 0) 0f else {
            (elapsedMillis.toFloat() / (goalMinutes * 60_000L)).coerceIn(0f, 1f)
        }

    val completed: Boolean
        get() = goalMinutes > 0 && elapsedMillis >= goalMinutes * 60_000L
}

enum class DailyMemoAction {
    COMPLETE,
    REVIEW,
}

enum class DailyMemoTarget {
    DICTATION,
    CONTRAST,
    QUIZ_BANK,
}

data class DailyMemoItem(
    val id: String,
    val action: DailyMemoAction,
    val target: DailyMemoTarget,
    val quizBankId: String? = null,
    val quizBankName: String? = null,
    val amount: Int? = null,
)

data class DailyMemoSettings(
    val items: List<DailyMemoItem> = emptyList(),
    val restDays: Set<Int> = emptySet(),
) {
    fun isRestDay(dayOfWeek: Int): Boolean = dayOfWeek in restDays
}

data class ReminderSettings(
    val matchingEnabled: Boolean = false,
    val reviewEnabled: Boolean = false,
    val questionEnabled: Boolean = false,
    val matchingQuestionTarget: Int = 20,
    val questionGroupCount: Int = 3,
    val questionsPerGroup: Int = 10,
    val reminderHour: Int = 8,
) {
    val anyEnabled: Boolean
        get() = matchingEnabled || reviewEnabled || questionEnabled
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class DictationMode {
    REVIEW,
    PRACTICE,
}

enum class ReviewCategory {
    WORDS,
    QUESTIONS,
    CONTRAST,
    WRONG_BOOK,
}

enum class QuizQueueMode {
    SEQUENTIAL,
    RANDOM,
}

enum class QueueSort {
    EARLIEST,
    LATEST,
    PROFICIENCY_LOW,
    PROFICIENCY_HIGH,
    RANDOM,
}

enum class SyncMode {
    ON_LOCAL_CHANGE,
    PERIODIC,
}

data class SyncSettings(
    val enabled: Boolean = true,
    val mode: SyncMode = SyncMode.PERIODIC,
    val intervalMinutes: Long = 15L,
)

data class SyncReport(
    val downloadedLogs: Int,
    val downloadedTitleLists: Int,
    val downloadedWrongQuestions: Int,
    val downloadedWords: Int,
    val uploadedLogs: Int,
    val uploadedTitleLists: Int,
    val uploadedWrongQuestions: Int,
    val uploadedWords: Int,
)
