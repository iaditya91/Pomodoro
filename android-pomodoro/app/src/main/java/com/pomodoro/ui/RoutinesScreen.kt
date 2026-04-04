package com.pomodoro.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity

@Composable
fun RoutinesScreen(
    viewModel: TimerViewModel,
    onStartRoutine: () -> Unit
) {
    val routines by viewModel.routines.observeAsState(emptyList())
    val ctx = LocalContext.current
    var showForm by remember { mutableStateOf(false) }
    var editingRoutine by remember { mutableStateOf<Routine?>(null) }

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
                    text = "Routines",
                    style = MaterialTheme.typography.h5,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${routines.size} routine${if (routines.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.5f)
                )
            }

            IconButton(
                onClick = {
                    editingRoutine = null
                    showForm = true
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colors.primary, RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add routine",
                    tint = MaterialTheme.colors.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (showForm) {
            RoutineForm(
                initial = editingRoutine,
                onSave = { routine ->
                    if (editingRoutine != null) {
                        viewModel.updateRoutine(routine)
                    } else {
                        viewModel.addRoutine(routine)
                    }
                    showForm = false
                    editingRoutine = null
                },
                onCancel = {
                    showForm = false
                    editingRoutine = null
                }
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        if (routines.isEmpty() && !showForm) {
            EmptyRoutinesPlaceholder()
        } else {
            routines.forEach { routine ->
                RoutineCard(
                    routine = routine,
                    onPlay = {
                        viewModel.startRoutine(ctx, routine)
                        onStartRoutine()
                    },
                    onEdit = {
                        editingRoutine = routine
                        showForm = true
                    },
                    onDeleteRoutine = { viewModel.deleteRoutine(routine.id) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun EmptyRoutinesPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "\uD83D\uDD01", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No routines yet",
            style = MaterialTheme.typography.h6,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Create a routine with custom timer settings and subtasks",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun RoutineCard(
    routine: Routine,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDeleteRoutine: () -> Unit
) {
    Card(
        backgroundColor = MaterialTheme.colors.surface,
        shape = RoundedCornerShape(16.dp),
        elevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = routine.name.ifBlank { "Untitled" },
                        style = MaterialTheme.typography.body1,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colors.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val info = buildString {
                        append("${routine.focusMinutes}m focus")
                        if (routine.reviewEnabled) append(" / ${routine.reviewMinutes}m review")
                        if (routine.breakEnabled) append(" / ${routine.breakMinutes}m break")
                        append(" x${routine.focusCycles}")
                    }
                    Text(
                        text = if (routine.useDefaultSettings) "Default settings x${routine.focusCycles}" else info,
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                    )
                }

                IconButton(onClick = onPlay, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start routine",
                        tint = MaterialTheme.colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit routine",
                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onDeleteRoutine, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove routine",
                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.35f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (routine.subtasks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(8.dp))
                routine.subtasks.forEach { subtask ->
                    Column(modifier = Modifier.padding(vertical = 3.dp, horizontal = 4.dp)) {
                        Text(
                            text = "\u2022 ${subtask.text}",
                            style = MaterialTheme.typography.body1.copy(fontSize = 15.sp),
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f)
                        )
                        if (subtask.description.isNotBlank()) {
                            Text(
                                text = "  ${subtask.description}",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.45f),
                                modifier = Modifier.padding(start = 10.dp)
                            )
                        }
                        if (subtask.minutes > 0) {
                            Text(
                                text = "  ${subtask.minutes} min",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.primary.copy(alpha = 0.6f),
                                modifier = Modifier.padding(start = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutineForm(
    initial: Routine?,
    onSave: (Routine) -> Unit,
    onCancel: () -> Unit
) {
    val isEdit = initial != null
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var subtaskItems by remember {
        mutableStateOf(initial?.subtasks ?: emptyList())
    }
    var newSubtask by remember { mutableStateOf("") }
    var editingIndex by remember { mutableStateOf(-1) }
    var editingText by remember { mutableStateOf("") }
    var editingDesc by remember { mutableStateOf("") }
    var editingMins by remember { mutableStateOf("") }
    var expandedIndex by remember { mutableStateOf(-1) }
    var dragIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(0f) }
    val itemHeights = remember { mutableStateMapOf<Int, Int>() }
    val density = LocalDensity.current
    var useDefaults by remember { mutableStateOf(initial?.useDefaultSettings ?: true) }
    var focusMin by remember { mutableStateOf((initial?.focusMinutes ?: 25).toString()) }
    var reviewMin by remember { mutableStateOf((initial?.reviewMinutes ?: 5).toString()) }
    var breakMin by remember { mutableStateOf((initial?.breakMinutes ?: 15).toString()) }
    var cycles by remember { mutableStateOf((initial?.focusCycles ?: 4).toString()) }
    var reviewOn by remember { mutableStateOf(initial?.reviewEnabled ?: true) }
    var breakOn by remember { mutableStateOf(initial?.breakEnabled ?: true) }

    Card(
        backgroundColor = MaterialTheme.colors.surface,
        shape = RoundedCornerShape(16.dp),
        elevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (isEdit) "Edit Routine" else "New Routine",
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Routine name", color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    backgroundColor = MaterialTheme.colors.surface,
                    focusedBorderColor = MaterialTheme.colors.primary,
                    unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.15f),
                    cursorColor = MaterialTheme.colors.primary,
                    textColor = MaterialTheme.colors.onSurface
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Subtasks
            Text(
                text = "Subtasks",
                style = MaterialTheme.typography.caption,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(6.dp))

            subtaskItems.forEachIndexed { index, item ->
                val isDragging = dragIndex == index
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { itemHeights[index] = it.size.height }
                        .graphicsLayer {
                            if (isDragging) {
                                translationY = dragOffset
                                shadowElevation = 8f
                            }
                        }
                        .zIndex(if (isDragging) 1f else 0f)
                        .background(
                            if (isDragging) MaterialTheme.colors.surface else MaterialTheme.colors.surface.copy(alpha = 0f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Drag handle
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Drag to reorder",
                            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier
                                .size(24.dp)
                                .pointerInput(index) {
                                    detectVerticalDragGestures(
                                        onDragStart = {
                                            dragIndex = index
                                            dragOffset = 0f
                                        },
                                        onDragEnd = {
                                            dragIndex = -1
                                            dragOffset = 0f
                                        },
                                        onDragCancel = {
                                            dragIndex = -1
                                            dragOffset = 0f
                                        },
                                        onVerticalDrag = { _, dragAmount ->
                                            dragOffset += dragAmount
                                            val currentIdx = dragIndex
                                            if (currentIdx < 0) return@detectVerticalDragGestures
                                            val h = itemHeights[currentIdx] ?: return@detectVerticalDragGestures
                                            if (dragOffset > h * 0.6f && currentIdx < subtaskItems.size - 1) {
                                                subtaskItems = subtaskItems.toMutableList().also {
                                                    val moved = it.removeAt(currentIdx)
                                                    it.add(currentIdx + 1, moved)
                                                }
                                                dragIndex = currentIdx + 1
                                                dragOffset -= h
                                                if (editingIndex == currentIdx) editingIndex = currentIdx + 1
                                                else if (editingIndex == currentIdx + 1) editingIndex = currentIdx
                                            } else if (dragOffset < -h * 0.6f && currentIdx > 0) {
                                                subtaskItems = subtaskItems.toMutableList().also {
                                                    val moved = it.removeAt(currentIdx)
                                                    it.add(currentIdx - 1, moved)
                                                }
                                                dragIndex = currentIdx - 1
                                                dragOffset += h
                                                if (editingIndex == currentIdx) editingIndex = currentIdx - 1
                                                else if (editingIndex == currentIdx - 1) editingIndex = currentIdx
                                            }
                                        }
                                    )
                                }
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        if (editingIndex == index) {
                            Column(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = editingText,
                                    onValueChange = { editingText = it },
                                    placeholder = { Text("Task name", style = MaterialTheme.typography.body1) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    textStyle = MaterialTheme.typography.body1.copy(fontSize = 15.sp),
                                    colors = TextFieldDefaults.outlinedTextFieldColors(
                                        focusedBorderColor = MaterialTheme.colors.primary,
                                        unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.15f),
                                        textColor = MaterialTheme.colors.onSurface,
                                        cursorColor = MaterialTheme.colors.primary
                                    ),
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = editingDesc,
                                    onValueChange = { editingDesc = it },
                                    placeholder = { Text("Description (optional)", style = MaterialTheme.typography.caption) },
                                    singleLine = false,
                                    maxLines = 3,
                                    shape = RoundedCornerShape(8.dp),
                                    textStyle = MaterialTheme.typography.body2,
                                    colors = TextFieldDefaults.outlinedTextFieldColors(
                                        focusedBorderColor = MaterialTheme.colors.primary.copy(alpha = 0.5f),
                                        unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.1f),
                                        textColor = MaterialTheme.colors.onSurface,
                                        cursorColor = MaterialTheme.colors.primary
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = editingMins,
                                    onValueChange = { v -> editingMins = v.filter { it.isDigit() } },
                                    placeholder = { Text("Time in minutes (optional)", style = MaterialTheme.typography.caption) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    textStyle = MaterialTheme.typography.body2,
                                    colors = TextFieldDefaults.outlinedTextFieldColors(
                                        focusedBorderColor = MaterialTheme.colors.primary.copy(alpha = 0.5f),
                                        unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.1f),
                                        textColor = MaterialTheme.colors.onSurface,
                                        cursorColor = MaterialTheme.colors.primary
                                    ),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                TextButton(
                                    onClick = {
                                        if (editingText.isNotBlank()) {
                                            subtaskItems = subtaskItems.toMutableList().also {
                                                it[index] = RoutineSubtask(
                                                    text = editingText.trim(),
                                                    description = editingDesc.trim(),
                                                    minutes = editingMins.toIntOrNull() ?: 0
                                                )
                                            }
                                        }
                                        editingIndex = -1
                                    }
                                ) {
                                    Text("Done", color = MaterialTheme.colors.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.text,
                                    style = MaterialTheme.typography.body1.copy(fontSize = 15.sp),
                                    color = MaterialTheme.colors.onSurface
                                )
                                if (item.description.isNotBlank()) {
                                    Text(
                                        text = item.description,
                                        style = MaterialTheme.typography.caption,
                                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                if (item.minutes > 0) {
                                    Text(
                                        text = "${item.minutes} min",
                                        style = MaterialTheme.typography.caption,
                                        color = MaterialTheme.colors.primary.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    editingIndex = index
                                    editingText = item.text
                                    editingDesc = item.description
                                    editingMins = if (item.minutes > 0) item.minutes.toString() else ""
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit",
                                    tint = MaterialTheme.colors.onSurface.copy(alpha = 0.3f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                subtaskItems = subtaskItems.toMutableList().also { it.removeAt(index) }
                                if (editingIndex == index) editingIndex = -1
                                else if (editingIndex > index) editingIndex--
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove",
                                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.3f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newSubtask,
                    onValueChange = { newSubtask = it },
                    placeholder = {
                        Text(
                            "Add subtask...",
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.3f),
                            style = MaterialTheme.typography.body2
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = MaterialTheme.colors.primary.copy(alpha = 0.5f),
                        unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.1f),
                        textColor = MaterialTheme.colors.onSurface,
                        cursorColor = MaterialTheme.colors.primary
                    ),
                    textStyle = MaterialTheme.typography.body2,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (newSubtask.isNotBlank()) {
                                subtaskItems = subtaskItems + RoutineSubtask(text = newSubtask.trim())
                                newSubtask = ""
                            }
                        }
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (newSubtask.isNotBlank()) {
                            subtaskItems = subtaskItems + RoutineSubtask(text = newSubtask.trim())
                            newSubtask = ""
                        }
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colors.primary.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add",
                        tint = MaterialTheme.colors.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(12.dp))

            // Timer settings
            Text(
                text = "Timer Settings",
                style = MaterialTheme.typography.caption,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Use default settings",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = useDefaults,
                    onCheckedChange = { useDefaults = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colors.primary)
                )
            }

            if (!useDefaults) {
                Spacer(modifier = Modifier.height(8.dp))

                NumberField(label = "Focus (min)", value = focusMin, onValueChange = { focusMin = it })
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Review", style = MaterialTheme.typography.body2, color = MaterialTheme.colors.onSurface, modifier = Modifier.weight(1f))
                    Switch(checked = reviewOn, onCheckedChange = { reviewOn = it }, colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colors.primary))
                }
                if (reviewOn) {
                    NumberField(label = "Review (min)", value = reviewMin, onValueChange = { reviewMin = it })
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Break", style = MaterialTheme.typography.body2, color = MaterialTheme.colors.onSurface, modifier = Modifier.weight(1f))
                    Switch(checked = breakOn, onCheckedChange = { breakOn = it }, colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colors.primary))
                }
                if (breakOn) {
                    NumberField(label = "Break (min)", value = breakMin, onValueChange = { breakMin = it })
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            NumberField(label = "Focus cycles", value = cycles, onValueChange = { cycles = it })

            Spacer(modifier = Modifier.height(16.dp))

            // Buttons
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Cancel", color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        val routine = Routine(
                            id = initial?.id ?: System.nanoTime(),
                            name = name.trim(),
                            subtasks = subtaskItems,
                            focusMinutes = focusMin.toIntOrNull() ?: 25,
                            reviewMinutes = reviewMin.toIntOrNull() ?: 5,
                            breakMinutes = breakMin.toIntOrNull() ?: 15,
                            focusCycles = cycles.toIntOrNull() ?: 4,
                            reviewEnabled = reviewOn,
                            breakEnabled = breakOn,
                            useDefaultSettings = useDefaults
                        )
                        onSave(routine)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (isEdit) "Update" else "Create",
                        color = MaterialTheme.colors.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newVal -> onValueChange(newVal.filter { it.isDigit() }) },
        label = { Text(label, style = MaterialTheme.typography.caption) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.outlinedTextFieldColors(
            backgroundColor = MaterialTheme.colors.surface,
            focusedBorderColor = MaterialTheme.colors.primary,
            unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.15f),
            cursorColor = MaterialTheme.colors.primary,
            textColor = MaterialTheme.colors.onSurface
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}
