package com.mockpilot.data

import android.content.Context
import com.mockpilot.model.MockConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tiny synchronous key/value store (SharedPreferences) for session-resume state. Kept separate from
 * Room because [BootReceiver] needs a fast, blocking read during BOOT_COMPLETED.
 */
@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("mockpilot_session", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** True while a spoofing session is active — used to auto-resume after a reboot. */
    var autoResume: Boolean
        get() = prefs.getBoolean(KEY_RESUME, false)
        set(value) = prefs.edit().putBoolean(KEY_RESUME, value).apply()

    /** True when the current session was started by the scheduler (so the scheduler may stop it). */
    var startedByScheduler: Boolean
        get() = prefs.getBoolean(KEY_BY_SCHEDULER, false)
        set(value) = prefs.edit().putBoolean(KEY_BY_SCHEDULER, value).apply()

    fun saveConfig(config: MockConfig) {
        prefs.edit()
            .putString(KEY_CONFIG, json.encodeToString(MockConfig.serializer(), config))
            .putBoolean(KEY_RESUME, true)
            .apply()
    }

    fun loadConfig(): MockConfig? {
        val raw = prefs.getString(KEY_CONFIG, null) ?: return null
        return runCatching { json.decodeFromString(MockConfig.serializer(), raw) }.getOrNull()
    }

    fun clear() {
        prefs.edit().putBoolean(KEY_RESUME, false).apply()
    }

    companion object {
        private const val KEY_CONFIG = "last_config"
        private const val KEY_RESUME = "auto_resume"
        private const val KEY_BY_SCHEDULER = "by_scheduler"
    }
}
