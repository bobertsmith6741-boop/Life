package com.mockpilot.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * LSPosed entry point. On every package load it consults the user-selected scope and, for targeted
 * apps, installs the mock-location-hiding hooks. The module's own app and the MockPilot app are
 * never hooked (that would defeat the Self-Test's ground-truth reading).
 */
class MockGuardHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName
        if (pkg == ScopeStore.MODULE_PKG || pkg == "com.mockpilot" || pkg == "com.mockpilot.debug") return

        val prefs = XSharedPreferences(ScopeStore.MODULE_PKG, ScopeStore.SCOPE_PREFS)
        prefs.makeWorldReadable()
        prefs.reload()
        val selected = prefs.getStringSet(ScopeStore.KEY_PACKAGES, emptySet()) ?: emptySet()

        if (pkg !in selected) return

        XposedBridge.log("[MockPilot] Installing mock-hiding hooks into $pkg")
        LocationHooks.install(lpparam.classLoader, pkg)
    }
}
