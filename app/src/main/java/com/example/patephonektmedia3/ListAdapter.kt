package com.example.patephonektmedia3

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController

class ListAdapter(context: Context, items: ArrayList<String>, val controller: MediaController?, var currentSong: String) : ArrayAdapter<String>(context, 0, items) {
    lateinit var currentSongView: View

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val item = getItem(position) ?: return convertView!!

        val view =
            convertView ?: LayoutInflater.from(context).inflate(R.layout.list_item, parent, false)
        view.setBackgroundResource(R.drawable.frame)
        val textView = view.findViewById<TextView>(R.id.item_text)
        textView.text = item

        view.setOnClickListener { _ -> setSong(position, view) }

        //Light frame for current song
        if (item == currentSong) {
            val drawable = view.background as Drawable
            drawable.setTint(ContextCompat.getColor(context, R.color.activeSongInFrame))
            view.invalidate()
        } else {
            view.setBackgroundColor(Color.TRANSPARENT)
        }
        return view
    }

    private fun setSong(position: Int, view: View) {
        currentSongView = view
        notifyDataSetChanged()
        controller?.seekToDefaultPosition(position)
        currentSong = controller?.currentMediaItem?.mediaMetadata?.title as? String ?: ""
    }
}