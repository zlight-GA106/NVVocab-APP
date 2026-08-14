package com.zlight106.nvvocab.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.view.View
import android.util.TypedValue
import android.widget.RemoteViews
import com.zlight106.nvvocab.MainActivity
import com.zlight106.nvvocab.R
import com.zlight106.nvvocab.data.AppPreferences
import com.zlight106.nvvocab.data.DailyMemoAction
import com.zlight106.nvvocab.data.DailyMemoSettings
import com.zlight106.nvvocab.data.DailyMemoTarget
import com.zlight106.nvvocab.data.DailyPracticeProgress
import com.zlight106.nvvocab.data.QuizBank
import com.zlight106.nvvocab.data.StudyTimeProgress
import com.zlight106.nvvocab.data.local.NvvocabDatabase
import java.time.LocalDate
import java.time.ZoneId

class DailyMemoWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetId ->
            manager.updateAppWidget(
                appWidgetId,
                buildRemoteViews(context, manager.getAppWidgetOptions(appWidgetId)),
            )
        }
        DailyWidgetResetScheduler.scheduleNext(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        manager.updateAppWidget(appWidgetId, buildRemoteViews(context, newOptions))
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        DailyWidgetResetScheduler.scheduleNext(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action in setOf(Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) {
            DailyMemoWidgetUpdater.updateAll(context)
            DailyWidgetResetScheduler.scheduleNext(context)
        }
    }
}

object DailyMemoWidgetUpdater {
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, DailyMemoWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return
        ids.forEach { appWidgetId ->
            manager.updateAppWidget(
                appWidgetId,
                buildRemoteViews(context, manager.getAppWidgetOptions(appWidgetId)),
            )
        }
    }
}

private data class WidgetTask(
    val text: String,
    val completed: Boolean,
)

private data class WidgetData(
    val progress: DailyPracticeProgress,
    val wordCount: Int,
    val quizBanks: List<QuizBank>,
)

internal data class WidgetTextScale(
    val titleSp: Float,
    val bodySp: Float,
)

private fun buildRemoteViews(context: Context, options: Bundle? = null): RemoteViews {
    val preferences = AppPreferences(context)
    val memoSettings = preferences.readDailyMemoSettings()
    val visibleTaskCount = if (memoSettings.isRestDay(LocalDate.now().dayOfWeek.value)) {
        1
    } else {
        memoSettings.items.size.coerceIn(1, 3)
    }
    val dayStart = LocalDate.now()
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
    val data = NvvocabDatabase(context).use { database ->
        WidgetData(
            progress = database.getDailyPracticeProgress(dayStart),
            wordCount = database.getWords().size,
            quizBanks = database.getQuizBanks(),
        )
    }
    val views = RemoteViews(context.packageName, R.layout.widget_daily_memo)
    applyWidgetTextScale(views, options, visibleTaskCount)
    val launchIntent = Intent(context, MainActivity::class.java)
    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    views.setOnClickPendingIntent(R.id.daily_memo_widget, pendingIntent)
    val studyTime = StudyTimeProgress(
        elapsedMillis = preferences.readStudyTimeTodayMillis(),
        goalMinutes = preferences.readStudyTimeGoalMinutes(),
    )
    bindStudyTime(views, studyTime)

    if (memoSettings.isRestDay(LocalDate.now().dayOfWeek.value)) {
        bindTask(views, R.id.memo_task_one, WidgetTask("今日宜休", false))
        views.setViewVisibility(R.id.memo_task_two, View.GONE)
        views.setViewVisibility(R.id.memo_task_three, View.GONE)
        return views
    }

    val tasks = buildTasks(memoSettings, data)
    val rows = listOf(R.id.memo_task_one, R.id.memo_task_two, R.id.memo_task_three)
    rows.forEachIndexed { index, viewId ->
        val task = tasks.getOrNull(index)
        if (task == null) {
            views.setViewVisibility(viewId, View.GONE)
        } else {
            bindTask(views, viewId, task)
        }
    }
    if (tasks.isEmpty()) {
        bindTask(views, R.id.memo_task_one, WidgetTask("未启用备忘项目", false))
    }
    return views
}

