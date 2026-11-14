package com.zlight106.nvvocab.data

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = Application::class)
class MixedReviewPreferencesTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val preferences get() = context.getSharedPreferences("nvvocab_preferences", Context.MODE_PRIVATE)

    @Before
    fun clearPreferences() {
        preferences.edit().clear().commit()
    }

    @Test
    fun retiredModeShareIsRedistributedAmongSelectedModes() {
        preferences.edit()
            .putStringSet("mixed_review_enabled_modes", setOf("DICTATION", "ENGLISH_TO_CHINESE", "POS"))
            .putInt("mixed_review_percentage_dictation", 50)
            .putInt("mixed_review_percentage_english_to_chinese", 30)
            .putInt("mixed_review_percentage_pos", 20)
            .commit()

        val appPreferences = AppPreferences(context)
        val migrated = appPreferences.readMixedReviewPreferences()
        assertEquals(setOf(MixedReviewMode.DICTATION, MixedReviewMode.ENGLISH_TO_CHINESE), migrated.enabledModes)
        assertEquals(63, migrated.modePercentages[MixedReviewMode.DICTATION])
        assertEquals(37, migrated.modePercentages[MixedReviewMode.ENGLISH_TO_CHINESE])

        appPreferences.saveMixedReviewPreferences(migrated)
        assertEquals(migrated, appPreferences.readMixedReviewPreferences())
    }

    @Test
    fun selectingOnlyRetiredModeRestoresAvailableDefaults() {
        preferences.edit()
            .putStringSet("mixed_review_enabled_modes", setOf("POS"))
            .putInt("mixed_review_percentage_dictation", 80)
            .putInt("mixed_review_percentage_pos", 100)
            .commit()

        val migrated = AppPreferences(context).readMixedReviewPreferences()
        assertEquals(MixedReviewMode.availableModes.toSet(), migrated.enabledModes)
        assertEquals(defaultMixedModePercentages(), migrated.modePercentages)
    }
}
