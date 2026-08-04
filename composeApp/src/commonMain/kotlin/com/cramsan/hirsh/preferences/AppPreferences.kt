package com.cramsan.hirsh.preferences

import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set

/**
 * Thin wrapper around the platform [Settings] store (backed by SharedPreferences on
 * Android, NSUserDefaults on iOS, java.util.prefs on desktop, localStorage on web).
 * Holds only what needs to survive a process restart on a device shared by one user
 * at a time -- the session, not app state.
 */
class AppPreferences(private val settings: Settings) {

    var sessionUsername: String?
        get() = settings[KEY_SESSION_USERNAME]
        set(value) {
            if (value == null) settings.remove(KEY_SESSION_USERNAME) else settings[KEY_SESSION_USERNAME] = value
        }

    fun clearSession() {
        settings.remove(KEY_SESSION_USERNAME)
    }

    private companion object {
        const val KEY_SESSION_USERNAME = "session_username"
    }
}