private fun applyWidgetTextScale(
    views: RemoteViews,
    options: Bundle?,
    visibleTaskCount: Int,
) {
    val minWidth = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 280
    val minHeight = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 110
    val scale = resolveWidgetTextScale(minWidth, minHeight, visibleTaskCount)
    views.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, scale.titleSp)
    listOf(
        R.id.study_time_text,
        R.id.memo_task_one,
        R.id.memo_task_two,
        R.id.memo_task_three,
    ).forEach { viewId ->
        views.setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_SP, scale.bodySp)
    }
}

internal fun resolveWidgetTextScale(
    minWidth: Int,
    minHeight: Int,
    visibleTaskCount: Int,
): WidgetTextScale {
    val taskCount = visibleTaskCount.coerceIn(1, 3)
    return when {
        taskCount == 1 && minWidth >= 260 && minHeight >= 105 -> WidgetTextScale(38f, 28f)
        minHeight >= 210 -> WidgetTextScale(34f, 25f)
        taskCount <= 2 && minWidth >= 260 && minHeight >= 125 -> WidgetTextScale(32f, 24f)
        minWidth >= 260 && minHeight >= 100 -> WidgetTextScale(28f, 21f)
        else -> WidgetTextScale(24f, 18f)
    }
}

private fun buildTasks(
    settings: DailyMemoSettings,
    data: WidgetData,
): List<WidgetTask> = settings.items.take(3).map { item ->
    val bank = item.quizBankId?.let { id -> data.quizBanks.firstOrNull { it.id == id } }
    val completed = when (item.target) {
        DailyMemoTarget.DICTATION -> data.progress.dictationCompleted
        DailyMemoTarget.CONTRAST -> data.progress.contrastCompleted
        DailyMemoTarget.QUIZ_BANK -> item.quizBankId?.let { data.progress.customQuizCompletedByBank[it] }
            ?: data.progress.customQuizCompleted
    }
    val capacity = when (item.target) {
        DailyMemoTarget.DICTATION, DailyMemoTarget.CONTRAST -> data.wordCount
        DailyMemoTarget.QUIZ_BANK -> bank?.questionCount ?: data.quizBanks.sumOf(QuizBank::questionCount)
    }
    val target = item.amount?.coerceAtLeast(1) ?: capacity
    val action = if (item.action == DailyMemoAction.COMPLETE) "完成" else "复习"
    val subject = when (item.target) {
        DailyMemoTarget.DICTATION -> "默写"
        DailyMemoTarget.CONTRAST -> "对照复习"
        DailyMemoTarget.QUIZ_BANK -> "题库 ${bank?.name ?: item.quizBankName ?: "全部题库"}"
    }
    val unit = if (item.target == DailyMemoTarget.DICTATION) "词" else "题"
    WidgetTask(
        text = "$action $subject $completed / $target $unit",
        completed = target > 0 && completed >= target,
    )
}

private fun bindTask(views: RemoteViews, viewId: Int, task: WidgetTask) {
    views.setViewVisibility(viewId, View.VISIBLE)
    views.setTextViewText(viewId, task.text.withStrikeThrough(task.completed))
    // Always reset host paint state. Using STRIKE_THRU_TEXT_FLAG directly is unstable
    // when launchers re-apply RemoteViews, while a span survives the rebind.
    views.setInt(viewId, "setPaintFlags", Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
}

private fun bindStudyTime(views: RemoteViews, progress: StudyTimeProgress) {
    val text = "学习 ${progress.elapsedMinutes} / ${progress.goalMinutes} 分钟"
    views.setTextViewText(R.id.study_time_text, text.withStrikeThrough(progress.completed))
    views.setInt(
        R.id.study_time_text,
        "setPaintFlags",
        Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG,
    )
    views.setProgressBar(
        R.id.study_time_progress,
        100,
        (progress.progressFraction * 100).toInt(),
        false,
    )
}

private fun String.withStrikeThrough(enabled: Boolean): CharSequence {
    if (!enabled) return this
    return SpannableString(this).apply {
        setSpan(StrikethroughSpan(), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}
