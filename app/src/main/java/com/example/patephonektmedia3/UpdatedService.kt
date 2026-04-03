package com.example.patephonektmedia3

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class UpdatedService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val notification = createNotification()
        startForeground(1, notification)

        val player = ExoPlayer.Builder(this).build()
        player.addListener(playerListener)
        mediaSession = MediaSession.Builder(this, player).build()
        Log.d("UpdatedService", "Service started")
    }

// This method starts intent for activity to update UI
    // Activity updates UI if intent contains extra playerStateChanged = true
    private val playerListener = object: Player.Listener{
        override fun onEvents(player: Player, events: Player.Events) {
            if (mediaSession == null) return
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED ) || events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)){
                val intent = Intent(applicationContext, MainActivity::class.java).apply {
                    putExtra("playerStateChanged", true)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                Log.d("UpdatedService", "Player state changed")
            }
            super.onEvents(player, events)
        }
    }


    private fun createNotification(): Notification {
        createNotificationChannel()

        return NotificationCompat.Builder(this, "media_channel")
            .setContentTitle("Музыкальный плеер")
            .setContentText("Воспроизведение")
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "media_channel",
                "Медиаплеер",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        Log.d("UpdatedService", "onGetSession called, returning: ${mediaSession != null}")
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
