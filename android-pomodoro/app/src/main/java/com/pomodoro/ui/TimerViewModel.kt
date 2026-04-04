package com.pomodoro.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.pomodoro.NotificationHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class TimerMode { FOCUS, REVIEW, BREAK }

data class Subtask(
    val id: Long = System.nanoTime(),
    val text: String = "",
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
    val answers: ReviewAnswers = ReviewAnswers(),
    val timestamp: Long = System.currentTimeMillis()
)

data class TimerUiState(
    val mode: TimerMode = TimerMode.FOCUS,
    val remainingMillis: Long = 0L,
    val isRunning: Boolean = false,
    val taskName: String = "",
    val subtasks: List<Subtask> = emptyList(),
    val reviewAnswers: ReviewAnswers = ReviewAnswers()
)

class TimerViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableLiveData(TimerUiState())
    val uiState: LiveData<TimerUiState> = _uiState

    private val _savedNotes = MutableLiveData<List<SavedReviewNote>>(emptyList())
    val savedNotes: LiveData<List<SavedReviewNote>> = _savedNotes

    private var tickJob: Job? = null

    // defaults (minutes)
    private var focusMinutes = 25
    private var reviewMinutes = 5
    private var breakMinutes = 15

    init {
        NotificationHelper.createChannel(application)
        _uiState.value = TimerUiState(remainingMillis = minutesToMillis(focusMinutes))
    }

    fun reloadDurations(ctx: Context) {
        val prefs = ctx.getSharedPreferences("pomodoro_prefs", Context.MODE_PRIVATE)
        focusMinutes = prefs.getInt("focus_minutes", 25)
        reviewMinutes = prefs.getInt("review_minutes", 5)
        breakMinutes = prefs.getInt("break_minutes", 15)
        val cur = _uiState.value ?: TimerUiState()
        _uiState.postValue(cur.copy(remainingMillis = minutesToMillis(durationForMode(cur.mode))))
    }

    fun toggleRunning() {
        val cur = _uiState.value ?: return
        if (cur.isRunning) stopTicker() else startTicker()
        _uiState.postValue(cur.copy(isRunning = !cur.isRunning))
    }

    private fun startTicker() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val cur = _uiState.value ?: continue
                if (cur.remainingMillis <= 0L) {
                    _uiState.postValue(cur.copy(remainingMillis = 0L, isRunning = false))
                    tickJob?.cancel()
                    NotificationHelper.notifyTimerComplete(
                        getApplication(),
                        cur.mode
                    )
                    break
                } else {
                    _uiState.postValue(cur.copy(remainingMillis = cur.remainingMillis - 1000L))
                }
            }
        }
    }

    private fun stopTicker() {
        tickJob?.cancel()
        tickJob = null
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
        val hasContent = answers.wentWell.isNotEmpty() ||
                answers.didntGoWell.isNotEmpty() ||
                answers.improvements.isNotEmpty()
        if (!hasContent) return
        val note = SavedReviewNote(
            taskName = cur.taskName,
            answers = answers
        )
        val existing = _savedNotes.value ?: emptyList()
        _savedNotes.postValue(listOf(note) + existing)
    }

    fun deleteNote(id: Long) {
        val existing = _savedNotes.value ?: return
        _savedNotes.postValue(existing.filter { it.id != id })
    }

    fun startReview(ctx: Context) {
        reloadDurations(ctx)
        val cur = _uiState.value ?: TimerUiState()
        val millis = minutesToMillis(reviewMinutes)
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
        _uiState.postValue(cur.copy(
            mode = TimerMode.BREAK,
            remainingMillis = millis,
            isRunning = true
        ))
        startTicker()
    }

    fun startFocus(ctx: Context) {
        reloadDurations(ctx)
        val cur = _uiState.value ?: TimerUiState()
        if (cur.mode == TimerMode.REVIEW) saveCurrentReview()
        val millis = minutesToMillis(focusMinutes)
        _uiState.postValue(TimerUiState(
            mode = TimerMode.FOCUS,
            remainingMillis = millis,
            isRunning = true
        ))
        startTicker()
    }
}
