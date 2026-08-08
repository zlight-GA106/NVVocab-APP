package com.zlight106.nvvocab.data

import android.os.SystemClock
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Tracks foreground study time and persists it per local calendar day. */
class StudyTimeTracker(
    private val preferences: AppPreferences,
    private val onPersistedProgressChanged: () -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var activeDate = LocalDate.now()
    private var storedMillis = preferences.readStudyTimeTodayMillis(activeDate)
    private var sessionStartedAt: Long? = null
    private var ticker: Job? = null
    private var lastPersistedSecond = storedMillis / 1_000L
    private val mutableProgress = MutableStateFlow(snapshot())

    val progress: StateFlow<StudyTimeProgress> = mutableProgress.asStateFlow()

    fun start() {
        if (sessionStartedAt != null) return
        rolloverIfNeeded()
        sessionStartedAt = SystemClock.elapsedRealtime()
        publish()
        ticker = scope.launch {
            while (isActive) {
                delay(1_000L)
                rolloverIfNeeded()
                publish()
                val elapsedSeconds = currentElapsedMillis() / 1_000L
                if (elapsedSeconds - lastPersistedSecond >= 30L) persist(notifyWidget = elapsedSeconds % 60L == 0L)
            }
        }
    }

    fun stop() {
        if (sessionStartedAt == null) return
        accumulateSession()
        ticker?.cancel()
        ticker = null
        persist(notifyWidget = true)
        publish()
    }

    fun setGoalMinutes(minutes: Int) {
        preferences.saveStudyTimeGoalMinutes(minutes)
        publish()
        onPersistedProgressChanged()
    }

    private fun rolloverIfNeeded() {
        val today = LocalDate.now()
        if (today == activeDate) return
        activeDate = today
        storedMillis = preferences.readStudyTimeTodayMillis(today)
        sessionStartedAt = sessionStartedAt?.let { SystemClock.elapsedRealtime() }
        lastPersistedSecond = storedMillis / 1_000L
        publish()
        onPersistedProgressChanged()
    }

    private fun accumulateSession() {
        val startedAt = sessionStartedAt ?: return
        storedMillis += (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
        sessionStartedAt = null
    }

    private fun currentElapsedMillis(): Long = storedMillis + (sessionStartedAt?.let { startedAt ->
        (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
    } ?: 0L)

    private fun snapshot(): StudyTimeProgress = StudyTimeProgress(
        elapsedMillis = currentElapsedMillis(),
        goalMinutes = preferences.readStudyTimeGoalMinutes(),
    )

    private fun publish() {
        mutableProgress.value = snapshot()
    }

    private fun persist(notifyWidget: Boolean) {
        val elapsed = currentElapsedMillis()
        preferences.saveStudyTimeTodayMillis(activeDate, elapsed)
        lastPersistedSecond = elapsed / 1_000L
        if (notifyWidget) onPersistedProgressChanged()
    }
}
