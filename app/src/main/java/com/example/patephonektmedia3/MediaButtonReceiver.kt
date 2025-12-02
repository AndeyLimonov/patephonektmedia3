package com.example.patephonektmedia3

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent

class MediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (Intent.ACTION_MEDIA_BUTTON == intent?.action) {
            val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
            keyEvent?.let {
                when (it.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        // Логика для воспроизведения
                    }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        // Логика для паузы
                    }
                    KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        // Логика для следующей песни
                    }
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        // Логика для предыдущей песни
                    }
                }
            }
        }
    }
}
