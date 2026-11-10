package com.hightechentertainment.app.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import com.hightechentertainment.app.R

/** One-shot brand sound, isolated from the Media3 music and advert queue. */
class StartupAudio(context: Context) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var player: MediaPlayer? = null
    private var request: AudioFocusRequest? = null
    private var released = false

    fun play() {
        if (released || player != null) return
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) finish()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs).setOnAudioFocusChangeListener(focusListener).build()
            request = req
            if (audio.requestAudioFocus(req) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.w(TAG, "Startup audio skipped: audio focus unavailable")
                return
            }
        } else {
            @Suppress("DEPRECATION")
            val granted = audio.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            if (granted != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        }
        val media = runCatching { MediaPlayer.create(app, R.raw.hte_startup) }.getOrNull()
        if (media == null) {
            Log.e(TAG, "Bundled startup MP3 failed to decode")
            abandonFocus()
            return
        }
        player = media
        media.setAudioAttributes(attrs)
        media.setVolume(0.78f, 0.78f)
        media.setOnCompletionListener { Log.i(TAG, "Startup audio completed"); finish() }
        media.setOnErrorListener { _, what, extra -> Log.e(TAG, "Startup audio error what=$what extra=$extra"); finish(); true }
        runCatching { media.start() }.onSuccess { Log.i(TAG, "Startup audio started") }
            .onFailure { Log.e(TAG, "Startup audio could not start", it); finish() }
    }
    fun release() { released = true; finish() }
    private fun finish() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        abandonFocus()
    }
    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) request?.let { runCatching { audio.abandonAudioFocusRequest(it) } }
        request = null
    }
    companion object { private const val TAG = "HTE.StartupAudio" }
}
