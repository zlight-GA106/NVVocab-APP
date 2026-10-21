package com.zlight106.nvvocab.data

import android.app.Application
import android.content.Context
import com.zlight106.nvvocab.data.local.NvvocabDatabase
import com.zlight106.nvvocab.domain.PosPractice
import com.zlight106.nvvocab.ui.screens.PracticeSessionRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ObjectInputStream
import java.io.ObjectStreamClass
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = Application::class)
class PosCompatibilityTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun clearBefore() = clearStorage()
    @After fun clearAfter() = clearStorage()

    private fun clearStorage() {
        context.deleteDatabase("nvvocab.db")
        context.getSharedPreferences("nvvocab_preferences", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun version160PausedRequestLoadsWithOriginalUidAndSafeNewDefaults() {
        // Created with the original 1.6.0 compiled class, before adding the training flags.
        val bytes = Base64.getDecoder().decode(LEGACY_POS_REQUEST)
        val request = ObjectInputStream(bytes.inputStream()).use { it.readObject() as PracticeSessionRequest.Pos }
        assertTrue(request.queue.isEmpty())
        assertTrue(request.completeMode)
        assertEquals(5, request.timeLimitSeconds)
        assertEquals(setOf(2, 4), request.focusBlanks)
        assertFalse(request.slotFirst)
        assertFalse(request.foundation)
        assertNull(request.followUpCet4)
        assertEquals(-5488165408987117757L, ObjectStreamClass.lookup(PracticeSessionRequest.Pos::class.java).serialVersionUID)
    }

    @Test fun originalSettingsKeepCountAndPromptsAndUseNewSettingDefaults() {
        context.getSharedPreferences("nvvocab_preferences", Context.MODE_PRIVATE).edit()
            .putString("pos_settings", """{"count":37,"time":5,"complete":true,"annotation":"legacy annotation","context":"legacy context","slot":"legacy slot","cet4":"legacy cet4"}""")
            .commit()
        val original = AppPreferences(context).readPosSettings()
        assertEquals(37, original.questionCount)
        assertEquals(5, original.timeLimitSeconds)
        assertTrue(original.completeMode)
        assertEquals("legacy annotation", original.annotationPrompt)
        assertEquals("legacy context", original.contextPrompt)
        assertEquals("legacy slot", original.slotPrompt)
        assertEquals("legacy cet4", original.cet4Prompt)
        assertTrue(original.slotFirst)
        assertFalse(original.foundationIncludeCet4)

        val changed = original.copy(questionCount = 10, completeMode = false, slotFirst = false, foundationIncludeCet4 = true)
        AppPreferences(context).savePosSettings(changed)
        assertEquals(changed, AppPreferences(context).readPosSettings())
    }

    @Test fun sqliteVersion11PreservesUnknownSeparatelyFromUnprocessedAnswer() {
        val word = WordEntry("impact", null, "impact", null, "影响", "CET4", 1, 0, 1, 2.5, 2, 0, true,
            setOf(PartOfSpeech.NOUN, PartOfSpeech.VERB))
        val exercise = PosPractice.drill(word)
        val unknown = PosPractice.attempt(exercise, "compatibility-session", 0, setOf(PartOfSpeech.UNKNOWN), false, 1200)
        val unprocessed = PosPractice.attempt(exercise, "compatibility-session", 1, emptySet(), false, 1300)
        NvvocabDatabase(context).use { database ->
            database.insertWords(listOf(word))
            database.insertPracticeAttempts(listOf(unknown, unprocessed))
            assertEquals(11, database.readableDatabase.version)
            val saved = database.getPracticeAttempts().associateBy { it.sequenceIndex }
            assertEquals(unknown, saved.getValue(0))
            assertEquals(unprocessed, saved.getValue(1))
            assertEquals(setOf(PartOfSpeech.UNKNOWN), saved.getValue(0).selectedPos)
            assertTrue(saved.getValue(1).selectedPos.isEmpty())
            assertTrue(saved.values.all { it.mode == PracticeAttemptMode.WORD_POS_CLASSIFICATION && !it.correct })
            assertEquals(word, database.getWords().single())
            database.readableDatabase.rawQuery("SELECT selected_pos FROM practice_attempts ORDER BY sequence_index", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("UNKNOWN", cursor.getString(0))
                assertTrue(cursor.moveToNext())
                assertEquals("", cursor.getString(0))
            }
        }
    }

    companion object {
        private const val LEGACY_POS_REQUEST = "rO0ABXNyADtjb20uemxpZ2h0MTA2Lm52dm9jYWIudWkuc2NyZWVucy5QcmFjdGljZVNlc3Npb25SZXF1ZXN0JFBvc7PWHqNYvIdDAgAEWgAMY29tcGxldGVNb2RlSQAQdGltZUxpbWl0U2Vjb25kc0wAC2ZvY3VzQmxhbmtzdAAPTGphdmEvdXRpbC9TZXQ7TAAFcXVldWV0ABBMamF2YS91dGlsL0xpc3Q7eHABAAAABXNyABdqYXZhLnV0aWwuTGlua2VkSGFzaFNldNhs11qV3SoeAgAAeHIAEWphdmEudXRpbC5IYXNoU2V0ukSFlZa4tzQDAAB4cHcMAAAAED9AAAAAAAACc3IAEWphdmEubGFuZy5JbnRlZ2VyEuKgpPeBhzgCAAFJAAV2YWx1ZXhyABBqYXZhLmxhbmcuTnVtYmVyhqyVHQuU4IsCAAB4cAAAAAJzcQB+AAcAAAAEeHNyAB9qYXZhLnV0aWwuQ29sbGVjdGlvbnMkRW1wdHlMaXN0ergXtDynnt4CAAB4cA=="
    }
}
