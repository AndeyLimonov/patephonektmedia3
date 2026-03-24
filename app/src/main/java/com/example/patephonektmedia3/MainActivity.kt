package com.example.patephonektmedia3

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.slider.Slider
import java.util.Timer
import java.util.TimerTask

const val REQUEST_CODE_OPEN_DIRECTORY: Int = 1
val supportedFiles: Array<String> = arrayOf("mp3")

class MainActivity : AppCompatActivity() {
    private var songList: ArrayList<Song> = ArrayList()

    var service: PlayerService? = null
    var hasSupFiles = false
    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(className: ComponentName, service: IBinder) {
                if (hasSupFiles) {
                    val binder = service as PlayerService.LocalBinder
                    this@MainActivity.service = binder.getService()
                    this@MainActivity.service?.addSongs(songList)
                    this@MainActivity.service?.setViews(findViewById (R.id.songLabel), findViewById(R.id.artistLabel))
                }
            }

            override fun onServiceDisconnected(p0: ComponentName?) {
                TODO("Not yet implemented")
            }
        }
    fun getSongTitle(mediaUri: Uri): String {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(applicationContext, mediaUri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            return title ?: DocumentFile.fromSingleUri(applicationContext, mediaUri)!!.name
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
            when (intent?.action) {
                "ACTION_PLAY_BUTTON_TEXT" -> onChangePlayButton(intent)
            }
            super.onNewIntent(intent)
        }

        private fun onChangePlayButton(intent: Intent) {
            val button = findViewById<Button>(R.id.playButton)

            val text = when (intent.getBooleanExtra("PLAYING", false)) {
                true -> ContextCompat.getString(applicationContext, R.string.pause_button)
                false -> ContextCompat.getString(applicationContext, R.string.play_button)
            }
            button.text = text

            Log.d(
                "MainActivity",
                "Button text changed: $text, ${intent.getBooleanExtra("PLAYING", false)}"
            )
        }


        @SuppressLint("ClickableViewAccessibility")
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            enableEdgeToEdge()
            setContentView(R.layout.activity_main)

            val importButton: Button = findViewById(R.id.importButton)
            importButton.setOnClickListener { _ -> onImport() }

            val playButton: Button = findViewById(R.id.playButton)
            playButton.setOnClickListener { _ -> service?.onPlay() }

            val nextButton: Button = findViewById(R.id.nextButton)
            nextButton.setOnClickListener { _ -> service?.onNextButton() }

            val prevButton: Button = findViewById(R.id.prevButton)
            prevButton.setOnClickListener { _ -> service?.onPrevButton() }

            val stopButton: Button = findViewById(R.id.stopButton)
            stopButton.setOnClickListener {_ -> service?.onStopButton() }

            val slider: Slider = findViewById(R.id.slider)
            slider.addOnChangeListener { _ , value, fromUser ->
                service?.sliderMoved(
                    value,
                    fromUser
                )
            }

            val frameLayout: FrameLayout = findViewById(R.id.frameLayout2)
            frameLayout.setOnClickListener { _ -> onFrameClick(frameLayout) }

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

            val handler = Handler(Looper.getMainLooper())

            val sliderTask = object : TimerTask() {
                override fun run() {
                    handler.post {
                        val float: Float? = service?.getTime()
                        if (float != null) {
                            val clampedValue = float.coerceIn(0f, 1f)
                            slider.value = clampedValue
                        }
                    }
                }
            }
            val sliderTimer = Timer()
            sliderTimer.schedule(sliderTask, 0, 1000)

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
            if (service == null) {
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
            val songNameList = service?.getSongList()
            val currentSong = service?.getCurrentSong() ?: ""
            val position = songNameList?.indexOf(currentSong) ?: 0

            //Fill songList
            val adapter = ListAdapter(this, songNameList!!, service, currentSong)
            songList.adapter = adapter
            songList.setSelection(position)
        }

        @Suppress("DEPRECATION")
        private fun onImport() {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(intent, REQUEST_CODE_OPEN_DIRECTORY)
        }

        @SuppressLint("UnsafeIntentLaunch")
        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)

            if (requestCode == REQUEST_CODE_OPEN_DIRECTORY && resultCode == RESULT_OK) {
                if (findViewById<ListView>(R.id.songList).isVisible) {
                    collapseFrame(findViewById(R.id.frameLayout2))
                }
                //Reset service if it's already initialized
                val intent = Intent(applicationContext, PlayerService::class.java)

                if (service != null) {
                    service?.onDestroy()
                    unbindService(connection)
                }
                service = null
                if (data != null) {
                    val uri: Uri? = data.data
                    if (uri != null) {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        //Starting service
                        val context = applicationContext
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        }
                        try {
                            bindService(intent, connection, BIND_AUTO_CREATE)
                        } catch (e: Exception) {
                            val iME = IllegalMediaException(null, e, "no supported files found")
                            iME.printStackTrace()
                        }

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
                        return fileType == type
                    }
                } catch (e: Exception) {
                    val ex = IllegalMediaException(fileName, e)
                    ex.printStackTrace()
                }
            }
            return false
        }

        override fun onResume() {
            super.onResume()
            service?.addLabels(findViewById(R.id.songLabel), findViewById(R.id.artistLabel))
            service?.updateUI()
        }


        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        private fun listFiles(uri: Uri) {
            val directory: DocumentFile = DocumentFile.fromTreeUri(this, uri)
            if (directory.isDirectory) {
                for (file: DocumentFile in directory.listFiles()) {
                    if (fileSupported(file)) {
                        hasSupFiles = true
                        val fileUri = file.uri
                        val song = Song(
                            fileUri,
                            getSongTitle(fileUri),
                            getSongArtist(fileUri)
                        )
                        songList.add(song)
                    }
                }
            }
        }

        override fun onDestroy() {
            super.onDestroy()

            service?.onDestroy()
            unbindService(connection)
        }
    }