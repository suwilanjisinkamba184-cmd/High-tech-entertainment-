package com.hightechentertainment.app

import android.content.Context

/** Local, non-sensitive application preferences. */
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
}
