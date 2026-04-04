package com.pomodoro.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

// Compose-based settings dialog state holder
object SettingsState {
    val isVisible = mutableStateOf(false)
}

object SettingsScreen {

    private const val PREFS = "pomodoro_prefs"
    private const val KEY_FOCUS = "focus_minutes"
    private const val KEY_REVIEW = "review_minutes"
    private const val KEY_BREAK = "break_minutes"

    fun show(ctx: Context, vm: TimerViewModel) {
        // Legacy bridge - triggers Compose dialog
        SettingsState.isVisible.value = true
    }

    fun loadPrefs(ctx: Context): Triple<Int, Int, Int> {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Triple(
            prefs.getInt(KEY_FOCUS, 25),
            prefs.getInt(KEY_REVIEW, 5),
            prefs.getInt(KEY_BREAK, 15)
        )
    }

    fun savePrefs(ctx: Context, focus: Int, review: Int, brk: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_FOCUS, focus)
            .putInt(KEY_REVIEW, review)
            .putInt(KEY_BREAK, brk)
            .apply()
    }
}

@Composable
fun SettingsDialog(ctx: Context, viewModel: TimerViewModel) {
    if (!SettingsState.isVisible.value) return

    val (defFocus, defReview, defBreak) = remember { SettingsScreen.loadPrefs(ctx) }

    val focusText = remember { mutableStateOf(defFocus.toString()) }
    val reviewText = remember { mutableStateOf(defReview.toString()) }
    val breakText = remember { mutableStateOf(defBreak.toString()) }

    AlertDialog(
        onDismissRequest = { SettingsState.isVisible.value = false },
        shape = RoundedCornerShape(24.dp),
        backgroundColor = MaterialTheme.colors.surface,
        title = {
            Text(
                text = "Timer Settings",
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onSurface
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(8.dp))
                SettingsField(label = "Focus (min)", state = focusText)
                Spacer(modifier = Modifier.height(12.dp))
                SettingsField(label = "Review (min)", state = reviewText)
                Spacer(modifier = Modifier.height(12.dp))
                SettingsField(label = "Break (min)", state = breakText)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val f = focusText.value.toIntOrNull() ?: defFocus
                val r = reviewText.value.toIntOrNull() ?: defReview
                val b = breakText.value.toIntOrNull() ?: defBreak
                SettingsScreen.savePrefs(ctx, f, r, b)
                viewModel.reloadDurations(ctx)
                SettingsState.isVisible.value = false
            }) {
                Text(
                    "Save",
                    color = MaterialTheme.colors.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { SettingsState.isVisible.value = false }) {
                Text(
                    "Cancel",
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .padding(16.dp)
    )
}

@Composable
private fun SettingsField(label: String, state: MutableState<String>) {
    OutlinedTextField(
        value = state.value,
        onValueChange = { state.value = it.filter { c -> c.isDigit() } },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.outlinedTextFieldColors(
            focusedBorderColor = MaterialTheme.colors.primary,
            unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.2f),
            textColor = MaterialTheme.colors.onSurface,
            cursorColor = MaterialTheme.colors.primary,
            focusedLabelColor = MaterialTheme.colors.primary,
            unfocusedLabelColor = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
