package com.mockpilot.data.geocode

import com.mockpilot.model.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Thin coroutine wrapper over [NominatimApi] with error swallowing suited to a search box. */
@Singleton
class GeocodingRepository @Inject constructor(
    private val api: NominatimApi,
) {
    suspend fun search(query: String): List<GeocodeResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching { api.search(query).mapNotNull { it.toResult() } }
            .getOrDefault(emptyList())
    }

    suspend fun reverse(point: GeoPoint): String? = withContext(Dispatchers.IO) {
        runCatching { api.reverse(point.latitude, point.longitude).displayName }
            .getOrNull()
    }
}
