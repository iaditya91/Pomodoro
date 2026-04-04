package com.pomodoro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotesScreen(viewModel: TimerViewModel) {
    val notes by viewModel.savedNotes.observeAsState(emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Review Notes",
            style = MaterialTheme.typography.h5,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.onBackground,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "${notes.size} session${if (notes.size != 1) "s" else ""} recorded",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (notes.isEmpty()) {
            EmptyNotesPlaceholder()
        } else {
            notes.forEach { note ->
                NoteCard(
                    note = note,
                    onDelete = { viewModel.deleteNote(note.id) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun EmptyNotesPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "\uD83D\uDCDD",
            fontSize = 48.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No review notes yet",
            style = MaterialTheme.typography.h6,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Complete a review session to see your notes here",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun NoteCard(note: SavedReviewNote, onDelete: () -> Unit) {
    val dateFormat = SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault())
    val dateStr = dateFormat.format(Date(note.timestamp))

    Card(
        backgroundColor = MaterialTheme.colors.surface,
        shape = RoundedCornerShape(16.dp),
        elevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: task name + date + delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (note.taskName.isNotBlank()) {
                        Text(
                            text = note.taskName,
                            style = MaterialTheme.typography.subtitle1,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colors.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.45f)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete note",
                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.35f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            val answers = note.answers

            if (answers.wentWell.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                NoteQuestionSection(
                    emoji = "\u2705",
                    title = "What went well",
                    items = answers.wentWell
                )
            }

            if (answers.didntGoWell.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                NoteQuestionSection(
                    emoji = "\u26A0\uFE0F",
                    title = "What didn't go well",
                    items = answers.didntGoWell
                )
            }

            if (answers.improvements.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                NoteQuestionSection(
                    emoji = "\uD83D\uDCA1",
                    title = "Improvements",
                    items = answers.improvements
                )
            }
        }
    }
}

@Composable
private fun NoteQuestionSection(
    emoji: String,
    title: String,
    items: List<ReviewItem>
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = emoji, fontSize = 14.sp)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.caption,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
            letterSpacing = 1.sp
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
    items.forEach { item ->
        Row(
            modifier = Modifier.padding(start = 8.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "\u2022",
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
