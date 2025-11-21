package com.example.patephonektmedia3

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent

class MediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (Intent.ACTION_MEDIA_BUTTON == intent?.action) {
            val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
            if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                when (keyEvent.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY -> {

                    }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        // Обработайте нажатие кнопки Pause
                    }
                }
            }
        }
    }
}
