package com.example.patephonektmedia3

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.preference.PreferenceManager
import com.google.android.material.slider.Slider
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask

const val REQUEST_CODE_OPEN_DIRECTORY: Int = 1
val supportedFiles: Array<String> = arrayOf("mp3", "flac")

class MainActivity : AppCompatActivity() {
    var mediaController: MediaController? = null
    var hasSupFiles = false
    fun getSongTitle(mediaUri: Uri): String {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(applicationContext, mediaUri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            return title ?: DocumentFile.fromSingleUri(applicationContext, mediaUri)!!.name
            ?: "Unknown"
        } catch (e: Exception) {
            e.printStackTrace()
            return "Unknown"
        } finally {
            retriever.release()
        }
    }

    fun getSongArtist(mediaUri: Uri): String {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(applicationContext, mediaUri)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            return artist ?: "Unknown"
        } catch (e: Exception) {
            e.printStackTrace()
            return "Unknown"
        } finally {
            retriever.release()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        if (intent != null && intent.getBooleanExtra("playerStateChanged", false)) updateUI()
        super.onNewIntent(intent)
    }

    fun updateUI() {
        //Checking if session was initialized
        if (mediaController == null) return

        onChangePlayButton(mediaController!!.isPlaying)
        //Setting title
        val songLabel: TextView = findViewById(R.id.songLabel)
        songLabel.text = mediaController!!.currentMediaItem?.mediaMetadata?.title ?: "Unknown"

        //Setting artist
        val artistLabel: TextView = findViewById(R.id.artistLabel)
        artistLabel.text = mediaController!!.currentMediaItem?.mediaMetadata?.artist ?: "Unknown"

        Log.d("MainActivity", "UI updated")
    }

    private fun onChangePlayButton(isPlaying: Boolean) {
        val button = findViewById<Button>(R.id.playButton)

        val text = when (isPlaying) {
            true -> ContextCompat.getString(applicationContext, R.string.pause_button)
            false -> ContextCompat.getString(applicationContext, R.string.play_button)
        }
        button.text = text

        Log.d(
            "MainActivity",
            "Button text changed: $text, ${intent.getBooleanExtra("PLAYING", false)}"
        )
    }


    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startForegroundService(Intent(this, UpdatedService::class.java))
        lifecycleScope.launch {
            val context = applicationContext
            val sessionToken = SessionToken(
                context,
                ComponentName(this@MainActivity, UpdatedService::class.java)
            )
            mediaController =
                MediaController.Builder(context, sessionToken).buildAsync().await()
        }
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val importButton: Button = findViewById(R.id.importButton)
        importButton.setOnClickListener { _ -> onImport() }

        val playButton: Button = findViewById(R.id.playButton)
        playButton.setOnClickListener { _ -> onPlayButtonClicked() }

        val nextButton: Button = findViewById(R.id.nextButton)
        nextButton.setOnClickListener { _ -> mediaController?.seekToNext() }

        val prevButton: Button = findViewById(R.id.prevButton)
        prevButton.setOnClickListener { _ -> mediaController?.seekToPrevious() }

        val stopButton: Button = findViewById(R.id.stopButton)
        stopButton.setOnClickListener { _ -> onStopButton() }

        val frameLayout: FrameLayout = findViewById(R.id.frameLayout2)
        frameLayout.setOnClickListener { _ -> onFrameClick(frameLayout) }

        val settingsButton: ImageButton = findViewById(R.id.settingsButton)
        settingsButton.setOnClickListener { _ -> onSettingsClick() }

