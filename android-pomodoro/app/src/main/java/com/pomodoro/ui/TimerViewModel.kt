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

enum class TodoSection { TODAY, PLANNED }

data class TodoTask(
    val id: Long = System.nanoTime(),
    val text: String = "",
    val section: TodoSection = TodoSection.PLANNED,
    val isDone: Boolean = false
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

class TimerViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableLiveData(TimerUiState())
    val uiState: LiveData<TimerUiState> = _uiState

    private val _todoTasks = MutableLiveData<List<TodoTask>>(emptyList())
    val todoTasks: LiveData<List<TodoTask>> = _todoTasks

    private val _savedNotes = MutableLiveData<List<SavedReviewNote>>(emptyList())
    val savedNotes: LiveData<List<SavedReviewNote>> = _savedNotes

    private val _routines = MutableLiveData<List<Routine>>(emptyList())
    val routines: LiveData<List<Routine>> = _routines

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
        _savedNotes.observeForever { persistNotes(it) }
        _todoTasks.observeForever { persistTodos(it) }
        _routines.observeForever { persistRoutines(it) }
    }

    private fun loadPersistedData() {
        prefs.getString("saved_notes", null)?.let { json ->
            try {
                val type = object : TypeToken<List<SavedReviewNote>>() {}.type
                _savedNotes.value = gson.fromJson(json, type)
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

    private fun persistTodos(todos: List<TodoTask>) {
        prefs.edit().putString("todo_tasks", gson.toJson(todos)).apply()
    }

    private fun persistRoutines(routines: List<Routine>) {
        prefs.edit().putString("routines", gson.toJson(routines)).apply()
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
        _uiState.postValue(cur.copy(isRunning = !cur.isRunning))
    }

    private fun startTicker() {
        tickJob?.cancel()
        val app = getApplication<Application>()
        val cur = _uiState.value ?: return
        TimerForegroundService.start(app, deadlineMillis, cur.mode)
        tickJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val remaining = (deadlineMillis - System.currentTimeMillis()).coerceAtLeast(0L)
                val latest = _uiState.value ?: continue
                if (remaining <= 0L) {
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

    fun syncFromDeadline() {
        if (!TimerForegroundService.isServiceRunning) return
        val remaining = (TimerForegroundService.deadlineMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        val cur = _uiState.value ?: return
        if (remaining <= 0L) {
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

    // --- Backup / Restore ---

    enum class BackupStatus { IDLE, LOADING, SUCCESS, ERROR }

    private val _backupStatus = MutableLiveData(BackupStatus.IDLE)
    val backupStatus: LiveData<BackupStatus> = _backupStatus

    private val _backupMessage = MutableLiveData("")
    val backupMessage: LiveData<String> = _backupMessage

    fun backupToDrive(ctx: Context) {
        val notes = _savedNotes.value ?: emptyList()
        val todos = _todoTasks.value ?: emptyList()
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
            val result = DriveBackupHelper.backup(ctx, notes, todos, settings)
            if (result.isSuccess) {
                _backupStatus.postValue(BackupStatus.SUCCESS)
                _backupMessage.postValue("Backed up ${notes.size} note(s), ${todos.size} task(s), settings")
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
        val cur = _uiState.value ?: TimerUiState()
        val millis = minutesToMillis(reviewMinutes)
        deadlineMillis = System.currentTimeMillis() + millis
        _uiState.postValue(cur.copy(
            mode = TimerMode.REVIEW,
            remainingMillis = millis,
            isRunning = true,
            reviewAnswers = ReviewAnswers()
        ))
        startTicker()
    }

    fun startBreak(ctx: Context) {
        reloadDurations(ctx)
        val cur = _uiState.value ?: TimerUiState()
        if (cur.mode == TimerMode.REVIEW) saveCurrentReview()
        val millis = minutesToMillis(breakMinutes)
        deadlineMillis = System.currentTimeMillis() + millis
        _uiState.postValue(cur.copy(
            mode = TimerMode.BREAK,
            remainingMillis = millis,
            isRunning = true
        ))
        startTicker()
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
        val millis = minutesToMillis(focusMinutes)
        val checklist = buildChecklist(ctx)
        val hasChecklist = checklist.isNotEmpty()
        _uiState.postValue(TimerUiState(
            mode = TimerMode.FOCUS,
            remainingMillis = millis,
            isRunning = !hasChecklist,
            focusChecklist = checklist,
            checklistCompleted = !hasChecklist
        ))
        if (!hasChecklist) {
            deadlineMillis = System.currentTimeMillis() + millis
            startTicker()
        }
    }

    fun startFocusWithTask(ctx: Context, taskName: String) {
        reloadDurations(ctx)
        val cur = _uiState.value ?: TimerUiState()
        if (cur.mode == TimerMode.REVIEW) saveCurrentReview()
        val millis = minutesToMillis(focusMinutes)
        val checklist = buildChecklist(ctx)
        val hasChecklist = checklist.isNotEmpty()
        _uiState.postValue(TimerUiState(
            mode = TimerMode.FOCUS,
            remainingMillis = millis,
            isRunning = !hasChecklist,
            taskName = taskName,
            focusChecklist = checklist,
            checklistCompleted = !hasChecklist
        ))
        if (!hasChecklist) {
            deadlineMillis = System.currentTimeMillis() + millis
            startTicker()
        }
    }

    // --- Todo Tasks ---

    fun addTodoTask(text: String) {
        if (text.isBlank()) return
        val existing = _todoTasks.value ?: emptyList()
        _todoTasks.postValue(existing + TodoTask(text = text.trim()))
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
            if (it.id == id) it.copy(section = TodoSection.PLANNED) else it
        })
    }

    fun toggleTodoDone(id: Long) {
        val existing = _todoTasks.value ?: return
        _todoTasks.postValue(existing.map {
            if (it.id == id) it.copy(isDone = !it.isDone) else it
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
            deadlineMillis = System.currentTimeMillis() + millis
            startTicker()
        }
    }

    fun advanceRoutine(ctx: Context) {
        val routine = activeRoutine ?: return
        val cur = _uiState.value ?: return

        when (cur.mode) {
            TimerMode.FOCUS -> {
                if (routine.reviewEnabled) {
                    if (!routine.useDefaultSettings) reviewMinutes = routine.reviewMinutes
                    val millis = minutesToMillis(reviewMinutes)
                    deadlineMillis = System.currentTimeMillis() + millis
                    _uiState.postValue(cur.copy(
                        mode = TimerMode.REVIEW,
                        remainingMillis = millis,
                        isRunning = true,
                        reviewAnswers = ReviewAnswers()
                    ))
                    startTicker()
                } else if (routine.breakEnabled) {
                    if (!routine.useDefaultSettings) breakMinutes = routine.breakMinutes
                    if (cur.mode == TimerMode.REVIEW) saveCurrentReview()
                    val millis = minutesToMillis(breakMinutes)
                    deadlineMillis = System.currentTimeMillis() + millis
                    _uiState.postValue(cur.copy(
                        mode = TimerMode.BREAK,
                        remainingMillis = millis,
                        isRunning = true
                    ))
                    startTicker()
                } else {
                    startNextCycleOrFinish(ctx)
                }
            }
            TimerMode.REVIEW -> {
                saveCurrentReview()
                if (routine.breakEnabled) {
                    if (!routine.useDefaultSettings) breakMinutes = routine.breakMinutes
                    val millis = minutesToMillis(breakMinutes)
                    deadlineMillis = System.currentTimeMillis() + millis
                    _uiState.postValue(cur.copy(
                        mode = TimerMode.BREAK,
                        remainingMillis = millis,
                        isRunning = true
                    ))
                    startTicker()
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
        if (currentCycle < routine.focusCycles) {
            currentCycle++
            if (!routine.useDefaultSettings) focusMinutes = routine.focusMinutes
            val millis = minutesToMillis(focusMinutes)
            val checklist = buildChecklist(ctx)
            val hasChecklist = checklist.isNotEmpty()
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
                deadlineMillis = System.currentTimeMillis() + millis
                startTicker()
            }
        } else {
            activeRoutine = null
            currentCycle = 0
            reloadDurations(ctx)
            _uiState.postValue(TimerUiState(
                mode = TimerMode.FOCUS,
                remainingMillis = minutesToMillis(focusMinutes)
            ))
        }
    }

    fun isRoutineActive(): Boolean = activeRoutine != null

    fun getRoutineCycleInfo(): Pair<Int, Int>? {
        val routine = activeRoutine ?: return null
        return currentCycle to routine.focusCycles
    }
}
