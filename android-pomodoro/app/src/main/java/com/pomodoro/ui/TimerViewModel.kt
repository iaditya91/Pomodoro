package com.pomodoro.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.pomodoro.BackupChecklistItem
import com.pomodoro.BackupPayload
import com.pomodoro.BackupSettings
import com.pomodoro.DriveBackupHelper
import com.pomodoro.MiniTaskGenerator
import com.pomodoro.NotificationHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pomodoro.TimerForegroundService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

enum class TimerMode { FOCUS, REVIEW, BREAK }

data class Subtask(
    val id: Long = System.nanoTime(),
    val text: String = "",
    val description: String = "",
    val minutes: Int = 0,
    val isDone: Boolean = false
)

data class ReviewItem(
    val id: Long = System.nanoTime(),
    val text: String = ""
)

data class ReviewAnswers(
    val wentWell: List<ReviewItem> = emptyList(),
    val didntGoWell: List<ReviewItem> = emptyList(),
    val improvements: List<ReviewItem> = emptyList()
)

enum class ReviewQuestion { WENT_WELL, DIDNT_GO_WELL, IMPROVEMENTS }

data class SavedReviewNote(
    val id: Long = System.nanoTime(),
    val taskName: String = "",
    val improvements: List<ReviewItem> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * A single completed focus session recorded in the Journal.
 * The task name is written when the focus timer ends; [improvements]
 * are attached afterwards (as subpoints) if the review timer produced any.
 *
 * [startTime] is when the focus session began and [timestamp] is when it ended.
 * [note] holds any free-text the user adds manually. Entries can also be created
 * fully by hand from the Journal screen.
 */
data class JournalEntry(
    val id: Long = System.nanoTime(),
    val taskName: String = "",
    val improvements: List<ReviewItem> = emptyList(),
    val note: String = "",
    val startTime: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)

enum class TodoSection { TODAY, PLANNED }

data class TodoSubtask(
    val id: Long = System.nanoTime(),
    val text: String = "",
    val isDone: Boolean = false
)

data class TodoTask(
    val id: Long = System.nanoTime(),
    val text: String = "",
    val section: TodoSection = TodoSection.PLANNED,
    val isDone: Boolean = false,
    val description: String = "",
    val subtasks: List<TodoSubtask> = emptyList(),
    val scheduledDate: Long? = null,
    val scheduledTime: Long? = null,
    val isRepeatable: Boolean = false
)

data class RoutineSubtask(
    val id: Long = System.nanoTime(),
    val text: String = "",
    val description: String = "",
    val minutes: Int = 0
)

data class Routine(
    val id: Long = System.nanoTime(),
    val name: String = "",
    val subtasks: List<RoutineSubtask> = emptyList(),
    val focusMinutes: Int = 25,
    val reviewMinutes: Int = 5,
    val breakMinutes: Int = 15,
    val focusCycles: Int = 4,
    val reviewEnabled: Boolean = true,
    val breakEnabled: Boolean = true,
    val useDefaultSettings: Boolean = true
)

enum class CheckItemMode { CHECK, TYPE }

data class FocusCheckItem(
    val text: String,
    val mode: CheckItemMode = CheckItemMode.CHECK,
    val isChecked: Boolean = false,
    val typedText: String = ""
) {
    val isCompleted: Boolean
        get() = when (mode) {
            CheckItemMode.CHECK -> isChecked
            CheckItemMode.TYPE -> typedText.trim().equals(text.trim(), ignoreCase = true)
        }
}

data class TimerUiState(
    val mode: TimerMode = TimerMode.FOCUS,
    val remainingMillis: Long = 0L,
    val isRunning: Boolean = false,
    val taskName: String = "",
    val subtasks: List<Subtask> = emptyList(),
    val reviewAnswers: ReviewAnswers = ReviewAnswers(),
    val isGenerating: Boolean = false,
    val generateError: String? = null,
    val focusChecklist: List<FocusCheckItem> = emptyList(),
    val checklistCompleted: Boolean = true
)

// Keys for the in-flight focus session mirrored to disk (see persistPendingFocus).
private const val KEY_PENDING_TASK = "pending_focus_task"
private const val KEY_PENDING_START = "pending_focus_start"
private const val KEY_PENDING_DEADLINE = "pending_focus_deadline"

class TimerViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableLiveData(TimerUiState())
    val uiState: LiveData<TimerUiState> = _uiState

    private val _todoTasks = MutableLiveData<List<TodoTask>>(emptyList())
    val todoTasks: LiveData<List<TodoTask>> = _todoTasks

    private val _savedNotes = MutableLiveData<List<SavedReviewNote>>(emptyList())
    val savedNotes: LiveData<List<SavedReviewNote>> = _savedNotes

    private val _journalEntries = MutableLiveData<List<JournalEntry>>(emptyList())
    val journalEntries: LiveData<List<JournalEntry>> = _journalEntries

    // Id of the journal entry created for the focus session currently in progress,
    // so its improvements can be attached once the review is saved.
    private var currentJournalEntryId: Long? = null

    // Whether the current focus session has already been written to the Journal.
    // Guards against double-logging: the session is logged the moment the focus
    // timer reaches 0 (even in the background), and the later manual-advance calls
    // become no-ops. Reset to false whenever a new focus session begins.
    private var focusLogged = false

    // Wall-clock time the current focus session started, recorded in the journal entry.
    private var currentFocusStartMillis: Long = System.currentTimeMillis()

    private val _routines = MutableLiveData<List<Routine>>(emptyList())
    val routines: LiveData<List<Routine>> = _routines

    private val _navigateHomeEvent = MutableLiveData<Long>()
    val navigateHomeEvent: LiveData<Long> = _navigateHomeEvent

    // Active routine tracking
    private var activeRoutine: Routine? = null
    private var currentCycle = 0

    private var tickJob: Job? = null
    private var deadlineMillis: Long = 0L  // absolute time when timer ends

    // defaults (minutes)
    private var focusMinutes = 25
    private var reviewMinutes = 5
    private var breakMinutes = 15

    private val prefs = application.getSharedPreferences("pomodoro_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    init {
        NotificationHelper.createChannel(application)
        _uiState.value = TimerUiState(remainingMillis = minutesToMillis(focusMinutes))
        loadPersistedData()
        promoteScheduledTasks()
        _savedNotes.observeForever { persistNotes(it) }
        _journalEntries.observeForever { persistJournal(it) }
        _todoTasks.observeForever { persistTodos(it) }
        _routines.observeForever { persistRoutines(it) }
        // After the observers are attached, so a recovered entry gets persisted too.
        flushPendingFocusSession()
    }

    private fun loadPersistedData() {
        prefs.getString("saved_notes", null)?.let { json ->
            try {
                val type = object : TypeToken<List<SavedReviewNote>>() {}.type
                _savedNotes.value = gson.fromJson(json, type)
            } catch (_: Exception) {}
        }
        prefs.getString("journal_entries", null)?.let { json ->
            try {
                val type = object : TypeToken<List<JournalEntry>>() {}.type
                _journalEntries.value = gson.fromJson(json, type)
            } catch (_: Exception) {}
        }
        prefs.getString("todo_tasks", null)?.let { json ->
            try {
                val type = object : TypeToken<List<TodoTask>>() {}.type
                _todoTasks.value = gson.fromJson(json, type)
            } catch (_: Exception) {}
        }
        prefs.getString("routines", null)?.let { json ->
            try {
                val type = object : TypeToken<List<Routine>>() {}.type
                _routines.value = gson.fromJson(json, type)
            } catch (_: Exception) {}
        }
    }

    private fun persistNotes(notes: List<SavedReviewNote>) {
        prefs.edit().putString("saved_notes", gson.toJson(notes)).apply()
    }

    private fun persistJournal(entries: List<JournalEntry>) {
        prefs.edit().putString("journal_entries", gson.toJson(entries)).apply()
    }

    private fun persistTodos(todos: List<TodoTask>) {
        prefs.edit().putString("todo_tasks", gson.toJson(todos)).apply()
    }

    private fun persistRoutines(routines: List<Routine>) {
        prefs.edit().putString("routines", gson.toJson(routines)).apply()
    }

    // --- Pending focus session ---
    //
    // The focus session in progress is mirrored to disk so it can still be journaled
    // if it finishes while we are not around to see it: the app may be backgrounded,
    // the Activity (and with it the ViewModel and its tick job) may be destroyed, or
    // the process may be reclaimed once the foreground service stops itself at zero.
    // [deadline] is 0 while the session is paused/not yet started, so a paused session
    // is never mistaken for a finished one.

    private fun persistPendingFocus(taskName: String, startMillis: Long, deadline: Long) {
        prefs.edit()
            .putString(KEY_PENDING_TASK, taskName)
            .putLong(KEY_PENDING_START, startMillis)
            .putLong(KEY_PENDING_DEADLINE, deadline)
            .apply()
    }

    private fun clearPendingFocus() {
        prefs.edit()
            .remove(KEY_PENDING_TASK)
            .remove(KEY_PENDING_START)
            .remove(KEY_PENDING_DEADLINE)
            .apply()
    }

    /**
     * Journals a focus session that ran past its deadline while we weren't watching,
     * and restores the "focus complete" state so the completion notification still
     * advances the timer correctly. No-op unless a running focus session is on disk
     * whose deadline has passed.
     */
    private fun flushPendingFocusSession() {
        val deadline = prefs.getLong(KEY_PENDING_DEADLINE, 0L)
        if (deadline <= 0L || System.currentTimeMillis() < deadline) return
        val name = prefs.getString(KEY_PENDING_TASK, "").orEmpty().trim()
        val start = prefs.getLong(KEY_PENDING_START, 0L)
        clearPendingFocus()
        if (focusLogged) return
        focusLogged = true

        deadlineMillis = deadline
        currentFocusStartMillis = start
        val cur = _uiState.value ?: TimerUiState()
        _uiState.value = cur.copy(
            mode = TimerMode.FOCUS,
            remainingMillis = 0L,
            isRunning = false,
            taskName = name
        )

        if (name.isBlank()) {
            currentJournalEntryId = null
            return
        }
        val entry = JournalEntry(taskName = name, startTime = start, timestamp = deadline)
        currentJournalEntryId = entry.id
        _journalEntries.value = listOf(entry) + (_journalEntries.value ?: emptyList())
    }

    fun reloadDurations(ctx: Context) {
        val prefs = ctx.getSharedPreferences("pomodoro_prefs", Context.MODE_PRIVATE)
        focusMinutes = prefs.getInt("focus_minutes", 25)
        reviewMinutes = prefs.getInt("review_minutes", 5)
        breakMinutes = prefs.getInt("break_minutes", 15)
        val cur = _uiState.value ?: TimerUiState()
        if (!cur.isRunning) {
            _uiState.postValue(cur.copy(remainingMillis = minutesToMillis(durationForMode(cur.mode))))
        }
    }

    fun loadFocusChecklist(ctx: Context) {
        val items = SettingsPrefs.loadFocusChecklistItems(ctx)
        val mode = SettingsPrefs.loadChecklistMode(ctx)
        val cur = _uiState.value ?: TimerUiState()
        val checklist = items.map { FocusCheckItem(text = it, mode = mode) }
        _uiState.postValue(cur.copy(
            focusChecklist = checklist,
            checklistCompleted = checklist.isEmpty()
        ))
    }

    fun toggleChecklistItem(index: Int) {
        val cur = _uiState.value ?: return
        val updated = cur.focusChecklist.toMutableList()
        if (index in updated.indices) {
            updated[index] = updated[index].copy(isChecked = !updated[index].isChecked)
        }
        val allCompleted = updated.isEmpty() || updated.all { it.isCompleted }
        _uiState.postValue(cur.copy(focusChecklist = updated, checklistCompleted = allCompleted))
    }

    fun updateChecklistTypedText(index: Int, text: String) {
        val cur = _uiState.value ?: return
        val updated = cur.focusChecklist.toMutableList()
        if (index in updated.indices) {
            updated[index] = updated[index].copy(typedText = text)
        }
        val allCompleted = updated.isEmpty() || updated.all { it.isCompleted }
        _uiState.postValue(cur.copy(focusChecklist = updated, checklistCompleted = allCompleted))
    }

    fun toggleRunning() {
        val cur = _uiState.value ?: return
        if (cur.isRunning) {
            stopTicker()
        } else {
            deadlineMillis = System.currentTimeMillis() + cur.remainingMillis
            startTicker()
        }
        if (cur.mode == TimerMode.FOCUS && !focusLogged) {
            // Pausing stores a 0 deadline so a paused session is never recovered as finished.
            persistPendingFocus(
                cur.taskName,
                currentFocusStartMillis,
                if (cur.isRunning) 0L else deadlineMillis
            )
        }
        _uiState.postValue(cur.copy(isRunning = !cur.isRunning))
    }

    private fun startTicker(mode: TimerMode? = null) {
        tickJob?.cancel()
        val app = getApplication<Application>()
        val serviceMode = mode ?: _uiState.value?.mode ?: return
        TimerForegroundService.start(app, deadlineMillis, serviceMode)
        tickJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val remaining = (deadlineMillis - System.currentTimeMillis()).coerceAtLeast(0L)
                val latest = _uiState.value ?: continue
                if (remaining <= 0L) {
                    // Record the focus session as soon as the timer ends, so it is
                    // journaled even if the user never manually advances.
                    if (latest.mode == TimerMode.FOCUS) logFocusToJournal()
                    _uiState.postValue(latest.copy(remainingMillis = 0L, isRunning = false))
                    tickJob?.cancel()
                    // Service handles its own completion notification
                    break
                } else {
                    _uiState.postValue(latest.copy(remainingMillis = remaining))
                }
            }
        }
    }

    private fun stopTicker() {
        tickJob?.cancel()
        tickJob = null
        TimerForegroundService.stop(getApplication())
    }

    private fun cancelTickJob() {
        tickJob?.cancel()
        tickJob = null
    }

    fun syncFromDeadline() {
        // A focus timer that finished while the app was away is journaled from disk —
        // the service stops itself at zero, so it is no longer running by the time we
        // get here and the in-memory tick job may never have seen the timer end.
        flushPendingFocusSession()

        if (!TimerForegroundService.isServiceRunning) {
            // Nothing ticking: make sure a timer that ran out while we were away shows
            // as finished rather than frozen mid-countdown.
            val cur = _uiState.value ?: return
            if (cur.isRunning && deadlineMillis > 0L && System.currentTimeMillis() >= deadlineMillis) {
                cancelTickJob()
                if (cur.mode == TimerMode.FOCUS) logFocusToJournal()
                _uiState.postValue(cur.copy(remainingMillis = 0L, isRunning = false))
            }
            return
        }

        val remaining = (TimerForegroundService.deadlineMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        val cur = _uiState.value ?: return
        if (remaining <= 0L) {
            if (cur.mode == TimerMode.FOCUS) logFocusToJournal()
            _uiState.postValue(cur.copy(remainingMillis = 0L, isRunning = false))
        } else {
            deadlineMillis = TimerForegroundService.deadlineMillis
            _uiState.postValue(cur.copy(remainingMillis = remaining, isRunning = true))
            startTicker()
        }
    }

    fun totalMillisForMode(mode: TimerMode): Long = minutesToMillis(durationForMode(mode))

    private fun minutesToMillis(min: Int) = min * 60_000L

    private fun durationForMode(mode: TimerMode) = when (mode) {
        TimerMode.FOCUS -> focusMinutes
        TimerMode.REVIEW -> reviewMinutes
        TimerMode.BREAK -> breakMinutes
    }

    fun updateTaskName(name: String) {
        val cur = _uiState.value ?: return
        // Keep the on-disk copy current: the name is usually typed after the focus
        // timer has already started, and it is what ends up in the Journal.
        if (cur.mode == TimerMode.FOCUS && !focusLogged) {
            prefs.edit().putString(KEY_PENDING_TASK, name).apply()
        }
        _uiState.postValue(cur.copy(taskName = name))
    }

    fun addSubtask(text: String) {
        if (text.isBlank()) return
        val cur = _uiState.value ?: return
        _uiState.postValue(cur.copy(subtasks = cur.subtasks + Subtask(text = text)))
    }

    fun toggleSubtask(id: Long) {
        val cur = _uiState.value ?: return
        val updated = cur.subtasks.map {
            if (it.id == id) it.copy(isDone = !it.isDone) else it
        }
        _uiState.postValue(cur.copy(subtasks = updated))
    }

    fun removeSubtask(id: Long) {
        val cur = _uiState.value ?: return
        _uiState.postValue(cur.copy(subtasks = cur.subtasks.filter { it.id != id }))
    }

    fun generateMiniTasks(taskText: String) {
        if (taskText.isBlank()) return
        val cur = _uiState.value ?: return
        _uiState.postValue(cur.copy(isGenerating = true))
        viewModelScope.launch {
            val result = MiniTaskGenerator.generate(taskText)
            val latest = _uiState.value ?: return@launch
            if (result.isSuccess) {
                val newSubtasks = result.getOrDefault(emptyList()).map { Subtask(text = it) }
                _uiState.postValue(latest.copy(
                    subtasks = latest.subtasks + newSubtasks,
                    isGenerating = false,
                    generateError = null
                ))
            } else {
                _uiState.postValue(latest.copy(
                    isGenerating = false,
                    generateError = result.exceptionOrNull()?.message ?: "Failed to generate tasks"
                ))
            }
        }
    }

    fun clearGenerateError() {
        val cur = _uiState.value ?: return
        _uiState.postValue(cur.copy(generateError = null))
    }

    fun addReviewItem(question: ReviewQuestion, text: String) {
        if (text.isBlank()) return
        val cur = _uiState.value ?: return
        val answers = cur.reviewAnswers
        val item = ReviewItem(text = text)
        val updated = when (question) {
            ReviewQuestion.WENT_WELL -> answers.copy(wentWell = answers.wentWell + item)
            ReviewQuestion.DIDNT_GO_WELL -> answers.copy(didntGoWell = answers.didntGoWell + item)
            ReviewQuestion.IMPROVEMENTS -> answers.copy(improvements = answers.improvements + item)
        }
        _uiState.postValue(cur.copy(reviewAnswers = updated))
    }

    fun removeReviewItem(question: ReviewQuestion, id: Long) {
        val cur = _uiState.value ?: return
        val answers = cur.reviewAnswers
        val updated = when (question) {
            ReviewQuestion.WENT_WELL -> answers.copy(wentWell = answers.wentWell.filter { it.id != id })
            ReviewQuestion.DIDNT_GO_WELL -> answers.copy(didntGoWell = answers.didntGoWell.filter { it.id != id })
            ReviewQuestion.IMPROVEMENTS -> answers.copy(improvements = answers.improvements.filter { it.id != id })
        }
        _uiState.postValue(cur.copy(reviewAnswers = updated))
    }

    private fun saveCurrentReview() {
        val cur = _uiState.value ?: return
        val answers = cur.reviewAnswers
        // Attach any review improvements onto the journal entry created when
        // the focus timer ended, so they appear as subpoints under the task.
        attachImprovementsToJournal(answers.improvements)
        if (answers.improvements.isEmpty()) return
        val note = SavedReviewNote(
            taskName = cur.taskName,
            improvements = answers.improvements
        )
        val existing = _savedNotes.value ?: emptyList()
        _savedNotes.postValue(listOf(note) + existing)
    }

    fun deleteNote(id: Long) {
        val existing = _savedNotes.value ?: return
        _savedNotes.postValue(existing.filter { it.id != id })
    }

    // --- Journal ---

    /**
     * Records the current focus task in the Journal under today's date.
     * Called when a focus session ends (transitions out of FOCUS mode).
     * Entries with a blank task name are skipped.
     */
    private fun logFocusToJournal() {
        val cur = _uiState.value ?: return
        if (cur.mode != TimerMode.FOCUS) return
        if (focusLogged) return
        focusLogged = true
        clearPendingFocus()
        val name = cur.taskName.trim()
        if (name.isBlank()) {
            currentJournalEntryId = null
            return
        }
        val entry = JournalEntry(
            taskName = name,
            startTime = currentFocusStartMillis,
            timestamp = System.currentTimeMillis()
        )
        currentJournalEntryId = entry.id
        val existing = _journalEntries.value ?: emptyList()
        _journalEntries.postValue(listOf(entry) + existing)
    }

    /** Creates a brand-new journal entry from user input (custom note). */
    fun addCustomJournalEntry(
        taskName: String,
        startTime: Long,
        endTime: Long,
        improvements: List<ReviewItem>,
        note: String
    ) {
        val entry = JournalEntry(
            taskName = taskName.trim(),
            improvements = improvements,
            note = note.trim(),
            startTime = startTime,
            timestamp = endTime
        )
        val existing = _journalEntries.value ?: emptyList()
        _journalEntries.postValue(listOf(entry) + existing)
    }

    /** Applies user edits to an existing journal entry. */
    fun updateJournalEntry(
        id: Long,
        taskName: String,
        startTime: Long,
        endTime: Long,
        improvements: List<ReviewItem>,
        note: String
    ) {
        val existing = _journalEntries.value ?: return
        _journalEntries.postValue(existing.map {
            if (it.id == id) it.copy(
                taskName = taskName.trim(),
                startTime = startTime,
                timestamp = endTime,
                improvements = improvements,
                note = note.trim()
            ) else it
        })
    }

    private fun attachImprovementsToJournal(improvements: List<ReviewItem>) {
        val entryId = currentJournalEntryId ?: return
        if (improvements.isEmpty()) return
        val existing = _journalEntries.value ?: return
        _journalEntries.postValue(existing.map {
            if (it.id == entryId) it.copy(improvements = it.improvements + improvements) else it
        })
    }

    fun deleteJournalEntry(id: Long) {
        val existing = _journalEntries.value ?: return
        if (currentJournalEntryId == id) currentJournalEntryId = null
        _journalEntries.postValue(existing.filter { it.id != id })
    }

    // --- Backup / Restore ---

    enum class BackupStatus { IDLE, LOADING, SUCCESS, ERROR }

    private val _backupStatus = MutableLiveData(BackupStatus.IDLE)
    val backupStatus: LiveData<BackupStatus> = _backupStatus

    private val _backupMessage = MutableLiveData("")
    val backupMessage: LiveData<String> = _backupMessage

    fun backupToDrive(ctx: Context) {
        val notes = _savedNotes.value ?: emptyList()
        val todos = _todoTasks.value ?: emptyList()
        val journal = _journalEntries.value ?: emptyList()
        // Gather settings
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
        _backupStatus.postValue(BackupStatus.LOADING)
        viewModelScope.launch {
            val result = DriveBackupHelper.backup(ctx, notes, todos, journal, settings)
            if (result.isSuccess) {
                _backupStatus.postValue(BackupStatus.SUCCESS)
                _backupMessage.postValue("Backed up ${notes.size} note(s), ${todos.size} task(s), ${journal.size} journal entr(ies), settings")
            } else {
                _backupStatus.postValue(BackupStatus.ERROR)
                _backupMessage.postValue(result.exceptionOrNull()?.message ?: "Backup failed")
            }
        }
    }

    fun restoreFromDrive(ctx: Context) {
        _backupStatus.postValue(BackupStatus.LOADING)
        viewModelScope.launch {
            val result = DriveBackupHelper.restore(ctx)
            if (result.isSuccess) {
                val payload = result.getOrNull() ?: return@launch
                _savedNotes.postValue(payload.notes)
                _todoTasks.postValue(payload.todoTasks)
                if (payload.journalEntries.isNotEmpty()) {
                    _journalEntries.postValue(payload.journalEntries)
                }
                // Restore settings if present
                payload.settings?.let { s ->
                    SettingsPrefs.savePrefs(ctx, s.focusMinutes, s.reviewMinutes, s.breakMinutes)
                    reloadDurations(ctx)
                    SettingsPrefs.saveFocusChecklistItems(ctx, s.focusChecklist.map { it.text })
                    try {
                        SettingsPrefs.saveChecklistMode(ctx, CheckItemMode.valueOf(s.checklistMode))
                    } catch (_: Exception) { }
                    try {
                        val themeMode = com.pomodoro.ui.theme.ThemeMode.valueOf(s.theme)
                        com.pomodoro.ui.theme.ThemePreference.save(ctx, themeMode)
                    } catch (_: Exception) { }
                }
                _backupStatus.postValue(BackupStatus.SUCCESS)
                _backupMessage.postValue("Restored ${payload.notes.size} note(s), ${payload.todoTasks.size} task(s), settings")
            } else {
                _backupStatus.postValue(BackupStatus.ERROR)
                _backupMessage.postValue(result.exceptionOrNull()?.message ?: "Restore failed")
            }
        }
    }

    fun clearBackupStatus() {
        _backupStatus.postValue(BackupStatus.IDLE)
        _backupMessage.postValue("")
    }

    fun startReview(ctx: Context) {
        reloadDurations(ctx)
        logFocusToJournal()
        cancelTickJob()
        val cur = _uiState.value ?: TimerUiState()
        val millis = minutesToMillis(reviewMinutes)
        deadlineMillis = System.currentTimeMillis() + millis
        _uiState.postValue(cur.copy(
            mode = TimerMode.REVIEW,
            remainingMillis = millis,
            isRunning = true,
            reviewAnswers = ReviewAnswers()
        ))
        startTicker(TimerMode.REVIEW)
    }

    fun startBreak(ctx: Context) {
        reloadDurations(ctx)
        val cur = _uiState.value ?: TimerUiState()
        if (cur.mode == TimerMode.REVIEW) saveCurrentReview()
        cancelTickJob()
        val millis = minutesToMillis(breakMinutes)
        deadlineMillis = System.currentTimeMillis() + millis
        _uiState.postValue(cur.copy(
            mode = TimerMode.BREAK,
            remainingMillis = millis,
            isRunning = true
        ))
        startTicker(TimerMode.BREAK)
    }

    private fun buildChecklist(ctx: Context): List<FocusCheckItem> {
        val items = SettingsPrefs.loadFocusChecklistItems(ctx)
        val mode = SettingsPrefs.loadChecklistMode(ctx)
        return items.map { FocusCheckItem(text = it, mode = mode) }
    }

    fun startFocus(ctx: Context) {
        reloadDurations(ctx)
        val cur = _uiState.value ?: TimerUiState()
        if (cur.mode == TimerMode.REVIEW) saveCurrentReview()
        cancelTickJob()
        val millis = minutesToMillis(focusMinutes)
        val checklist = buildChecklist(ctx)
        val hasChecklist = checklist.isNotEmpty()
        currentFocusStartMillis = System.currentTimeMillis()
        focusLogged = false
        deadlineMillis = System.currentTimeMillis() + millis
        persistPendingFocus("", currentFocusStartMillis, if (hasChecklist) 0L else deadlineMillis)
        _uiState.postValue(TimerUiState(
            mode = TimerMode.FOCUS,
            remainingMillis = millis,
            isRunning = !hasChecklist,
            focusChecklist = checklist,
            checklistCompleted = !hasChecklist
        ))
        if (!hasChecklist) {
            startTicker(TimerMode.FOCUS)
        } else {
            TimerForegroundService.stop(getApplication())
        }
    }

    fun startFocusWithTask(ctx: Context, taskName: String, todoSubtasks: List<TodoSubtask> = emptyList()) {
        reloadDurations(ctx)
        val cur = _uiState.value ?: TimerUiState()
        if (cur.mode == TimerMode.REVIEW) saveCurrentReview()
        cancelTickJob()
        val millis = minutesToMillis(focusMinutes)
        val checklist = buildChecklist(ctx)
        val hasChecklist = checklist.isNotEmpty()
        val focusSubtasks = todoSubtasks.map { sub ->
            Subtask(id = sub.id, text = sub.text, isDone = sub.isDone)
        }
        currentFocusStartMillis = System.currentTimeMillis()
        focusLogged = false
        deadlineMillis = System.currentTimeMillis() + millis
        persistPendingFocus(taskName, currentFocusStartMillis, if (hasChecklist) 0L else deadlineMillis)
        _uiState.postValue(TimerUiState(
            mode = TimerMode.FOCUS,
            remainingMillis = millis,
            isRunning = !hasChecklist,
            taskName = taskName,
            subtasks = focusSubtasks,
            focusChecklist = checklist,
            checklistCompleted = !hasChecklist
        ))
        if (!hasChecklist) {
            startTicker(TimerMode.FOCUS)
        } else {
            TimerForegroundService.stop(getApplication())
        }
    }

    // --- Todo Tasks ---

    fun addTodoTask(text: String) {
        if (text.isBlank()) return
        val existing = _todoTasks.value ?: emptyList()
        _todoTasks.postValue(existing + TodoTask(text = text.trim()))
    }

    fun addTodoTaskAtSlot(text: String, slotMillis: Long) {
        if (text.isBlank()) return
        val existing = _todoTasks.value ?: emptyList()
        val cal = Calendar.getInstance().apply { timeInMillis = slotMillis }
        val today = Calendar.getInstance()
        val isToday = cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
        val dateOnly = Calendar.getInstance().apply {
            timeInMillis = slotMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        _todoTasks.postValue(existing + TodoTask(
            text = text.trim(),
            section = if (isToday) TodoSection.TODAY else TodoSection.PLANNED,
            scheduledDate = dateOnly,
            scheduledTime = slotMillis
        ))
    }

    fun unscheduleTodoTime(id: Long) {
        val existing = _todoTasks.value ?: return
        _todoTasks.postValue(existing.map {
            if (it.id == id) it.copy(scheduledTime = null) else it
        })
    }

    fun assignTodoToSlot(id: Long, slotMillis: Long) {
        val existing = _todoTasks.value ?: return
        val cal = Calendar.getInstance().apply { timeInMillis = slotMillis }
        val today = Calendar.getInstance()
        val isToday = cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
        val dateOnly = Calendar.getInstance().apply {
            timeInMillis = slotMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        _todoTasks.postValue(existing.map {
            if (it.id == id) it.copy(
                section = if (isToday) TodoSection.TODAY else TodoSection.PLANNED,
                scheduledDate = dateOnly,
                scheduledTime = slotMillis
            ) else it
        })
    }

    fun removeTodoTask(id: Long) {
        val existing = _todoTasks.value ?: return
        _todoTasks.postValue(existing.filter { it.id != id })
    }

    fun moveTodoToToday(id: Long) {
        val existing = _todoTasks.value ?: return
        _todoTasks.postValue(existing.map {
            if (it.id == id) it.copy(section = TodoSection.TODAY) else it
        })
    }

    fun moveTodoToPlanned(id: Long) {
        val existing = _todoTasks.value ?: return
        _todoTasks.postValue(existing.map {
            if (it.id == id) {
                it.copy(section = TodoSection.PLANNED, scheduledDate = null, scheduledTime = null)
            } else it
        })
    }

    fun toggleTodoDone(id: Long) {
        val existing = _todoTasks.value ?: return
        _todoTasks.postValue(existing.map {
            if (it.id == id) it.copy(isDone = !it.isDone) else it
        })
    }

    private fun promoteScheduledTasks() {
        val tasks = _todoTasks.value ?: return
        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayMillis = todayCal.timeInMillis
        val todayYear = todayCal.get(Calendar.YEAR)
        val todayDay = todayCal.get(Calendar.DAY_OF_YEAR)

        val updated = tasks.map { task ->
            if (task.isRepeatable) {
                val isAlreadyToday = task.scheduledDate?.let { d ->
                    val c = Calendar.getInstance().apply { timeInMillis = d }
                    c.get(Calendar.YEAR) == todayYear && c.get(Calendar.DAY_OF_YEAR) == todayDay
                } ?: false
                if (!isAlreadyToday) {
                    val newScheduledTime = task.scheduledTime?.let { t ->
                        val tc = Calendar.getInstance().apply { timeInMillis = t }
                        Calendar.getInstance().apply {
                            timeInMillis = todayMillis
                            set(Calendar.HOUR_OF_DAY, tc.get(Calendar.HOUR_OF_DAY))
                            set(Calendar.MINUTE, tc.get(Calendar.MINUTE))
                        }.timeInMillis
                    }
                    task.copy(
                        section = TodoSection.TODAY,
                        isDone = false,
                        scheduledDate = todayMillis,
                        scheduledTime = newScheduledTime
                    )
                } else task
            } else if (task.section == TodoSection.PLANNED && task.scheduledDate != null) {
                val taskCal = Calendar.getInstance().apply { timeInMillis = task.scheduledDate }
                if (taskCal.get(Calendar.YEAR) == todayYear && taskCal.get(Calendar.DAY_OF_YEAR) == todayDay) {
                    task.copy(section = TodoSection.TODAY)
                } else task
            } else task
        }
        if (updated != tasks) _todoTasks.value = updated
    }

    fun updateTodoTask(id: Long, name: String, description: String, subtasks: List<TodoSubtask>, scheduledDate: Long?, scheduledTime: Long?, isRepeatable: Boolean) {
        val existing = _todoTasks.value ?: return
        val today = Calendar.getInstance()
        val todayYear = today.get(Calendar.YEAR)
        val todayDay = today.get(Calendar.DAY_OF_YEAR)
        val todayDateOnly = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        _todoTasks.postValue(existing.map {
            if (it.id == id) {
                val isDateToday = scheduledDate != null && Calendar.getInstance().apply {
                    timeInMillis = scheduledDate
                }.let { cal -> cal.get(Calendar.YEAR) == todayYear && cal.get(Calendar.DAY_OF_YEAR) == todayDay }

                val newSection = if (isDateToday && it.section == TodoSection.PLANNED) TodoSection.TODAY else it.section

                // Anchor a freshly-marked-repeatable task to today so it isn't reset on its own creation day.
                val finalDate = if (isRepeatable && scheduledDate == null) todayDateOnly else scheduledDate

                it.copy(
                    text = name.trim().ifBlank { it.text },
                    description = description,
                    subtasks = subtasks.filter { s -> s.text.isNotBlank() },
                    scheduledDate = finalDate,
                    scheduledTime = scheduledTime,
                    section = newSection,
                    isRepeatable = isRepeatable
                )
            } else it
        })
    }

    fun toggleTodoSubtask(taskId: Long, subtaskId: Long) {
        val existing = _todoTasks.value ?: return
        _todoTasks.postValue(existing.map { task ->
            if (task.id == taskId) {
                task.copy(subtasks = task.subtasks.map { sub ->
                    if (sub.id == subtaskId) sub.copy(isDone = !sub.isDone) else sub
                })
            } else task
        })
    }

    // --- Routines ---

    fun addRoutine(routine: Routine) {
        val existing = _routines.value ?: emptyList()
        _routines.postValue(existing + routine)
    }

    fun updateRoutine(routine: Routine) {
        val existing = _routines.value ?: return
        _routines.postValue(existing.map { if (it.id == routine.id) routine else it })
    }

    fun deleteRoutine(id: Long) {
        val existing = _routines.value ?: return
        _routines.postValue(existing.filter { it.id != id })
    }

    fun startRoutine(ctx: Context, routine: Routine) {
        val cur = _uiState.value ?: TimerUiState()
        if (cur.mode == TimerMode.REVIEW) saveCurrentReview()
        cancelTickJob()

        activeRoutine = routine
        currentCycle = 1

        if (routine.useDefaultSettings) {
            reloadDurations(ctx)
        } else {
            focusMinutes = routine.focusMinutes
            reviewMinutes = routine.reviewMinutes
            breakMinutes = routine.breakMinutes
        }

        val millis = minutesToMillis(focusMinutes)
        val checklist = buildChecklist(ctx)
        val hasChecklist = checklist.isNotEmpty()
        val subtasks = routine.subtasks.map { Subtask(text = it.text, description = it.description, minutes = it.minutes) }
        currentFocusStartMillis = System.currentTimeMillis()
        focusLogged = false
        deadlineMillis = System.currentTimeMillis() + millis
        persistPendingFocus(routine.name, currentFocusStartMillis, if (hasChecklist) 0L else deadlineMillis)
        _uiState.postValue(TimerUiState(
            mode = TimerMode.FOCUS,
            remainingMillis = millis,
            isRunning = !hasChecklist,
            taskName = routine.name,
            subtasks = subtasks,
            focusChecklist = checklist,
            checklistCompleted = !hasChecklist
        ))
        if (!hasChecklist) {
            startTicker(TimerMode.FOCUS)
        } else {
            TimerForegroundService.stop(getApplication())
        }
    }

    fun advanceRoutine(ctx: Context) {
        val routine = activeRoutine ?: return
        val cur = _uiState.value ?: return

        when (cur.mode) {
            TimerMode.FOCUS -> {
                logFocusToJournal()
                if (routine.reviewEnabled) {
                    if (!routine.useDefaultSettings) reviewMinutes = routine.reviewMinutes
                    cancelTickJob()
                    val millis = minutesToMillis(reviewMinutes)
                    deadlineMillis = System.currentTimeMillis() + millis
                    _uiState.postValue(cur.copy(
                        mode = TimerMode.REVIEW,
                        remainingMillis = millis,
                        isRunning = true,
                        reviewAnswers = ReviewAnswers()
                    ))
                    startTicker(TimerMode.REVIEW)
                } else if (routine.breakEnabled) {
                    if (!routine.useDefaultSettings) breakMinutes = routine.breakMinutes
                    if (cur.mode == TimerMode.REVIEW) saveCurrentReview()
                    cancelTickJob()
                    val millis = minutesToMillis(breakMinutes)
                    deadlineMillis = System.currentTimeMillis() + millis
                    _uiState.postValue(cur.copy(
                        mode = TimerMode.BREAK,
                        remainingMillis = millis,
                        isRunning = true
                    ))
                    startTicker(TimerMode.BREAK)
                } else {
                    startNextCycleOrFinish(ctx)
                }
            }
            TimerMode.REVIEW -> {
                saveCurrentReview()
                if (routine.breakEnabled) {
                    if (!routine.useDefaultSettings) breakMinutes = routine.breakMinutes
                    cancelTickJob()
                    val millis = minutesToMillis(breakMinutes)
                    deadlineMillis = System.currentTimeMillis() + millis
                    _uiState.postValue(cur.copy(
                        mode = TimerMode.BREAK,
                        remainingMillis = millis,
                        isRunning = true
                    ))
                    startTicker(TimerMode.BREAK)
                } else {
                    startNextCycleOrFinish(ctx)
                }
            }
            TimerMode.BREAK -> {
                startNextCycleOrFinish(ctx)
            }
        }
    }

    private fun startNextCycleOrFinish(ctx: Context) {
        val routine = activeRoutine ?: return
        cancelTickJob()
        if (currentCycle < routine.focusCycles) {
            currentCycle++
            if (!routine.useDefaultSettings) focusMinutes = routine.focusMinutes
            val millis = minutesToMillis(focusMinutes)
            val checklist = buildChecklist(ctx)
            val hasChecklist = checklist.isNotEmpty()
            currentFocusStartMillis = System.currentTimeMillis()
            focusLogged = false
            deadlineMillis = System.currentTimeMillis() + millis
            persistPendingFocus(routine.name, currentFocusStartMillis, if (hasChecklist) 0L else deadlineMillis)
            _uiState.postValue(TimerUiState(
                mode = TimerMode.FOCUS,
                remainingMillis = millis,
                isRunning = !hasChecklist,
                taskName = routine.name,
                subtasks = routine.subtasks.map { Subtask(text = it.text) },
                focusChecklist = checklist,
                checklistCompleted = !hasChecklist
            ))
            if (!hasChecklist) {
                startTicker(TimerMode.FOCUS)
            } else {
                TimerForegroundService.stop(getApplication())
            }
        } else {
            activeRoutine = null
            currentCycle = 0
            clearPendingFocus()
            reloadDurations(ctx)
            _uiState.postValue(TimerUiState(
                mode = TimerMode.FOCUS,
                remainingMillis = minutesToMillis(focusMinutes)
            ))
            TimerForegroundService.stop(getApplication())
        }
    }

    fun advanceToNext(ctx: Context) {
        if (activeRoutine != null) {
            advanceRoutine(ctx)
            return
        }
        val cur = _uiState.value ?: return
        when (cur.mode) {
            TimerMode.FOCUS -> startReview(ctx)
            TimerMode.REVIEW -> startBreak(ctx)
            TimerMode.BREAK -> startFocus(ctx)
        }
    }

    fun advanceFromNotification(ctx: Context, completedMode: TimerMode?) {
        val cur = _uiState.value
        // Only advance if the timer is still sitting at the completed state this
        // notification was posted for. If the user has since started a new session
        // (or the timer has already moved on), the notification is stale — just
        // open the app without touching the running timer.
        if (cur != null && completedMode != null &&
            (cur.mode != completedMode || cur.remainingMillis > 0L)
        ) {
            _navigateHomeEvent.postValue(System.currentTimeMillis())
            return
        }
        advanceToNext(ctx)
        _navigateHomeEvent.postValue(System.currentTimeMillis())
    }

    fun isRoutineActive(): Boolean = activeRoutine != null

    fun getRoutineCycleInfo(): Pair<Int, Int>? {
        val routine = activeRoutine ?: return null
        return currentCycle to routine.focusCycles
    }
}
