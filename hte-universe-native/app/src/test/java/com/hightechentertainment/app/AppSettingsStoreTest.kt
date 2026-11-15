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

    @Test
    fun wifiOnlyUpdateDownloadsAreBlockedOnMeteredNetworks() {
        assertFalse(allowsUpdateDownload("WIFI_ONLY", isMeteredNetwork = true))
        assertTrue(allowsUpdateDownload("WIFI_ONLY", isMeteredNetwork = false))
    }

    @Test
    fun mobileUpdateDownloadsRequireExplicitUserChoice() {
        assertTrue(allowsUpdateDownload("WIFI_AND_MOBILE", isMeteredNetwork = true))
    }
}
