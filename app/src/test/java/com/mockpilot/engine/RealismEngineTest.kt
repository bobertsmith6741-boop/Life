package com.mockpilot.engine

import com.mockpilot.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealismEngineTest {

    @Test
    fun haversine_distance_is_reasonable() {
        // ~0.001 degree latitude ≈ 111 m.
        val a = GeoPoint(40.0, -73.0)
        val b = GeoPoint(40.001, -73.0)
        val d = a.distanceTo(b)
        assertTrue("expected ~111m, got $d", d in 105.0..117.0)
    }

    @Test
    fun destination_roundtrips_through_bearing_and_distance() {
        val start = GeoPoint(51.5, -0.12)
        val moved = start.destination(500.0, 90.0)
        assertEquals(500.0, start.distanceTo(moved), 2.0)
        assertEquals(90.0, start.bearingTo(moved), 1.0)
    }

    @Test
    fun stationary_walk_stays_within_leash() {
        val center = GeoPoint(37.42, -122.08)
        val walk = StationaryWalk(center, Rng(42), leashMeters = 8.0)
        var maxRadius = 0.0
        repeat(2000) {
            val kin = walk.tick(1.0)
            val r = center.distanceTo(kin.point)
            if (r > maxRadius) maxRadius = r
            assertTrue("accuracy in 5..20", kin.accuracyM in 5f..20f)
        }
        // Leash is 8 m; allow a little slack for the destination projection.
        assertTrue("wander should stay leashed, was $maxRadius", maxRadius <= 9.0)
    }

    @Test
    fun route_runner_advances_and_finishes() {
        val wps = listOf(
            GeoPoint(37.4000, -122.0800),
            GeoPoint(37.4020, -122.0800),
            GeoPoint(37.4020, -122.0770),
        )
        val runner = RouteRunner(wps, Rng(7), targetSpeed = 10.0, loop = false)
        assertTrue(runner.totalLength > 300.0)

        var moved = false
        var guard = 0
        while (!runner.finished && guard < 10_000) {
            val kin = runner.tick(1.0)
            if (kin.speedMps > 0.5f) moved = true
            guard++
        }
        assertTrue("route should move at some point", moved)
        assertTrue("route should finish", runner.finished)
    }

    @Test
    fun route_runner_speed_never_exceeds_cap_much() {
        val wps = listOf(GeoPoint(37.40, -122.08), GeoPoint(37.44, -122.08))
        val runner = RouteRunner(wps, Rng(1), targetSpeed = 8.0, loop = false)
        var guard = 0
        while (!runner.finished && guard < 10_000) {
            val kin = runner.tick(1.0)
            assertTrue("speed within cap+slack", kin.speedMps <= 10f)
            guard++
        }
    }
}
