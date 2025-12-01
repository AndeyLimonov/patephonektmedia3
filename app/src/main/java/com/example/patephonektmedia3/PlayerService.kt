package com.example.patephonektmedia3
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.C.WAKE_MODE_LOCAL
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class PlayerService : Service() {
    lateinit var notificationManager: NotificationManager
    lateinit var exoPlayer: ExoPlayer
    lateinit var notification: Notification
    private val binder = LocalBinder()
    val channelId: String = "1"
    var wasPlayed = false
    var uriArray: ArrayList<Uri>? = null
    lateinit var songLabel: TextView
    lateinit var mediaSession: MediaSessionCompat
    var artists: ArrayList<String>? = null

    inner class LocalBinder : Binder() {
        fun getService(): PlayerService = this@PlayerService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action){
            "ACTION_PLAY" -> onPlay(null)
            "ACTION_NEXT" -> onNextButton()
            "ACTION_PREV" -> onPrevButton()
            else -> onServiceStarted()
        }

        return START_STICKY
    }

    fun onServiceStarted(){
        initMediaSession()
        while (!mediaSession.isActive){
            println("No init")
        }
        updateNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                1,
                notification,
                FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        }
    }

    private fun createActionPendingEvent(action: String): PendingIntent? {
        val intent = Intent(this, PlayerService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "PlayerService")

//        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
//            setPackage(packageName)
//        }
//
//        val mediaButtonPendingIntent = PendingIntent.getBroadcast(
//            this,
//            0,
//            mediaButtonIntent,
//            PendingIntent.FLAG_IMMUTABLE
//        )
//        mediaSession.setMediaButtonReceiver(mediaButtonPendingIntent)

//        mediaSession.setCallback(mediaSessionCallback)
        mediaSession.isActive = true
    }

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
            val keyEvent: KeyEvent? = mediaButtonEvent?.extras?.getParcelable(Intent.EXTRA_KEY_EVENT)
            when (keyEvent?.keyCode){
                KeyEvent.KEYCODE_MEDIA_PLAY -> println("gg")
            }
            return super.onMediaButtonEvent(mediaButtonEvent)
        }
    }

    override fun onCreate() {
        super.onCreate()

        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        createChannel()
        exoPlayer = ExoPlayer.Builder(this).build()
        exoPlayer.setWakeMode(WAKE_MODE_LOCAL)
        exoPlayer.addListener(object: Player.Listener{
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)

                try {
                    updateUI()
                    updateNotification()
                } catch (_: UninitializedPropertyAccessException){
                    println("No textview initialized")
                }
            }
        })
    }
    fun getSongList(): ArrayList<String> {
        val list = ArrayList<String>()
        if (uriArray != null){
            for (uri in uriArray){
                val file = DocumentFile.fromSingleUri(this, uri)
                if (file != null){
                    list.add(file.name ?: "")
                }
            }
        }
        return list
    }

    fun getSongName(i: Int): String{
        val uri = uriArray?.get(i) ?: return ""
        val file = DocumentFile.fromSingleUri(this, uri)
        return file?.name ?: ""
    }
    fun createChannel(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH

            val mChannel = NotificationChannel(channelId, name, importance)
            mChannel.setSound(null, null)
            mChannel.enableVibration(false)
            mChannel.description = descriptionText
            notificationManager.createNotificationChannel(mChannel)
        }
    }

    fun addMedia(list: ArrayList<Uri>){
        uriArray = list
        if (uriArray != null){
            for (uri in uriArray) {
                exoPlayer.addMediaItem(MediaItem.fromUri(uri))
            }
        }
    }

    fun onPlay(view: View?){
        if (!wasPlayed){
            wasPlayed = true
            exoPlayer.prepare()
            updateUI()
            if (view != null){
                val button = view as Button
                button.text = ContextCompat.getString(this, R.string.pause_button)
            }
        }

        if (!exoPlayer.isPlaying){
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }

        if (view != null){
            updatePlayButton(view as Button)
        }
    }

    fun onNextButton(){
        if (exoPlayer.hasNextMediaItem()){
            exoPlayer.seekToNext()
            updateUI()
            println("current Artist: ")
        }
    }

    fun onPrevButton(){
        if (exoPlayer.hasPreviousMediaItem()){
            exoPlayer.seekToPrevious()
            updateUI()
        }
    }

    fun addSongLabel(textView: TextView) {
        songLabel = textView
    }
    override fun onDestroy() {
        mediaSession.release()
        exoPlayer.release()
        stopSelf()
        super.onDestroy()
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
    
    fun updateUI(){
        val fileName = getSongName(exoPlayer.currentMediaItemIndex)
        updateNotification()
        notificationManager.notify(1, notification)
        songLabel.text = fileName
    }

    private fun updateNotification() {
        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)
            .setMediaSession(mediaSession.sessionToken)
        val songName = getSongName(exoPlayer.currentMediaItemIndex)
        val artist = artists?.get(exoPlayer.currentMediaItemIndex) ?: "Unknown"

        //Create actions
        val actionPlay = NotificationCompat.Action(android.R.drawable.ic_media_play, "play", createActionPendingEvent("ACTION_PLAY"))
        val actionNext = NotificationCompat.Action(android.R.drawable.ic_media_next, "next", createActionPendingEvent("ACTION_NEXT"))
        val actionPrev = NotificationCompat.Action(android.R.drawable.ic_media_previous, "prev", createActionPendingEvent("ACTION_PREV"))

        notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setStyle(mediaStyle)
                .setContentTitle(songName)
                .setContentText(artist)
                .addAction(actionPrev)
                .addAction(actionNext)
                .addAction(actionPlay)
                .setOngoing(true)
                .build()
        } else {
            throw kotlin.NullPointerException("Bad sdk version")
        }
        notificationManager.notify(1, notification)
    }

    fun sliderMoved(value: Float, fromUser: Boolean) {
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

//    fun updateUI() {
//        if (exoPlayer.currentMediaItem != null){
//            updateUI(exoPlayer.currentMediaItemIndex, exoPlayer.currentMediaItem)
//        }
//    }

    fun setSong(position: Int) {
        exoPlayer.seekTo(position, 0)
        updateUI()
    }

    fun getCurrentSong(): String{
        if (exoPlayer.currentMediaItem != null && uriArray != null){
            val file = DocumentFile.fromSingleUri(this, uriArray!![exoPlayer.currentMediaItemIndex])
            return file?.name ?: ""
        }
        return ""
    }

    fun addArtists(artists: ArrayList<String>) {
        this.artists = artists
    }

    fun updatePlayButton(button: Button) {
        when (exoPlayer.isPlaying){
            true -> button.text = getString(R.string.pause_button)
            false -> button.text = getString(R.string.play_button)
        }
    }
}
