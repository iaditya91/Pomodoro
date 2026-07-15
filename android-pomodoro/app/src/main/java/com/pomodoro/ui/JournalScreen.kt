package com.pomodoro.ui

import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.KeyboardOptions
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun JournalScreen(viewModel: TimerViewModel) {
    val entries by viewModel.journalEntries.observeAsState(emptyList())

    // Selected day (normalized to start-of-day) and the month currently shown.
    var selectedDay by remember { mutableStateOf(startOfDay(System.currentTimeMillis())) }
    var visibleMonth by remember { mutableStateOf(startOfMonth(System.currentTimeMillis())) }

    // Non-null while an entry is being edited; toggled true to add a new custom entry.
    var editingEntry by remember { mutableStateOf<JournalEntry?>(null) }
    var addingEntry by remember { mutableStateOf(false) }

    val selectedEntries = remember(entries, selectedDay) {
        entries.filter { isSameDay(it.timestamp, selectedDay) }
            .sortedByDescending { it.timestamp }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Journal",
            style = MaterialTheme.typography.h5,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.onBackground,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "${entries.size} session${if (entries.size != 1) "s" else ""} logged",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        CalendarCard(
            visibleMonth = visibleMonth,
            selectedDay = selectedDay,
            entryDays = entries.map { startOfDay(it.timestamp) }.toSet(),
            onPrevMonth = { visibleMonth = shiftMonth(visibleMonth, -1) },
            onNextMonth = { visibleMonth = shiftMonth(visibleMonth, 1) },
            onSelectDay = { selectedDay = it }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Day heading + add-entry button
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatFullDate(selectedDay),
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colors.onBackground,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { addingEntry = true },
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colors.primary.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add note",
                    tint = MaterialTheme.colors.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedEntries.isEmpty()) {
            EmptyJournalPlaceholder()
        } else {
            selectedEntries.forEach { entry ->
                JournalEntryCard(
                    entry = entry,
                    onEdit = { editingEntry = entry },
                    onDelete = { viewModel.deleteJournalEntry(entry.id) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }

    // Edit an existing entry
    editingEntry?.let { entry ->
        JournalEditDialog(
            title = "Edit entry",
            entry = entry,
            dayMillis = startOfDay(entry.timestamp),
            onDismiss = { editingEntry = null },
            onSave = { name, start, end, improvements, note ->
                viewModel.updateJournalEntry(entry.id, name, start, end, improvements, note)
                editingEntry = null
            }
        )
    }

    // Add a new custom entry for the selected day
    if (addingEntry) {
        val now = Calendar.getInstance()
        val defaultTime = combineDayTime(
            selectedDay, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE)
        )
        JournalEditDialog(
            title = "New entry",
            entry = JournalEntry(startTime = defaultTime, timestamp = defaultTime),
            dayMillis = selectedDay,
            onDismiss = { addingEntry = false },
            onSave = { name, start, end, improvements, note ->
                viewModel.addCustomJournalEntry(name, start, end, improvements, note)
                addingEntry = false
            }
        )
    }
}

@Composable
private fun CalendarCard(
    visibleMonth: Long,
    selectedDay: Long,
    entryDays: Set<Long>,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDay: (Long) -> Unit
) {
    Card(
        backgroundColor = MaterialTheme.colors.surface,
        shape = RoundedCornerShape(16.dp),
        elevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Month header with navigation arrows
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevMonth, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Previous month",
                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }
                Text(
                    text = formatMonthYear(visibleMonth),
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colors.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onNextMonth, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Next month",
                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Day-of-week labels
            Row(modifier = Modifier.fillMaxWidth()) {
                weekdayLabels().forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.caption,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val today = startOfDay(System.currentTimeMillis())
            val weeks = monthWeeks(visibleMonth)
            weeks.forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { dayMillis ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (dayMillis != null) {
                                DayCell(
                                    dayMillis = dayMillis,
                                    isSelected = isSameDay(dayMillis, selectedDay),
                                    isToday = isSameDay(dayMillis, today),
                                    hasEntries = entryDays.contains(startOfDay(dayMillis)),
                                    onClick = { onSelectDay(startOfDay(dayMillis)) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    dayMillis: Long,
    isSelected: Boolean,
    isToday: Boolean,
    hasEntries: Boolean,
    onClick: () -> Unit
) {
    val dayNumber = Calendar.getInstance().apply { timeInMillis = dayMillis }
        .get(Calendar.DAY_OF_MONTH)

    val bg = when {
        isSelected -> MaterialTheme.colors.primary
        isToday -> MaterialTheme.colors.primary.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    val textColor = when {
        isSelected -> Color.White
        isToday -> MaterialTheme.colors.primary
        else -> MaterialTheme.colors.onSurface
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = dayNumber.toString(),
            style = MaterialTheme.typography.body2,
            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
            color = textColor
        )
        // Dot indicator for days that have journal entries
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(
                    if (hasEntries) {
                        if (isSelected) Color.White else MaterialTheme.colors.primary
                    } else Color.Transparent
                )
        )
    }
}

@Composable
private fun JournalEntryCard(
    entry: JournalEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        backgroundColor = MaterialTheme.colors.surface,
        shape = RoundedCornerShape(16.dp),
        elevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.taskName.ifBlank { "Untitled" },
                        style = MaterialTheme.typography.subtitle1,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colors.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatTimeRange(entry.startTime, entry.timestamp),
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.45f)
                    )
                }

                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit entry",
                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.35f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete entry",
                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.35f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (entry.improvements.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Improvements",
                    style = MaterialTheme.typography.caption,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(start = 4.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                entry.improvements.forEach { item ->
                    Row(
                        modifier = Modifier.padding(start = 8.dp, top = 3.dp, bottom = 3.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            color = MaterialTheme.colors.primary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(end = 8.dp, top = 1.dp)
                        )
                        Text(
                            text = item.text,
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            if (entry.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Notes",
                    style = MaterialTheme.typography.caption,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(start = 4.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = entry.note,
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.85f),
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun JournalEditDialog(
    title: String,
    entry: JournalEntry,
    dayMillis: Long,
    onDismiss: () -> Unit,
    onSave: (taskName: String, startTime: Long, endTime: Long, improvements: List<ReviewItem>, note: String) -> Unit
) {
    val ctx = LocalContext.current
    var taskName by remember { mutableStateOf(entry.taskName) }
    var noteText by remember { mutableStateOf(entry.note) }
    var startMillis by remember {
        mutableStateOf(if (entry.startTime > 0L) entry.startTime else entry.timestamp)
    }
    var endMillis by remember { mutableStateOf(entry.timestamp) }
    // Keep the original ids where possible so edits are stable.
    val improvementItems = remember {
        mutableStateListOf<ReviewItem>().apply { addAll(entry.improvements) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            backgroundColor = MaterialTheme.colors.surface,
            shape = RoundedCornerShape(20.dp),
            elevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                DialogFieldLabel("Task")
                OutlinedTextField(
                    value = taskName,
                    onValueChange = { taskName = it },
                    placeholder = { Text("What did you work on?") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = dialogFieldColors(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Start / End time pickers
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        DialogFieldLabel("Start")
                        TimeChip(text = formatTime(startMillis)) {
                            pickTime(ctx, startMillis) { picked ->
                                startMillis = combineDayTime(dayMillis, picked.first, picked.second)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        DialogFieldLabel("End")
                        TimeChip(text = formatTime(endMillis)) {
                            pickTime(ctx, endMillis) { picked ->
                                endMillis = combineDayTime(dayMillis, picked.first, picked.second)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                DialogFieldLabel("Improvements")
                improvementItems.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = item.text,
                            onValueChange = { improvementItems[index] = item.copy(text = it) },
                            placeholder = { Text("Improvement…", style = MaterialTheme.typography.body2) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = dialogFieldColors(),
                            textStyle = MaterialTheme.typography.body2,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { improvementItems.removeAt(index) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove improvement",
                                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.35f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                TextButton(onClick = { improvementItems.add(ReviewItem(text = "")) }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colors.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add improvement", color = MaterialTheme.colors.primary)
                }

                Spacer(modifier = Modifier.height(10.dp))

                DialogFieldLabel("Notes")
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    placeholder = { Text("Add your own notes…") },
                    shape = RoundedCornerShape(12.dp),
                    colors = dialogFieldColors(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            val cleaned = improvementItems
                                .map { it.copy(text = it.text.trim()) }
                                .filter { it.text.isNotBlank() }
                            // Guard against an end that precedes the start.
                            val safeEnd = if (endMillis < startMillis) startMillis else endMillis
                            onSave(taskName, startMillis, safeEnd, cleaned, noteText)
                        }
                    ) {
                        Text("Save", color = MaterialTheme.colors.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogFieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
    )
}

@Composable
private fun TimeChip(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colors.background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface
        )
    }
}

@Composable
private fun dialogFieldColors() = TextFieldDefaults.outlinedTextFieldColors(
    focusedBorderColor = MaterialTheme.colors.primary,
    unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.15f),
    textColor = MaterialTheme.colors.onSurface,
    cursorColor = MaterialTheme.colors.primary,
    backgroundColor = MaterialTheme.colors.surface
)

@Composable
private fun EmptyJournalPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "📓", fontSize = 40.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Nothing logged this day",
            style = MaterialTheme.typography.subtitle1,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Finish a focus session or tap + to add your own note",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.4f),
            textAlign = TextAlign.Center
        )
    }
}

// --- Date helpers ---

private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun startOfMonth(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.DAY_OF_MONTH, 1)
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun shiftMonth(monthMillis: Long, delta: Int): Long = Calendar.getInstance().apply {
    timeInMillis = monthMillis
    add(Calendar.MONTH, delta)
}.timeInMillis

private fun combineDayTime(dayMillis: Long, hour: Int, minute: Int): Long =
    Calendar.getInstance().apply {
        timeInMillis = dayMillis
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun pickTime(ctx: Context, currentMillis: Long, onPicked: (Pair<Int, Int>) -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = currentMillis }
    TimePickerDialog(
        ctx,
        { _, h, m -> onPicked(h to m) },
        cal.get(Calendar.HOUR_OF_DAY),
        cal.get(Calendar.MINUTE),
        false
    ).show()
}

private fun isSameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
        ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

/** Builds the calendar grid for the given month as rows of 7 cells (null = blank). */
private fun monthWeeks(monthMillis: Long): List<List<Long?>> {
    val cal = Calendar.getInstance().apply {
        timeInMillis = startOfMonth(monthMillis)
    }
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1 = Sunday
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

    val cells = mutableListOf<Long?>()
    repeat(firstDayOfWeek - 1) { cells.add(null) }
    for (day in 1..daysInMonth) {
        val dayCal = Calendar.getInstance().apply {
            timeInMillis = startOfMonth(monthMillis)
            set(Calendar.DAY_OF_MONTH, day)
        }
        cells.add(dayCal.timeInMillis)
    }
    while (cells.size % 7 != 0) cells.add(null)
    return cells.chunked(7)
}

private fun weekdayLabels(): List<String> = listOf("S", "M", "T", "W", "T", "F", "S")

private fun formatMonthYear(millis: Long): String =
    SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(millis))

private fun formatFullDate(millis: Long): String =
    SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault()).format(Date(millis))

private fun formatTime(millis: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))

/** "9:00 AM – 9:25 AM", or just the end time when no start was recorded. */
private fun formatTimeRange(startMillis: Long, endMillis: Long): String =
    if (startMillis > 0L) "${formatTime(startMillis)} – ${formatTime(endMillis)}"
    else formatTime(endMillis)