        val root = findViewById<View>(R.id.main)
        root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (findViewById<ListView>(R.id.songList).isVisible) {
                    val x = event.x
                    val y = event.y
                    if (!isPointInsideView(x, y, frameLayout)) {
                        collapseFrame(frameLayout)
                    }
                }
            }
            false
        }

        val slider: Slider = findViewById(R.id.slider)

        slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}

            override fun onStopTrackingTouch(slider: Slider) {
                val time: Long = (slider.value * mediaController!!.contentDuration).toLong()
                mediaController?.seekTo(time)
            }
        })

        val handler = Handler(Looper.getMainLooper())

        val sliderTask = object : TimerTask() {
            override fun run() {
                if (mediaController != null) {
                    handler.post {
                        val float: Float =
                            mediaController!!.currentPosition.toFloat() / mediaController!!.duration
                        val clampedValue = float.coerceIn(0f, 1f)
                        slider.value = clampedValue
                    }
                }
            }
        }
        val sliderTimer = Timer()
        sliderTimer.schedule(sliderTask, 0, 1000)
    }

    private fun onSettingsClick() {
        val intent = Intent(applicationContext, SettingsActivity::class.java)
        startActivity(intent)
    }

    private fun onStopButton() {
        mediaController?.stop()
        mediaController?.release()
        mediaController = null
    }

    private fun onPlayButtonClicked() {
        if (mediaController == null) return
        val isPlaying = mediaController!!.isPlaying
        if (isPlaying) {
            mediaController?.pause()
        } else {
            mediaController?.play()
        }
        onChangePlayButton(!isPlaying)
    }

    private fun collapseFrame(frameLayout: FrameLayout) {
        //Target properties in DP:
        val targetWidth = 294
        val targetHeight = 60
        val targetMarginBottom = 100

        //Converting values to pixels
        val targetWidthPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            targetWidth.toFloat(),
            resources.displayMetrics
        ).toInt()
        val targetHeightPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            targetHeight.toFloat(),
            resources.displayMetrics
        ).toInt()
        val targetMarginBottomPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            targetMarginBottom.toFloat(),
            resources.displayMetrics
        ).toInt()

        //Starting animations
        val layoutParams = frameLayout.layoutParams

        val widthAnimator = ValueAnimator.ofInt(frameLayout.width, targetWidthPx)
        widthAnimator.addUpdateListener { animation ->
            layoutParams.width = animation.animatedValue as Int
            frameLayout.layoutParams = layoutParams
        }
        val heightAnimator = ValueAnimator.ofInt(frameLayout.height, targetHeightPx)
        heightAnimator.addUpdateListener { animation ->
            layoutParams.height = animation.animatedValue as Int
            frameLayout.layoutParams = layoutParams
        }

        widthAnimator.duration = 300
        heightAnimator.duration = 300

        widthAnimator.start()
        heightAnimator.start()

        //Margin animation
        val params = frameLayout.layoutParams as ViewGroup.MarginLayoutParams
        val marginAnimation = ValueAnimator.ofInt(params.bottomMargin, targetMarginBottomPx)
        marginAnimation.addUpdateListener { animation ->
            params.bottomMargin = animation.animatedValue as Int
            frameLayout.layoutParams = params
        }
        marginAnimation.duration = 300
        marginAnimation.start()

        //Animating alpha for all frameLayout children
        val songList = findViewById<ListView>(R.id.songList)
        for (child in frameLayout.children) {
            if (child.id != R.id.songList) {
                val opacityAnimator: ObjectAnimator =
                    ObjectAnimator.ofFloat(child, "alpha", 0f, 1f)
                opacityAnimator.duration = 300
                opacityAnimator.start()
            } else {
                val opacityAnimator: ObjectAnimator =
                    ObjectAnimator.ofFloat(child, "alpha", 1f, 0f)
                opacityAnimator.duration = 300
                opacityAnimator.addListener(object : Animator.AnimatorListener {
                    override fun onAnimationEnd(animation: Animator) {
                        songList.visibility = View.INVISIBLE
                    }

                    override fun onAnimationCancel(animation: Animator) {}

                    override fun onAnimationRepeat(animation: Animator) {}

                    override fun onAnimationStart(animation: Animator) {}
                })
                opacityAnimator.start()
            }
        }
        //Setting main frame visible
        mainFrameAnimator(true)
    }

    //Use true to set frame visible or false to set it invisible
    private fun mainFrameAnimator(arg: Boolean) {
        val mainFrame = findViewById<FrameLayout>(R.id.frameLayout)
        var objectAnimator: ObjectAnimator

        if (arg) {
            objectAnimator = ObjectAnimator.ofFloat(mainFrame, "alpha", 0f, 1f)
            objectAnimator.duration = 300
            objectAnimator.addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {
                    mainFrame.visibility = View.VISIBLE
                }

                override fun onAnimationEnd(animation: Animator) {}
                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}
            })
        } else {
            objectAnimator = ObjectAnimator.ofFloat(mainFrame, "alpha", 1f, 0f)
            objectAnimator.duration = 300
            objectAnimator.addListener(object : Animator.AnimatorListener {
                override fun onAnimationEnd(animation: Animator) {
                    mainFrame.visibility = View.INVISIBLE
                }

                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationStart(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}
            })
        }
        objectAnimator.start()
    }

    private fun isPointInsideView(x: Float, y: Float, view: View): Boolean {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return x >= location[0] && x <= location[0] + view.width &&
                y >= location[1] && y <= location[1] + view.height
    }

    private fun onFrameClick(frameLayout: FrameLayout) {
        if (mediaController == null) {
            return
        }

        val layoutParams = frameLayout.layoutParams
        val displayMetrics = resources.displayMetrics

        val widthAnimator = ValueAnimator.ofInt(frameLayout.width, displayMetrics.widthPixels)
        widthAnimator.addUpdateListener { animation ->
            layoutParams.width = animation.animatedValue as Int
            frameLayout.layoutParams = layoutParams
        }
        val heightAnimator = ValueAnimator.ofInt(
            frameLayout.height,
            displayMetrics.heightPixels / 2
        )//Cause frame should take a half
        heightAnimator.addUpdateListener { animation ->
            layoutParams.height = animation.animatedValue as Int
            frameLayout.layoutParams = layoutParams
        }
        //Setting main frame visible
        mainFrameAnimator(false)

        widthAnimator.duration = 300
        heightAnimator.duration = 300

        widthAnimator.start()
        heightAnimator.start()

        //Margin animation
        val params = frameLayout.layoutParams as ViewGroup.MarginLayoutParams
        val marginAnimation = ValueAnimator.ofInt(params.bottomMargin, 0)
        marginAnimation.addUpdateListener { animation ->
            params.bottomMargin = animation.animatedValue as Int
            frameLayout.layoutParams = params
        }
        marginAnimation.duration = 300
        marginAnimation.start()

        val songList: ListView = findViewById(R.id.songList)
        for (child in frameLayout.children) {
            if (child.id != R.id.songList) {
                val opacityAnimator: ObjectAnimator =
                    ObjectAnimator.ofFloat(child, "alpha", 1f, 0f)
                opacityAnimator.duration = 300
                opacityAnimator.start()
            } else {
                songList.visibility = View.VISIBLE
                val opacityAnimator: ObjectAnimator =
                    ObjectAnimator.ofFloat(child, "alpha", 0f, 1f)
                opacityAnimator.duration = 300
                opacityAnimator.start()
            }
        }
        val songNameList = ArrayList<String>()
        for (i in 0..<mediaController!!.mediaItemCount) {
            val title: String =
                mediaController!!.getMediaItemAt(i).mediaMetadata.title as? String ?: "Unknown"
            songNameList.add(title)
        }
        val currentSong = mediaController?.currentMediaItem?.mediaMetadata?.title as? String ?: ""
        val position = mediaController!!.currentMediaItemIndex

        //Fill songList
        val adapter = ListAdapter(this, songNameList, mediaController, currentSong)
        songList.adapter = adapter
        songList.setSelection(position)
    }

    @Suppress("DEPRECATION")
    private fun onImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, REQUEST_CODE_OPEN_DIRECTORY)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("UnsafeIntentLaunch")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_OPEN_DIRECTORY && resultCode == RESULT_OK) {
            if (findViewById<ListView>(R.id.songList).isVisible) {
                collapseFrame(findViewById(R.id.frameLayout2))
            }

            mediaController?.stop()
            mediaController?.clearMediaItems()

            if (data != null) {
                val uri: Uri? = data.data
                if (uri != null) {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )

                    listFiles(uri)
                }
            }
        } else {
            return
        }
    }

    private fun fileSupported(file: DocumentFile): Boolean {
        if (file.isDirectory) {
            return false
        }
        val fileName: String? = file.name
        if (fileName != null) {
            try {
                val fileArr = fileName.split(".")
                val fileType: String = fileArr[fileArr.size - 1]
                if (fileType == "jpg") {
                    val image: ImageView = findViewById(R.id.mainFramePicture)
                    image.setImageURI(file.uri)
                    val innerPic: ImageView = findViewById(R.id.mainFrameNoImage)
                    innerPic.visibility = View.INVISIBLE
                    return false
                }
                for (type in supportedFiles) {
                    if (fileType == type){
                        return true
                    }
                }
                return false
            } catch (e: Exception) {
                val ex = IllegalMediaException(fileName, e)
                ex.printStackTrace()
            }
        }
        return false
    }

    override fun onResume() {
        updateSettings()
        if (mediaController != null){
            updateUI()
        }
        super.onResume()
    }

    private fun updateSettings() {
        var prefString = ""
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        //Shuffle mode
        val shuffleModeEnabled = prefs.getBoolean("preference_shuffle", false)
        mediaController?.shuffleModeEnabled = shuffleModeEnabled
        prefString += "Shuffle mode: $shuffleModeEnabled\n"
        //Repeat mode
        val repeatMode = when (prefs.getString("list_preference_repeat", "0")){
            "1" -> Player.REPEAT_MODE_ONE
            "2" -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        mediaController?.repeatMode = repeatMode
        prefString += "Repeat mode: $repeatMode\n"

        Log.i("New preferences applied:", prefString)
    }

    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    private fun listFiles(uri: Uri) {
        val directory: DocumentFile? = DocumentFile.fromTreeUri(this, uri)
        if (directory != null && directory.isDirectory) {
            for (file: DocumentFile in directory.listFiles()) {
                if (fileSupported(file)) {
                    hasSupFiles = true
                    val fileUri = file.uri
                    val mediaItem =
                        MediaItem.Builder()
                            .setMediaId("media-1")
                            .setUri(fileUri)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setArtist(getSongArtist(fileUri))
                                    .setTitle(getSongTitle(fileUri))
                                    .build()
                            )
                            .build()
                    mediaController?.addMediaItem(mediaItem)
                }
            }
            Log.d("MainActivity", "added " + mediaController?.mediaItemCount + " songs")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaController?.stop()
        mediaController?.release()
        mediaController = null
    }
}