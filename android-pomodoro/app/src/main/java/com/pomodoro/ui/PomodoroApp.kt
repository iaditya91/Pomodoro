package com.pomodoro.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.pomodoro.ui.theme.PomodoroTheme
import com.pomodoro.ui.theme.ThemePreference

@Composable
fun PomodoroApp() {
    val ctx = LocalContext.current

    LaunchedEffect(Unit) {
        ThemePreference.load(ctx)
    }

    PomodoroTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.background
        ) {
            PomodoroNavHost()
        }
    }
}
