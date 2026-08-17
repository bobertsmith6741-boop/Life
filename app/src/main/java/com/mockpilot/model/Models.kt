package com.mockpilot.model

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A WGS84 coordinate. */
@Serializable
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    /** Metres above the WGS84 ellipsoid; null lets the engine synthesize a plausible value. */
    val altitude: Double? = null,
) {
    companion object {
        private const val EARTH_RADIUS_M = 6_371_000.0

        fun toRad(deg: Double) = deg * PI / 180.0
        fun toDeg(rad: Double) = rad * 180.0 / PI
    }

    /** Great-circle distance in metres (haversine). */
    fun distanceTo(other: GeoPoint): Double {
        val dLat = toRad(other.latitude - latitude)
        val dLon = toRad(other.longitude - longitude)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(toRad(latitude)) * cos(toRad(other.latitude)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_M * c
    }

    /** Initial bearing (degrees, 0..360) from this point toward [other]. */
    fun bearingTo(other: GeoPoint): Double {
        val phi1 = toRad(latitude)
        val phi2 = toRad(other.latitude)
        val dLon = toRad(other.longitude - longitude)
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        return (toDeg(atan2(y, x)) + 360.0) % 360.0
    }

    /** Move [distanceM] metres along [bearingDeg] and return the destination point. */
    fun destination(distanceM: Double, bearingDeg: Double): GeoPoint {
        val delta = distanceM / EARTH_RADIUS_M
        val theta = toRad(bearingDeg)
        val phi1 = toRad(latitude)
        val lambda1 = toRad(longitude)
        val sinPhi2 = sin(phi1) * cos(delta) + cos(phi1) * sin(delta) * cos(theta)
        val phi2 = kotlin.math.asin(sinPhi2)
        val y = sin(theta) * sin(delta) * cos(phi1)
        val x = cos(delta) - sin(phi1) * sinPhi2
        val lambda2 = lambda1 + atan2(y, x)
        return GeoPoint(toDeg(phi2), ((toDeg(lambda2) + 540) % 360) - 180, altitude)
    }
}

/** How the engine derives the next position each tick. */
enum class EngineMode { STATIONARY, ROUTE }

/** Behaviour when the user selects a distant target while the engine is already running. */
enum class TeleportPolicy {
    /** Reject the change and keep the current position. */
    REFUSE,

    /** Simulate a plausible trip from the current spot to the new one before settling. */
    TRAVEL,

    /** Stop emitting for a spell (tunnel / airplane), then resume at the new spot. */
    GPS_LOST,
}

/** Immutable request describing what the engine should emit. */
@Serializable
data class MockConfig(
    val mode: EngineMode = EngineMode.STATIONARY,
    val target: GeoPoint,
    /** Ordered waypoints for ROUTE mode (first element is the start). */
    val route: List<GeoPoint> = emptyList(),
    val updateRateHz: Double = 1.0,
    /** Target cruising speed for ROUTE / TRAVEL simulation, metres per second. */
    val targetSpeedMps: Double = 8.0,
    val loopRoute: Boolean = false,
    val teleportPolicy: TeleportPolicy = TeleportPolicy.TRAVEL,
    /** Metres beyond which a target change counts as a teleport. */
    val teleportThresholdM: Double = 60.0,
    val nightIdleEnabled: Boolean = true,
    /** Label surfaced in the notification / status card. */
    val label: String = "Custom location",
)

/** High-level lifecycle of the engine, surfaced to the UI. */
sealed interface EngineState {
    data object Stopped : EngineState
    data class Running(val subMode: String) : EngineState
    data class Traveling(val remainingMeters: Double) : EngineState
    data class GpsLost(val remainingSeconds: Int) : EngineState
    data class Error(val message: String) : EngineState
}

/** One emitted (or about-to-be-emitted) location, mirrored to the UI for the status card & self-test. */
data class LocationSample(
    val point: GeoPoint,
    val accuracyM: Float,
    val altitudeM: Double,
    val verticalAccuracyM: Float,
    val speedMps: Float,
    val speedAccuracyMps: Float,
    val bearingDeg: Float,
    val bearingAccuracyDeg: Float,
    val satelliteCount: Int,
    val timeMillis: Long,
    val elapsedRealtimeNanos: Long,
)

/** One-shot events the engine raises for the UI (e.g. teleport handling feedback). */
sealed interface EngineEvent {
    data class Info(val message: String) : EngineEvent
    data class TeleportRefused(val distanceM: Double) : EngineEvent
    data class TravelStarted(val distanceM: Double, val etaSeconds: Int) : EngineEvent
    data class GpsLostStarted(val seconds: Int) : EngineEvent
}
