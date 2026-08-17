package com.mockpilot.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.mockpilot.model.GeoPoint
import kotlinx.serialization.json.Json

/** A named point the user can activate with one tap. */
@Entity(tableName = "saved_places")
data class SavedPlace(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toGeoPoint() = GeoPoint(latitude, longitude, altitude)
}

/** A saved multi-waypoint route with its playback settings. */
@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val waypoints: List<GeoPoint>,
    val targetSpeedMps: Double = 8.0,
    val loop: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * A scheduled "be at X during a time window on certain days" entry. Chaining several of these
 * (home overnight → office 9–5 → gym → home) reproduces a believable daily pattern.
 */
@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val placeName: String,
    val latitude: Double,
    val longitude: Double,
    /** Minutes since local midnight. */
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    /** Bit 0 = Monday … bit 6 = Sunday. */
    val daysMask: Int,
    val enabled: Boolean = true,
) {
    fun toGeoPoint() = GeoPoint(latitude, longitude)
}

/** Room type converter for the waypoint list (stored as a JSON blob). */
class Converters {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @TypeConverter
    fun fromWaypoints(points: List<GeoPoint>): String =
        json.encodeToString(kotlinx.serialization.builtins.ListSerializer(GeoPoint.serializer()), points)

    @TypeConverter
    fun toWaypoints(raw: String): List<GeoPoint> =
        runCatching {
            json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(GeoPoint.serializer()), raw)
        }.getOrDefault(emptyList())
}
