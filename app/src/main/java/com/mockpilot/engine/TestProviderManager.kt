package com.mockpilot.engine

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.util.Log

/**
 * Registers and drives Android *test providers*. The [addTestProvider] signature changed across
 * releases: API 31+ exposes a [ProviderProperties] overload, while older releases only offer the
 * legacy 10-boolean/int overload. Both are handled here, and every call is wrapped so that a
 * SecurityException (app not selected as the mock-location app) is reported rather than crashing.
 */
class TestProviderManager(context: Context) {

    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /** Providers we spoof. FUSED is best-effort — some OEM builds reject a test "fused" provider. */
    private val providers = buildList {
        add(LocationManager.GPS_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        add(fusedProviderName())
    }

    private val registered = mutableSetOf<String>()

    sealed interface Result {
        data object Ok : Result
        data class Denied(val message: String) : Result
    }

    /**
     * Adds (or re-adds) every test provider and enables it. Returns [Result.Denied] if the app is
     * not the selected mock-location app — the common first-run failure.
     */
    fun enable(): Result {
        var deniedMessage: String? = null
        for (p in providers) {
            try {
                // Remove any stale registration first (e.g. after a crash) — ignore if absent.
                runCatching { lm.removeTestProvider(p) }
                addProvider(p)
                lm.setTestProviderEnabled(p, true)
                registered.add(p)
            } catch (se: SecurityException) {
                deniedMessage = se.message ?: "Not permitted as mock location app"
                Log.w(TAG, "addTestProvider denied for $p", se)
            } catch (e: Exception) {
                // FUSED in particular can throw IllegalArgumentException on some devices — skip it.
                Log.w(TAG, "addTestProvider failed for $p: ${e.message}")
            }
        }
        return if (registered.isEmpty() && deniedMessage != null) Result.Denied(deniedMessage)
        else Result.Ok
    }

    /** Pushes one fix to every successfully registered provider. */
    fun push(location: Location) {
        for (p in registered) {
            try {
                // Re-tag the location with the right provider name for each push.
                val perProvider = Location(location).apply { provider = p }
                lm.setTestProviderLocation(p, perProvider)
            } catch (e: Exception) {
                Log.w(TAG, "setTestProviderLocation failed for $p: ${e.message}")
            }
        }
    }

    /** Reads back the last mock fix we emitted (used by the Detection Self-Test). */
    fun readBack(): Location? =
        try {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } catch (e: Exception) {
            null
        }

    fun disable() {
        for (p in registered.toList()) {
            runCatching { lm.setTestProviderEnabled(p, false) }
            runCatching { lm.removeTestProvider(p) }
        }
        registered.clear()
    }

    private fun addProvider(provider: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API 31+: ProviderProperties overload.
            val props = ProviderProperties.Builder()
                .setHasNetworkRequirement(false)
                .setHasSatelliteRequirement(provider == LocationManager.GPS_PROVIDER)
                .setHasCellRequirement(false)
                .setHasMonetaryCost(false)
                .setHasAltitudeSupport(true)
                .setHasSpeedSupport(true)
                .setHasBearingSupport(true)
                .setPowerUsage(ProviderProperties.POWER_USAGE_HIGH)
                .setAccuracy(ProviderProperties.ACCURACY_FINE)
                .build()
            lm.addTestProvider(provider, props)
        } else {
            // API 26–30: legacy 10-argument overload.
            @Suppress("DEPRECATION")
            lm.addTestProvider(
                provider,
                false,                       // requiresNetwork
                provider == LocationManager.GPS_PROVIDER, // requiresSatellite
                false,                       // requiresCell
                false,                       // hasMonetaryCost
                true,                        // supportsAltitude
                true,                        // supportsSpeed
                true,                        // supportsBearing
                POWER_HIGH,
                ACCURACY_FINE,
            )
        }
    }

    private fun fusedProviderName(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) LocationManager.FUSED_PROVIDER else "fused"

    companion object {
        private const val TAG = "TestProviderManager"
        // android.location.Criteria constants (POWER_HIGH=3, ACCURACY_FINE=1) inlined to avoid the
        // deprecated Criteria import.
        private const val POWER_HIGH = 3
        private const val ACCURACY_FINE = 1
    }
}
