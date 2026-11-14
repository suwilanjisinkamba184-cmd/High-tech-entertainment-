package com.hightechentertainment.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityCompatibilityTest {
    @Test
    fun mainActivityLaunchesOnSupportedAndroidApi() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // Debug builds intentionally add ".dev"; compare against the
                // actual variant application ID instead of the production ID.
                assertEquals(BuildConfig.APPLICATION_ID, activity.packageName)
                assertNotNull(activity.window.decorView)
            }
        }
    }
}
