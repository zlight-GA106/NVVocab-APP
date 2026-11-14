package com.zlight106.nvvocab.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import com.zlight106.nvvocab.data.local.NvvocabDatabase
import com.zlight106.nvvocab.domain.SessionTelemetryXmlWriter
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = Application::class)
class PosDatabaseTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun clearBefore() { context.deleteDatabase("nvvocab.db") }
    @After fun clearAfter() { context.deleteDatabase("nvvocab.db") }

    @Test fun version10UpgradesInPlaceAndOldWordsBecomeUnknown() {
        val old = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("nvvocab.db").apply { parentFile?.mkdirs() }, null)
        old.execSQL("""CREATE TABLE words (id TEXT PRIMARY KEY NOT NULL,user_id TEXT,spelling TEXT NOT NULL,phonetic TEXT,translation TEXT NOT NULL,book_tag TEXT NOT NULL,intro_time INTEGER NOT NULL,repetitions INTEGER NOT NULL DEFAULT 0,interval_days INTEGER NOT NULL DEFAULT 1,easiness REAL NOT NULL DEFAULT 2.5,next_review_at INTEGER NOT NULL,wrong_count INTEGER NOT NULL DEFAULT 0,dirty INTEGER NOT NULL DEFAULT 1)""")
        old.execSQL("""CREATE TABLE practice_attempts (id TEXT PRIMARY KEY NOT NULL,user_id TEXT,session_id TEXT NOT NULL,item_id TEXT NOT NULL,source_id TEXT,mode TEXT NOT NULL,sequence_index INTEGER NOT NULL,question TEXT NOT NULL,options_json TEXT NOT NULL DEFAULT '[]',first_answer TEXT NOT NULL DEFAULT '',final_answer TEXT NOT NULL DEFAULT '',reference_answer TEXT NOT NULL DEFAULT '',accepted_answers_json TEXT NOT NULL DEFAULT '[]',explanation TEXT,correct INTEGER NOT NULL,first_answer_correct INTEGER NOT NULL,active_time_ms INTEGER NOT NULL,hint_used INTEGER NOT NULL DEFAULT 0,answered_at INTEGER NOT NULL,dirty INTEGER NOT NULL DEFAULT 1)""")
        old.execSQL("INSERT INTO words (id,spelling,translation,book_tag,intro_time,next_review_at,repetitions,wrong_count) VALUES ('old','increase','增加','CET4',123,456,7,2)")
        old.version = 10; old.close()
        NvvocabDatabase(context).use { db ->
            val word = db.getWords().single()
            assertEquals(11, db.readableDatabase.version); assertEquals("old", word.id)
            assertEquals(7, word.repetitions); assertEquals(2, word.wrongCount)
            assertEquals(456L, word.nextReviewAt); assertEquals(setOf(PartOfSpeech.UNKNOWN), word.partsOfSpeech)
        }
    }

    @Test fun historicalWordAttemptsWrongBookAndTelemetryRoundTrip() {
        val word = WordEntry(
            id = "effective",
            userId = null,
            spelling = "effective",
            phonetic = null,
            translation = "有效",
            bookTag = "CET4",
            introTime = 1,
            repetitions = 0,
            intervalDays = 1,
            easiness = 2.5,
            nextReviewAt = 2,
            wrongCount = 0,
            dirty = true,
            partsOfSpeech = setOf(PartOfSpeech.ADJECTIVE),
            posNeedsReview = true,
            posExplanation = "an effective method",
        )
        val attempt = PracticeAttempt(
            id = "historical-attempt",
            userId = null,
            sessionId = "session",
            itemId = "pos-word:${word.id}",
            sourceId = word.bookTag,
            mode = PracticeAttemptMode.WORD_POS_CLASSIFICATION,
            sequenceIndex = 0,
            question = word.spelling,
            options = listOf(QuizOption("ADJECTIVE", "A / Adj 形容词"), QuizOption("ADVERB", "Adv 副词")),
            firstAnswer = "ADVERB",
            finalAnswer = "ADVERB",
            referenceAnswer = "ADJECTIVE",
            acceptedAnswers = setOf("ADJECTIVE"),
            explanation = word.posExplanation,
            correct = false,
            firstAnswerCorrect = false,
            activeTimeMs = 2500,
            hintUsed = false,
            timestamp = 3,
            dirty = true,
            actualPos = word.partsOfSpeech,
            selectedPos = setOf(PartOfSpeech.ADVERB),
            errorType = PosErrorType.POS_WRONG,
        )
        val unknown = attempt.copy(
            id = "historical-unknown", sequenceIndex = 1, firstAnswer = "UNKNOWN", finalAnswer = "UNKNOWN",
            selectedPos = setOf(PartOfSpeech.UNKNOWN),
        )
        val unanswered = attempt.copy(
            id = "historical-unanswered", sequenceIndex = 2, firstAnswer = "", finalAnswer = "", selectedPos = emptySet(),
        )
        val wrongQuestion = WrongQuestionResult(
            source = WrongQuestionSource.QUIZ,
            bankId = null,
            bankName = "词性速判",
            questionKey = attempt.itemId,
            questionText = attempt.question,
            options = attempt.options,
            correctAnswers = attempt.acceptedAnswers,
            correct = false,
            explanation = attempt.explanation,
            category = attempt.mode.name,
            sourceReference = attempt.sourceId,
            userAnswer = attempt.finalAnswer,
        )
        NvvocabDatabase(context).use { db ->
            db.insertWords(listOf(word))
            db.insertPracticeAttempts(listOf(attempt, unknown, unanswered))
            assertEquals(word, db.getWords().single())
            assertEquals(listOf(attempt, unknown, unanswered), db.getPracticeAttempts().sortedBy { it.sequenceIndex })
            db.recordWrongQuestionResults(listOf(wrongQuestion), null)
            assertEquals(1, db.getWrongQuestions().single().wrongCount)
            assertEquals(PracticeAttemptMode.WORD_POS_CLASSIFICATION.name, db.getWrongQuestions().single().category)
            val xml = ByteArrayOutputStream().also { SessionTelemetryXmlWriter.write("session", listOf(attempt), it, true) }
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.toByteArray().inputStream())
            assertEquals("ADJECTIVE", document.getElementsByTagName("actual_pos").item(0).textContent)
            assertEquals("ADVERB", document.getElementsByTagName("selected_pos").item(0).textContent)
            assertEquals("POS_WRONG", document.getElementsByTagName("error_type").item(0).textContent)
        }
    }
}
