package com.hightechentertainment.app

import android.content.Context

/** Local, non-sensitive preferences. Secrets and authentication tokens never belong here. */
class AppSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("hte_universe_preferences", Context.MODE_PRIVATE)

    var notificationsEnabled: Boolean
        get() = preferences.getBoolean("notifications_enabled", true)
        set(value) { preferences.edit().putBoolean("notifications_enabled", value).apply() }

    var autoplayNext: Boolean
        get() = preferences.getBoolean("autoplay_next", true)
        set(value) { preferences.edit().putBoolean("autoplay_next", value).apply() }

    var dataSaver: Boolean
        get() = preferences.getBoolean("data_saver", false)
        set(value) { preferences.edit().putBoolean("data_saver", value).apply() }

    var networkPolicy: String
        get() = preferences.getString("network_policy", "ASK") ?: "ASK"
        set(value) { preferences.edit().putString("network_policy", value).apply() }

    var updateNetworkPolicy: String
        get() = preferences.getString("update_network_policy", "WIFI_ONLY") ?: "WIFI_ONLY"
        set(value) { preferences.edit().putString("update_network_policy", value).apply() }

    var streamingQuality: String
        get() = preferences.getString("streaming_quality", "AUTO") ?: "AUTO"
        set(value) { preferences.edit().putString("streaming_quality", value).apply() }

    var downloadQuality: String
        get() = preferences.getString("download_quality", "HIGH") ?: "HIGH"
        set(value) { preferences.edit().putString("download_quality", value).apply() }

    var videoQuality: String
        get() = preferences.getString("video_quality", "AUTO") ?: "AUTO"
        set(value) { preferences.edit().putString("video_quality", value).apply() }

    var backgroundPlayback: Boolean
        get() = preferences.getBoolean("background_playback", true)
        set(value) { preferences.edit().putBoolean("background_playback", value).apply() }

    var startupSoundEnabled: Boolean
        get() = preferences.getBoolean("startup_sound_enabled", true)
        set(value) { preferences.edit().putBoolean("startup_sound_enabled", value).apply() }

    var reduceMotion: Boolean
        get() = preferences.getBoolean("reduce_motion", false)
        set(value) { preferences.edit().putBoolean("reduce_motion", value).apply() }

    var personalisedRecommendations: Boolean
        get() = preferences.getBoolean("personalised_recommendations", true)
        set(value) { preferences.edit().putBoolean("personalised_recommendations", value).apply() }

    var equalizerEnabled: Boolean
        get() = preferences.getBoolean("equalizer_enabled", false)
        set(value) { preferences.edit().putBoolean("equalizer_enabled", value).apply() }

    var equalizerPreset: String
        get() = preferences.getString("equalizer_preset", "FLAT") ?: "FLAT"
        set(value) { preferences.edit().putString("equalizer_preset", value).apply() }

    fun equalizerBands(): List<Int> {
        val values = preferences.getString("equalizer_bands", null)?.split(",")?.mapNotNull { it.toIntOrNull() }
        return values?.takeIf { it.size == 5 } ?: listOf(0, 0, 0, 0, 0)
    }

    fun setEqualizerBands(values: List<Int>) {
        require(values.size == 5)
        preferences.edit().putString("equalizer_bands", values.joinToString(",")).apply()
    }

    fun clearPreferences() {
        preferences.edit().clear().apply()
    }
}
