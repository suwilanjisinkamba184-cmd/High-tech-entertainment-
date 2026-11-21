package com.hightechentertainment.app

import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertFalse
import org.junit.Test

class TemporaryFileCleanupTest {
    @Test
    fun cancellationAfterFileCreationDeletesStagedFile() = runBlocking {
        val created = CompletableDeferred<File>()
        val job = launch {
            withTemporaryFileCleanup { registerFile ->
                withContext(Dispatchers.IO) {
                    val file = File.createTempFile("hte-cleanup-test-", ".tmp")
                    registerFile(file)
                    created.complete(file)
                    awaitCancellation()
                }
            }
        }

        val staged = created.await()
        assertFalse("Test precondition: staged file should exist", !staged.exists())
        job.cancelAndJoin()

        assertFalse("Cancelled staging must delete its temporary file", staged.exists())
    }
}
