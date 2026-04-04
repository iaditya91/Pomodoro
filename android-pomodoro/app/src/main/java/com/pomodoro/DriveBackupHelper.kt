package com.pomodoro

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pomodoro.ui.ReviewItem
import com.pomodoro.ui.SavedReviewNote
import com.pomodoro.ui.TodoTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class BackupPayload(
    val notes: List<SavedReviewNote> = emptyList(),
    val todoTasks: List<TodoTask> = emptyList()
)

object DriveBackupHelper {

    private const val BACKUP_FILENAME = "pomodoro_notes_backup.json"
    private const val BACKUP_MIME = "application/json"

    fun getSignInClient(ctx: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .build()
        return GoogleSignIn.getClient(ctx, gso)
    }

    fun getSignInIntent(ctx: Context): Intent {
        return getSignInClient(ctx).signInIntent
    }

    fun isSignedIn(ctx: Context): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(ctx)
        return account != null && account.grantedScopes.contains(Scope(DriveScopes.DRIVE_APPDATA))
    }

    fun getAccountEmail(ctx: Context): String? {
        return GoogleSignIn.getLastSignedInAccount(ctx)?.email
    }

    suspend fun signOut(ctx: Context) {
        withContext(Dispatchers.IO) {
            try {
                getSignInClient(ctx).signOut()
            } catch (_: Exception) {
            }
        }
    }

    private fun getDriveService(ctx: Context): Drive? {
        val account: GoogleSignInAccount = GoogleSignIn.getLastSignedInAccount(ctx) ?: return null
        val credential = GoogleAccountCredential.usingOAuth2(
            ctx, listOf(DriveScopes.DRIVE_APPDATA)
        )
        credential.selectedAccount = account.account
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("Pomodoro")
            .build()
    }

    suspend fun backup(ctx: Context, notes: List<SavedReviewNote>, todoTasks: List<TodoTask> = emptyList()): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val drive = getDriveService(ctx) ?: return@withContext Result.failure(
                    Exception("Not signed in")
                )
                val payload = BackupPayload(notes = notes, todoTasks = todoTasks)
                val json = Gson().toJson(payload)
                val content = ByteArrayContent.fromString(BACKUP_MIME, json)

                // Find existing backup file
                val existingId = findBackupFileId(drive)

                if (existingId != null) {
                    // Update existing file
                    drive.files().update(existingId, null, content).execute()
                } else {
                    // Create new file in appDataFolder
                    val metadata = DriveFile().apply {
                        name = BACKUP_FILENAME
                        parents = listOf("appDataFolder")
                    }
                    drive.files().create(metadata, content).execute()
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun restore(ctx: Context): Result<BackupPayload> {
        return withContext(Dispatchers.IO) {
            try {
                val drive = getDriveService(ctx) ?: return@withContext Result.failure(
                    Exception("Not signed in")
                )

                val fileId = findBackupFileId(drive)
                    ?: return@withContext Result.failure(Exception("No backup found"))

                val outputStream = ByteArrayOutputStream()
                drive.files().get(fileId).executeMediaAndDownloadTo(outputStream)
                val json = outputStream.toString("UTF-8")

                // Try parsing as BackupPayload first, fall back to legacy format (List<SavedReviewNote>)
                try {
                    val payload: BackupPayload = Gson().fromJson(json, BackupPayload::class.java)
                    Result.success(payload)
                } catch (_: Exception) {
                    val type = object : TypeToken<List<SavedReviewNote>>() {}.type
                    val notes: List<SavedReviewNote> = Gson().fromJson(json, type)
                    Result.success(BackupPayload(notes = notes))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun findBackupFileId(drive: Drive): String? {
        val result = drive.files().list()
            .setSpaces("appDataFolder")
            .setQ("name = '$BACKUP_FILENAME'")
            .setFields("files(id)")
            .setPageSize(1)
            .execute()
        return result.files?.firstOrNull()?.id
    }
}
