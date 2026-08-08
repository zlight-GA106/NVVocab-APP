package com.zlight106.nvvocab.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.zlight106.nvvocab.data.ContrastPracticeSession
import com.zlight106.nvvocab.data.ContrastPracticeType
import com.zlight106.nvvocab.data.DailyPracticeProgress
import com.zlight106.nvvocab.data.ParsedQuizBank
import com.zlight106.nvvocab.data.PracticeDifficulty
import com.zlight106.nvvocab.data.QuizAttempt
import com.zlight106.nvvocab.data.QuizBank
import com.zlight106.nvvocab.data.QuizOption
import com.zlight106.nvvocab.data.QuizQuestion
import com.zlight106.nvvocab.data.QuizSource
import com.zlight106.nvvocab.data.ReviewLogEntry
import com.zlight106.nvvocab.data.TitleListEntry
import com.zlight106.nvvocab.data.WordEntry
import com.zlight106.nvvocab.domain.ReviewCadenceState
import java.io.FileInputStream
import java.io.OutputStream
import java.util.UUID

class NvvocabDatabase(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {
    override fun onConfigure(database: SQLiteDatabase) {
        super.onConfigure(database)
        database.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE words (
                id TEXT PRIMARY KEY NOT NULL,
                user_id TEXT,
                spelling TEXT NOT NULL,
                phonetic TEXT,
                translation TEXT NOT NULL,
                book_tag TEXT NOT NULL,
                intro_time INTEGER NOT NULL,
                repetitions INTEGER NOT NULL DEFAULT 0,
                interval_days INTEGER NOT NULL DEFAULT 1,
                easiness REAL NOT NULL DEFAULT 2.5,
                next_review_at INTEGER NOT NULL,
                wrong_count INTEGER NOT NULL DEFAULT 0,
                dirty INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE review_logs (
                id TEXT PRIMARY KEY NOT NULL,
                user_id TEXT,
                word_id TEXT NOT NULL,
                reviewed_at INTEGER NOT NULL,
                quality INTEGER NOT NULL,
                dirty INTEGER NOT NULL DEFAULT 1,
                FOREIGN KEY(word_id) REFERENCES words(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        database.execSQL("CREATE INDEX idx_words_intro_time ON words(intro_time ASC)")
        database.execSQL("CREATE INDEX idx_words_due ON words(next_review_at ASC)")
        database.execSQL("CREATE INDEX idx_words_tag ON words(book_tag)")
        database.execSQL("CREATE INDEX idx_review_logs_dirty ON review_logs(dirty)")
        createQuizTables(database)
        createDeletedTitleListsTable(database)
        createContrastPracticeTables(database)
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createQuizTables(database)
        if (oldVersion < 3) createContrastPracticeTables(database)
        if (oldVersion < 4) {
            database.execSQL("ALTER TABLE quiz_banks ADD COLUMN user_id TEXT")
            database.execSQL("ALTER TABLE quiz_banks ADD COLUMN source TEXT NOT NULL DEFAULT 'XML'")
            database.execSQL("ALTER TABLE quiz_banks ADD COLUMN practice_type TEXT")
            database.execSQL("ALTER TABLE quiz_banks ADD COLUMN difficulty TEXT")
            database.execSQL("ALTER TABLE quiz_banks ADD COLUMN dirty INTEGER NOT NULL DEFAULT 1")
            database.execSQL("CREATE INDEX IF NOT EXISTS idx_quiz_banks_dirty ON quiz_banks(dirty)")
        }
        if (oldVersion < 5) createDeletedTitleListsTable(database)
    }

    fun getWords(): List<WordEntry> = readableDatabase.query(
        TABLE_WORDS,
        WORD_COLUMNS,
        null,
        null,
        null,
        null,
        "intro_time ASC, id ASC",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(cursor.toWordEntry())
        }
    }

    fun getBookTags(): List<String> = readableDatabase.rawQuery(
        "SELECT DISTINCT book_tag FROM words ORDER BY book_tag COLLATE NOCASE ASC",
        null,
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                cursor.getString(0)?.trim()?.takeIf(String::isNotEmpty)?.let(::add)
            }
        }
    }

    fun insertWords(words: List<WordEntry>) {
        writableDatabase.inTransaction {
            words.forEach { word ->
                insertWithOnConflict(
                    TABLE_WORDS,
                    null,
                    word.toContentValues(),
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
        }
    }

    fun updateWordTag(wordId: String, bookTag: String) {
        writableDatabase.update(
            TABLE_WORDS,
            ContentValues().apply {
                put("book_tag", bookTag)
                put("dirty", 1)
            },
            "id = ?",
            arrayOf(wordId),
        )
    }

    fun applyReview(
        word: WordEntry,
        state: ReviewCadenceState,
        quality: Int,
        userId: String?,
        reviewedAt: Long,
    ) {
        writableDatabase.inTransaction {
            val wordValues = ContentValues().apply {
                put("user_id", userId ?: word.userId)
                put("repetitions", state.repetitions)
                put("interval_days", state.intervalDays)
                put("next_review_at", state.nextReviewAt)
                put("wrong_count", state.wrongCount)
                put("dirty", 1)
            }
            update(TABLE_WORDS, wordValues, "id = ?", arrayOf(word.id))

            val log = ReviewLogEntry(
                id = UUID.randomUUID().toString(),
                userId = userId ?: word.userId,
                wordId = word.id,
                reviewedAt = reviewedAt,
                quality = quality,
                dirty = true,
            )
            insertOrThrow(TABLE_REVIEW_LOGS, null, log.toContentValues())
        }
    }

    fun getDirtyWords(): List<WordEntry> = queryDirtyWords()

    fun getReviewLogs(): List<ReviewLogEntry> = readableDatabase.query(
        TABLE_REVIEW_LOGS,
        REVIEW_LOG_COLUMNS,
        null,
        null,
        null,
        null,
        "reviewed_at ASC",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(cursor.toReviewLogEntry())
        }
    }

    fun getDailyPracticeProgress(dayStart: Long): DailyPracticeProgress {
        val arguments = arrayOf(dayStart.toString())
        return DailyPracticeProgress(
            dictationCompleted = queryScalarInt(
                // Count completed review actions, including a repeated word. Memo targets
                // describe how many times/items must be finished before strike-through.
                "SELECT COUNT(*) FROM review_logs WHERE reviewed_at >= ? AND quality >= 3",
                arguments,
            ),
            contrastCompleted = queryScalarInt(
                "SELECT COALESCE(SUM(question_count), 0) FROM contrast_practice_sessions WHERE completed_at >= ?",
                arguments,
            ),
            customQuizCompleted = queryScalarInt(
                "SELECT COUNT(*) FROM quiz_attempts WHERE answered_at >= ?",
                arguments,
            ),
            customQuizCompletedByBank = readableDatabase.rawQuery(
                "SELECT bank_id, COUNT(*) FROM quiz_attempts WHERE answered_at >= ? GROUP BY bank_id",
                arguments,
            ).use { cursor ->
                buildMap {
                    while (cursor.moveToNext()) put(cursor.getString(0), cursor.getInt(1))
                }
            },
        )
    }

    fun getDirtyReviewLogs(): List<ReviewLogEntry> = readableDatabase.query(
        TABLE_REVIEW_LOGS,
        REVIEW_LOG_COLUMNS,
        "dirty = 1",
        null,
        null,
        null,
        "reviewed_at ASC",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(cursor.toReviewLogEntry())
        }
    }

    fun upsertRemoteReviewLogs(logs: List<ReviewLogEntry>) {
        if (logs.isEmpty()) return
        writableDatabase.inTransaction {
            logs.forEach { log ->
                // Review rows are immutable events. Keep an existing local row (including
                // a dirty row waiting to upload) and only add events missing on this device.
                insertWithOnConflict(
                    TABLE_REVIEW_LOGS,
                    null,
                    log.copy(dirty = false).toContentValues(),
                    SQLiteDatabase.CONFLICT_IGNORE,
                )
            }
        }
    }

    fun assignUnsyncedRowsToUser(userId: String) {
        val values = ContentValues().apply { put("user_id", userId) }
        writableDatabase.inTransaction {
            update(TABLE_WORDS, values, "dirty = 1 AND (user_id IS NULL OR user_id = '')", null)
            update(TABLE_REVIEW_LOGS, values, "dirty = 1 AND (user_id IS NULL OR user_id = '')", null)
            update(TABLE_QUIZ_BANKS, values, "dirty = 1 AND (user_id IS NULL OR user_id = '')", null)
            update(TABLE_DELETED_TITLELISTS, values, "user_id IS NULL OR user_id = ''", null)
        }
    }

    fun markWordsClean(ids: List<String>) {
        if (ids.isEmpty()) return
        writableDatabase.inTransaction {
            val values = ContentValues().apply { put("dirty", 0) }
            ids.forEach { id -> update(TABLE_WORDS, values, "id = ?", arrayOf(id)) }
        }
    }

    fun markReviewLogsClean(ids: List<String>) {
        if (ids.isEmpty()) return
        writableDatabase.inTransaction {
            val values = ContentValues().apply { put("dirty", 0) }
            ids.forEach { id -> update(TABLE_REVIEW_LOGS, values, "id = ?", arrayOf(id)) }
        }
    }

    fun markTitleListsClean(ids: List<String>) {
        if (ids.isEmpty()) return
        writableDatabase.inTransaction {
            val values = ContentValues().apply { put("dirty", 0) }
            ids.forEach { id -> update(TABLE_QUIZ_BANKS, values, "id = ?", arrayOf(id)) }
        }
    }

    fun upsertRemoteWords(words: List<WordEntry>) {
        val dirtyIds = queryDirtyWords().mapTo(mutableSetOf(), WordEntry::id)
        writableDatabase.inTransaction {
            words.filterNot { it.id in dirtyIds }.forEach { word ->
                insertWithOnConflict(
                    TABLE_WORDS,
                    null,
                    word.copy(dirty = false).toContentValues(),
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
        }
    }

    fun getQuizBanks(): List<QuizBank> = readableDatabase.rawQuery(
        """
        SELECT b.id, b.name, b.password, b.imported_at, COUNT(q.id) AS question_count
        FROM quiz_banks b
        LEFT JOIN quiz_questions q ON q.bank_id = b.id
        GROUP BY b.id
        ORDER BY b.imported_at DESC, b.name COLLATE NOCASE ASC
        """.trimIndent(),
        null,
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    QuizBank(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        password = cursor.getStringOrNull("password"),
                        importedAt = cursor.getLong(cursor.getColumnIndexOrThrow("imported_at")),
                        questionCount = cursor.getInt(cursor.getColumnIndexOrThrow("question_count")),
                    ),
                )
            }
        }
    }

    fun replaceQuizBank(
        parsedBank: ParsedQuizBank,
        userId: String? = null,
        source: QuizSource = QuizSource.XML,
        practiceType: ContrastPracticeType? = null,
        difficulty: PracticeDifficulty? = null,
    ): QuizBank {
        val bankId = UUID.randomUUID().toString()
        val importedAt = System.currentTimeMillis()
        writableDatabase.inTransaction {
            delete(TABLE_QUIZ_BANKS, "name = ?", arrayOf(parsedBank.name))
            insertOrThrow(
                TABLE_QUIZ_BANKS,
                null,
                ContentValues().apply {
                    put("id", bankId)
                    put("name", parsedBank.name)
                    put("password", parsedBank.password)
                    put("imported_at", importedAt)
                    put("user_id", userId)
                    put("source", source.name)
                    put("practice_type", practiceType?.name)
                    put("difficulty", difficulty?.name)
                    put("dirty", 1)
                },
            )
            parsedBank.questions.forEach { parsedQuestion ->
                val questionId = UUID.randomUUID().toString()
                insertOrThrow(
                    TABLE_QUIZ_QUESTIONS,
                    null,
                    ContentValues().apply {
                        put("id", questionId)
                        put("bank_id", bankId)
                        put("original_index", parsedQuestion.originalIndex)
                        put("score", parsedQuestion.score)
                        put("text", parsedQuestion.text)
                    },
                )
                parsedQuestion.options.forEach { option ->
                    insertOrThrow(
                        TABLE_QUIZ_OPTIONS,
                        null,
                        ContentValues().apply {
                            put("question_id", questionId)
                            put("option_id", option.id)
                            put("text", option.text)
                        },
                    )
                }
                parsedQuestion.answers.forEach { answer ->
                    insertOrThrow(
                        TABLE_QUIZ_ANSWERS,
                        null,
                        ContentValues().apply {
                            put("question_id", questionId)
                            put("option_id", answer)
                        },
                    )
                }
            }
        }
        return QuizBank(
            id = bankId,
            name = parsedBank.name,
            password = parsedBank.password,
            importedAt = importedAt,
            questionCount = parsedBank.questions.size,
        )
    }

    fun getDirtyTitleLists(): List<TitleListEntry> = getTitleLists(dirtyOnly = true)

    fun getTitleLists(dirtyOnly: Boolean = false): List<TitleListEntry> {
        val selection = if (dirtyOnly) "dirty = 1" else null
        return readableDatabase.query(
            TABLE_QUIZ_BANKS,
            arrayOf("id", "user_id", "name", "source", "practice_type", "difficulty", "imported_at", "dirty"),
            selection,
            null,
            null,
            null,
            "imported_at ASC, id ASC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val bankId = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                    add(
                        TitleListEntry(
                            id = bankId,
                            userId = cursor.getStringOrNull("user_id"),
                            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                            source = cursor.enumOrDefault("source", QuizSource.XML),
                            practiceType = cursor.enumOrNull<ContrastPracticeType>("practice_type"),
                            difficulty = cursor.enumOrNull<PracticeDifficulty>("difficulty"),
                            importedAt = cursor.getLong(cursor.getColumnIndexOrThrow("imported_at")),
                            questions = getQuizQuestions(bankId),
                            dirty = cursor.getInt(cursor.getColumnIndexOrThrow("dirty")) == 1,
                        ),
                    )
                }
            }
        }
    }

    fun upsertRemoteTitleLists(entries: List<TitleListEntry>) {
        if (entries.isEmpty()) return
        val dirtyEntries = getDirtyTitleLists()
        val dirtyIds = dirtyEntries.mapTo(mutableSetOf(), TitleListEntry::id)
        val dirtyNames = dirtyEntries.mapTo(mutableSetOf(), TitleListEntry::name)
        val deletedIds = getDeletedTitleListIds().toSet()
        writableDatabase.inTransaction {
            entries.filterNot { entry ->
                entry.id in dirtyIds || entry.id in deletedIds || entry.name in dirtyNames
            }.forEach { entry ->
                delete(TABLE_QUIZ_BANKS, "id = ? OR name = ?", arrayOf(entry.id, entry.name))
                insertOrThrow(
                    TABLE_QUIZ_BANKS,
                    null,
                    ContentValues().apply {
                        put("id", entry.id)
                        put("user_id", entry.userId)
                        put("name", entry.name)
                        putNull("password")
                        put("source", entry.source.name)
                        put("practice_type", entry.practiceType?.name)
                        put("difficulty", entry.difficulty?.name)
                        put("imported_at", entry.importedAt)
                        put("dirty", 0)
                    },
                )
                entry.questions.forEach { question -> insertQuizQuestion(question.copy(bankId = entry.id)) }
            }
        }
    }

    fun getQuizQuestions(bankId: String): List<QuizQuestion> {
        val optionsByQuestion = mutableMapOf<String, MutableList<QuizOption>>()
        readableDatabase.rawQuery(
            """
            SELECT o.question_id, o.option_id, o.text
            FROM quiz_options o
            INNER JOIN quiz_questions q ON q.id = o.question_id
            WHERE q.bank_id = ?
            ORDER BY q.original_index ASC, o.rowid ASC
            """.trimIndent(),
            arrayOf(bankId),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val questionId = cursor.getString(0)
                optionsByQuestion.getOrPut(questionId, ::mutableListOf).add(
                    QuizOption(id = cursor.getString(1), text = cursor.getString(2)),
                )
            }
        }
        val answersByQuestion = mutableMapOf<String, MutableSet<String>>()
        readableDatabase.rawQuery(
            """
            SELECT a.question_id, a.option_id
            FROM quiz_answers a
            INNER JOIN quiz_questions q ON q.id = a.question_id
            WHERE q.bank_id = ?
            ORDER BY a.rowid ASC
            """.trimIndent(),
            arrayOf(bankId),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                answersByQuestion.getOrPut(cursor.getString(0), ::linkedSetOf).add(cursor.getString(1))
            }
        }
        return readableDatabase.query(
            TABLE_QUIZ_QUESTIONS,
            arrayOf("id", "bank_id", "original_index", "score", "text"),
            "bank_id = ?",
            arrayOf(bankId),
            null,
            null,
            "original_index ASC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val questionId = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                    add(
                        QuizQuestion(
                            id = questionId,
                            bankId = cursor.getString(cursor.getColumnIndexOrThrow("bank_id")),
                            originalIndex = cursor.getInt(cursor.getColumnIndexOrThrow("original_index")),
                            score = cursor.getInt(cursor.getColumnIndexOrThrow("score")),
                            text = cursor.getString(cursor.getColumnIndexOrThrow("text")),
                            options = optionsByQuestion[questionId].orEmpty(),
                            answers = answersByQuestion[questionId].orEmpty(),
                        ),
                    )
                }
            }
        }
    }

    fun insertQuizAttempt(attempt: QuizAttempt) {
        writableDatabase.insertOrThrow(
            TABLE_QUIZ_ATTEMPTS,
            null,
            ContentValues().apply {
                put("id", attempt.id)
                put("bank_id", attempt.bankId)
                put("question_id", attempt.questionId)
                put("answered_at", attempt.answeredAt)
                put("selected_answers", attempt.selectedAnswers.sorted().joinToString(","))
                put("correct", if (attempt.correct) 1 else 0)
                put("score_gained", attempt.scoreGained)
            },
        )
    }

    fun renameQuizBank(bankId: String, name: String) {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "题库名称不能为空。" }
        val updated = writableDatabase.update(
            TABLE_QUIZ_BANKS,
            ContentValues().apply {
                put("name", normalizedName)
                put("dirty", 1)
            },
            "id = ?",
            arrayOf(bankId),
        )
        require(updated == 1) { "未找到需要重命名的题库。" }
    }

    fun deleteQuizBank(bankId: String, userId: String?) {
        writableDatabase.inTransaction {
            insertWithOnConflict(
                TABLE_DELETED_TITLELISTS,
                null,
                ContentValues().apply {
                    put("id", bankId)
                    put("user_id", userId)
                    put("deleted_at", System.currentTimeMillis())
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            delete(TABLE_QUIZ_BANKS, "id = ?", arrayOf(bankId))
        }
    }

    fun getDeletedTitleListIds(): List<String> = readableDatabase.query(
        TABLE_DELETED_TITLELISTS,
        arrayOf("id"),
        null,
        null,
        null,
        null,
        "deleted_at ASC",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(cursor.getString(0))
        }
    }

    fun clearDeletedTitleLists(ids: List<String>) {
        if (ids.isEmpty()) return
        writableDatabase.inTransaction {
            ids.forEach { id -> delete(TABLE_DELETED_TITLELISTS, "id = ?", arrayOf(id)) }
        }
    }

    fun insertContrastPracticeSession(session: ContrastPracticeSession) {
        writableDatabase.insertOrThrow(
            TABLE_CONTRAST_SESSIONS,
            null,
            ContentValues().apply {
                put("id", session.id)
                put("completed_at", session.completedAt)
                put("practice_type", session.practiceType.name)
                put("difficulty", session.difficulty.name)
                put("question_count", session.questionCount)
                put("correct_count", session.correctCount)
                put("elapsed_seconds", session.elapsedSeconds)
                put("hint_enabled", if (session.hintEnabled) 1 else 0)
            },
        )
    }

    fun getRecentContrastPracticeSessions(limit: Int = 5): List<ContrastPracticeSession> =
        readableDatabase.query(
            TABLE_CONTRAST_SESSIONS,
            arrayOf(
                "id",
                "completed_at",
                "practice_type",
                "difficulty",
                "question_count",
                "correct_count",
                "elapsed_seconds",
                "hint_enabled",
            ),
            null,
            null,
            null,
            null,
            "completed_at DESC",
            limit.coerceIn(1, 100).toString(),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ContrastPracticeSession(
                            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                            completedAt = cursor.getLong(cursor.getColumnIndexOrThrow("completed_at")),
                            practiceType = ContrastPracticeType.valueOf(
                                cursor.getString(cursor.getColumnIndexOrThrow("practice_type")),
                            ),
                            difficulty = PracticeDifficulty.valueOf(
                                cursor.getString(cursor.getColumnIndexOrThrow("difficulty")),
                            ),
                            questionCount = cursor.getInt(cursor.getColumnIndexOrThrow("question_count")),
                            correctCount = cursor.getInt(cursor.getColumnIndexOrThrow("correct_count")),
                            elapsedSeconds = cursor.getInt(cursor.getColumnIndexOrThrow("elapsed_seconds")),
                            hintEnabled = cursor.getInt(cursor.getColumnIndexOrThrow("hint_enabled")) == 1,
                        ),
                    )
                }
            }
        }

    fun exportDatabase(output: OutputStream) {
        writableDatabase.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0)
        }
        FileInputStream(readableDatabase.path).use { input -> input.copyTo(output) }
    }

    private fun queryDirtyWords(): List<WordEntry> = readableDatabase.query(
        TABLE_WORDS,
        WORD_COLUMNS,
        "dirty = 1",
        null,
        null,
        null,
        "intro_time ASC",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(cursor.toWordEntry())
        }
    }

    private fun WordEntry.toContentValues(): ContentValues = ContentValues().apply {
        put("id", id)
        put("user_id", userId)
        put("spelling", spelling)
        put("phonetic", phonetic)
        put("translation", translation)
        put("book_tag", bookTag)
        put("intro_time", introTime)
        put("repetitions", repetitions)
        put("interval_days", intervalDays)
        put("easiness", easiness)
        put("next_review_at", nextReviewAt)
        put("wrong_count", wrongCount)
        put("dirty", if (dirty) 1 else 0)
    }

    private fun ReviewLogEntry.toContentValues(): ContentValues = ContentValues().apply {
        put("id", id)
        put("user_id", userId)
        put("word_id", wordId)
        put("reviewed_at", reviewedAt)
        put("quality", quality)
        put("dirty", if (dirty) 1 else 0)
    }

    private fun Cursor.toWordEntry(): WordEntry = WordEntry(
        id = getString(getColumnIndexOrThrow("id")),
        userId = getStringOrNull("user_id"),
        spelling = getString(getColumnIndexOrThrow("spelling")),
        phonetic = getStringOrNull("phonetic"),
        translation = getString(getColumnIndexOrThrow("translation")),
        bookTag = getString(getColumnIndexOrThrow("book_tag")),
        introTime = getLong(getColumnIndexOrThrow("intro_time")),
        repetitions = getInt(getColumnIndexOrThrow("repetitions")),
        intervalDays = getInt(getColumnIndexOrThrow("interval_days")),
        easiness = getDouble(getColumnIndexOrThrow("easiness")),
        nextReviewAt = getLong(getColumnIndexOrThrow("next_review_at")),
        wrongCount = getInt(getColumnIndexOrThrow("wrong_count")),
        dirty = getInt(getColumnIndexOrThrow("dirty")) == 1,
    )

    private fun Cursor.toReviewLogEntry(): ReviewLogEntry = ReviewLogEntry(
        id = getString(getColumnIndexOrThrow("id")),
        userId = getStringOrNull("user_id"),
        wordId = getString(getColumnIndexOrThrow("word_id")),
        reviewedAt = getLong(getColumnIndexOrThrow("reviewed_at")),
        quality = getInt(getColumnIndexOrThrow("quality")),
        dirty = getInt(getColumnIndexOrThrow("dirty")) == 1,
    )

    private fun Cursor.getStringOrNull(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }

    private inline fun <reified T : Enum<T>> Cursor.enumOrNull(column: String): T? =
        getStringOrNull(column)?.let { value -> runCatching { enumValueOf<T>(value) }.getOrNull() }

    private inline fun <reified T : Enum<T>> Cursor.enumOrDefault(column: String, fallback: T): T =
        enumOrNull<T>(column) ?: fallback

    private fun SQLiteDatabase.insertQuizQuestion(question: QuizQuestion) {
        insertOrThrow(
            TABLE_QUIZ_QUESTIONS,
            null,
            ContentValues().apply {
                put("id", question.id)
                put("bank_id", question.bankId)
                put("original_index", question.originalIndex)
                put("score", question.score)
                put("text", question.text)
            },
        )
        question.options.forEach { option ->
            insertOrThrow(
                TABLE_QUIZ_OPTIONS,
                null,
                ContentValues().apply {
                    put("question_id", question.id)
                    put("option_id", option.id)
                    put("text", option.text)
                },
            )
        }
        question.answers.forEach { answer ->
            insertOrThrow(
                TABLE_QUIZ_ANSWERS,
                null,
                ContentValues().apply {
                    put("question_id", question.id)
                    put("option_id", answer)
                },
            )
        }
    }

    private fun queryScalarInt(sql: String, arguments: Array<String>): Int =
        readableDatabase.rawQuery(sql, arguments).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    private fun createQuizTables(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS quiz_banks (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL UNIQUE,
                password TEXT,
                imported_at INTEGER NOT NULL,
                user_id TEXT,
                source TEXT NOT NULL DEFAULT 'XML',
                practice_type TEXT,
                difficulty TEXT,
                dirty INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS quiz_questions (
                id TEXT PRIMARY KEY NOT NULL,
                bank_id TEXT NOT NULL,
                original_index INTEGER NOT NULL,
                score INTEGER NOT NULL DEFAULT 0,
                text TEXT NOT NULL,
                FOREIGN KEY(bank_id) REFERENCES quiz_banks(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS quiz_options (
                question_id TEXT NOT NULL,
                option_id TEXT NOT NULL,
                text TEXT NOT NULL,
                PRIMARY KEY(question_id, option_id),
                FOREIGN KEY(question_id) REFERENCES quiz_questions(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS quiz_answers (
                question_id TEXT NOT NULL,
                option_id TEXT NOT NULL,
                PRIMARY KEY(question_id, option_id),
                FOREIGN KEY(question_id) REFERENCES quiz_questions(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS quiz_attempts (
                id TEXT PRIMARY KEY NOT NULL,
                bank_id TEXT NOT NULL,
                question_id TEXT NOT NULL,
                answered_at INTEGER NOT NULL,
                selected_answers TEXT NOT NULL,
                correct INTEGER NOT NULL,
                score_gained INTEGER NOT NULL,
                FOREIGN KEY(bank_id) REFERENCES quiz_banks(id) ON DELETE CASCADE,
                FOREIGN KEY(question_id) REFERENCES quiz_questions(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_quiz_questions_bank ON quiz_questions(bank_id, original_index)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_quiz_attempts_time ON quiz_attempts(answered_at DESC)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_quiz_banks_dirty ON quiz_banks(dirty)")
    }

    private fun createContrastPracticeTables(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS contrast_practice_sessions (
                id TEXT PRIMARY KEY NOT NULL,
                completed_at INTEGER NOT NULL,
                practice_type TEXT NOT NULL,
                difficulty TEXT NOT NULL,
                question_count INTEGER NOT NULL,
                correct_count INTEGER NOT NULL,
                elapsed_seconds INTEGER NOT NULL,
                hint_enabled INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_contrast_sessions_time ON contrast_practice_sessions(completed_at DESC)",
        )
    }

    private fun createDeletedTitleListsTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS deleted_titlelists (
                id TEXT PRIMARY KEY NOT NULL,
                user_id TEXT,
                deleted_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    private inline fun SQLiteDatabase.inTransaction(block: SQLiteDatabase.() -> Unit) {
        beginTransaction()
        try {
            block()
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }

    private companion object {
        const val DATABASE_NAME = "nvvocab.db"
        const val DATABASE_VERSION = 5
        const val TABLE_DELETED_TITLELISTS = "deleted_titlelists"
        const val TABLE_CONTRAST_SESSIONS = "contrast_practice_sessions"
        const val TABLE_QUIZ_ANSWERS = "quiz_answers"
        const val TABLE_QUIZ_ATTEMPTS = "quiz_attempts"
        const val TABLE_QUIZ_BANKS = "quiz_banks"
        const val TABLE_QUIZ_OPTIONS = "quiz_options"
        const val TABLE_QUIZ_QUESTIONS = "quiz_questions"
        const val TABLE_REVIEW_LOGS = "review_logs"
        const val TABLE_WORDS = "words"

        val WORD_COLUMNS = arrayOf(
            "id",
            "user_id",
            "spelling",
            "phonetic",
            "translation",
            "book_tag",
            "intro_time",
            "repetitions",
            "interval_days",
            "easiness",
            "next_review_at",
            "wrong_count",
            "dirty",
        )
        val REVIEW_LOG_COLUMNS = arrayOf(
            "id",
            "user_id",
            "word_id",
            "reviewed_at",
            "quality",
            "dirty",
        )
    }
}
