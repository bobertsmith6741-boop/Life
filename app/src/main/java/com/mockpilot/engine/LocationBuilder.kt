package com.mockpilot.engine

import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import com.mockpilot.model.Kinematics
import com.mockpilot.model.LocationSample
import kotlin.math.max

/**
 * Builds a *fully populated* [Location]. A location that only carries lat/lon/accuracy is trivial to
 * flag as fake — real fixes always ship time bases, vertical/speed/bearing values with their own
 * uncertainties, and a satellite count in the extras. Every field below is filled deliberately.
 */
object LocationBuilder {

    fun build(
        provider: String,
        kin: Kinematics,
        satellites: Int,
        nowMillis: Long = System.currentTimeMillis(),
        elapsedNanos: Long = SystemClock.elapsedRealtimeNanos(),
    ): Location {
        val loc = Location(provider)
        loc.latitude = kin.point.latitude
        loc.longitude = kin.point.longitude

        // Altitude: use the supplied value or synthesize a plausible one.
        val altitude = kin.point.altitude ?: DEFAULT_ALTITUDE
        loc.altitude = altitude

        loc.accuracy = max(1f, kin.accuracyM)
        loc.speed = max(0f, kin.speedMps)
        loc.bearing = ((kin.bearingDeg % 360f) + 360f) % 360f

        // Time bases — both are mandatory. A missing/zero elapsedRealtimeNanos is a classic tell.
        loc.time = nowMillis
        loc.elapsedRealtimeNanos = elapsedNanos

        // Per-field uncertainties (all available since API 26 / O, our minSdk).
        loc.verticalAccuracyMeters = (kin.accuracyM * 1.5f).coerceIn(3f, 30f)
        // A stationary receiver reports a large, unreliable bearing uncertainty; a moving one small.
        loc.bearingAccuracyDegrees = if (kin.moving) 8f else 90f
        loc.speedAccuracyMetersPerSecond = if (kin.moving) 0.7f else 0.4f

        // elapsedRealtimeUncertaintyNanos (API 29+) — a few ms is typical.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            loc.elapsedRealtimeUncertaintyNanos = 3_000_000.0 // ~3 ms
        }

        // Extras: several detectors read the satellite count out of the extras bundle.
        val extras = Bundle().apply {
            putInt("satellites", satellites)
            putInt("satellitesUsedInFix", satellites)
        }
        loc.extras = extras

        return loc
    }

    fun toSample(kin: Kinematics, satellites: Int, nowMillis: Long, elapsedNanos: Long): LocationSample =
        LocationSample(
            point = kin.point,
            accuracyM = max(1f, kin.accuracyM),
            altitudeM = kin.point.altitude ?: DEFAULT_ALTITUDE,
            verticalAccuracyM = (kin.accuracyM * 1.5f).coerceIn(3f, 30f),
            speedMps = max(0f, kin.speedMps),
            speedAccuracyMps = if (kin.moving) 0.7f else 0.4f,
            bearingDeg = ((kin.bearingDeg % 360f) + 360f) % 360f,
            bearingAccuracyDeg = if (kin.moving) 8f else 90f,
            satelliteCount = satellites,
            timeMillis = nowMillis,
            elapsedRealtimeNanos = elapsedNanos,
        )

    private const val DEFAULT_ALTITUDE = 42.0
}
