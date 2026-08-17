# MockPilot

A realistic GPS location simulator for Android, built for **development, QA, and privacy testing on
your own device**. MockPilot emits fully-formed location fixes with correlated, human-looking motion
so that location behaviour under test looks like a real phone rather than a teleporting pin at
accuracy 1.0.

It ships in two parts:

1. **`:app`** — the main app (Compose + Material 3). Non-root. Uses Android's built-in *test
   provider* API, which requires selecting MockPilot as the system "mock location app". The mock
   flags on the emitted `Location` **are** visible to other apps (this is how Android is designed).
2. **`:xposed`** — an *optional* LSPosed/Xposed companion module (root + LSPosed). Hooks the
   mock-detection code paths inside **apps you explicitly scope**, so those apps see MockPilot's
   fixes as genuine. Includes a scope picker and a live log of what each target app checks.

> **Scope & intent.** This is a dual-use testing tool for your own device and apps you're authorized
> to test. Spoofing location to defraud a service, evade court-ordered monitoring, or violate an
> app's terms may be illegal. You are responsible for how you use it.

### iOS note & `web/` GPX Studio

iOS has **no mock-location API**, so the Android app above can't run there and no on-device iOS app
can spoof system location for other apps. On iOS the only working paths are **Xcode ▸ Simulate
Location** (Mac, free) or a tethered desktop spoofer (Windows/Mac). Those consume a **GPX** route.

`web/index.html` is a self-contained **GPX Studio** for that workflow: open it in any desktop
browser (needs internet for the map/search), drop a pin or draw a route, and it exports a realistic
`mockpilot.gpx` — the same realism engine (Ornstein–Uhlenbeck jitter when parked, accel/decel/
cornering when moving, no teleport) rendered as dense, correctly-timed waypoints — that you load into
Xcode or a desktop tool. Snap Map reflects it; **Life360 actively detects spoofing** and may flag it
regardless of realism.

---

## Building

```bash
./gradlew assembleDebug
# APKs:
#   app/build/outputs/apk/debug/app-debug.apk        (main app)
#   xposed/build/outputs/apk/debug/xposed-debug.apk  (LSPosed module)
```

Requirements: JDK 17+, Android SDK with API 35 (`compileSdk = 35`), `minSdk = 26`. The Gradle
wrapper (8.11.1) is committed. If the Android SDK isn't found, create `local.properties` with
`sdk.dir=/path/to/Android/Sdk` (or set `ANDROID_HOME`).

Run the pure-logic unit tests (realism/geometry) without a device:

```bash
./gradlew :app:testDebugUnitTest
```

---

## Setup — non-root path (main app)

The in-app **Setup Wizard** (first launch, or the gear icon on the Map screen) walks through this
with a **live indicator** that flips to ✓ the moment MockPilot becomes the mock-location app:

1. **Enable Developer Options** — Settings → About phone → tap *Build number* 7×.
2. **Select the mock location app** — Developer Options → *Select mock location app* → **MockPilot**.
3. **Allow notifications** — the foreground service shows an ongoing notification while emitting
   (mandatory on modern Android).
4. **Ignore battery optimizations** — so the engine keeps ticking under Doze.
5. **Allow exact alarms** (API 31+) — for punctual Scheduler transitions.

Then: drop a pin (long-press the map) or search an address, pick a **teleport guard** mode, and hit
**START**. The status card shows live coordinates, accuracy, speed, satellites and uptime.

## Setup — root path (LSPosed module)

1. Root your device and install **LSPosed** (Zygisk or Riru variant for your setup).
2. Install `xposed-debug.apk`. It appears in LSPosed as **“MockPilot Guard”**.
3. In LSPosed: **enable the module** and set its **scope** to the app(s) you want fooled, then
   force-stop those apps so the hooks load.
4. Open **MockPilot Guard** → pick the same target app(s) in the in-module **scope picker** and
   *Save scope* (this drives the per-app targeting the hooks read via `XSharedPreferences`).
5. Start MockPilot (main app) as usual. In the target app, location now reads as non-mock.
6. **View detection log** in MockPilot Guard to see exactly which mock-check APIs each target app
   called and when.

> The module never hooks MockPilot itself, so the **Detection Self-Test** screen keeps showing the
> real, un-hidden flags — that's your ground truth for verifying the bypass on *other* apps.

---

## Realism engine (the important part)

- **Stationary mode** — horizontal offset is a 2-D **Ornstein–Uhlenbeck (mean-reverting) process**,
  which produces the slow, *correlated* wander real GPS shows, not white noise. Kept on an ~8 m
  leash; reported accuracy "breathes" between 5–20 m. (`StationaryWalk`)
- **Route mode** — tap waypoints; the path is interpolated and driven by a real speed profile:
  accelerate from stops, cruise, **decelerate into turns** (sharper bend → slower corner), brief
  stops at waypoints, with per-sample along/cross-track noise. Bearing follows the actual heading of
  travel. One-shot or looping. (`RouteRunner`)
- **Teleport guard** — a distant target change while running never jumps instantly. Choose:
  - **Travel** — simulate the trip at a plausible speed, or
  - **GPS lost** — stop emitting for a tunnel/airplane-like blackout, then resume at the new spot, or
  - **Refuse** — reject the jump.
