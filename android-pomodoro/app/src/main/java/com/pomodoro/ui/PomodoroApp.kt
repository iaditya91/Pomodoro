package com.pomodoro.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pomodoro.ui.theme.PomodoroTheme

@Composable
fun PomodoroApp() {
    PomodoroTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.background
        ) {
            PomodoroNavHost()
        }
    }
}
