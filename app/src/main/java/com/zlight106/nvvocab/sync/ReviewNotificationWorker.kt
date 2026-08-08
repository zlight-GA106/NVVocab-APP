package com.zlight106.nvvocab.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zlight106.nvvocab.MainActivity
import com.zlight106.nvvocab.NvvocabApplication
import com.zlight106.nvvocab.R
import com.zlight106.nvvocab.data.ReminderSettings
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class ReviewNotificationWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as NvvocabApplication
        val settings = app.preferences.readReminderSettings()
        if (!settings.anyEnabled || !canPostNotifications()) return Result.success()

        createChannel(applicationContext)
        if (settings.matchingEnabled) {
            postNotification(
                id = MATCHING_NOTIFICATION_ID,
                text = "今日需要完成单词匹配 ${settings.matchingQuestionTarget} 题",
            )
        }
        if (settings.reviewEnabled) {
            app.repository.refreshLocal()
            val dueCount = app.repository.words.value.count { it.nextReviewAt <= System.currentTimeMillis() }
            val reviewCount = dueCount.coerceAtMost(app.preferences.readDailyReviewTarget())
            if (reviewCount > 0) {
                postNotification(
                    id = REVIEW_NOTIFICATION_ID,
                    text = "今日需要完成复习默写 $reviewCount 数量",
                )
            }
        }
        if (settings.questionEnabled) {
            val totalQuestions = settings.questionGroupCount * settings.questionsPerGroup
            postNotification(
                id = QUESTION_NOTIFICATION_ID,
                text = "今日需要完成题目 ${settings.questionGroupCount} 分组 $totalQuestions 题",
            )
        }
        return Result.success()
    }

    private fun canPostNotifications(): Boolean =
        ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

    private fun postNotification(id: Int, text: String) {
        if (
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            id,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("单词速记每日任务")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(id, notification)
        } catch (_: SecurityException) {
            return
        }
    }

    companion object {
        const val CHANNEL_ID = "daily_tasks"
        const val MATCHING_NOTIFICATION_ID = 1001
        const val REVIEW_NOTIFICATION_ID = 1002
        const val QUESTION_NOTIFICATION_ID = 1003

        fun createChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "每日任务提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "提醒每日单词匹配、复习默写与分组题目任务"
                },
            )
        }
    }
}

object ReviewNotificationScheduler {
    private const val WORK_NAME = "nvvocab_daily_task_notification"

    fun configure(context: Context, settings: ReminderSettings) {
        val manager = WorkManager.getInstance(context)
        if (!settings.anyEnabled) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        ReviewNotificationWorker.createChannel(context)
        val now = ZonedDateTime.now()
        var next = now.withHour(settings.reminderHour).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<ReviewNotificationWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, next))
            .build()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
