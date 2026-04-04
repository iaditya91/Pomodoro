package com.pomodoro.ui

import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.RadioButton
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.pomodoro.AutoBackupWorker
import com.pomodoro.DriveBackupHelper
import com.pomodoro.ui.theme.ThemeMode
import com.pomodoro.ui.theme.ThemePreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object SettingsPrefs {
    private const val PREFS = "pomodoro_prefs"
    private const val KEY_FOCUS = "focus_minutes"
    private const val KEY_REVIEW = "review_minutes"
    private const val KEY_BREAK = "break_minutes"
    private const val KEY_FOCUS_CHECKLIST = "focus_checklist"
    private const val KEY_CHECKLIST_MODE = "checklist_mode"

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

    fun loadFocusChecklistItems(ctx: Context): List<String> {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_FOCUS_CHECKLIST, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("\n").filter { it.isNotBlank() }
    }

    fun saveFocusChecklistItems(ctx: Context, items: List<String>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FOCUS_CHECKLIST, items.joinToString("\n"))
            .apply()
    }

    fun loadChecklistMode(ctx: Context): CheckItemMode {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val mode = prefs.getString(KEY_CHECKLIST_MODE, "CHECK") ?: "CHECK"
        return try { CheckItemMode.valueOf(mode) } catch (_: Exception) { CheckItemMode.CHECK }
    }

    fun saveChecklistMode(ctx: Context, mode: CheckItemMode) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CHECKLIST_MODE, mode.name)
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

        Spacer(modifier = Modifier.height(24.dp))

        // Focus Checklist Section
        SectionHeader(title = "FOCUS CHECKLIST")

        FocusChecklistSection(ctx = ctx)

        Spacer(modifier = Modifier.height(24.dp))

        // Backup Section
        SectionHeader(title = "BACKUP & RESTORE")

        BackupSection(ctx = ctx, viewModel = viewModel)

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun FocusChecklistSection(ctx: Context) {
    var items by remember { mutableStateOf(SettingsPrefs.loadFocusChecklistItems(ctx)) }
    var mode by remember { mutableStateOf(SettingsPrefs.loadChecklistMode(ctx)) }
    var newItemText by remember { mutableStateOf("") }

    fun saveItems(updated: List<String>) {
        items = updated
        SettingsPrefs.saveFocusChecklistItems(ctx, updated)
    }

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
                text = "Pre-focus checklist",
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colors.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Items must be completed before the focus timer runs",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Global mode toggle: Check / Type
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colors.background)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isCheck = mode == CheckItemMode.CHECK
                Text(
                    text = "Check",
                    style = MaterialTheme.typography.body2,
                    fontWeight = if (isCheck) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCheck) MaterialTheme.colors.onPrimary
                        else MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isCheck) MaterialTheme.colors.primary else Color.Transparent)
                        .clickable {
                            mode = CheckItemMode.CHECK
                            SettingsPrefs.saveChecklistMode(ctx, CheckItemMode.CHECK)
                        }
                        .padding(vertical = 8.dp)
                )
                Text(
                    text = "Type",
                    style = MaterialTheme.typography.body2,
                    fontWeight = if (!isCheck) FontWeight.Bold else FontWeight.Normal,
                    color = if (!isCheck) MaterialTheme.colors.onPrimary
                        else MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (!isCheck) MaterialTheme.colors.primary else Color.Transparent)
                        .clickable {
                            mode = CheckItemMode.TYPE
                            SettingsPrefs.saveChecklistMode(ctx, CheckItemMode.TYPE)
                        }
                        .padding(vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (mode == CheckItemMode.CHECK)
                    "Tick each item to start focus"
                else
                    "Type each item to start focus",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "\u2022",
                        color = MaterialTheme.colors.primary,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(start = 4.dp, end = 10.dp)
                    )
                    Text(
                        text = item,
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { saveItems(items.toMutableList().also { it.removeAt(index) }) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.35f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newItemText,
                    onValueChange = { newItemText = it },
                    placeholder = {
                        Text(
                            "Add checklist item...",
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
                            style = MaterialTheme.typography.body2
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = MaterialTheme.colors.primary,
                        unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.15f),
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
                            if (newItemText.isNotBlank()) {
                                saveItems(items + newItemText.trim())
                                newItemText = ""
                            }
                        }
                    ),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (newItemText.isNotBlank()) {
                            saveItems(items + newItemText.trim())
                            newItemText = ""
                        }
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colors.primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add item",
                        tint = MaterialTheme.colors.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupSection(ctx: Context, viewModel: TimerViewModel) {
    val backupStatus by viewModel.backupStatus.observeAsState(TimerViewModel.BackupStatus.IDLE)
    val backupMessage by viewModel.backupMessage.observeAsState("")
    val scope = rememberCoroutineScope()

    var isSignedIn by remember { mutableStateOf(DriveBackupHelper.isSignedIn(ctx)) }
    var accountEmail by remember { mutableStateOf(DriveBackupHelper.getAccountEmail(ctx)) }
    var autoBackupEnabled by remember { mutableStateOf(AutoBackupWorker.isEnabled(ctx)) }

    // Pending action after sign-in
    var pendingAction by remember { mutableStateOf<String?>(null) }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                task.getResult(Exception::class.java)
                isSignedIn = true
                accountEmail = DriveBackupHelper.getAccountEmail(ctx)
                when (pendingAction) {
                    "backup" -> viewModel.backupToDrive(ctx)
                    "restore" -> viewModel.restoreFromDrive(ctx)
                    "enable_auto_backup" -> {
                        autoBackupEnabled = true
                        AutoBackupWorker.setEnabled(ctx, true)
                    }
                }
                pendingAction = null
            } catch (_: Exception) {
                isSignedIn = false
            }
        }
    }

    fun signInThen(action: String) {
        pendingAction = action
        signInLauncher.launch(DriveBackupHelper.getSignInIntent(ctx))
    }

    // Auto-clear status message after 3 seconds
    LaunchedEffect(backupStatus) {
        if (backupStatus == TimerViewModel.BackupStatus.SUCCESS ||
            backupStatus == TimerViewModel.BackupStatus.ERROR) {
            delay(3000)
            viewModel.clearBackupStatus()
        }
    }

    Card(
        backgroundColor = MaterialTheme.colors.surface,
        shape = RoundedCornerShape(16.dp),
        elevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = null,
                    tint = MaterialTheme.colors.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Google Drive",
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colors.onSurface
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isSignedIn && accountEmail != null)
                    "Signed in as $accountEmail"
                else
                    "Sign in to back up your improvement notes",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Backup & Restore buttons side by side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (isSignedIn) viewModel.backupToDrive(ctx)
                        else signInThen("backup")
                    },
                    enabled = backupStatus != TimerViewModel.BackupStatus.LOADING,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.primary,
                        contentColor = MaterialTheme.colors.onPrimary
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    if (backupStatus == TimerViewModel.BackupStatus.LOADING) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colors.onPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Backup")
                }

                OutlinedButton(
                    onClick = {
                        if (isSignedIn) viewModel.restoreFromDrive(ctx)
                        else signInThen("restore")
                    },
                    enabled = backupStatus != TimerViewModel.BackupStatus.LOADING,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colors.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Restore", color = MaterialTheme.colors.primary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Auto-backup toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto backup",
                        style = MaterialTheme.typography.body2,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colors.onSurface
                    )
                    Text(
                        text = "Daily at 2:00 AM",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                    )
                }
                Switch(
                    checked = autoBackupEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && !isSignedIn) {
                            signInThen("enable_auto_backup")
                        } else {
                            autoBackupEnabled = enabled
                            AutoBackupWorker.setEnabled(ctx, enabled)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colors.primary,
                        checkedTrackColor = MaterialTheme.colors.primary.copy(alpha = 0.5f)
                    )
                )
            }

            // Status message
            if (backupMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = backupMessage,
                    style = MaterialTheme.typography.caption,
                    fontWeight = FontWeight.Medium,
                    color = when (backupStatus) {
                        TimerViewModel.BackupStatus.SUCCESS -> MaterialTheme.colors.secondary
                        TimerViewModel.BackupStatus.ERROR -> MaterialTheme.colors.error
                        else -> MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                    }
                )
            }

            // Sign out option
            if (isSignedIn) {
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = {
                        scope.launch {
                            DriveBackupHelper.signOut(ctx)
                            isSignedIn = false
                            accountEmail = null
                        }
                    }
                ) {
                    Text(
                        "Sign out",
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.caption
                    )
                }
            }
        }
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
