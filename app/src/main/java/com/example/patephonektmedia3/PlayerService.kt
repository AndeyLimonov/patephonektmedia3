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
import android.util.Log
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
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
    lateinit var artistLabel: TextView
    lateinit var mediaSession: MediaSessionCompat
    var titles: ArrayList<String>? = null
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
            "ACTION_PLAY" -> onPlay()
            "ACTION_NEXT" -> onNextButton()
            "ACTION_PREV" -> onPrevButton()
            else -> onServiceStarted()
        }

        return START_STICKY
    }

    fun onServiceStarted(){
        initMediaSession()
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
//        val mediaButtonReceiver = MediaButtonReceiver()
//        val filter = IntentFilter(Intent.ACTION_MEDIA_BUTTON)
//        registerReceiver(mediaButtonReceiver, filter)

        mediaSession = MediaSessionCompat(this, "PlayerService").apply {
//            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            isActive = true
        }

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

    fun onPlay(){
        if (!wasPlayed){
            wasPlayed = true
            exoPlayer.prepare()
            updateUI()
        }
        var playing = exoPlayer.isPlaying
        if (!playing){
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
        playing = !playing
        val intent = Intent(this, MainActivity::class.java).apply {
            action = "ACTION_PLAY_BUTTON_TEXT"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("PLAYING", playing)
        }
        startActivity(intent)
        Log.d("PlayerService", "Starting intent: ${intent.action} with value ${intent.getBooleanExtra("PLAYING", false)}")
    }

    fun onNextButton(){
        if (exoPlayer.hasNextMediaItem()){
            exoPlayer.seekToNext()
            updateUI()
        }
    }

    fun onPrevButton(){
        if (exoPlayer.hasPreviousMediaItem()){
            exoPlayer.seekToPrevious()
            updateUI()
        }
    }

    fun addLabels(songLabel: TextView, artistLabel: TextView) {
        this.songLabel = songLabel
        this.artistLabel = artistLabel
    }
    override fun onDestroy() {
        Log.d("PlayerService", "Service stopped")
        uriArray = null
        mediaSession.release()
        exoPlayer.release()
        stopSelf()
        super.onDestroy()
    }

    fun onStopButton() {
        wasPlayed = false
        exoPlayer.stop()
        exoPlayer.seekToDefaultPosition()
        val intent = Intent(this, MainActivity::class.java).apply {
            action = "ACTION_PLAY_BUTTON_TEXT"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("PLAYING", false)
        }
        startActivity(intent)
        Log.d("PlayerService", "Starting intent: ${intent.action} with value ${intent.getBooleanExtra("PLAYING", false)}")
    }
    
    fun updateUI(){
        val songName = titles?.get(exoPlayer.currentMediaItemIndex) ?: "Unknown"
        val artist = artists?.get(exoPlayer.currentMediaItemIndex) ?: "Unknown"
        updateNotification()
        notificationManager.notify(1, notification)
        songLabel.text = songName
        artistLabel.text = artist
    }

    private fun updateNotification() {
        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)
            .setMediaSession(mediaSession.sessionToken)
        val songName = titles?.get(exoPlayer.currentMediaItemIndex) ?: "Unknown"
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

    fun addTitles(titles: ArrayList<String>) {
        this.titles = titles
    }
}
