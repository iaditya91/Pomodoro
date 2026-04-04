package com.pomodoro.ui

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pomodoro.ui.theme.*

@Composable
fun MainScreen(viewModel: TimerViewModel = viewModel()) {
    val uiState by viewModel.uiState.observeAsState()
    val ctx: Context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    val state = uiState ?: return

    // Mode-specific accent color
    val accentColor by animateColorAsState(
        targetValue = modeAccentColor(state.mode, isDark),
        animationSpec = tween(400),
        label = "accentColor"
    )

    // Progress for circular indicator
    val totalMillis = remember(state.mode) {
        viewModel.totalMillisForMode(state.mode)
    }
    val progress by animateFloatAsState(
        targetValue = if (totalMillis > 0) state.remainingMillis.toFloat() / totalMillis else 1f,
        animationSpec = tween(300),
        label = "progress"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top: Mode selector chips
        Spacer(modifier = Modifier.height(16.dp))
        ModeChips(currentMode = state.mode, accentColor = accentColor)

        // Center: Circular timer
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(280.dp)
        ) {
            val trackColor = if (isDark) TrackDark else TrackLight
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Background track
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                )
                // Progress arc
                drawArc(
                    color = accentColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Timer text inside circle
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatTime(state.remainingMillis),
                    style = MaterialTheme.typography.h3,
                    color = MaterialTheme.colors.onBackground,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = modeDisplayName(state.mode),
                    style = MaterialTheme.typography.body2,
                    color = accentColor,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.sp
                )
            }
        }

        // Bottom: Controls
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 32.dp)
        ) {
            // Play/Pause + Skip buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Play/Pause button (large, filled)
                IconButton(
                    onClick = { viewModel.toggleRunning() },
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                ) {
                    Icon(
                        imageVector = if (state.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isRunning) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.width(24.dp))

                // Skip to next mode
                IconButton(
                    onClick = {
                        when (state.mode) {
                            TimerMode.FOCUS -> viewModel.startReview(ctx)
                            TimerMode.REVIEW -> viewModel.startBreak(ctx)
                            TimerMode.BREAK -> viewModel.startFocus(ctx)
                        }
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colors.surface)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Skip to next",
                        tint = MaterialTheme.colors.onSurface,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Next mode label
            Text(
                text = nextModeLabel(state.mode),
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Settings button
            IconButton(
                onClick = { openSettings(ctx, viewModel) },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colors.surface)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun ModeChips(currentMode: TimerMode, accentColor: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimerMode.values().forEach { mode ->
            val isSelected = mode == currentMode
            val chipBackground by animateColorAsState(
                targetValue = if (isSelected) accentColor.copy(alpha = 0.15f)
                else MaterialTheme.colors.surface,
                animationSpec = tween(300),
                label = "chipBg"
            )
            val chipTextColor by animateColorAsState(
                targetValue = if (isSelected) accentColor
                else MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                animationSpec = tween(300),
                label = "chipText"
            )

            Card(
                backgroundColor = chipBackground,
                elevation = if (isSelected) 2.dp else 0.dp,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = modeDisplayName(mode),
                    style = MaterialTheme.typography.caption,
                    color = chipTextColor,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)
                )
            }
        }
    }
}

private fun modeAccentColor(mode: TimerMode, isDark: Boolean): Color = when (mode) {
    TimerMode.FOCUS -> if (isDark) FocusColorDark else FocusColor
    TimerMode.REVIEW -> if (isDark) ReviewColorDark else ReviewColor
    TimerMode.BREAK -> if (isDark) BreakColorDark else BreakColor
}

private fun modeDisplayName(mode: TimerMode): String = when (mode) {
    TimerMode.FOCUS -> "FOCUS"
    TimerMode.REVIEW -> "REVIEW"
    TimerMode.BREAK -> "BREAK"
}

private fun nextModeLabel(mode: TimerMode): String = when (mode) {
    TimerMode.FOCUS -> "Next: Review"
    TimerMode.REVIEW -> "Next: Break"
    TimerMode.BREAK -> "Next: Focus"
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%02d:%02d".format(min, sec)
}

private fun openSettings(ctx: Context, viewModel: TimerViewModel) {
    SettingsScreen.show(ctx, viewModel)
}
