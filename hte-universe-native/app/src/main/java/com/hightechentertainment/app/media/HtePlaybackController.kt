package com.hightechentertainment.app.media

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.IdentityHashMap

@OptIn(markerClass = [UnstableApi::class])
object HtePlaybackController {
    @Volatile private var sharedFuture: ListenableFuture<MediaController>? = null
    @Volatile private var activeController: MediaController? = null
    // A screen/dialog may share the same future. Keep it alive until every caller releases its lease.
    private val connectionLeases = IdentityHashMap<ListenableFuture<MediaController>, Int>()

    fun connect(
        context: Context,
        onConnected: (MediaController) -> Unit,
        onError: (Throwable) -> Unit
    ): ListenableFuture<MediaController> {
        val future = synchronized(this) {
            val connection = sharedFuture ?: run {
                val token = SessionToken(context, ComponentName(context, HtePlaybackService::class.java))
                MediaController.Builder(context, token).buildAsync().also { sharedFuture = it }
            }
            // Count every consumer, including callers reusing an in-flight or completed future.
            connectionLeases[connection] = (connectionLeases[connection] ?: 0) + 1
            connection
        }
        future.addListener({
            runCatching { future.get() }
                .onSuccess { controller ->
                    val isCurrentConnection = synchronized(this) {
                        if (sharedFuture === future && connectionLeases.containsKey(future)) {
                            activeController = controller
                            true
                        } else {
                            false
                        }
                    }
                    if (isCurrentConnection) onConnected(controller)
                }
                .onFailure { error ->
                    val stillLeased = synchronized(this) {
                        if (sharedFuture === future) {
                            sharedFuture = null
                            activeController = null
                        }
                        connectionLeases.containsKey(future)
                    }
                    // Ignore failures from a connection whose last consumer has already left.
                    if (stillLeased) onError(error)
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
        val shouldRelease = synchronized(this) {
            val leases = connectionLeases[future] ?: return
            if (leases > 1) {
                connectionLeases[future] = leases - 1
                false
            } else {
                connectionLeases.remove(future)
                if (sharedFuture === future) {
                    sharedFuture = null
                    activeController = null
                }
                true
            }
        }
        if (shouldRelease) MediaController.releaseFuture(future)
    }
}
