package com.zlight106.nvvocab

import android.app.Application
import com.zlight106.nvvocab.data.AppPreferences
import com.zlight106.nvvocab.data.local.NvvocabDatabase
import com.zlight106.nvvocab.data.network.AiPracticeGateway
import com.zlight106.nvvocab.data.network.SupabaseGateway
import com.zlight106.nvvocab.data.repository.VocabularyRepository
import com.zlight106.nvvocab.data.StudyTimeTracker
import com.zlight106.nvvocab.sync.SyncScheduler
import com.zlight106.nvvocab.sync.ReviewNotificationScheduler
import com.zlight106.nvvocab.widget.DailyMemoWidgetUpdater
import com.zlight106.nvvocab.widget.DailyWidgetResetScheduler

class NvvocabApplication : Application() {
    lateinit var preferences: AppPreferences
        private set
    lateinit var repository: VocabularyRepository
        private set
    lateinit var studyTimeTracker: StudyTimeTracker
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = AppPreferences(this)
        studyTimeTracker = StudyTimeTracker(preferences) {
            DailyMemoWidgetUpdater.updateAll(this)
        }
        repository = VocabularyRepository(
            database = NvvocabDatabase(this),
            preferences = preferences,
            gateway = SupabaseGateway(),
            aiPracticeGateway = AiPracticeGateway(),
            onLocalDataChanged = {
                SyncScheduler.runAfterLocalChange(this, preferences.readSyncSettings())
                DailyMemoWidgetUpdater.updateAll(this)
            },
        )
        SyncScheduler.configure(this, preferences.readSyncSettings())
        ReviewNotificationScheduler.configure(this, preferences.readReminderSettings())
        DailyMemoWidgetUpdater.updateAll(this)
        DailyWidgetResetScheduler.scheduleNext(this)
    }
}
