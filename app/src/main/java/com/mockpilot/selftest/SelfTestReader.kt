package com.mockpilot.selftest

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** One inspected signal a tracking app could read. [concern] = true means the fake is detectable. */
data class SelfTestCheck(
    val name: String,
    val value: String,
    val concern: Boolean,
    val detail: String,
)

data class SelfTestReport(
    val hadFix: Boolean,
    val checks: List<SelfTestCheck>,
) {
    val anyConcern: Boolean get() = checks.any { it.concern }
}

/**
 * Reads back the location MockPilot is currently emitting and reports exactly which mock-related
 * flags a tracking app would see. This is a *ground-truth* view: without the LSPosed module the mock
 * flags WILL be visible (that's expected and honest). With the module active on this app they should
 * read clean — that's what this screen lets you verify before trusting the bypass.
 */
@Singleton
class SelfTestReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun run(): SelfTestReport {
        val fix = lastMockFix()
        val checks = mutableListOf<SelfTestCheck>()

        if (fix == null) {
            return SelfTestReport(
                hadFix = false,
                checks = listOf(
                    SelfTestCheck(
                        name = "Last known fix",
                        value = "none",
                        concern = false,
                        detail = "Start emission first, then re-run the self-test.",
                    ),
                ),
            )
        }

        // 1. isFromMockProvider() — the classic pre-API-31 check.
        @Suppress("DEPRECATION")
        val fromMock = fix.isFromMockProvider
        checks += SelfTestCheck(
            name = "isFromMockProvider()",
            value = fromMock.toString(),
            concern = fromMock,
            detail = "Deprecated in API 31 but still queried by many SDKs.",
        )

        // 2. isMock() — the API 31+ replacement.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val isMock = fix.isMock
            checks += SelfTestCheck(
                name = "isMock()",
                value = isMock.toString(),
                concern = isMock,
                detail = "Reads the HAS_MOCK_PROVIDER_MASK bit of the private fields mask.",
            )
        }

        // 3. Raw private fields mask (reflection) — what the bit clearing targets.
        readFieldsMask(fix)?.let { mask ->
            checks += SelfTestCheck(
                name = "mFieldsMask (reflected)",
                value = "0x${mask.toString(16)}",
                concern = false,
                detail = "Bit 10 (HAS_MOCK_PROVIDER_MASK) is the one the module clears on API 31+.",
            )
        }

        // 4. Provider name.
        checks += SelfTestCheck(
            name = "provider",
            value = fix.provider ?: "null",
            concern = false,
            detail = "Should be 'gps' — a fused-only fix with no gps is itself suspicious.",
        )

        // 5. Test providers visible in the provider list.
        val testProvidersVisible = testProvidersPresent()
        checks += SelfTestCheck(
            name = "Test providers listed",
            value = testProvidersVisible.toString(),
            concern = testProvidersVisible,
            detail = "getProviders()/isProviderEnabled can reveal an injected test provider.",
        )

        // 6. Fully-populated fields (a sparse Location is a tell on its own).
        val populated = fix.hasAltitude() && fix.hasSpeed() && fix.hasBearing() &&
            fix.elapsedRealtimeNanos > 0 &&
            fix.hasVerticalAccuracy() && fix.hasSpeedAccuracy() && fix.hasBearingAccuracy()
        checks += SelfTestCheck(
            name = "Fields fully populated",
            value = populated.toString(),
            concern = !populated,
            detail = "time/elapsedRealtimeNanos/accuracy/altitude/speed/bearing + uncertainties.",
        )

        // 7. Legacy Settings.Secure mock toggle (pre-API-23 only).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            val allow = try {
                Settings.Secure.getInt(context.contentResolver, Settings.Secure.ALLOW_MOCK_LOCATION, 0)
            } catch (e: Exception) {
                0
            }
            checks += SelfTestCheck(
                name = "ALLOW_MOCK_LOCATION",
                value = allow.toString(),
                concern = allow != 0,
                detail = "Legacy global toggle read by old anti-cheat code.",
            )
        }

        return SelfTestReport(hadFix = true, checks = checks)
    }

    /**
     * Live check for whether this app is the selected mock-location app: try to register (and
     * immediately remove) a throwaway test provider. A SecurityException means we're not selected.
     */
    fun isMockAppSelected(): Boolean {
        val probe = "mockpilot_probe"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val props = android.location.provider.ProviderProperties.Builder().build()
                lm.addTestProvider(probe, props)
            } else {
                @Suppress("DEPRECATION")
                lm.addTestProvider(probe, false, false, false, false, true, true, true, 3, 1)
            }
            lm.removeTestProvider(probe)
            true
        } catch (se: SecurityException) {
            false
        } catch (e: Exception) {
            // Some OEMs throw IllegalArgumentException on cleanup but the add succeeded → treat as ok.
            runCatching { lm.removeTestProvider(probe) }
            true
        }
    }

    private fun lastMockFix(): Location? =
        try {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (e: SecurityException) {
            null
        }

    private fun testProvidersPresent(): Boolean =
        try {
            // Heuristic: if GPS reports enabled while there is no real GNSS session, and our provider
            // set is registered, treat the test providers as "visible". We simply report whether the
            // providers we spoof are enabled and present.
            lm.allProviders.contains(LocationManager.GPS_PROVIDER) &&
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (e: Exception) {
            false
        }

    /** Reflect the private `mFieldsMask` int; returns null if the field/layout isn't accessible. */
    private fun readFieldsMask(location: Location): Int? =
        try {
            val field = Location::class.java.getDeclaredField("mFieldsMask")
            field.isAccessible = true
            field.getInt(location)
        } catch (e: Throwable) {
            null
        }
}
