package com.hightechentertainment.app

import android.content.Context
import androidx.core.content.edit

/** True only when the current download policy and Data Saver both permit metered media downloads. */
internal fun allowsMeteredMediaDownloads(networkPolicy: String, dataSaverEnabled: Boolean): Boolean =
    networkPolicy == "WIFI_AND_MOBILE" && !dataSaverEnabled

/** Local, non-sensitive preferences. Secrets and authentication tokens never belong here. */
class AppSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("hte_universe_preferences", Context.MODE_PRIVATE)

    var notificationsEnabled: Boolean
        get() = preferences.getBoolean("notifications_enabled", false)
        set(value) { preferences.edit { putBoolean("notifications_enabled", value) } }

    var autoplayNext: Boolean
        get() = preferences.getBoolean("autoplay_next", true)
        set(value) { preferences.edit { putBoolean("autoplay_next", value) } }

    var dataSaver: Boolean
        get() = preferences.getBoolean("data_saver", false)
        set(value) { preferences.edit { putBoolean("data_saver", value) } }

    var networkPolicy: String
        get() = preferences.getString("network_policy", "WIFI_AND_MOBILE") ?: "WIFI_AND_MOBILE"
        set(value) { preferences.edit { putString("network_policy", value) } }

    var updateNetworkPolicy: String
        get() = preferences.getString("update_network_policy", "WIFI_ONLY") ?: "WIFI_ONLY"
        set(value) { preferences.edit { putString("update_network_policy", value) } }

    var streamingQuality: String
        get() = preferences.getString("streaming_quality", "AUTO") ?: "AUTO"
        set(value) { preferences.edit { putString("streaming_quality", value) } }

    var downloadQuality: String
        get() = preferences.getString("download_quality", "HIGH") ?: "HIGH"
        set(value) { preferences.edit { putString("download_quality", value) } }

    var videoQuality: String
        get() = preferences.getString("video_quality", "AUTO") ?: "AUTO"
        set(value) { preferences.edit { putString("video_quality", value) } }

    var voiceAssistantVoiceId: String
        get() = preferences.getString("voice_assistant_voice_id", VoiceAssistantVoices.DEFAULT_ID) ?: VoiceAssistantVoices.DEFAULT_ID
        set(value) { preferences.edit { putString("voice_assistant_voice_id", value) } }

    var backgroundPlayback: Boolean
        get() = preferences.getBoolean("background_playback", true)
        set(value) { preferences.edit { putBoolean("background_playback", value) } }

    var startupSoundEnabled: Boolean
        get() = preferences.getBoolean("startup_sound_enabled", true)
        set(value) { preferences.edit { putBoolean("startup_sound_enabled", value) } }

    var reduceMotion: Boolean
        get() = preferences.getBoolean("reduce_motion", false)
        set(value) { preferences.edit { putBoolean("reduce_motion", value) } }

    var personalisedRecommendations: Boolean
        get() = preferences.getBoolean("personalised_recommendations", true)
        set(value) { preferences.edit { putBoolean("personalised_recommendations", value) } }

    var equalizerEnabled: Boolean
        get() = preferences.getBoolean("equalizer_enabled", false)
        set(value) { preferences.edit { putBoolean("equalizer_enabled", value) } }

    var equalizerPreset: String
        get() = preferences.getString("equalizer_preset", "FLAT") ?: "FLAT"
        set(value) { preferences.edit { putString("equalizer_preset", value) } }

    fun equalizerBands(): List<Int> {
        val values = preferences.getString("equalizer_bands", null)?.split(",")?.mapNotNull { it.toIntOrNull() }
        return values?.takeIf { it.size == 5 } ?: listOf(0, 0, 0, 0, 0)
    }

    fun setEqualizerBands(values: List<Int>) {
        require(values.size == 5)
        preferences.edit { putString("equalizer_bands", values.joinToString(",")) }
    }

    fun clearPreferences() {
        preferences.edit { clear() }
    }
}
