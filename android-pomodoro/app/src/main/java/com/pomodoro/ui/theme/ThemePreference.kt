package com.pomodoro.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

enum class ThemeMode { LIGHT, DARK, SYSTEM }

object ThemePreference {
    private const val PREFS = "pomodoro_prefs"
    private const val KEY_THEME = "theme_mode"

    val currentTheme: MutableState<ThemeMode> = mutableStateOf(ThemeMode.SYSTEM)

    fun load(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        currentTheme.value = try {
            ThemeMode.valueOf(stored)
        } catch (_: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }

    fun save(ctx: Context, mode: ThemeMode) {
        currentTheme.value = mode
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, mode.name)
            .apply()
    }
}
