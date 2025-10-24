package com.example.patephonektmedia3

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.MediaItem

const val REQUEST_CODE_OPEN_DIRECTORY: Int = 1
val supportedFiles: Array<String> = arrayOf("mp3")

class MainActivity : AppCompatActivity() {
    var mediaList: ArrayList<Uri> = ArrayList()

    var service: PlayerService? = null
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as PlayerService.LocalBinder
            this@MainActivity.service = binder.getService()
            for (mediaUri in mediaList){
                val mediaItem = MediaItem.fromUri(mediaUri)
                this@MainActivity.service?.addMedia(mediaItem)
                this@MainActivity.service?.addSongLabel(findViewById(R.id.songLabel))
            }
        }

        //хуй

        override fun onServiceDisconnected(arg0: ComponentName) {
            service = null
        }
    }

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
    }

    @Suppress("DEPRECATION")
    private fun  onImport() {
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
                        bindService(intent, connection, BIND_AUTO_CREATE)

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
                val fileType: String = fileName.split(".")[1]
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

    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    private fun listFiles(uri: Uri) {
        val directory: DocumentFile = DocumentFile.fromTreeUri(this, uri)
        if (directory.isDirectory){
            for (file: DocumentFile in directory.listFiles()){
                if (fileSupported(file)){
                    mediaList.add(file.uri)
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

class IllegalMediaException(fileName: String, e: Exception, message: String = "File not supported: $fileName; $e") : Exception(message)
