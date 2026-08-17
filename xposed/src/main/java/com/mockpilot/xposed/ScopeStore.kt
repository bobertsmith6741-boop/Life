package com.mockpilot.xposed

import android.content.Context
import java.io.File

/** Shared constants + preference helpers used by both the module app and the injected hooks. */
object ScopeStore {
    const val MODULE_PKG = "com.mockpilot.xposed"

    const val SCOPE_PREFS = "scope"
    const val KEY_PACKAGES = "packages"

    const val ACTION_DETECTION = "com.mockpilot.xposed.DETECTION"
    const val DETECTION_PREFS = "detections"
    const val KEY_LOG = "log"

    /** Standard providers the hooks allow through; anything else is treated as a test provider. */
    val STANDARD_PROVIDERS = setOf("gps", "network", "passive", "fused")

    /**
     * Write the scope set from the module app. LSPosed lets a module use MODE_WORLD_READABLE; if the
     * platform refuses, fall back to MODE_PRIVATE and make the file readable manually so the hooks'
     * XSharedPreferences can still read it.
     */
    fun saveScope(context: Context, packages: Set<String>) {
        val prefs = try {
            @Suppress("DEPRECATION", "WorldReadableFiles")
            context.getSharedPreferences(SCOPE_PREFS, Context.MODE_WORLD_READABLE)
        } catch (e: SecurityException) {
            context.getSharedPreferences(SCOPE_PREFS, Context.MODE_PRIVATE)
        }
        prefs.edit().putStringSet(KEY_PACKAGES, packages).commit()
        makeReadable(context, SCOPE_PREFS)
    }

    fun loadScope(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(SCOPE_PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_PACKAGES, emptySet()) ?: emptySet()
    }

    // ---- Detection log (app side) ----

    private const val MAX_LOG_ENTRIES = 300

    fun appendDetection(context: Context, pkg: String, method: String, time: Long) {
        val prefs = context.getSharedPreferences(DETECTION_PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_LOG, "").orEmpty()
        val line = "$time|$pkg|$method"
        val lines = (listOf(line) + existing.split("\n")).filter { it.isNotBlank() }.take(MAX_LOG_ENTRIES)
        prefs.edit().putString(KEY_LOG, lines.joinToString("\n")).apply()
    }

    fun loadDetections(context: Context): List<Triple<Long, String, String>> {
        val prefs = context.getSharedPreferences(DETECTION_PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LOG, "").orEmpty()
            .split("\n")
            .filter { it.isNotBlank() }
            .mapNotNull { row ->
                val parts = row.split("|", limit = 3)
                if (parts.size == 3) Triple(parts[0].toLongOrNull() ?: 0L, parts[1], parts[2]) else null
            }
    }

    fun clearDetections(context: Context) {
        context.getSharedPreferences(DETECTION_PREFS, Context.MODE_PRIVATE).edit().remove(KEY_LOG).apply()
    }

    /** Best-effort chmod so a private prefs file becomes world-readable for the hook process. */
    private fun makeReadable(context: Context, name: String) {
        runCatching {
            val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
            prefsDir.setExecutable(true, false)
            prefsDir.setReadable(true, false)
            File(prefsDir, "$name.xml").apply {
                setReadable(true, false)
            }
            File(context.applicationInfo.dataDir).setExecutable(true, false)
        }
    }
}