- **Overnight idle** — at night the update rate and jitter drop to mimic a phone on a nightstand.

Every emitted `Location` is **fully populated** (`LocationBuilder`): `time`,
`elapsedRealtimeNanos`, time-varying `accuracy` (4–15 m moving / 5–20 m still), `altitude` +
`verticalAccuracyMeters`, `speed` + `speedAccuracyMetersPerSecond`, `bearing` +
`bearingAccuracyDegrees`, `elapsedRealtimeUncertaintyNanos` (API 29+), and an extras bundle with a
slowly-varying **satellite count**. Fixes are pushed to `gps`, `network`, and (best-effort) `fused`
test providers. `addTestProvider` is handled for both the **API 31+ `ProviderProperties`** overload
and the **legacy pre-31** overload. A `PARTIAL_WAKE_LOCK` keeps the 1 Hz cadence alive under Doze,
and a `BOOT_COMPLETED` receiver resumes an interrupted session after reboot.

---

## Detection vectors — what is and isn't covered

### Main app alone (no root)
Android exposes the mock nature of test-provider fixes on purpose, so **without** the LSPosed module
these are visible and MockPilot does **not** hide them:

| Vector | Covered without root? |
|---|---|
| `Location.isFromMockProvider()` | ❌ returns `true` |
| `Location.isMock()` (API 31+) | ❌ returns `true` |
| Realistic field population (no `accuracy=1.0`, correlated motion, sats, etc.) | ✅ |
| Teleport / impossible-speed heuristics | ✅ (teleport guard) |
| App enumerating an odd test provider name | ✅ (we only use `gps`/`network`/`fused`) |

### With the LSPosed module (scoped apps)

| Vector | Covered | How |
|---|---|---|
| `Location.isFromMockProvider()` | ✅ | hooked → `false` |
| `Location.isMock()` (API 31+) | ✅ | hooked → `false` |
| Reflection on private `mFieldsMask` (`HAS_MOCK_PROVIDER_MASK`, bit 10) | ✅ | bit cleared in place on the Location object |
| `LocationManager.getProviders` / `getAllProviders` / `isProviderEnabled` | ✅ | non-standard providers filtered/hidden |
| `Settings.Secure.getInt(ALLOW_MOCK_LOCATION)` (pre-API-23) | ✅ | hooked → `0` |
| Seeing what the app checks | ✅ | every hooked call is logged (module log + XposedBridge) |

### Not covered (be aware)
- **Play Integrity / SafetyNet / hardware attestation** — a separate problem from mock-location
  flags; MockPilot doesn't touch it. Use Zygisk-based integrity tooling if a target requires it.
- **Google's fused/`FusedLocationProviderClient` (GMS) path** — apps using Play Services location
  may read from GMS rather than the framework `LocationManager`. The framework test providers cover
  the AOSP path; the GMS path is only as good as the app honouring the system mock provider. The
  LSPosed hooks target `android.location.*`, not the GMS client classes.
- **Sensor cross-checks** — an app correlating the accelerometer/step counter/Wi-Fi/cell with your
  "movement" can still spot inconsistencies. MockPilot only simulates GPS.
- **Server-side heuristics** — impossible travel detected in a backend, IP-vs-GPS mismatch, etc., is
  outside anything an on-device tool can address.
- **Root/LSPosed detection** — a hardened app may detect that it's hooked and refuse to run.

---

## Architecture

```
app/
  engine/      MockLocationEngine (driver), RealismEngine (StationaryWalk/RouteRunner),
               LocationBuilder, TestProviderManager, MockLocationService (foreground),
               EngineController (UI↔service state bridge), SpoofManager, BootReceiver
  model/       GeoPoint (haversine/bearing/destination), MockConfig, EngineState, ...
  data/        Room (SavedPlace/RouteEntity/ScheduleEntity + DAOs), SessionStore,
               geocode/ (Nominatim — no API key)
  scheduler/   ScheduleManager (AlarmManager exact alarms + WorkManager), planner worker, receiver
  selftest/    SelfTestReader (reads back emitted fix, reports every visible flag)
  ui/          Compose screens: Map, Places, Routes, Schedule, Self-Test, Setup Wizard
xposed/        LSPosed module: MockGuardHook (entry), LocationHooks, ScopeStore,
               DetectionReceiver, ui/ (ScopeActivity, DetectionLogActivity)
```

**Stack:** Kotlin, Jetpack Compose + Material 3, Hilt, Room, WorkManager, osmdroid (OpenStreetMap
tiles — no Google Maps / no API key), Retrofit + kotlinx.serialization for Nominatim geocoding.

## Notes on the LSPosed preferences bridge
The scope selection is written by the module app and read inside hooked processes via
`XSharedPreferences`. LSPosed makes a module's prefs world-readable; `ScopeStore` also falls back to
`MODE_WORLD_READABLE` and a manual chmod so the hook process can read the file across LSPosed
variants. Detection reports are sent from the hooked process to the module app via an explicit
broadcast and stored for the in-app log.
