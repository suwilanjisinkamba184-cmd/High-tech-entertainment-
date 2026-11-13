package com.hightechentertainment.app.media

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

@UnstableApi
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
                .onFailure { error ->
                    synchronized(this) {
                        if (sharedFuture === future) {
                            sharedFuture = null
                            activeController = null
                        }
                    }
                    onError(error)
                }
        }, MoreExecutors.directExecutor())
        return future
    }

    fun isConnected(): Boolean = activeController != null

    /** Executes only actions that the active Media3 player can actually perform. */
    fun performAction(action: String): Boolean {
        val controller = activeController ?: return false
        return when (action) {
            "pause" -> if (controller.currentMediaItem == null) false else { controller.pause(); true }
            "resume" -> if (controller.currentMediaItem == null) false else { controller.play(); true }
            "next" -> if (controller.hasNextMediaItem()) { controller.seekToNextMediaItem(); true } else false
            "previous" -> if (controller.currentMediaItem == null) false else { controller.seekToPreviousMediaItem(); true }
            "stop" -> if (controller.currentMediaItem == null) false else { controller.stop(); true }
            else -> false
        }
    }
    fun pause() { activeController?.pause() }
    fun resume() { activeController?.play() }
    fun next() { activeController?.seekToNextMediaItem() }
    fun previous() { activeController?.seekToPreviousMediaItem() }
    fun stop() { activeController?.stop() }

    fun release(future: ListenableFuture<MediaController>) {
        synchronized(this) {
            if (sharedFuture === future) { sharedFuture = null; activeController = null }
        }
        MediaController.releaseFuture(future)
    }
}
