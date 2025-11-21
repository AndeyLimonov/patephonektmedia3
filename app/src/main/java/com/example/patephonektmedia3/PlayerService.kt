package com.example.patephonektmedia3
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
import android.media.session.MediaSession
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.view.View
import android.widget.Button
import android.widget.RemoteViews
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.C.WAKE_MODE_LOCAL
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.patephonektmedia3.R.string.pause_button
import com.google.android.material.slider.Slider

class PlayerService : Service() {
    lateinit var notificationManager: NotificationManager
    lateinit var exoPlayer: ExoPlayer
    lateinit var notification: Notification
    private val binder = LocalBinder()
    val channelId: String = "1"
    var wasPlayed = false
    var uriArray = ArrayList<Uri>()
    lateinit var notificationSmall: RemoteViews
    lateinit var notificationBig: RemoteViews
    lateinit var songLabel: TextView
    lateinit var mediaSession: MediaSession

    inner class LocalBinder : Binder() {
        fun getService(): PlayerService = this@PlayerService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        notificationSmall = RemoteViews(packageName, R.layout.notification_small)
        notificationBig = RemoteViews(packageName, R.layout.notification_big)
        notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(notificationSmall)
                .setCustomBigContentView(notificationBig)
                .build()
        } else {
             throw kotlin.NullPointerException("Bad sdk version")
        }
        notificationManager.notify(1, notification)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, 1, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        }

        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()

        //Initializing mediaSession object
        mediaSession = MediaSession(this, "PlayerSession")
        mediaSession.setCallback(object: MediaSession.Callback() {
            override fun onPlay(){
                super.onPlay()

                onPlay()
            }
        })

        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        createChannel()
        exoPlayer = ExoPlayer.Builder(this).build()
        exoPlayer.setWakeMode(WAKE_MODE_LOCAL)
        exoPlayer.addListener(object: Player.Listener{
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)

                try {
                    setUI(exoPlayer.currentMediaItemIndex, exoPlayer.currentMediaItem)
                } catch (_: UninitializedPropertyAccessException){
                    println("No textview initialized")
                }
            }
        })
    }
    fun getSongList(): ArrayList<String> {
        val list = ArrayList<String>()
        for (uri in uriArray){
            val file = DocumentFile.fromSingleUri(this, uri)
            if (file != null){
                list.add(file.name)
            }
        }
        return list
    }
    fun createChannel(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT

            val mChannel = NotificationChannel(channelId, name, importance)
            mChannel.setSound(null, null)
            mChannel.enableVibration(false)
            mChannel.description = descriptionText
            notificationManager.createNotificationChannel(mChannel)
        }
    }

    fun addMedia(uri: Uri){
        uriArray.add(uri)
        val media = MediaItem.fromUri(uri)
        exoPlayer.addMediaItem(media)
    }

    fun onPlay(view: View){

        if (!wasPlayed){
            wasPlayed = true
            exoPlayer.prepare()
            setUI(exoPlayer.currentMediaItemIndex, exoPlayer.currentMediaItem)
        }

        val playButton: Button = view as Button
        if (!exoPlayer.isPlaying){
            exoPlayer.play()
            playButton.text = getString(pause_button)
        } else {
            exoPlayer.pause()
            playButton.text = getString(R.string.play_button)
        }
    }

    fun onNextButton(){
        if (exoPlayer.hasNextMediaItem()){
            exoPlayer.seekToNext()
            setUI(exoPlayer.currentMediaItemIndex, exoPlayer.currentMediaItem)
        }
    }

    fun onPrevButton(){
        if (exoPlayer.hasPreviousMediaItem()){
            exoPlayer.seekToPrevious()
            setUI(exoPlayer.currentMediaItemIndex, exoPlayer.currentMediaItem)
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

    fun onStopButton(playButton: Button) {
        wasPlayed = false
        exoPlayer.stop()
        exoPlayer.seekToDefaultPosition()
        playButton.text = getString(R.string.play_button)
    }

    fun resetPlayer() {
        uriArray = ArrayList()
        exoPlayer.release()
        exoPlayer = ExoPlayer.Builder(this).build()
        wasPlayed = false
    }
    
    fun setUI(i: Int, media: MediaItem?){
        val uri = uriArray[i]
        val file = DocumentFile.fromSingleUri(this, uri)
        if (file != null) {
            val fileName = file.name
            notificationSmall.setTextViewText(R.id.notification_title, fileName)
            notificationBig.setTextViewText(R.id.notification_title_big, fileName)
            notificationSmall.setTextViewText(R.id.notification_description, media?.mediaMetadata?.artist)
            notificationBig.setTextViewText(R.id.notification_description_big, media?.mediaMetadata?.artist)
            notificationManager.notify(1, notification)
            songLabel.text = fileName
        }
    }

    fun sliderMoved(slider: Slider, value: Float, fromUser: Boolean) {
        if (fromUser){
            val time: Long = (value*exoPlayer.duration).toLong()
            exoPlayer.seekTo(time)
        }
    }

    fun getTime(): Float? {
        return try {
            exoPlayer.currentPosition.toFloat()/exoPlayer.duration
        } catch (_: IllegalStateException){
            null
        }
    }

    fun updateUI() {
        if (exoPlayer.currentMediaItem != null){
            setUI(exoPlayer.currentMediaItemIndex, exoPlayer.currentMediaItem)
        }
    }

    fun setSong(position: Int) {
        exoPlayer.seekTo(position, 0)
        setUI(exoPlayer.currentMediaItemIndex, exoPlayer.currentMediaItem)
    }
}
