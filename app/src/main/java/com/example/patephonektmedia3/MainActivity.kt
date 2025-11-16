package com.example.patephonektmedia3

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.slider.Slider
import java.util.Timer
import java.util.TimerTask
import androidx.core.view.isVisible


const val REQUEST_CODE_OPEN_DIRECTORY: Int = 1
val supportedFiles: Array<String> = arrayOf("mp3")

class MainActivity : AppCompatActivity() {
    var mediaList: ArrayList<Uri> = ArrayList()

    var service: PlayerService? = null
    var hasSupFiles = false
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            if (hasSupFiles){
                val binder = service as PlayerService.LocalBinder
                this@MainActivity.service = binder.getService()
                for (mediaUri in mediaList){
                    this@MainActivity.service?.addMedia(mediaUri)
                    this@MainActivity.service?.addSongLabel(findViewById(R.id.songLabel))
                }
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            service = null
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val importButton: Button = findViewById(R.id.importButton)
        importButton.setOnClickListener { v -> onImport() }

        val playButton: Button = findViewById(R.id.playButton)
        playButton.setOnClickListener { v -> service?.onPlay(v) }

        val nextButton: Button = findViewById(R.id.nextButton)
        nextButton.setOnClickListener { v -> service?.onNextButton() }

        val prevButton: Button = findViewById(R.id.prevButton)
        prevButton.setOnClickListener { v -> service?.onPrevButton() }

        val stopButton: Button = findViewById(R.id.stopButton)
        stopButton.setOnClickListener { v -> service?.onStopButton(playButton) }

        val slider: Slider = findViewById(R.id.slider)
        slider.addOnChangeListener {slider, value, fromUser  -> service?.sliderMoved(slider, value, fromUser) }

        val frameLayout: FrameLayout = findViewById(R.id.frameLayout2)
        frameLayout.setOnClickListener {v -> onFrameClick(frameLayout)}

        val root = findViewById<View>(R.id.main)
        root.setOnTouchListener { v, event -> 
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

        val timerTask = object : TimerTask() {
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
        val timer = Timer()
        timer.schedule(timerTask, 0, 1000)
    }

    private fun collapseFrame(frameLayout: FrameLayout) {
        //Target properties in DP:
        val targetWidth = 294
        val targetHeight = 60

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

        val songList = findViewById<ListView>(R.id.songList)

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


        for (child in frameLayout.children){
            if (child.id != R.id.songList) {
                val opacityAnimator: ObjectAnimator = ObjectAnimator.ofFloat(child, "alpha", 0f, 1f)
                opacityAnimator.duration = 300
                opacityAnimator.start()
            } else {
                val opacityAnimator: ObjectAnimator = ObjectAnimator.ofFloat(child, "alpha", 1f, 0f)
                opacityAnimator.duration = 300
                opacityAnimator.addListener(object: Animator.AnimatorListener {
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
    }


    private fun isPointInsideView(x: Float, y: Float, view: View): Boolean {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return x >= location[0] && x <= location[0] + view.width &&
                y >= location[1] && y <= location[1] + view.height
    }
    private fun onFrameClick(frameLayout: FrameLayout) {
        if (service == null){return}

        val layoutParams = frameLayout.layoutParams

        val widthAnimator = ValueAnimator.ofInt(frameLayout.width, resources.displayMetrics.widthPixels)
        widthAnimator.addUpdateListener { animation ->
            layoutParams.width = animation.animatedValue as Int
            frameLayout.layoutParams = layoutParams
        }
        val heightAnimator = ValueAnimator.ofInt(frameLayout.height, 450)
        heightAnimator.addUpdateListener { animation ->
            layoutParams.height = animation.animatedValue as Int
            frameLayout.layoutParams = layoutParams
        }

        widthAnimator.duration = 300
        heightAnimator.duration = 300

        widthAnimator.start()
        heightAnimator.start()

        val songList: ListView = findViewById(R.id.songList)
        for (child in frameLayout.children){
            if (child.id != R.id.songList) {
                val opacityAnimator: ObjectAnimator = ObjectAnimator.ofFloat(child, "alpha", 1f, 0f)
                opacityAnimator.duration = 300
                opacityAnimator.start()
            } else {
                songList.visibility = View.VISIBLE
                val opacityAnimator: ObjectAnimator = ObjectAnimator.ofFloat(child, "alpha", 0f, 1f)
                opacityAnimator.duration = 300
                opacityAnimator.start()
            }
        }
        songList.visibility = View.VISIBLE
        val songNameList = service?.getSongList()

        val adapter = MyListAdapter(this, songNameList!!, service)
        songList.adapter = adapter

    }

    @Suppress("DEPRECATION")
    private fun  onImport() {
        service?.onStopButton(findViewById(R.id.playButton))
        service?.resetPlayer()
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)

        startActivityForResult(intent, REQUEST_CODE_OPEN_DIRECTORY)
    }

    @SuppressLint("UnsafeIntentLaunch")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?){
        super.onActivityResult(requestCode, resultCode, data)

            if (requestCode == REQUEST_CODE_OPEN_DIRECTORY && resultCode == RESULT_OK){
                if (data != null){
                    val uri: Uri? = data.data
                    if (uri != null) {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        val intent = Intent(this, PlayerService::class.java)
                        val context = applicationContext
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        }
                        val notificationManager = this.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                        if(!notificationManager.areNotificationsEnabled()){
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val intentNotification = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                intentNotification.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                startActivity(intentNotification)
                            }
                        }
                        try {
                            bindService(intent, connection, BIND_AUTO_CREATE)
                        } catch (e: Exception){
                            val iME = IllegalMediaException(null, e, "no supported files found")
                            iME.printStackTrace()
                        }

                        listFiles(uri)
                    }

                }
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

    override fun onResume(){
        super.onResume()
        service?.updateUI()
    }

    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    private fun listFiles(uri: Uri) {
        val directory: DocumentFile = DocumentFile.fromTreeUri(this, uri)
        if (directory.isDirectory){
            for (file: DocumentFile in directory.listFiles()){
                if (fileSupported(file)){
                    hasSupFiles = true
                    if (service != null){
                        service!!.addMedia(file.uri)
                    } else {
                        mediaList.add(file.uri)
                    }
                }

            }
        }
    }


    override fun onDestroy(){
        super.onDestroy()

        service?.onDestroy()
        unbindService(connection)
    }

}

class MyListAdapter(context: Context, items: ArrayList<String>, val service: PlayerService?) : ArrayAdapter<String>(context, 0, items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val item = getItem(position) ?: return convertView!!

        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.list_item, parent, false)
        val textView = view.findViewById<TextView>(R.id.item_text)
        textView.text = item

        view.setOnClickListener { v -> setSong(position) }

        return view
    }

    private fun setSong(position: Int) {
        service?.setSong(position)
    }
}

class IllegalMediaException(fileName: String?, e: Exception, message: String = "File not supported: $fileName; $e") : Exception(message)
