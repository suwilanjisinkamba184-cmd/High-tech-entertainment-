package com.hightechentertainment.app.media

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

object HtePlaybackController {
    @Volatile private var sharedFuture: ListenableFuture<MediaController>? = null
    @Volatile private var activeController: MediaController? = null

    fun connect(
        context: Context,
        onConnected: (MediaController) -> Unit,
        onError: (Throwable) -> Unit
    ): ListenableFuture<MediaController> {
        val future = synchronized(this) {
            sharedFuture ?: run {
                val token = SessionToken(context, ComponentName(context, HtePlaybackService::class.java))
                MediaController.Builder(context, token).buildAsync().also { sharedFuture = it }
            }
        }
        future.addListener({
            runCatching { future.get() }
                .onSuccess { controller -> activeController = controller; onConnected(controller) }
                .onFailure(onError)
        }, MoreExecutors.directExecutor())
        return future
    }

    fun pause() { activeController?.pause() }
    fun resume() { activeController?.play() }
    fun next() { activeController?.seekToNextMediaItem() }
    fun previous() { activeController?.seekToPreviousMediaItem() }

    fun release(future: ListenableFuture<MediaController>) {
        synchronized(this) {
            if (sharedFuture === future) { sharedFuture = null; activeController = null }
        }
        MediaController.releaseFuture(future)
    }
}
