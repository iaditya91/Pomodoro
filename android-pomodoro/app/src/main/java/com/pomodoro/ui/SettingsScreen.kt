package com.pomodoro.ui

import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.RadioButton
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
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

        // Backup Section
        SectionHeader(title = "BACKUP & RESTORE")

        BackupSection(ctx = ctx, viewModel = viewModel)

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun BackupSection(ctx: Context, viewModel: TimerViewModel) {
    val backupStatus by viewModel.backupStatus.observeAsState(TimerViewModel.BackupStatus.IDLE)
    val backupMessage by viewModel.backupMessage.observeAsState("")
    val scope = rememberCoroutineScope()

    var isSignedIn by remember { mutableStateOf(DriveBackupHelper.isSignedIn(ctx)) }
    var accountEmail by remember { mutableStateOf(DriveBackupHelper.getAccountEmail(ctx)) }

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

            // Backup button
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
                modifier = Modifier.fillMaxWidth()
            ) {
                if (backupStatus == TimerViewModel.BackupStatus.LOADING) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colors.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Backup to Drive")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Restore button
            OutlinedButton(
                onClick = {
                    if (isSignedIn) viewModel.restoreFromDrive(ctx)
                    else signInThen("restore")
                },
                enabled = backupStatus != TimerViewModel.BackupStatus.LOADING,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colors.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Restore from Drive",
                    color = MaterialTheme.colors.primary
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
