package com.mockpilot.engine

import com.mockpilot.model.GeoPoint
import java.util.Random
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Instantaneous kinematic state produced by the realism models. The engine turns this into a
 * fully-populated [android.location.Location] in [LocationBuilder].
 */
data class Kinematics(
    val point: GeoPoint,
    val speedMps: Float,
    val bearingDeg: Float,
    val accuracyM: Float,
    /** Whether the underlying model considers itself "moving" (affects bearing accuracy etc.). */
    val moving: Boolean,
)

/**
 * Small wrapper around [java.util.Random] so the noise source is injectable (deterministic in
 * tests, seeded from the clock in production).
 */
class Rng(seed: Long) {
    private val random = Random(seed)
    fun gaussian(): Double = random.nextGaussian()
    fun next(): Double = random.nextDouble()
    fun range(lo: Double, hi: Double): Double = lo + (hi - lo) * random.nextDouble()
}

/**
 * STATIONARY MODE.
 *
 * Real stationary GPS does not jitter as white noise — successive fixes are strongly correlated and
 * wander in slow, meandering patterns. We model the horizontal offset from the true point as a 2-D
 * Ornstein–Uhlenbeck (mean-reverting) process, which produces exactly that correlated drift. The
 * stationary standard deviation is sigma / sqrt(2*theta); the defaults target ~4 m with excursions
 * kept inside an 8 m leash. Reported accuracy "breathes" on a slow sinusoid between 5–20 m.
 */
class StationaryWalk(
    private val center: GeoPoint,
    private val rng: Rng,
    private val theta: Double = 0.05,      // mean-reversion strength per second
    private val sigma: Double = 1.3,       // drift volatility (m / sqrt(s))
    private val leashMeters: Double = 8.0, // hard cap on excursion radius
) {
    private var east = 0.0   // metres east of centre
    private var north = 0.0  // metres north of centre
    private var accPhase = rng.range(0.0, 2 * Math.PI)
    private var lastBearing = rng.range(0.0, 360.0).toFloat()

    /**
     * Overnight idle: when set, the wander tightens (a phone on a nightstand barely moves). Halves
     * the leash and drops the drift volatility.
     */
    var calm: Boolean = false

    fun tick(dt: Double): Kinematics {
        val effSigma = if (calm) sigma * 0.35 else sigma
        val effLeash = if (calm) leashMeters * 0.5 else leashMeters

        // Ornstein–Uhlenbeck update in each axis.
        east += -theta * east * dt + effSigma * sqrt(dt) * rng.gaussian()
        north += -theta * north * dt + effSigma * sqrt(dt) * rng.gaussian()

        // Keep the wander on a leash so a bad run of noise can't drift the pin away.
        val r = hypot(east, north)
        if (r > effLeash) {
            val k = effLeash / r
            east *= k
            north *= k
        }

        val radius = hypot(east, north)
        val bearing = (Math.toDegrees(atan2(east, north)) + 360.0) % 360.0
        val point = center.destination(radius, bearing)

        // Accuracy breathes slowly between ~5 and ~20 m.
        accPhase += dt * 0.15
        val accuracy = (11.0 + 6.0 * sin(accPhase) + rng.gaussian() * 1.2)
            .coerceIn(5.0, 20.0)

        // A parked phone reports a tiny residual speed and an essentially random, unreliable bearing.
        val speed = max(0.0, 0.15 + rng.gaussian() * 0.1)
        if (radius > 0.5) lastBearing = bearing.toFloat()

        return Kinematics(
            point = point,
            speedMps = speed.toFloat(),
            bearingDeg = lastBearing,
            accuracyM = accuracy.toFloat(),
            moving = false,
        )
    }
}

/**
 * ROUTE MODE.
 *
 * Interpolates a smooth path along the user's waypoints and drives a plausible speed profile:
 * accelerate away from a stop, cruise at the target speed, decelerate into turns (sharper turn →
 * slower corner) and to a brief stop at each waypoint, then move on. Per-sample along-track and
 * cross-track Gaussian noise is layered on top, and the reported bearing follows the actual heading
 * of travel. Set [loop] to seamlessly restart at the first waypoint.
 */
