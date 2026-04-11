package com.pomodoro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pomodoro.ui.TimerMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TimerForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "pomodoro_timer_service"
        private const val NOTIFICATION_ID = 2001
        const val ACTION_START = "com.pomodoro.ACTION_START_TIMER"
        const val ACTION_STOP = "com.pomodoro.ACTION_STOP_TIMER"
        const val EXTRA_DEADLINE = "deadline"
        const val EXTRA_MODE = "mode"

        var deadlineMillis: Long = 0L
            private set
        var currentMode: TimerMode = TimerMode.FOCUS
            private set
        var isServiceRunning: Boolean = false
            private set

        fun start(ctx: Context, deadlineMs: Long, mode: TimerMode) {
            deadlineMillis = deadlineMs
            currentMode = mode
            val intent = Intent(ctx, TimerForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DEADLINE, deadlineMs)
                putExtra(EXTRA_MODE, mode.name)
            }
            ctx.startForegroundService(intent)
        }

        fun stop(ctx: Context) {
            if (!isServiceRunning) return
            val intent = Intent(ctx, TimerForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            ctx.startService(intent)
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var tickJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createServiceChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                deadlineMillis = intent.getLongExtra(EXTRA_DEADLINE, 0L)
                val modeName = intent.getStringExtra(EXTRA_MODE) ?: TimerMode.FOCUS.name
                currentMode = TimerMode.valueOf(modeName)
                isServiceRunning = true
                startForeground(NOTIFICATION_ID, buildNotification(remainingMillis()))
                startTicking()
            }
            ACTION_STOP -> {
                stopTicking()
                isServiceRunning = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopTicking()
        isServiceRunning = false
        scope.cancel()
        super.onDestroy()
    }

    private fun remainingMillis(): Long {
        return (deadlineMillis - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (true) {
                val remaining = remainingMillis()
                if (remaining <= 0L) {
                    NotificationHelper.notifyTimerComplete(this@TimerForegroundService, currentMode)
                    isServiceRunning = false
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                }
                updateNotification(remaining)
                delay(1000L)
            }
        }
    }

    private fun stopTicking() {
        tickJob?.cancel()
        tickJob = null
    }

    private fun createServiceChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Timer Running",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows while Pomodoro timer is active"
            setShowBadge(false)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(remainingMs: Long): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val minutes = (remainingMs / 60_000).toInt()
        val seconds = ((remainingMs % 60_000) / 1000).toInt()
        val timeText = String.format("%02d:%02d", minutes, seconds)
        val modeText = when (currentMode) {
            TimerMode.FOCUS -> "Focus"
            TimerMode.REVIEW -> "Review"
            TimerMode.BREAK -> "Break"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("$modeText - $timeText")
            .setContentText("Pomodoro timer is running")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(remainingMs: Long) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(remainingMs))
    }
}
