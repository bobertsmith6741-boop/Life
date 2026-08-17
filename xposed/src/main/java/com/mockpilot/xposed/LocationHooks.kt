package com.mockpilot.xposed

import android.app.AndroidAppHelper
import android.content.ContentResolver
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * The actual hooks that make a targeted app believe MockPilot's fixes are genuine.
 *
 * Covered vectors:
 *  1. [Location.isFromMockProvider] → false          (pre-API-31 classic check)
 *  2. [Location.isMock] → false                      (API 31+ replacement)
 *  3. Private `mFieldsMask` HAS_MOCK_PROVIDER_MASK bit cleared via reflection (API 31+), so apps
 *     that reflect into the Location object directly also see a clean fix.
 *  4. [LocationManager.getProviders]/getAllProviders/isProviderEnabled → non-standard (test)
 *     providers hidden.
 *  5. [Settings.Secure.getInt] for `mock_location` → 0 (legacy pre-API-23 global toggle).
 *
 * Every hooked call the target app makes is reported (XposedBridge log + a broadcast the module app
 * records) so you can see exactly what the app is checking.
 */
object LocationHooks {

    // AOSP: private static final int HAS_MOCK_PROVIDER_MASK = 1 << 10; (Android 12+)
    private const val HAS_MOCK_PROVIDER_MASK = 1 shl 10

    fun install(classLoader: ClassLoader, pkg: String) {
        hookIsFromMockProvider(pkg)
        hookIsMock(pkg)
        hookProviderEnumeration(classLoader, pkg)
        hookSettingsSecure(classLoader, pkg)
    }

    private fun hookIsFromMockProvider(pkg: String) {
        runCatching {
            XposedHelpers.findAndHookMethod(
                Location::class.java, "isFromMockProvider",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        report(pkg, "Location.isFromMockProvider")
                        (param.thisObject as? Location)?.let { clearMockBit(it) }
                        param.result = false
                    }
                },
            )
        }.onFailure { XposedBridge.log("[MockPilot] isFromMockProvider hook failed: ${it.message}") }
    }

    private fun hookIsMock(pkg: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            XposedHelpers.findAndHookMethod(
                Location::class.java, "isMock",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        report(pkg, "Location.isMock")
                        (param.thisObject as? Location)?.let { clearMockBit(it) }
                        param.result = false
                    }
                },
            )
        }.onFailure { XposedBridge.log("[MockPilot] isMock hook failed: ${it.message}") }
    }

    /**
     * Hide injected test providers from provider enumeration. Anything outside the standard set is
     * dropped from the returned lists, and isProviderEnabled reports false for such names.
     */
    private fun hookProviderEnumeration(classLoader: ClassLoader, pkg: String) {
        val filterList = object : XC_MethodHook() {
            @Suppress("UNCHECKED_CAST")
            override fun afterHookedMethod(param: MethodHookParam) {
                val result = param.result as? MutableList<String> ?: return
                val filtered = result.filter { it in ScopeStore.STANDARD_PROVIDERS }
                if (filtered.size != result.size) report(pkg, "LocationManager.${param.method.name} (filtered)")
                param.result = ArrayList(filtered)
            }
        }
        runCatching {
            XposedHelpers.findAndHookMethod(
                LocationManager::class.java, "getProviders", Boolean::class.javaPrimitiveType, filterList,
            )
        }
        runCatching {
            XposedHelpers.findAndHookMethod(LocationManager::class.java, "getAllProviders", filterList)
        }
        runCatching {
            XposedHelpers.findAndHookMethod(
                LocationManager::class.java, "isProviderEnabled", String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val name = param.args[0] as? String ?: return
                        if (name !in ScopeStore.STANDARD_PROVIDERS) {
                            report(pkg, "LocationManager.isProviderEnabled($name)")
                            param.result = false
                        }
                    }
                },
            )
        }
    }

    private fun hookSettingsSecure(classLoader: ClassLoader, pkg: String) {
        val zeroMockLocation = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val key = param.args.getOrNull(1) as? String ?: return
                if (key == Settings.Secure.ALLOW_MOCK_LOCATION || key == "mock_location") {
                    report(pkg, "Settings.Secure.getInt(mock_location)")
                    param.result = 0
                }
            }
        }
        runCatching {
            XposedHelpers.findAndHookMethod(
                Settings.Secure::class.java, "getInt",
                ContentResolver::class.java, String::class.java, zeroMockLocation,
            )
        }
        runCatching {
            XposedHelpers.findAndHookMethod(
                Settings.Secure::class.java, "getInt",
                ContentResolver::class.java, String::class.java, Int::class.javaPrimitiveType, zeroMockLocation,
            )
        }
    }

    /** Reflect the private `mFieldsMask` int and clear the mock-provider bit in place. */
    private fun clearMockBit(location: Location) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            val field = Location::class.java.getDeclaredField("mFieldsMask")
            field.isAccessible = true
            val mask = field.getInt(location)
            if (mask and HAS_MOCK_PROVIDER_MASK != 0) {
                field.setInt(location, mask and HAS_MOCK_PROVIDER_MASK.inv())
            }
        }
    }

    // ---- Detection reporting ----

    private val lastReport = HashMap<String, Long>()

    private fun report(pkg: String, method: String) {
        XposedBridge.log("[MockPilot] $pkg → $method")

        // Throttle identical reports to avoid flooding when an app polls in a tight loop.
        val now = System.currentTimeMillis()
        val key = "$pkg|$method"
        val last = lastReport[key] ?: 0
        if (now - last < 2000) return
        lastReport[key] = now

        runCatching {
            val ctx = AndroidAppHelper.currentApplication() ?: return
            val intent = Intent(ScopeStore.ACTION_DETECTION).apply {
                setPackage(ScopeStore.MODULE_PKG)
                putExtra("pkg", pkg)
                putExtra("method", method)
                putExtra("time", now)
            }
            ctx.sendBroadcast(intent)
        }
    }
}
