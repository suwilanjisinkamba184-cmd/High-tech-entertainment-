package com.hightechentertainment.app

import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        assertTrue("Test precondition: staged file should exist", staged.exists())
        job.cancelAndJoin()

        assertFalse("Cancelled staging must delete its temporary file", staged.exists())
    }
    @Test
    fun promptCancellationAfterSuccessfulStageDeletesFile() = runBlocking {
        var staged: File? = null
        lateinit var job: Job
        job = launch(start = CoroutineStart.LAZY) {
            withTemporaryFileCleanup { registerFile ->
                withContext(Dispatchers.IO) {
                    val file = File.createTempFile("hte-cleanup-prompt-", ".tmp")
                    registerFile(file)
                    staged = file
                    // Cancel the caller after the stage block has created and registered
                    // the file, then return normally. withContext must discard the result
                    // and the outer cleanup wrapper must still delete the staged file.
                    job.cancel()
                    file
                }
            }
        }

        job.start()
        job.join()

        assertTrue("Test precondition: staged file should exist", staged != null)
        assertFalse("Prompt cancellation must delete a staged file whose result was discarded", staged!!.exists())
    }

}
