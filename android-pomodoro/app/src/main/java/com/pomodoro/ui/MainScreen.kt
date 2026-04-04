package com.pomodoro.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pomodoro.ui.theme.*

@Composable
fun MainScreen(viewModel: TimerViewModel = viewModel()) {
    val uiState by viewModel.uiState.observeAsState()
    val ctx = LocalContext.current
    val isDark = when (ThemePreference.currentTheme.value) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val state = uiState ?: return

    // Load checklist when entering focus mode
    LaunchedEffect(state.mode) {
        if (state.mode == TimerMode.FOCUS && !state.isRunning && state.focusChecklist.isEmpty()) {
            viewModel.loadFocusChecklist(ctx)
        }
    }

    // Show toast on generate error
    LaunchedEffect(state.generateError) {
        state.generateError?.let {
            Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
            viewModel.clearGenerateError()
        }
    }

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
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top: Mode selector chips
        Spacer(modifier = Modifier.height(16.dp))
        ModeChips(currentMode = state.mode, accentColor = accentColor)

        Spacer(modifier = Modifier.height(24.dp))

        // Center: Circular timer
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(260.dp)
        ) {
            val trackColor = if (isDark) TrackDark else TrackLight
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = accentColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                )
            }

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

        Spacer(modifier = Modifier.height(20.dp))

        // Focus checklist — must complete before timer runs
        AnimatedVisibility(
            visible = state.mode == TimerMode.FOCUS && state.focusChecklist.isNotEmpty() && !state.checklistCompleted,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            FocusChecklistCard(
                checklist = state.focusChecklist,
                accentColor = accentColor,
                onToggle = { viewModel.toggleChecklistItem(it) }
            )
        }

        // Auto-start timer when checklist completed
        LaunchedEffect(state.checklistCompleted, state.mode) {
            if (state.mode == TimerMode.FOCUS && state.checklistCompleted
                && !state.isRunning && state.focusChecklist.isNotEmpty()
                && state.focusChecklist.all { it.isChecked }) {
                viewModel.toggleRunning()
            }
        }

        // Task name & subtasks — visible only in FOCUS mode
        AnimatedVisibility(
            visible = state.mode == TimerMode.FOCUS,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            TaskSection(
                taskName = state.taskName,
                subtasks = state.subtasks,
                accentColor = accentColor,
                isGenerating = state.isGenerating,
                onTaskNameChange = { viewModel.updateTaskName(it) },
                onAddSubtask = { viewModel.addSubtask(it) },
                onToggleSubtask = { viewModel.toggleSubtask(it) },
                onRemoveSubtask = { viewModel.removeSubtask(it) },
                onGenerateMiniTasks = { viewModel.generateMiniTasks(it) }
            )
        }

        // Review questions — visible only in REVIEW mode
        AnimatedVisibility(
            visible = state.mode == TimerMode.REVIEW,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            ReviewSection(
                answers = state.reviewAnswers,
                accentColor = accentColor,
                onAddItem = { q, text -> viewModel.addReviewItem(q, text) },
                onRemoveItem = { q, id -> viewModel.removeReviewItem(q, id) }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Controls
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 32.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                val canPlay = state.mode != TimerMode.FOCUS || state.checklistCompleted

                IconButton(
                    onClick = { if (canPlay) viewModel.toggleRunning() },
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(if (canPlay) accentColor else accentColor.copy(alpha = 0.3f))
                ) {
                    Icon(
                        imageVector = if (state.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isRunning) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.width(24.dp))

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

            Text(
                text = nextModeLabel(state.mode),
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun TaskSection(
    taskName: String,
    subtasks: List<Subtask>,
    accentColor: Color,
    isGenerating: Boolean,
    onTaskNameChange: (String) -> Unit,
    onAddSubtask: (String) -> Unit,
    onToggleSubtask: (Long) -> Unit,
    onRemoveSubtask: (Long) -> Unit,
    onGenerateMiniTasks: (String) -> Unit
) {
    var newSubtaskText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Task name input + AI button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = taskName,
                onValueChange = onTaskNameChange,
                placeholder = {
                    Text(
                        "What are you working on?",
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.35f)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.15f),
                    textColor = MaterialTheme.colors.onSurface,
                    cursorColor = accentColor,
                    backgroundColor = MaterialTheme.colors.surface
                ),
                textStyle = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Medium),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // AI generate button for main task
            IconButton(
                onClick = { if (taskName.isNotBlank()) onGenerateMiniTasks(taskName) },
                enabled = taskName.isNotBlank() && !isGenerating,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (taskName.isNotBlank() && !isGenerating)
                            accentColor.copy(alpha = 0.15f)
                        else
                            MaterialTheme.colors.onSurface.copy(alpha = 0.05f)
                    )
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        color = accentColor,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Generate mini-tasks",
                        tint = if (taskName.isNotBlank()) accentColor
                        else MaterialTheme.colors.onSurface.copy(alpha = 0.2f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Subtask list
        subtasks.forEach { subtask ->
            SubtaskRow(
                subtask = subtask,
                accentColor = accentColor,
                isGenerating = isGenerating,
                onToggle = { onToggleSubtask(subtask.id) },
                onRemove = { onRemoveSubtask(subtask.id) },
                onGenerate = { onGenerateMiniTasks(subtask.text) }
            )
        }

        // Add subtask input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newSubtaskText,
                onValueChange = { newSubtaskText = it },
                placeholder = {
                    Text(
                        "Add a subtask...",
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.3f),
                        style = MaterialTheme.typography.body2
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = accentColor.copy(alpha = 0.5f),
                    unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.1f),
                    textColor = MaterialTheme.colors.onSurface,
                    cursorColor = accentColor,
                    backgroundColor = MaterialTheme.colors.surface
                ),
                textStyle = MaterialTheme.typography.body2,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (newSubtaskText.isNotBlank()) {
                            onAddSubtask(newSubtaskText.trim())
                            newSubtaskText = ""
                        }
                    }
                ),
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (newSubtaskText.isNotBlank()) {
                        onAddSubtask(newSubtaskText.trim())
                        newSubtaskText = ""
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add subtask",
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SubtaskRow(
    subtask: Subtask,
    accentColor: Color,
    isGenerating: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    onGenerate: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = subtask.isDone,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = accentColor,
                uncheckedColor = MaterialTheme.colors.onSurface.copy(alpha = 0.3f),
                checkmarkColor = Color.White
            )
        )

        Text(
            text = subtask.text,
            style = MaterialTheme.typography.body2,
            color = if (subtask.isDone)
                MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
            else
                MaterialTheme.colors.onSurface,
            textDecoration = if (subtask.isDone) TextDecoration.LineThrough else TextDecoration.None,
            modifier = Modifier.weight(1f)
        )

        // AI generate for this subtask
        if (!subtask.isDone) {
            IconButton(
                onClick = onGenerate,
                enabled = !isGenerating,
                modifier = Modifier.size(28.dp)
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        color = accentColor.copy(alpha = 0.5f),
                        strokeWidth = 1.5.dp,
                        modifier = Modifier.size(14.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Break down this subtask",
                        tint = accentColor.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove subtask",
                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun ReviewSection(
    answers: ReviewAnswers,
    accentColor: Color,
    onAddItem: (ReviewQuestion, String) -> Unit,
    onRemoveItem: (ReviewQuestion, Long) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ReviewQuestionCard(
            emoji = "\u2705",
            title = "What went well?",
            items = answers.wentWell,
            accentColor = accentColor,
            onAdd = { onAddItem(ReviewQuestion.WENT_WELL, it) },
            onRemove = { onRemoveItem(ReviewQuestion.WENT_WELL, it) }
        )

        ReviewQuestionCard(
            emoji = "\u26A0\uFE0F",
            title = "What didn't go well?",
            items = answers.didntGoWell,
            accentColor = accentColor,
            onAdd = { onAddItem(ReviewQuestion.DIDNT_GO_WELL, it) },
            onRemove = { onRemoveItem(ReviewQuestion.DIDNT_GO_WELL, it) }
        )

        ReviewQuestionCard(
            emoji = "\uD83D\uDCA1",
            title = "Improvements for next time?",
            items = answers.improvements,
            accentColor = accentColor,
            onAdd = { onAddItem(ReviewQuestion.IMPROVEMENTS, it) },
            onRemove = { onRemoveItem(ReviewQuestion.IMPROVEMENTS, it) }
        )
    }
}

@Composable
private fun ReviewQuestionCard(
    emoji: String,
    title: String,
    items: List<ReviewItem>,
    accentColor: Color,
    onAdd: (String) -> Unit,
    onRemove: (Long) -> Unit
) {
    var inputText by remember { mutableStateOf("") }

    Card(
        backgroundColor = MaterialTheme.colors.surface,
        shape = RoundedCornerShape(16.dp),
        elevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = emoji, fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.subtitle2,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colors.onSurface
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Existing items
            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "\u2022",
                        color = accentColor,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(start = 4.dp, end = 10.dp)
                    )
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onRemove(item.id) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Add input
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            "Add an item...",
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.3f),
                            style = MaterialTheme.typography.body2
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = accentColor.copy(alpha = 0.5f),
                        unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.1f),
                        textColor = MaterialTheme.colors.onSurface,
                        cursorColor = accentColor
                    ),
                    textStyle = MaterialTheme.typography.body2,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (inputText.isNotBlank()) {
                                onAdd(inputText.trim())
                                inputText = ""
                            }
                        }
                    ),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onAdd(inputText.trim())
                            inputText = ""
                        }
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add",
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusChecklistCard(
    checklist: List<FocusCheckItem>,
    accentColor: Color,
    onToggle: (Int) -> Unit
) {
    Card(
        backgroundColor = MaterialTheme.colors.surface,
        shape = RoundedCornerShape(16.dp),
        elevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Before you start",
                style = MaterialTheme.typography.subtitle2,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colors.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            checklist.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onToggle(index) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = item.isChecked,
                        onCheckedChange = { onToggle(index) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = accentColor,
                            uncheckedColor = MaterialTheme.colors.onSurface.copy(alpha = 0.3f),
                            checkmarkColor = Color.White
                        )
                    )
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.body2,
                        color = if (item.isChecked)
                            MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                        else
                            MaterialTheme.colors.onSurface,
                        textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None
                    )
                }
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
