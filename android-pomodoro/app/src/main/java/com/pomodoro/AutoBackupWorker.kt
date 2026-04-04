package com.pomodoro

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pomodoro.ui.CheckItemMode
import com.pomodoro.ui.SettingsPrefs
import com.pomodoro.ui.SavedReviewNote
import com.pomodoro.ui.TodoTask
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar
import java.util.concurrent.TimeUnit

class AutoBackupWorker(
    private val ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        if (!DriveBackupHelper.isSignedIn(ctx)) return Result.failure()

        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val notesJson = prefs.getString(KEY_SAVED_NOTES, null)
        val todosJson = prefs.getString(KEY_TODO_TASKS, null)

        val gson = Gson()
        val notes: List<SavedReviewNote> = if (notesJson != null) {
            try {
                val type = object : TypeToken<List<SavedReviewNote>>() {}.type
                gson.fromJson(notesJson, type)
            } catch (_: Exception) { emptyList() }
        } else emptyList()

        val todos: List<TodoTask> = if (todosJson != null) {
            try {
                val type = object : TypeToken<List<TodoTask>>() {}.type
                gson.fromJson(todosJson, type)
            } catch (_: Exception) { emptyList() }
        } else emptyList()

        val (f, r, b) = SettingsPrefs.loadPrefs(ctx)
        val checklistItems = SettingsPrefs.loadFocusChecklistItems(ctx)
        val checklistMode = SettingsPrefs.loadChecklistMode(ctx)
        val theme = com.pomodoro.ui.theme.ThemePreference.currentTheme.value.name

        val settings = BackupSettings(
            focusMinutes = f,
            reviewMinutes = r,
            breakMinutes = b,
            theme = theme,
            checklistMode = checklistMode.name,
            focusChecklist = checklistItems.map { BackupChecklistItem(text = it) }
        )

        val result = DriveBackupHelper.backup(ctx, notes, todos, settings)
        return if (result.isSuccess) Result.success() else Result.retry()
    }

    companion object {
        private const val WORK_NAME = "auto_backup_daily"
        private const val PREFS_NAME = "pomodoro_prefs"
        private const val KEY_SAVED_NOTES = "saved_notes"
        private const val KEY_TODO_TASKS = "todo_tasks"
        private const val KEY_AUTO_BACKUP = "auto_backup_enabled"

        fun isEnabled(ctx: Context): Boolean {
            return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_AUTO_BACKUP, false)
        }

        fun setEnabled(ctx: Context, enabled: Boolean) {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_AUTO_BACKUP, enabled)
                .apply()
            if (enabled) schedule(ctx) else cancel(ctx)
        }

        fun schedule(ctx: Context) {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 2)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
            }
            val initialDelayMs = target.timeInMillis - now.timeInMillis

            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(WORK_NAME)
        }
    }
}
