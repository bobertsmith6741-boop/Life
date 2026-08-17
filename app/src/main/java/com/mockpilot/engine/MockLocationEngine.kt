package com.mockpilot.engine

import android.content.Context
import android.os.SystemClock
import com.mockpilot.model.EngineEvent
import com.mockpilot.model.EngineMode
import com.mockpilot.model.EngineState
import com.mockpilot.model.GeoPoint
import com.mockpilot.model.MockConfig
import com.mockpilot.model.TeleportPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * The stateful driver that turns a [MockConfig] into a stream of realistic fixes. It owns the
 * realism models, enforces the teleport guard, applies overnight idle, and pushes every fix through
 * the [TestProviderManager]. Pure timing math is parameterised (`nowMillis`, `elapsedNanos`) so the
 * loop can be unit-tested without a device clock.
 */
class MockLocationEngine @Inject constructor(
    @ApplicationContext context: Context,
    private val controller: EngineController,
) {
    private val providers = TestProviderManager(context)
    private val rng = Rng(SystemClock.elapsedRealtimeNanos())

    private enum class Phase { STATIONARY, ROUTE, TRAVELING, GPS_LOST }

    private var config: MockConfig? = null
    private var phase = Phase.STATIONARY

    private var stationary: StationaryWalk? = null
    private var route: RouteRunner? = null
    private var travel: RouteRunner? = null

    /** Config to settle into once a TRAVEL / GPS_LOST transition completes. */
    private var pendingAfter: MockConfig? = null
    private var gpsLostUntilElapsedMs: Long = 0

    /** Last emitted position — the anchor for teleport-distance checks. */
    private var lastPoint: GeoPoint? = null

    // Slowly-varying satellite count so it isn't a static tell.
    private var satBase = rng.range(9.0, 14.0)

    fun startProviders(): TestProviderManager.Result = providers.enable()

    fun stop() {
        providers.disable()
    }

    fun readBackLastFix() = providers.readBack()

    /**
     * Reconcile the engine with a new desired config, honouring the teleport guard when the target
     * jumps a large distance while already running.
     */
    fun applyConfig(new: MockConfig) {
        val current = config
        if (current == null) {
            initialiseFrom(new)
            return
        }

        val anchor = lastPoint ?: current.target
        val newAnchor = if (new.mode == EngineMode.ROUTE && new.route.isNotEmpty()) new.route.first() else new.target
        val distance = anchor.distanceTo(newAnchor)

        // Same-ish target: just adopt the new non-positional parameters (rate, speed, night, loop).
        if (distance <= new.teleportThresholdM && phase != Phase.TRAVELING && phase != Phase.GPS_LOST) {
            // Rebuild route models if the waypoint list changed; otherwise keep drifting in place.
            if (new.mode == EngineMode.ROUTE) {
                initialiseFrom(new)
            } else {
                config = new
                phase = Phase.STATIONARY
            }
            return
        }

        // Distant jump while running → apply the teleport policy.
        when (new.teleportPolicy) {
            TeleportPolicy.REFUSE -> {
                controller.emitEvent(EngineEvent.TeleportRefused(distance))
            }

            TeleportPolicy.TRAVEL -> {
                val speed = max(3.0, new.targetSpeedMps)
                travel = RouteRunner(
                    waypoints = listOf(anchor, newAnchor),
                    rng = rng,
                    targetSpeed = speed,
                    loop = false,
                )
                pendingAfter = new
                phase = Phase.TRAVELING
                val eta = (distance / speed).roundToInt()
                controller.emitEvent(EngineEvent.TravelStarted(distance, eta))
            }

            TeleportPolicy.GPS_LOST -> {
                // Longer blackout for longer jumps (tunnel/flight), capped to a sane range.
                val seconds = (8 + distance / 400.0).roundToInt().coerceIn(8, 90)
                gpsLostUntilElapsedMs = SystemClock.elapsedRealtime() + seconds * 1000L
                pendingAfter = new
                phase = Phase.GPS_LOST
                controller.emitEvent(EngineEvent.GpsLostStarted(seconds))
            }
        }
    }

    private fun initialiseFrom(new: MockConfig) {
        config = new
        pendingAfter = null
        when (new.mode) {
            EngineMode.STATIONARY -> {
                stationary = StationaryWalk(new.target, rng)
                route = null
                phase = Phase.STATIONARY
                lastPoint = new.target
            }

            EngineMode.ROUTE -> {
                val wps = new.route.ifEmpty { listOf(new.target) }
                route = RouteRunner(wps, rng, targetSpeed = new.targetSpeedMps, loop = new.loopRoute)
                stationary = StationaryWalk(wps.last(), rng)
                phase = if (wps.size >= 2) Phase.ROUTE else Phase.STATIONARY
                lastPoint = wps.first()
            }
        }
    }

    /**
     * Advance one tick. Returns the delay (ms) until the next tick should fire — the loop stays dumb
     * and just honours it, which is how overnight idle slows the cadence.
     */
    fun tick(
        nowMillis: Long = System.currentTimeMillis(),
        elapsedNanos: Long = SystemClock.elapsedRealtimeNanos(),
    ): Long {
        val cfg = config ?: return 1000L
        val night = cfg.nightIdleEnabled && isNight(nowMillis)
        val baseRate = cfg.updateRateHz.coerceIn(0.05, 5.0)
        val effectiveRate = if (night && phase == Phase.STATIONARY) baseRate * 0.15 else baseRate
        val dt = 1.0 / effectiveRate

        when (phase) {
            Phase.GPS_LOST -> {
                val remainingMs = gpsLostUntilElapsedMs - SystemClock.elapsedRealtime()
                if (remainingMs <= 0) {
                    settlePending()
                } else {
                    controller.publishState(EngineState.GpsLost((remainingMs / 1000).toInt()))
                    // Poll the blackout at ~1 Hz regardless of the configured rate.
                    return 1000L
                }
            }

            Phase.TRAVELING -> {
                val t = travel
                if (t == null) {
                    settlePending()
                } else {
                    val kin = t.tick(dt)
                    emit(kin, nowMillis, elapsedNanos)
                    controller.publishState(EngineState.Traveling(t.remaining()))
                    if (t.finished) settlePending()
                    return delayMs(effectiveRate)
                }
            }

            Phase.STATIONARY -> {
                val s = stationary ?: StationaryWalk(cfg.target, rng).also { stationary = it }
                s.calm = night
                val kin = s.tick(dt)
                emit(kin, nowMillis, elapsedNanos)
                controller.publishState(EngineState.Running(if (night) "Overnight idle" else "Stationary"))
                return delayMs(effectiveRate)
            }

            Phase.ROUTE -> {
                val r = route
                if (r == null) {
                    phase = Phase.STATIONARY
                } else {
                    val kin = r.tick(dt)
                    emit(kin, nowMillis, elapsedNanos)
                    controller.publishState(EngineState.Running("Route"))
                    if (r.finished) {
                        // One-shot route done — hold position, drifting like a parked phone.
                        phase = Phase.STATIONARY
                        stationary = StationaryWalk(kin.point, rng)
                    }
                    return delayMs(effectiveRate)
                }
            }
        }
        return delayMs(effectiveRate)
    }

    private fun settlePending() {
        val next = pendingAfter
        pendingAfter = null
        if (next != null) initialiseFrom(next)
    }

    private fun emit(
        kin: com.mockpilot.model.Kinematics,
        nowMillis: Long,
        elapsedNanos: Long,
    ) {
        lastPoint = kin.point
        // Satellite count drifts slowly and dips a little while moving fast.
        satBase += rng.gaussian() * 0.2
        satBase = satBase.coerceIn(6.0, 18.0)
        val moveMalus = if (kin.moving) rng.range(0.0, 2.0) else 0.0
        val satellites = (satBase - moveMalus).roundToInt().coerceIn(5, 19)

        val location = LocationBuilder.build(
            provider = android.location.LocationManager.GPS_PROVIDER,
            kin = kin,
            satellites = satellites,
            nowMillis = nowMillis,
            elapsedNanos = elapsedNanos,
        )
        providers.push(location)
        controller.publishSample(LocationBuilder.toSample(kin, satellites, nowMillis, elapsedNanos))
    }

    private fun delayMs(rateHz: Double): Long = (1000.0 / rateHz).roundToLong().coerceIn(50L, 60_000L)

    private fun isNight(nowMillis: Long): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return hour >= NIGHT_START_HOUR || hour < NIGHT_END_HOUR
    }

    companion object {
        private const val NIGHT_START_HOUR = 23
        private const val NIGHT_END_HOUR = 6
    }
}