class RouteRunner(
    waypoints: List<GeoPoint>,
    private val rng: Rng,
    private val targetSpeed: Double = 8.0,   // cruising speed, m/s
    private val maxAccel: Double = 1.4,      // m/s^2
    private val maxDecel: Double = 2.2,      // m/s^2
    private val loop: Boolean = false,
) {
    private val pts: List<GeoPoint> = waypoints.filterIndexed { i, p ->
        i == 0 || p.distanceTo(waypoints[i - 1]) > 0.5
    }.ifEmpty { waypoints }

    private val cum: DoubleArray
    val totalLength: Double

    private var traveled = 0.0
    private var speed = 0.0
    private var stopTimer = 0.0
    private var lastBearing = 0.0f
    var finished = false
        private set

    init {
        cum = DoubleArray(pts.size)
        var acc = 0.0
        for (i in 1 until pts.size) {
            acc += pts[i - 1].distanceTo(pts[i])
            cum[i] = acc
        }
        totalLength = acc
        if (pts.size >= 2) lastBearing = pts[0].bearingTo(pts[1]).toFloat()
    }

    /** Remaining path length in metres (0 once finished / at the end of a one-shot route). */
    fun remaining(): Double = max(0.0, totalLength - traveled)

    fun tick(dt: Double): Kinematics {
        if (pts.size < 2) {
            finished = true
            return Kinematics(pts.first(), 0f, lastBearing, 8f, moving = false)
        }

        // Honour a scheduled brief stop (e.g. at a waypoint) before moving again.
        if (stopTimer > 0) {
            stopTimer -= dt
            speed = 0.0
            return sampleAt(traveled, moving = false)
        }

        val distToEnd = totalLength - traveled
        val corner = cornerSpeedLimit(traveled)
        // Desired speed is the lower of cruise, the corner limit, and what still fits before the end.
        val stoppingDist = (speed * speed) / (2 * maxDecel) + 0.5
        var desired = min(targetSpeed, corner)
        if (!loop && distToEnd <= stoppingDist) desired = 0.0

        // Move current speed toward the desired speed within accel/decel limits.
        speed = if (desired > speed) {
            min(desired, speed + maxAccel * dt)
        } else {
            max(desired, speed - maxDecel * dt)
        }
        // A little organic variation so cruising isn't robotically constant.
        speed = max(0.0, speed + rng.gaussian() * 0.15 * dt)

        traveled += speed * dt

        if (traveled >= totalLength) {
            if (loop) {
                traveled -= totalLength
            } else {
                traveled = totalLength
                speed = 0.0
                finished = true
                return sampleAt(traveled, moving = false)
            }
        }

        // Brief stop with small probability when we cross a waypoint at low speed.
        maybeScheduleWaypointStop()

        return sampleAt(traveled, moving = speed > 0.4)
    }

    private fun sampleAt(s: Double, moving: Boolean): Kinematics {
        val seg = segmentIndexFor(s)
        val a = pts[seg]
        val b = pts[seg + 1]
        val segStart = cum[seg]
        val segLen = max(0.0001, cum[seg + 1] - segStart)
        val f = ((s - segStart) / segLen).coerceIn(0.0, 1.0)

        val base = interpolate(a, b, f)
        val bearing = a.bearingTo(b)

        // Cross-track + along-track Gaussian noise (metres), tighter than stationary because a
        // moving receiver generally has a better fix.
        val cross = rng.gaussian() * 1.8
        val along = rng.gaussian() * 1.2
        val noisy = base
            .destination(abs(along), if (along >= 0) bearing else (bearing + 180) % 360)
            .destination(abs(cross), (bearing + if (cross >= 0) 90 else 270) % 360)

        if (moving) lastBearing = ((bearing + rng.gaussian() * 3.0 + 360) % 360).toFloat()

        val accuracy = (7.0 + 4.0 * sin(s * 0.02) + rng.gaussian()).coerceIn(4.0, 15.0)

        return Kinematics(
            point = noisy,
            speedMps = speed.toFloat(),
            bearingDeg = lastBearing,
            accuracyM = accuracy.toFloat(),
            moving = moving,
        )
    }

    private fun interpolate(a: GeoPoint, b: GeoPoint, f: Double): GeoPoint {
        val lat = a.latitude + (b.latitude - a.latitude) * f
        val lon = a.longitude + (b.longitude - a.longitude) * f
        val alt = when {
            a.altitude != null && b.altitude != null -> a.altitude + (b.altitude - a.altitude) * f
            else -> null
        }
        return GeoPoint(lat, lon, alt)
    }

    private fun segmentIndexFor(s: Double): Int {
        var seg = 0
        while (seg < pts.size - 2 && cum[seg + 1] < s) seg++
        return seg
    }

    /** Lower speed cap approaching a turn — the sharper the upcoming bend, the slower the corner. */
    private fun cornerSpeedLimit(s: Double): Double {
        val seg = segmentIndexFor(s)
        // Look at the bend at the *end* of the current segment (the next waypoint).
        if (seg + 2 >= pts.size) return targetSpeed
        val inB = pts[seg].bearingTo(pts[seg + 1])
        val outB = pts[seg + 1].bearingTo(pts[seg + 2])
        var turn = abs(outB - inB) % 360.0
        if (turn > 180) turn = 360 - turn

        val distToTurn = cum[seg + 1] - s
        val decelZone = 25.0 // begin easing off this many metres out
        if (distToTurn > decelZone) return targetSpeed

        // 0° turn -> full speed; 180° hairpin -> ~1.5 m/s.
        val cornerMax = (targetSpeed * (1.0 - turn / 200.0)).coerceAtLeast(1.5)
        val blend = (distToTurn / decelZone).coerceIn(0.0, 1.0)
        return cornerMax + (targetSpeed - cornerMax) * blend
    }

    private fun maybeScheduleWaypointStop() {
        val seg = segmentIndexFor(traveled)
        val nearWaypoint = abs(cum[seg + 1] - traveled) < 2.0
        if (nearWaypoint && speed < 2.0 && rng.next() < 0.15) {
            stopTimer = rng.range(1.5, 5.0) // brief stop at a light / crossing
        }
    }
}
