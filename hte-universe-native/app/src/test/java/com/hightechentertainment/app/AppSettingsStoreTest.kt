package com.hightechentertainment.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsStoreTest {
    @Test
    fun wifiOnlyAlwaysBlocksMeteredMediaDownloads() {
        assertFalse(allowsMeteredMediaDownloads("WIFI_ONLY", dataSaverEnabled = false))
        assertFalse(allowsMeteredMediaDownloads("WIFI_ONLY", dataSaverEnabled = true))
    }

    @Test
    fun dataSaverBlocksMeteredMediaDownloadsEvenWhenMobileDataIsAllowed() {
        assertFalse(allowsMeteredMediaDownloads("WIFI_AND_MOBILE", dataSaverEnabled = true))
    }

    @Test
    fun mobileDownloadsRequireBothMobilePolicyAndDataSaverOff() {
        assertTrue(allowsMeteredMediaDownloads("WIFI_AND_MOBILE", dataSaverEnabled = false))
    }
}
