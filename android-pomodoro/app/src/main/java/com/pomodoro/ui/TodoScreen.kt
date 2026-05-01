package com.pomodoro.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.filled.Edit
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun TodoScreen(
    viewModel: TimerViewModel,
    onStartTask: () -> Unit
) {
    val tasks by viewModel.todoTasks.observeAsState(emptyList())
    val ctx = LocalContext.current
    var inputText by remember { mutableStateOf("") }

    val todayTasks = tasks.filter { it.section == TodoSection.TODAY }
    val plannedTasks = tasks.filter { it.section == TodoSection.PLANNED }

    var todayExpanded by rememberSaveable { mutableStateOf(true) }
    var plannedExpanded by rememberSaveable { mutableStateOf(true) }
    var editingTask by remember { mutableStateOf<TodoTask?>(null) }
    var showTimebox by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Todo",
                    style = MaterialTheme.typography.h5,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onBackground,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${tasks.size} task${if (tasks.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            IconButton(
                onClick = { showTimebox = true },
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        MaterialTheme.colors.primary.copy(alpha = 0.12f),
                        RoundedCornerShape(12.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = "Open timebox",
                    tint = MaterialTheme.colors.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Add task input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text(
                        "Add a task...",
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                    )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    backgroundColor = MaterialTheme.colors.surface,
                    focusedBorderColor = MaterialTheme.colors.primary,
                    unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.15f),
                    cursorColor = MaterialTheme.colors.primary,
                    textColor = MaterialTheme.colors.onSurface
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (inputText.isNotBlank()) {
                            viewModel.addTodoTask(inputText)
                            inputText = ""
                        }
                    }
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        viewModel.addTodoTask(inputText)
                        inputText = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        MaterialTheme.colors.primary,
                        RoundedCornerShape(12.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add task",
                    tint = MaterialTheme.colors.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (tasks.isEmpty()) {
            EmptyTodoPlaceholder()
        } else {
            // Today section
            SectionHeader(
                title = "Today",
                count = todayTasks.size,
                expanded = todayExpanded,
                onToggle = { todayExpanded = !todayExpanded }
            )

            AnimatedVisibility(
                visible = todayExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    if (todayTasks.isEmpty()) {
                        Text(
                            text = "No tasks for today",
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onBackground.copy(alpha = 0.4f),
                            modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp)
                        )
                    } else {
                        todayTasks.forEach { task ->
                            Spacer(modifier = Modifier.height(8.dp))
                            TodayTaskCard(
                                task = task,
                                onToggleDone = { viewModel.toggleTodoDone(task.id) },
                                onPlay = {
                                    viewModel.startFocusWithTask(ctx, task.text, task.subtasks)
                                    onStartTask()
                                },
                                onDelete = { viewModel.removeTodoTask(task.id) },
                                onEdit = { editingTask = task }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Planned section
            SectionHeader(
                title = "Planned",
                count = plannedTasks.size,
                expanded = plannedExpanded,
                onToggle = { plannedExpanded = !plannedExpanded }
            )

            AnimatedVisibility(
                visible = plannedExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    if (plannedTasks.isEmpty()) {
                        Text(
                            text = "No planned tasks",
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onBackground.copy(alpha = 0.4f),
                            modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp)
                        )
                    } else {
                        plannedTasks.forEach { task ->
                            Spacer(modifier = Modifier.height(8.dp))
                            PlannedTaskCard(
                                task = task,
                                onPlay = {
                                    viewModel.startFocusWithTask(ctx, task.text, task.subtasks)
                                    onStartTask()
                                },
                                onDelete = { viewModel.removeTodoTask(task.id) },
                                onMove = { viewModel.moveTodoToToday(task.id) },
                                onEdit = { editingTask = task }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }

    // Task detail editing dialog
    editingTask?.let { task ->
        TaskDetailDialog(
            task = task,
            isToday = task.section == TodoSection.TODAY,
            onDismiss = { editingTask = null },
            onSave = { name, desc, subtasks, date, time ->
                viewModel.updateTodoTask(task.id, name, desc, subtasks, date, time)
                editingTask = null
            },
            onMovePlanned = if (task.section == TodoSection.TODAY) {
                { viewModel.moveTodoToPlanned(task.id) }
            } else null
        )
    }

    if (showTimebox) {
        TimeboxDialog(
            tasks = tasks,
            onDismiss = { showTimebox = false },
            onAddAtSlot = { text, slotMillis ->
                viewModel.addTodoTaskAtSlot(text, slotMillis)
            },
            onAssignToSlot = { id, slotMillis ->
                viewModel.assignTodoToSlot(id, slotMillis)
            },
            onUnschedule = { id -> viewModel.unscheduleTodoTime(id) },
            onEditTask = { task -> editingTask = task }
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.subtitle1,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colors.onBackground
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "$count",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.weight(1f))

        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = MaterialTheme.colors.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun EmptyTodoPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "\u2705",
            fontSize = 48.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No tasks yet",
            style = MaterialTheme.typography.h6,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Add a task and tap play to start a focus session",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun TodayTaskCard(
    task: TodoTask,
    onToggleDone: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    Card(
        backgroundColor = MaterialTheme.colors.surface,
        shape = RoundedCornerShape(16.dp),
        elevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .clickable { onToggleDone() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = task.text,
                    style = MaterialTheme.typography.body1.copy(
                        textDecoration = if (task.isDone) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (task.isDone)
                        MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                    else
                        MaterialTheme.colors.onSurface,
                    modifier = Modifier.weight(1f)
                )

                if (!task.isDone) {
                    IconButton(
                        onClick = onPlay,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Start focus session",
                            tint = MaterialTheme.colors.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit task details",
                            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete task",
                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.35f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Show description, subtask count, and time if set
            if (task.description.isNotBlank() || task.scheduledTime != null || task.subtasks.isNotEmpty()) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)) {
                    if (task.description.isNotBlank()) {
                        Text(
                            text = task.description,
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.55f),
                            maxLines = 2
                        )
                    }
                    if (task.subtasks.isNotEmpty()) {
                        val done = task.subtasks.count { it.isDone }
                        Text(
                            text = "$done/${task.subtasks.size} subtasks",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    if (task.scheduledTime != null) {
                        Text(
                            text = timeFormat.format(Date(task.scheduledTime)),
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.primary.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

        }
    }
}

@Composable
private fun PlannedTaskCard(
    task: TodoTask,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    onEdit: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    Card(
        backgroundColor = MaterialTheme.colors.surface,
        shape = RoundedCornerShape(16.dp),
        elevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = task.text,
                    style = MaterialTheme.typography.body1,
                    color = MaterialTheme.colors.onSurface,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onPlay,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start focus session",
                        tint = MaterialTheme.colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit task details",
                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete task",
                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.35f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Show description, subtask count, and scheduled info if set
            if (task.description.isNotBlank() || task.subtasks.isNotEmpty() || task.scheduledDate != null || task.scheduledTime != null) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)) {
                    if (task.description.isNotBlank()) {
                        Text(
                            text = task.description,
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.55f),
                            maxLines = 2
                        )
                    }
                    if (task.subtasks.isNotEmpty()) {
                        val done = task.subtasks.count { it.isDone }
                        Text(
                            text = "$done/${task.subtasks.size} subtasks",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    val scheduleInfo = buildString {
                        if (task.scheduledDate != null) append(dateFormat.format(Date(task.scheduledDate)))
                        if (task.scheduledTime != null) {
                            if (isNotEmpty()) append(" at ")
                            append(timeFormat.format(Date(task.scheduledTime)))
                        }
                    }
                    if (scheduleInfo.isNotEmpty()) {
                        Text(
                            text = scheduleInfo,
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.primary.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            // Move to Today option
            Text(
                text = "Move to Today",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable { onMove() }
                    .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
            )
        }
    }
}

@Composable
private fun TaskDetailDialog(
    task: TodoTask,
    isToday: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String, subtasks: List<TodoSubtask>, date: Long?, time: Long?) -> Unit,
    onMovePlanned: (() -> Unit)? = null
) {
    val ctx = LocalContext.current
    var taskName by remember { mutableStateOf(task.text) }
    var description by remember { mutableStateOf(task.description) }
    var subtasks by remember { mutableStateOf(task.subtasks) }
    var newSubtaskText by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(task.scheduledDate) }
    var selectedTime by remember { mutableStateOf(task.scheduledTime) }

    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            backgroundColor = MaterialTheme.colors.surface,
            elevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Task name field
                Text(
                    text = "Task Name",
                    style = MaterialTheme.typography.caption,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = taskName,
                    onValueChange = { taskName = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        backgroundColor = MaterialTheme.colors.background,
                        focusedBorderColor = MaterialTheme.colors.primary,
                        unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.15f),
                        cursorColor = MaterialTheme.colors.primary,
                        textColor = MaterialTheme.colors.onSurface
                    ),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Bold),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Description field
                Text(
                    text = "Description",
                    style = MaterialTheme.typography.caption,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = {
                        Text(
                            "Add details...",
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        backgroundColor = MaterialTheme.colors.background,
                        focusedBorderColor = MaterialTheme.colors.primary,
                        unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.15f),
                        cursorColor = MaterialTheme.colors.primary,
                        textColor = MaterialTheme.colors.onSurface
                    ),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Subtasks section
                Text(
                    text = "Subtasks",
                    style = MaterialTheme.typography.caption,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(4.dp))

                subtasks.forEach { sub ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = sub.text,
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                subtasks = subtasks.filter { it.id != sub.id }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove subtask",
                                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.35f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newSubtaskText,
                        onValueChange = { newSubtaskText = it },
                        placeholder = {
                            Text(
                                "Add subtask...",
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            backgroundColor = MaterialTheme.colors.background,
                            focusedBorderColor = MaterialTheme.colors.primary,
                            unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.15f),
                            cursorColor = MaterialTheme.colors.primary,
                            textColor = MaterialTheme.colors.onSurface
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (newSubtaskText.isNotBlank()) {
                                    subtasks = subtasks + TodoSubtask(text = newSubtaskText.trim())
                                    newSubtaskText = ""
                                }
                            }
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (newSubtaskText.isNotBlank()) {
                                subtasks = subtasks + TodoSubtask(text = newSubtaskText.trim())
                                newSubtaskText = ""
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                MaterialTheme.colors.primary,
                                RoundedCornerShape(10.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add subtask",
                            tint = MaterialTheme.colors.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Date picker row
                Text(
                    text = "Date",
                    style = MaterialTheme.typography.caption,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colors.background,
                            RoundedCornerShape(12.dp)
                        )
                        .let { mod ->
                            if (isToday) mod else mod.clickable {
                                val cal = Calendar.getInstance()
                                if (selectedDate != null) cal.timeInMillis = selectedDate!!
                                DatePickerDialog(
                                    ctx,
                                    { _, y, m, d ->
                                        val picked = Calendar.getInstance()
                                        picked.set(y, m, d, 0, 0, 0)
                                        picked.set(Calendar.MILLISECOND, 0)
                                        selectedDate = picked.timeInMillis
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isToday) "Today"
                        else if (selectedDate != null) dateFormat.format(Date(selectedDate!!))
                        else "Pick a date",
                        style = MaterialTheme.typography.body1,
                        color = if (isToday)
                            MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                        else if (selectedDate != null)
                            MaterialTheme.colors.onSurface
                        else
                            MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                    )
                    if (isToday) {
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "Disabled for today tasks",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Time picker row
                Text(
                    text = "Time",
                    style = MaterialTheme.typography.caption,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colors.background,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            val cal = Calendar.getInstance()
                            if (selectedTime != null) cal.timeInMillis = selectedTime!!
                            TimePickerDialog(
                                ctx,
                                { _, h, m ->
                                    val picked = Calendar.getInstance()
                                    picked.set(Calendar.HOUR_OF_DAY, h)
                                    picked.set(Calendar.MINUTE, m)
                                    picked.set(Calendar.SECOND, 0)
                                    picked.set(Calendar.MILLISECOND, 0)
                                    selectedTime = picked.timeInMillis
                                },
                                cal.get(Calendar.HOUR_OF_DAY),
                                cal.get(Calendar.MINUTE),
                                false
                            ).show()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedTime != null) timeFormat.format(Date(selectedTime!!))
                        else "Pick a time",
                        style = MaterialTheme.typography.body1,
                        color = if (selectedTime != null)
                            MaterialTheme.colors.onSurface
                        else
                            MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                    )
                }

                if (onMovePlanned != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Move to Planned",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable {
                                onMovePlanned()
                                onDismiss()
                            }
                            .padding(vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Save and Cancel buttons
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.button,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "Save",
                        style = MaterialTheme.typography.button,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.primary,
                        modifier = Modifier
                            .clickable {
                                onSave(taskName, description, subtasks, selectedDate, selectedTime)
                                onDismiss()
                            }
                            .background(
                                MaterialTheme.colors.primary.copy(alpha = 0.1f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeboxDialog(
    tasks: List<TodoTask>,
    onDismiss: () -> Unit,
    onAddAtSlot: (text: String, slotMillis: Long) -> Unit,
    onAssignToSlot: (taskId: Long, slotMillis: Long) -> Unit,
    onUnschedule: (taskId: Long) -> Unit,
    onEditTask: (TodoTask) -> Unit
) {
    val hourLabelFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

    val todayStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val now = System.currentTimeMillis()
    val currentHourIndex = ((now - todayStart) / (60L * 60L * 1000L)).toInt().coerceIn(0, 23)

    // Bucket tasks into 24 hourly slots for today.
    val slotTasks: List<List<TodoTask>> = remember(tasks, todayStart) {
        val buckets = Array(24) { mutableListOf<TodoTask>() }
        tasks.forEach { task ->
            val time = task.scheduledTime ?: return@forEach
            val taskCal = Calendar.getInstance().apply { timeInMillis = time }
            // Match slot if scheduledDate is today, or (no date set and section==TODAY).
            val dateMatchesToday = task.scheduledDate?.let {
                val d = Calendar.getInstance().apply { timeInMillis = it }
                val t = Calendar.getInstance().apply { timeInMillis = todayStart }
                d.get(Calendar.YEAR) == t.get(Calendar.YEAR) &&
                    d.get(Calendar.DAY_OF_YEAR) == t.get(Calendar.DAY_OF_YEAR)
            } ?: (task.section == TodoSection.TODAY)
            if (!dateMatchesToday) return@forEach
            val hour = taskCal.get(Calendar.HOUR_OF_DAY)
            buckets[hour].add(task)
        }
        buckets.map { it.toList() }
    }

    var addingForHour by remember { mutableStateOf<Int?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            backgroundColor = MaterialTheme.colors.surface,
            elevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Timebox",
                            style = MaterialTheme.typography.h6,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colors.onSurface
                        )
                        Text(
                            text = dateFormat.format(Date(todayStart)),
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.55f)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close timebox",
                            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.55f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    for (hour in 0..23) {
                        val slotMillis = todayStart + hour * 60L * 60L * 1000L
                        val items = slotTasks[hour]
                        val isCurrentHour = hour == currentHourIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Left: time label
                            Text(
                                text = hourLabelFormat.format(Date(slotMillis)),
                                style = MaterialTheme.typography.caption,
                                fontWeight = if (isCurrentHour) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrentHour)
                                    MaterialTheme.colors.primary
                                else
                                    MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .width(72.dp)
                                    .padding(top = 12.dp)
                            )

                            // Right: slot content
                            if (items.isEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 44.dp)
                                        .background(
                                            MaterialTheme.colors.background,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { addingForHour = hour }
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add task at this hour",
                                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.35f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Add task",
                                        style = MaterialTheme.typography.body2,
                                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                                    )
                                }
                            } else {
                                Column(modifier = Modifier.weight(1f)) {
                                    items.forEach { task ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 4.dp)
                                                .background(
                                                    MaterialTheme.colors.primary.copy(alpha = 0.12f),
                                                    RoundedCornerShape(10.dp)
                                                )
                                                .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable {
                                                        onEditTask(task)
                                                        onDismiss()
                                                    }
                                                    .padding(vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = task.text,
                                                    style = MaterialTheme.typography.body2.copy(
                                                        textDecoration = if (task.isDone) TextDecoration.LineThrough else TextDecoration.None
                                                    ),
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (task.isDone)
                                                        MaterialTheme.colors.onSurface.copy(alpha = 0.45f)
                                                    else
                                                        MaterialTheme.colors.onSurface,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = hourLabelFormat.format(Date(task.scheduledTime ?: slotMillis)),
                                                    style = MaterialTheme.typography.caption,
                                                    color = MaterialTheme.colors.primary
                                                )
                                            }
                                            IconButton(
                                                onClick = { onUnschedule(task.id) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Untime task",
                                                    tint = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                    // Allow adding another task in the same hour
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { addingForHour = hour }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Add another task at this hour",
                                            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.35f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Add another",
                                            style = MaterialTheme.typography.caption,
                                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.45f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    addingForHour?.let { hour ->
        val slotMillis = todayStart + hour * 60L * 60L * 1000L
        val untimedTasks = tasks.filter {
            it.scheduledTime == null && !it.isDone &&
                (it.section == TodoSection.TODAY || it.section == TodoSection.PLANNED)
        }
        AddSlotTaskDialog(
            slotLabel = hourLabelFormat.format(Date(slotMillis)),
            untimedTasks = untimedTasks,
            onDismiss = { addingForHour = null },
            onConfirm = { text ->
                onAddAtSlot(text, slotMillis)
                addingForHour = null
            },
            onPickExisting = { taskId ->
                onAssignToSlot(taskId, slotMillis)
                addingForHour = null
            }
        )
    }
}

@Composable
private fun AddSlotTaskDialog(
    slotLabel: String,
    untimedTasks: List<TodoTask>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onPickExisting: (Long) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            backgroundColor = MaterialTheme.colors.surface,
            elevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "New task at $slotLabel",
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (untimedTasks.isNotEmpty()) {
                    Text(
                        text = "Pick existing task",
                        style = MaterialTheme.typography.caption,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colors.background,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { dropdownExpanded = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Choose from ${untimedTasks.size} untimed task${if (untimedTasks.size != 1) "s" else ""}",
                                style = MaterialTheme.typography.body2,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Show existing tasks",
                                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier.heightIn(max = 280.dp)
                        ) {
                            untimedTasks.forEach { existing ->
                                DropdownMenuItem(onClick = {
                                    dropdownExpanded = false
                                    onPickExisting(existing.id)
                                }) {
                                    Column {
                                        Text(
                                            text = existing.text,
                                            style = MaterialTheme.typography.body2,
                                            color = MaterialTheme.colors.onSurface,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = if (existing.section == TodoSection.TODAY) "Today" else "Planned",
                                            style = MaterialTheme.typography.caption,
                                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Or create new",
                        style = MaterialTheme.typography.caption,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = {
                        Text(
                            "Task name",
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        backgroundColor = MaterialTheme.colors.background,
                        focusedBorderColor = MaterialTheme.colors.primary,
                        unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.15f),
                        cursorColor = MaterialTheme.colors.primary,
                        textColor = MaterialTheme.colors.onSurface
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (text.isNotBlank()) onConfirm(text.trim())
                        }
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.button,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "Add",
                        style = MaterialTheme.typography.button,
                        fontWeight = FontWeight.Bold,
                        color = if (text.isNotBlank())
                            MaterialTheme.colors.primary
                        else
                            MaterialTheme.colors.primary.copy(alpha = 0.4f),
                        modifier = Modifier
                            .clickable(enabled = text.isNotBlank()) {
                                onConfirm(text.trim())
                            }
                            .background(
                                MaterialTheme.colors.primary.copy(alpha = 0.1f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}
