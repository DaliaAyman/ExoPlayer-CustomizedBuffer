/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.exoplayer_customizedbuffer

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Pair
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.exoplayer_customizedbuffer.Constants.BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_KEY
import com.example.exoplayer_customizedbuffer.Constants.BUFFER_FOR_PLAYBACK_MS
import com.example.exoplayer_customizedbuffer.Constants.CONNECT_TIMEOUT_KEY
import com.example.exoplayer_customizedbuffer.Constants.DEFAULT_BUFFER_SEGMENT_SIZE_KEY
import com.example.exoplayer_customizedbuffer.Constants.MAX_BUFFER_MS_KEY
import com.example.exoplayer_customizedbuffer.Constants.MIN_BUFFER_MS_KEY
import com.example.exoplayer_customizedbuffer.Constants.READ_TIMEOUT_KEY
import com.example.exoplayer_customizedbuffer.Constants.STREAM_URL_KEY
import com.example.exoplayer_customizedbuffer.exoplayer.CustomLoadControl
import com.example.exoplayer_customizedbuffer.exoplayer.CustomLoadErrorHandlingPolicy
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSourceFactory
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer.DecoderInitializationException
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException
import com.google.android.exoplayer2.source.BehindLiveWindowException
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.source.ads.AdsLoader
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.ParametersBuilder
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.ui.DebugTextViewHelper
import com.google.android.exoplayer2.ui.StyledPlayerControlView
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultAllocator
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.google.android.exoplayer2.util.ErrorMessageProvider
import com.google.android.exoplayer2.util.EventLogger
import com.google.android.exoplayer2.util.Util
import kotlinx.android.synthetic.main.activity_player.*
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit

/** An activity that plays media using [SimpleExoPlayer].  */
class PlayerActivity : AppCompatActivity(), PlaybackPreparer, StyledPlayerControlView.VisibilityListener {

    companion object {
        // Saved instance state keys.
        private const val KEY_TRACK_SELECTOR_PARAMETERS = "track_selector_parameters"
        private const val KEY_WINDOW = "window"
        private const val KEY_POSITION = "position"
        private const val KEY_AUTO_PLAY = "auto_play"
        private var DEFAULT_COOKIE_MANAGER: CookieManager? = null
        private fun isBehindLiveWindow(e: ExoPlaybackException): Boolean {
            if (e.type != ExoPlaybackException.TYPE_SOURCE) {
                return false
            }
            var cause: Throwable? = e.sourceException
            while (cause != null) {
                if (cause is BehindLiveWindowException) {
                    return true
                }
                cause = cause.cause
            }
            return false
        }

        init {
            DEFAULT_COOKIE_MANAGER = CookieManager()
            DEFAULT_COOKIE_MANAGER!!.setCookiePolicy(
                CookiePolicy.ACCEPT_ORIGINAL_SERVER
            )
        }
    }

    protected var player: SimpleExoPlayer? = null
    private var isShowingTrackSelectionDialog = false

    private var dataSourceFactory: DataSource.Factory? = null
    private lateinit var streamUrl: String
    private var trackSelector: DefaultTrackSelector? = null
    private var trackSelectorParameters: DefaultTrackSelector.Parameters? = null
    private var customLoadControl: CustomLoadControl? = null
    private var debugViewHelper: DebugTextViewHelper? = null

    private var lastSeenTrackGroupArray: TrackGroupArray? = null
    private var startAutoPlay = false
    private var startWindow = 0
    private var startPosition: Long = 0

    // Fields used only for ad playback.
    private var adsLoader: AdsLoader? = null
    private var loadedAdTagUri: Uri? = null
    private var connectTimeOut: Int = 0
    private var readTimeOut: Int = 0
    private var bufferSegmentSize: Int = 0
    private var minBufferMs: Int = 0
    private var maxBufferMs: Int = 0
    private var bufferForPlaybackMs: Int = 0
    private var bufferForPlaybackAfterRebufferMs: Int = 0

