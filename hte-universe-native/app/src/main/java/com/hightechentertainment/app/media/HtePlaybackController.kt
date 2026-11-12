package com.hightechentertainment.app.media

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

object HtePlaybackController {
    fun connect(
        context: Context,
        onConnected: (MediaController) -> Unit,
        onError: (Throwable) -> Unit
    ): ListenableFuture<MediaController> {
        val token = SessionToken(context, ComponentName(context, HtePlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            runCatching { future.get() }
                .onSuccess(onConnected)
                .onFailure(onError)
        }, MoreExecutors.directExecutor())
        return future
    }

    fun release(future: ListenableFuture<MediaController>) {
        MediaController.releaseFuture(future)
    }
}
