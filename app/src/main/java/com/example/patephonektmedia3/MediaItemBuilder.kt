package com.example.patephonektmedia3

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

class MediaItemBuilder(val uri: Uri, val context: Context) {
    fun buildMediaItem(): MediaItem {
        val mediaItem =
            MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setArtist(getSongArtist(uri))
                        .setTitle(getSongTitle(uri))
                        .build()
                )
                .build()
        return mediaItem
    }

    private fun getSongTitle(mediaUri: Uri): String {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, mediaUri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            return title ?: DocumentFile.fromSingleUri(context, mediaUri)!!.name
            ?: "Unknown"
        } catch (e: Exception) {
            e.printStackTrace()
            return "Unknown"
        } finally {
            retriever.release()
        }
    }

    private fun getSongArtist(mediaUri: Uri): String {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, mediaUri)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            return artist ?: "Unknown"
        } catch (e: Exception) {
            e.printStackTrace()
            return "Unknown"
        } finally {
            retriever.release()
        }
    }
}