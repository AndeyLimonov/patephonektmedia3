package com.example.patephonektmedia3

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

const val REQUEST_CODE_OPEN_DIRECTORY: Int = 1
val supportedFiles: Array<String> = arrayOf("mp3")

class MainActivity : AppCompatActivity() {
    lateinit var exoPlayer: ExoPlayer

    var playedYet = false
    var isPlaying = false
    var wasImported = false


        override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val importButton: Button = findViewById(R.id.importButton)
        importButton.setOnClickListener { v -> onImport() }

        val startButton: Button = findViewById(R.id.playButton)
        startButton.setOnClickListener { v -> onPlayButton() }

        val stopButton: Button = findViewById(R.id.stopButton)
        stopButton.setOnClickListener { v -> onStopButton() }

    }

    private fun  onImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)

        startActivityForResult(intent, REQUEST_CODE_OPEN_DIRECTORY)
    }

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
                        listFiles(uri)
                        playedYet = false
                        wasImported = true
                    }

                }
            }
    }

    private fun fileSupported(file: DocumentFile): Boolean {
        if (file.isDirectory) {
            return false
        }
        val fileName: String = file.name
        try {
            val fileType: String = fileName.split(".")[1]
            for (type in supportedFiles){
                return (fileType == type)
            }
        } catch (e: Exception){println("file doesn't support: $fileName")}
        return false
    }

    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    private fun listFiles(uri: Uri) {
        val directory: DocumentFile = DocumentFile.fromTreeUri(this, uri)
        if (directory.isDirectory){
            for (file: DocumentFile in directory.listFiles()){
                if (fileSupported(file)){
                    val mediaItem: MediaItem = MediaItem.fromUri(file.uri)
                    exoPlayer = ExoPlayer.Builder(this).build()
                    exoPlayer.addMediaItem(mediaItem)
                }
            }
        }
    }
    private fun onPlayButton() {
        if (wasImported) {
            val playButton: Button = findViewById(R.id.playButton)
            if (!isPlaying) {
                if (!playedYet) {
                    exoPlayer.prepare()
                    exoPlayer.play()
                    playedYet = true
                    isPlaying = true
                    playButton.text = "Pause"
                    val text: String = exoPlayer.toString()
                    val songLabel: TextView = findViewById(R.id.songLabel)
                    songLabel.text = text
                } else {
                    exoPlayer.play()
                    isPlaying = true
                    playButton.text = "Pause"
                }
            } else {
                exoPlayer.pause()
                isPlaying = false
                playButton.text = "Play"
            }
        }
    }


    private fun onStopButton(){
        if (playedYet){
            exoPlayer.stop()
            isPlaying = false
            playedYet = false
            val playButton: Button = findViewById(R.id.playButton)
            playButton.text = "Play"
        }
    }
}
