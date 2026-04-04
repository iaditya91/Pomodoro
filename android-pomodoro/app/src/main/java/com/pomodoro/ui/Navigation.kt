package com.pomodoro.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PomodoroNavHost(modifier: Modifier = Modifier) {
    val viewModel: TimerViewModel = viewModel()
    val ctx = LocalContext.current

    Box(modifier = modifier) {
        MainScreen(viewModel = viewModel)
        SettingsDialog(ctx = ctx, viewModel = viewModel)
    }
}
