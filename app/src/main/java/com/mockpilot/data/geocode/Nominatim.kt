package com.mockpilot.data.geocode

import com.mockpilot.model.GeoPoint
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/** Raw Nominatim search result. Coordinates arrive as strings. */
@Serializable
data class NominatimPlace(
    @SerialName("display_name") val displayName: String = "",
    val lat: String = "0",
    val lon: String = "0",
    val type: String? = null,
    @SerialName("class") val category: String? = null,
    val importance: Double? = null,
) {
    fun toResult(): GeocodeResult? {
        val la = lat.toDoubleOrNull() ?: return null
        val lo = lon.toDoubleOrNull() ?: return null
        return GeocodeResult(displayName, GeoPoint(la, lo))
    }
}

/** UI-friendly geocoding result. */
data class GeocodeResult(
    val label: String,
    val point: GeoPoint,
)

/**
 * Nominatim (OpenStreetMap) geocoding — no API key required. Usage policy asks for a descriptive
 * User-Agent and modest request rates; both are honoured by [GeocodingRepository] and the OkHttp
 * client that backs it.
 */
interface NominatimApi {
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("format") format: String = "jsonv2",
        @Query("limit") limit: Int = 8,
        @Query("addressdetails") addressDetails: Int = 0,
    ): List<NominatimPlace>

    @GET("reverse")
    suspend fun reverse(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("format") format: String = "jsonv2",
    ): NominatimPlace
}