    // Activity lifecycle
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView()

        val userAgent = Util.getUserAgent(this, getString(R.string.app_name))

        streamUrl = intent?.extras?.get(STREAM_URL_KEY).toString()

        connectTimeOut = intent?.extras?.getInt(CONNECT_TIMEOUT_KEY)!!
        readTimeOut = intent?.extras?.getInt(READ_TIMEOUT_KEY)!!

        bufferSegmentSize = intent?.extras?.getInt(DEFAULT_BUFFER_SEGMENT_SIZE_KEY)!!

        minBufferMs = intent?.extras?.getInt(MIN_BUFFER_MS_KEY)!!
        maxBufferMs = intent?.extras?.getInt(MAX_BUFFER_MS_KEY)!!
        bufferForPlaybackMs = intent?.extras?.getInt(BUFFER_FOR_PLAYBACK_MS)!!
        bufferForPlaybackAfterRebufferMs = intent?.extras?.getInt(
            BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_KEY)!!

        config_log_values.text =
            STREAM_URL_KEY + ": " + streamUrl + ", \n" +
            CONNECT_TIMEOUT_KEY + ": " + connectTimeOut.toString() + ", " +
                    READ_TIMEOUT_KEY + ": " + readTimeOut.toString() + ", \n" +
                    DEFAULT_BUFFER_SEGMENT_SIZE_KEY + ": " + bufferSegmentSize.toString() + ", " +
                    MIN_BUFFER_MS_KEY + ": " + minBufferMs.toString() + ", " +
                    MAX_BUFFER_MS_KEY + ": " + maxBufferMs.toString() + ", \n" +
                    BUFFER_FOR_PLAYBACK_MS + ": " + bufferForPlaybackMs.toString() + ", " +
                    BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_KEY + ": " + bufferForPlaybackAfterRebufferMs.toString()

        dataSourceFactory = DefaultHttpDataSourceFactory(
            userAgent,
//                DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
//                DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS,
            connectTimeOut, readTimeOut,
            true)

