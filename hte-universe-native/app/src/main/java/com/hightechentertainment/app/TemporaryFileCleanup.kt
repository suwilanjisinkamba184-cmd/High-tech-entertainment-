package com.hightechentertainment.app

import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Keeps ownership of a staged file outside a cancellable context so cleanup
 * still runs if withContext discards its result during prompt cancellation.
 */
internal suspend fun <T> withTemporaryFileCleanup(
    stage: suspend (registerFile: (File) -> Unit) -> T
): T {
    val stagedFile = AtomicReference<File?>()
    return try {
        stage { file -> stagedFile.set(file) }
    } catch (error: Throwable) {
        stagedFile.get()?.delete()
        throw error
    }
}
