package com.aistudio.sanctuary.audpbk

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AudiobookPlaybackService : Service() {

    companion object {
        const val CHANNEL_ID = "audiobook_playback_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY = "com.aistudio.sanctuary.audpbk.ACTION_PLAY"
        const val ACTION_PAUSE = "com.aistudio.sanctuary.audpbk.ACTION_PAUSE"
        const val ACTION_STOP = "com.aistudio.sanctuary.audpbk.ACTION_STOP"
        const val ACTION_UPDATE = "com.aistudio.sanctuary.audpbk.ACTION_UPDATE"

        var isServiceRunning = false

        fun startService(context: Context, bookTitle: String, bookAuthor: String, isPlaying: Boolean) {
            val intent = Intent(context, AudiobookPlaybackService::class.java).apply {
                action = ACTION_UPDATE
                putExtra("title", bookTitle)
                putExtra("author", bookAuthor)
                putExtra("isPlaying", isPlaying)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AudiobookPlaybackService::class.java)
            context.stopService(intent)
        }
    }

    private var currentTitle = "Santuario"
    private var currentAuthor = "Audio"
    private var isPlaying = false

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            when (intent.action) {
                ACTION_PLAY -> {
                    PlaybackController.play()
                    isPlaying = true
                    updateNotification()
                }
                ACTION_PAUSE -> {
                    PlaybackController.pause()
                    isPlaying = false
                    updateNotification()
                }
                ACTION_STOP -> {
                    PlaybackController.pause()
                    stopSelf()
                }
                ACTION_UPDATE -> {
                    currentTitle = intent.getStringExtra("title") ?: currentTitle
                    currentAuthor = intent.getStringExtra("author") ?: currentAuthor
                    isPlaying = intent.getBooleanExtra("isPlaying", false)
                    updateNotification()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Santuario Reproducción"
            val descriptionText = "Control de reproducción de audiolibros"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Play action
        val playIntent = Intent(this, AudiobookPlaybackService::class.java).apply { action = ACTION_PLAY }
        val playPendingIntent = PendingIntent.getService(
            this, 1, playIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Pause action
        val pauseIntent = Intent(this, AudiobookPlaybackService::class.java).apply { action = ACTION_PAUSE }
        val pausePendingIntent = PendingIntent.getService(
            this, 2, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop action
        val stopIntent = Intent(this, AudiobookPlaybackService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 3, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val actionIcon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        
        val actionText = if (isPlaying) "Pausar" else "Reproducir"
        val actionPendingIntent = if (isPlaying) pausePendingIntent else playPendingIntent

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText(currentAuthor)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .addAction(actionIcon, actionText, actionPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener", stopPendingIntent)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0)
            )
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
}
