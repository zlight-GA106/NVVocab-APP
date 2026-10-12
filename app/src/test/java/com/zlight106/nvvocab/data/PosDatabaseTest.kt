package com.zlight106.nvvocab.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import com.zlight106.nvvocab.data.local.NvvocabDatabase
import com.zlight106.nvvocab.domain.*
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
            db.updateWordPos(word.id, setOf(PartOfSpeech.NOUN, PartOfSpeech.VERB), false, "price increase")
            assertEquals(setOf(PartOfSpeech.NOUN, PartOfSpeech.VERB), db.getWords().single().partsOfSpeech)
        }
    }

    @Test fun attemptsWrongBookCacheAndTelemetryRoundTrip() {
        val word = WordEntry("effective", null, "effective", null, "有效", "CET4", 1, 0, 1, 2.5, 2, 0, true, setOf(PartOfSpeech.ADJECTIVE))
        val exercise = PosPractice.drill(word)
        val attempt = PosPractice.attempt(exercise, "session", 0, setOf(PartOfSpeech.ADVERB), false, 2500)
        NvvocabDatabase(context).use { db ->
            db.insertWords(listOf(word)); db.insertPracticeAttempts(listOf(attempt))
            assertEquals(attempt, db.getPracticeAttempts().single())
            db.recordWrongQuestionResults(listOf(PosPractice.wrongResult(attempt)), null)
            assertEquals(1, db.getWrongQuestions().single().wrongCount)
            assertEquals(PracticeAttemptMode.WORD_POS_CLASSIFICATION.name, db.getWrongQuestions().single().category)
            db.savePosExercises(listOf(exercise)); assertEquals(exercise, db.getPosExercises().single())
            val xml = ByteArrayOutputStream().also { SessionTelemetryXmlWriter.write("session", listOf(attempt), it, true) }
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.toByteArray().inputStream())
            assertEquals("ADJECTIVE", document.getElementsByTagName("actual_pos").item(0).textContent)
            assertEquals("ADVERB", document.getElementsByTagName("selected_pos").item(0).textContent)
            assertEquals("POS_WRONG", document.getElementsByTagName("error_type").item(0).textContent)
        }
    }
}
