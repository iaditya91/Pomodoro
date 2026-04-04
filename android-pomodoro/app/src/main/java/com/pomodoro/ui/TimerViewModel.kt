package com.pomodoro.ui

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class TimerMode { FOCUS, REVIEW, BREAK }

data class TimerUiState(
    val mode: TimerMode = TimerMode.FOCUS,
    val remainingMillis: Long = 0L,
    val isRunning: Boolean = false
)

class TimerViewModel : ViewModel() {
    private val _uiState = MutableLiveData(TimerUiState())
    val uiState: LiveData<TimerUiState> = _uiState

    private var tickJob: Job? = null

    // defaults (minutes)
    private var focusMinutes = 25
    private var reviewMinutes = 5
    private var breakMinutes = 15

    fun reloadDurations(ctx: Context) {
        val prefs = ctx.getSharedPreferences("pomodoro_prefs", Context.MODE_PRIVATE)
        focusMinutes = prefs.getInt("focus_minutes", 25)
        reviewMinutes = prefs.getInt("review_minutes", 5)
        breakMinutes = prefs.getInt("break_minutes", 15)
        val cur = _uiState.value ?: TimerUiState()
        _uiState.postValue(cur.copy(remainingMillis = minutesToMillis(durationForMode(cur.mode))))
    }

    init {
        // initialize remaining time using defaults
        _uiState.value = TimerUiState(remainingMillis = minutesToMillis(focusMinutes))
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
                    // auto-stop when reaching zero
                    _uiState.postValue(cur.copy(remainingMillis = 0L, isRunning = false))
                    tickJob?.cancel()
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

    fun startReview(ctx: Context) {
        reloadDurations(ctx)
        val millis = minutesToMillis(reviewMinutes)
        _uiState.postValue(TimerUiState(mode = TimerMode.REVIEW, remainingMillis = millis, isRunning = true))
        startTicker()
    }

    fun startBreak(ctx: Context) {
        reloadDurations(ctx)
        val millis = minutesToMillis(breakMinutes)
        _uiState.postValue(TimerUiState(mode = TimerMode.BREAK, remainingMillis = millis, isRunning = true))
        startTicker()
    }

    fun startFocus(ctx: Context) {
        reloadDurations(ctx)
        val millis = minutesToMillis(focusMinutes)
        _uiState.postValue(TimerUiState(mode = TimerMode.FOCUS, remainingMillis = millis, isRunning = true))
        startTicker()
    }
}
