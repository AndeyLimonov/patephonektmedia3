package com.example.patephonektmedia3
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.app.ServiceCompat
import androidx.media3.common.C.WAKE_MODE_LOCAL
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import java.lang.NullPointerException


class PlayerService : Service() {

    lateinit var exoPlayer: ExoPlayer
    lateinit var notification: Notification
    private val binder = LocalBinder()
    val channelId: String? = "1"
    var wasPlayed = false

    lateinit var songLabel: TextView

    inner class LocalBinder : Binder() {
        fun getService(): PlayerService = this@PlayerService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }


    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle("Patephone")
                .setContentText("Patephone is here!")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build()
        } else {
            throw NullPointerException("Bad sdk version")
        }
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, notification)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, 1, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        }

        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()

        createChannel()
        exoPlayer = ExoPlayer.Builder(this).build()
        exoPlayer.setWakeMode(WAKE_MODE_LOCAL)
    }

    fun createChannel(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val mChannel = NotificationChannel(channelId, name, importance)
            mChannel.description = descriptionText
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(mChannel)
        }
    }

    fun addMedia(media: MediaItem){
        exoPlayer.addMediaItem(media)
    }

    @SuppressLint("SetTextI18n")
    fun onPlay(view: View){

        if (!wasPlayed){
            wasPlayed = true
            exoPlayer.prepare()
            songLabel.text = exoPlayer.currentMediaItem.toString()
        }

        val playButton: Button = view as Button
        if (!exoPlayer.isPlaying){
            exoPlayer.play()
            playButton.text = "Pause"
        } else {
            exoPlayer.pause()
            playButton.text = "Play"
        }
    }

    fun onNextButton(){
        if (exoPlayer.hasNextMediaItem()){
            exoPlayer.seekToNext()
        }
    }

    fun onPrevButton(){
        if (exoPlayer.hasPreviousMediaItem()){
            exoPlayer.seekToPrevious()
        }
    }

    fun addSongLabel(textView: TextView) {
        songLabel = textView
    }
    override fun onDestroy() {
        super.onDestroy()

        exoPlayer.release()
        stopSelf()
    }

    @SuppressLint("SetTextI18n")
    fun onStopButton(playButton: Button) {
        if (exoPlayer.isPlaying){
            wasPlayed = false
            exoPlayer.stop()
            exoPlayer.seekToDefaultPosition()
            playButton.text = "Play"
        }
    }

    fun resetPlayer() {
        exoPlayer = ExoPlayer.Builder(this).build()
    }
}
