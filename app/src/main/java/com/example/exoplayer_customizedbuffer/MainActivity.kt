package com.example.exoplayer_customizedbuffer

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.exoplayer_customizedbuffer.Constants.BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_KEY
import com.example.exoplayer_customizedbuffer.Constants.BUFFER_FOR_PLAYBACK_MS
import com.example.exoplayer_customizedbuffer.Constants.CONNECT_TIMEOUT_KEY
import com.example.exoplayer_customizedbuffer.Constants.DEFAULT_BUFFER_SEGMENT_SIZE_KEY
import com.example.exoplayer_customizedbuffer.Constants.MAX_BUFFER_MS_KEY
import com.example.exoplayer_customizedbuffer.Constants.MIN_BUFFER_MS_KEY
import com.example.exoplayer_customizedbuffer.Constants.READ_TIMEOUT_KEY
import com.example.exoplayer_customizedbuffer.Constants.RTL_STREAM_URL
import com.example.exoplayer_customizedbuffer.Constants.STREAM_URL_KEY
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setInitialValues()

        start_streaming_button.setOnClickListener {
            val intent = Intent(this, PlayerActivity::class.java)
            intent.putExtra(STREAM_URL_KEY, stream_url_edittext.text.toString())
            intent.putExtra(CONNECT_TIMEOUT_KEY, connectTimeoutMillis_editTextNumber.text.toString().toInt())
            intent.putExtra(READ_TIMEOUT_KEY, readTimeoutMillis_editTextNumber.text.toString().toInt())

            intent.putExtra(DEFAULT_BUFFER_SEGMENT_SIZE_KEY, default_buffer_segment_size_editTextNumber.text.toString().toInt())
            intent.putExtra(MIN_BUFFER_MS_KEY, minBufferMs_editTextNumber.text.toString().toInt())
            intent.putExtra(MAX_BUFFER_MS_KEY, maxBufferMs_editTextNumber.text.toString().toInt())
            intent.putExtra(BUFFER_FOR_PLAYBACK_MS, bufferForPlaybackMs_editTextNumber.text.toString().toInt())
            intent.putExtra(BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_KEY, bufferForPlaybackAfterRebufferMs_editTextNumber.text.toString().toInt())

            startActivity(intent)
        }
    }

    fun setInitialValues(){
        stream_url_edittext.setText(RTL_STREAM_URL)
        stream_url_edittext.setSelection(0)


    }
}