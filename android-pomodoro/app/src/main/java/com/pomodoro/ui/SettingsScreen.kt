package com.pomodoro.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.RadioButton
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pomodoro.ui.theme.ThemeMode
import com.pomodoro.ui.theme.ThemePreference

object SettingsPrefs {
    private const val PREFS = "pomodoro_prefs"
    private const val KEY_FOCUS = "focus_minutes"
    private const val KEY_REVIEW = "review_minutes"
    private const val KEY_BREAK = "break_minutes"

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
fun SettingsScreen(
    ctx: Context,
    viewModel: TimerViewModel
) {
    val (defFocus, defReview, defBreak) = remember { SettingsPrefs.loadPrefs(ctx) }

    val focusText = remember { mutableStateOf(defFocus.toString()) }
    val reviewText = remember { mutableStateOf(defReview.toString()) }
    val breakText = remember { mutableStateOf(defBreak.toString()) }

    // Auto-save when values change
    fun saveNow() {
        val f = focusText.value.toIntOrNull() ?: defFocus
        val r = reviewText.value.toIntOrNull() ?: defReview
        val b = breakText.value.toIntOrNull() ?: defBreak
        SettingsPrefs.savePrefs(ctx, f, r, b)
        viewModel.reloadDurations(ctx)
    }

    DisposableEffect(Unit) {
        onDispose { saveNow() }
    }

    val selectedTheme = ThemePreference.currentTheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Settings",
            style = MaterialTheme.typography.h5,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.onBackground,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Theme Section
        SectionHeader(title = "APPEARANCE")

        Card(
            backgroundColor = MaterialTheme.colors.surface,
            shape = RoundedCornerShape(16.dp),
            elevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Theme",
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colors.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                ThemeOption(
                    label = "Light",
                    description = "Always use light theme",
                    selected = selectedTheme.value == ThemeMode.LIGHT,
                    onClick = { ThemePreference.save(ctx, ThemeMode.LIGHT) }
                )

                ThemeOption(
                    label = "Dark",
                    description = "Always use dark theme",
                    selected = selectedTheme.value == ThemeMode.DARK,
                    onClick = { ThemePreference.save(ctx, ThemeMode.DARK) }
                )

                ThemeOption(
                    label = "System",
                    description = "Follow system setting",
                    selected = selectedTheme.value == ThemeMode.SYSTEM,
                    onClick = { ThemePreference.save(ctx, ThemeMode.SYSTEM) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Timer Duration Section
        SectionHeader(title = "TIMER DURATIONS")

        Card(
            backgroundColor = MaterialTheme.colors.surface,
            shape = RoundedCornerShape(16.dp),
            elevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingsField(label = "Focus (minutes)", state = focusText)
                Spacer(modifier = Modifier.height(16.dp))
                SettingsField(label = "Review (minutes)", state = reviewText)
                Spacer(modifier = Modifier.height(16.dp))
                SettingsField(label = "Break (minutes)", state = breakText)
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.caption,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colors.onBackground.copy(alpha = 0.5f),
        letterSpacing = 2.sp,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

@Composable
private fun ThemeOption(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colors.primary,
                unselectedColor = MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
            )
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.body1,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colors.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
            )
        }
    }
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