        dataSourceFactory = OkHttpDataSourceFactory(
            OkHttpClient.Builder()
                    .readTimeout(0, TimeUnit.MILLISECONDS)
                    .connectTimeout(0, TimeUnit.MILLISECONDS)
                .protocols(Collections.singletonList(Protocol.HTTP_1_1))
//                .retryOnConnectionFailure(true)
//                .connectionPool(ConnectionPool(0, 5, TimeUnit.MINUTES))
                .connectionPool(ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
                .build(),
            userAgent
        )

        if (CookieHandler.getDefault() !== DEFAULT_COOKIE_MANAGER) {
            CookieHandler.setDefault(DEFAULT_COOKIE_MANAGER)
        }

        player_view.setControllerVisibilityListener(this)
        player_view.setErrorMessageProvider(PlayerErrorMessageProvider())
        player_view.requestFocus()
        if (savedInstanceState != null) {
            trackSelectorParameters =
                savedInstanceState.getParcelable(KEY_TRACK_SELECTOR_PARAMETERS)
            startAutoPlay =
                savedInstanceState.getBoolean(KEY_AUTO_PLAY)
            startWindow =
                savedInstanceState.getInt(KEY_WINDOW)
            startPosition =
                savedInstanceState.getLong(KEY_POSITION)
        } else {
            val builder = ParametersBuilder( /* context= */this)
            trackSelectorParameters = builder.build()
            clearStartPosition()
        }
    }

    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        releasePlayer()
        releaseAdsLoader()
        clearStartPosition()
        setIntent(intent)
    }

    public override fun onStart() {
        super.onStart()
        if (Util.SDK_INT > 23) {
            initializePlayer()
            if (player_view != null) {
                player_view.onResume()
            }
        }
    }

    public override fun onResume() {
        super.onResume()
        if (Util.SDK_INT <= 23 || player == null) {
            initializePlayer()
            if (player_view != null) {
                player_view.onResume()
            }
        }
    }

    public override fun onPause() {
        super.onPause()
        if (Util.SDK_INT <= 23) {
            if (player_view != null) {
                player_view.onPause()
            }
            releasePlayer()
        }
    }

    public override fun onStop() {
        super.onStop()
        if (Util.SDK_INT > 23) {
            if (player_view != null) {
                player_view.onPause()
            }
            releasePlayer()
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        releaseAdsLoader()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.size == 0) {
            // Empty results are triggered if a permission is requested while another request was already
            // pending and can be safely ignored in this case.
            return
        }
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initializePlayer()
        } else {
            showToast(R.string.storage_permission_denied)
            finish()
        }
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        updateTrackSelectorParameters()
        updateStartPosition()
        outState.putParcelable(
            KEY_TRACK_SELECTOR_PARAMETERS,
            trackSelectorParameters
        )
        outState.putBoolean(
            KEY_AUTO_PLAY,
            startAutoPlay
        )
        outState.putInt(
            KEY_WINDOW,
            startWindow
        )
        outState.putLong(
            KEY_POSITION,
            startPosition
        )
    }

    // Activity input
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // See whether the player view wants to handle media or DPAD keys events.
        return player_view.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
    }

    // PlaybackPreparer implementation
    override fun preparePlayback() {
        player!!.prepare()
    }

    // PlayerControlView.VisibilityListener implementation
    override fun onVisibilityChange(visibility: Int) {
        controls_root?.visibility = visibility
    }

    // Internal methods
    protected fun setContentView() {
        setContentView(R.layout.activity_player)
    }

    /** @return Whether initialization was successful.
     */
    protected fun initializePlayer(): Boolean {
        if (player == null) {
//            mediaItems = createMediaItems(intent)
//            if (mediaItems!!.isEmpty()) {
//                return false
//            }
            val renderersFactory: RenderersFactory = DefaultRenderersFactory(this)

            val mediaSourceFactory = prepareAudioSourceForUrl(streamUrl)

//                DefaultMediaSourceFactory(dataSourceFactory!!)
//                    .setAdsLoaderProvider { adTagUri: Uri ->
//                        getAdsLoader(
//                            adTagUri
//                        )
//                    }
//                    .setAdViewProvider(player_view)

            trackSelector = DefaultTrackSelector( /* context= */this)
            trackSelector!!.parameters = trackSelectorParameters!!
            lastSeenTrackGroupArray = null

            customLoadControl = CustomLoadControl.Builder()
                      .setAllocator(DefaultAllocator(true,
                          bufferSegmentSize))
//                          C.DEFAULT_BUFFER_SEGMENT_SIZE))
                      .setBufferDurationsMs(
                          minBufferMs,
                          maxBufferMs,
                          bufferForPlaybackMs,
                          bufferForPlaybackAfterRebufferMs
                      )
//                      .setTargetBufferBytes(DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES)
                      .setPrioritizeTimeOverSizeThresholds(DefaultLoadControl.DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS)
                      .build()

            player = SimpleExoPlayer.Builder( /* context= */this, renderersFactory)
                .setTrackSelector(trackSelector!!)
                .setLoadControl(customLoadControl!!)
                .build()
            player!!.setMediaSource(mediaSourceFactory)
            player!!.addListener(PlayerEventListener())
            player!!.addAnalyticsListener(EventLogger(trackSelector))
            player!!.setAudioAttributes(
                AudioAttributes.DEFAULT,  /* handleAudioFocus= */
                true
            )
            player!!.playWhenReady = startAutoPlay
            player_view.player = player
            player_view.setPlaybackPreparer(this)
            debugViewHelper = DebugTextViewHelper(player!!, debug_text_view!!)
            debugViewHelper!!.start()
        }
        val haveStartPosition =
            startWindow != C.INDEX_UNSET
        if (haveStartPosition) {
            player!!.seekTo(startWindow, startPosition)
        }
//        player!!.setMediaItems(mediaItems!!,  /* resetPosition= */!haveStartPosition)
        player!!.prepare()
//        updateButtonVisibility()
        return true
    }

    private fun prepareAudioSourceForUrl(url: String): MediaSource {
        val mediaItem: MediaItem = MediaItem.fromUri(url)

        return ProgressiveMediaSource.Factory(dataSourceFactory!!)
//            .setLoadErrorHandlingPolicy(CustomLoadErrorHandlingPolicy())
            .createMediaSource(mediaItem)
    }

    protected fun releasePlayer() {
        if (player != null) {
            updateTrackSelectorParameters()
            updateStartPosition()
            debugViewHelper!!.stop()
            debugViewHelper = null
            player!!.release()
            player = null
//            mediaItems = emptyList()
            trackSelector = null
        }
        if (adsLoader != null) {
            adsLoader!!.setPlayer(null)
        }
    }

    private fun releaseAdsLoader() {
        if (adsLoader != null) {
            adsLoader!!.release()
            adsLoader = null
            loadedAdTagUri = null
            player_view.overlayFrameLayout!!.removeAllViews()
        }
    }

    private fun updateTrackSelectorParameters() {
        if (trackSelector != null) {
            trackSelectorParameters = trackSelector!!.parameters
        }
    }

    private fun updateStartPosition() {
        if (player != null) {
            startAutoPlay = player!!.playWhenReady
            startWindow = player!!.currentWindowIndex
            startPosition = Math.max(0, player!!.contentPosition)
        }
    }

    protected fun clearStartPosition() {
        startAutoPlay = true
        startWindow = C.INDEX_UNSET
        startPosition = C.TIME_UNSET
    }

    private fun showControls() {
        controls_root!!.visibility = View.VISIBLE
    }

    private fun showToast(messageId: Int) {
        showToast(getString(messageId))
    }

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
    }

    private fun printPlaybackState(value: Int): String{
        return when(value){
            1 -> "STATE_IDLE"
            2 -> "STATE_BUFFERING"
            3 -> "STATE_READY"
            4 -> "STATE_ENDED"
            else -> ""
        }
    }
    private inner class PlayerEventListener : Player.EventListener {
        override fun onPlaybackStateChanged(@Player.State playbackState: Int) {
            Log.d("EventLogger", "onPlaybackStateChanged type ${printPlaybackState(playbackState)}");
            when(playbackState){
                Player.STATE_BUFFERING -> {
                    debug_text_view.append("/n STATE_BUFFERING /n")
                }
                Player.STATE_ENDED -> {
                    debug_text_view.append("/n STATE_ENDED /n")
                    showControls()
                }
                Player.STATE_IDLE -> {
                    debug_text_view.append("/n STATE_IDLE /n")
                }
                Player.STATE_READY -> {
                    debug_text_view.append("/n STATE_READY /n")
                }
            }
//            updateButtonVisibility()
        }

        override fun onPlayerError(e: ExoPlaybackException) {
            Log.d("EventLogger", "onPlayerError type ${e.type} ");

            when(e.type){
                ExoPlaybackException.TYPE_OUT_OF_MEMORY -> {
                    debug_text_view.append("/n onPlayerError TYPE_OUT_OF_MEMORY /n")
                }
                ExoPlaybackException.TYPE_REMOTE -> {
                    debug_text_view.append("/n onPlayerError TYPE_REMOTE /n")
                }
                ExoPlaybackException.TYPE_RENDERER -> {
                    debug_text_view.append("/n onPlayerError TYPE_RENDERER /n")
                }
                ExoPlaybackException.TYPE_SOURCE -> {
                    debug_text_view.append("/n onPlayerError TYPE_SOURCE /n")
                }
                ExoPlaybackException.TYPE_TIMEOUT -> {
                    debug_text_view.append("/n onPlayerError TYPE_TIMEOUT /n")
                }
                ExoPlaybackException.TYPE_UNEXPECTED -> {
                    debug_text_view.append("/n onPlayerError TYPE_UNEXPECTED /n")
                }
            }
            if (isBehindLiveWindow(e)) {
                clearStartPosition()
                initializePlayer()
            } else {
//                updateButtonVisibility()
                showControls()
            }
        }

        override fun onTracksChanged(
            trackGroups: TrackGroupArray, trackSelections: TrackSelectionArray
        ) {
//            updateButtonVisibility()
            if (trackGroups !== lastSeenTrackGroupArray) {
                val mappedTrackInfo = trackSelector!!.currentMappedTrackInfo
                if (mappedTrackInfo != null) {
                    if (mappedTrackInfo.getTypeSupport(C.TRACK_TYPE_VIDEO)
                        == MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS
                    ) {
                        showToast(R.string.error_unsupported_video)
                    }
                    if (mappedTrackInfo.getTypeSupport(C.TRACK_TYPE_AUDIO)
                        == MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS
                    ) {
                        showToast(R.string.error_unsupported_audio)
                    }
                }
                lastSeenTrackGroupArray = trackGroups
            }
        }

        fun printPositionDiscontinuityReason(reason: Int): String{
            return when(reason){
                0 -> "DISCONTINUITY_REASON_PERIOD_TRANSITION"
                1 -> "DISCONTINUITY_REASON_SEEK"
                2 -> "DISCONTINUITY_REASON_SEEK_ADJUSTMENT"
                3 -> "DISCONTINUITY_REASON_AD_INSERTION"
                4 -> "DISCONTINUITY_REASON_INTERNAL"
                else -> ""
            }
        }
        override fun onPositionDiscontinuity(reason: Int) {
            Log.d("EventLogger", "onPositionDiscontinuity reason ${printPositionDiscontinuityReason(reason)} ");

            when(reason){
                Player.DISCONTINUITY_REASON_AD_INSERTION -> {
                    debug_text_view.append("/n onPositionDiscontinuity DISCONTINUITY_REASON_AD_INSERTION /n")
                }
                Player.DISCONTINUITY_REASON_INTERNAL -> {
                    debug_text_view.append("/n onPositionDiscontinuity DISCONTINUITY_REASON_INTERNAL /n")
                }
                Player.DISCONTINUITY_REASON_PERIOD_TRANSITION -> {
                    debug_text_view.append("/n onPositionDiscontinuity DISCONTINUITY_REASON_PERIOD_TRANSITION /n")
                }
                Player.DISCONTINUITY_REASON_SEEK -> {
                    debug_text_view.append("/n onPositionDiscontinuity DISCONTINUITY_REASON_SEEK /n")
                }
                Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT -> {
                    debug_text_view.append("/n onPositionDiscontinuity DISCONTINUITY_REASON_SEEK_ADJUSTMENT /n")
                }
            }
        }
    }

    private inner class PlayerErrorMessageProvider :
        ErrorMessageProvider<ExoPlaybackException> {
        override fun getErrorMessage(e: ExoPlaybackException): Pair<Int, String> {
            var errorString = getString(R.string.error_generic)
            if (e.type == ExoPlaybackException.TYPE_RENDERER) {
                val cause = e.rendererException
                if (cause is DecoderInitializationException) {
                    // Special case for decoder initialization failures.
                    val decoderInitializationException =
                        cause
                    errorString = if (decoderInitializationException.codecInfo == null) {
                        if (decoderInitializationException.cause is DecoderQueryException) {
                            getString(R.string.error_querying_decoders)
                        } else if (decoderInitializationException.secureDecoderRequired) {
                            getString(
                                R.string.error_no_secure_decoder,
                                decoderInitializationException.mimeType
                            )
                        } else {
                            getString(
                                R.string.error_no_decoder,
                                decoderInitializationException.mimeType
                            )
                        }
                    } else {
                        getString(
                            R.string.error_instantiating_decoder,
                            decoderInitializationException.codecInfo!!.name
                        )
                    }
                }
            }
            return Pair.create(0, errorString)
        }
    }
}