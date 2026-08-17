package com.mockpilot.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.mockpilot.model.GeoPoint
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

data class MapPin(val point: GeoPoint, val label: String, val numbered: Int? = null)

/**
 * osmdroid [MapView] hosted in Compose. Handles the View lifecycle, long-press-to-drop-pin, and
 * rebuilding the marker/polyline overlays whenever inputs change. Uses the standard MAPNIK OSM tile
 * source — no API key.
 */
@Composable
fun OsmMap(
    modifier: Modifier = Modifier,
    center: GeoPoint? = null,
    zoom: Double = 15.0,
    pins: List<MapPin> = emptyList(),
    path: List<GeoPoint> = emptyList(),
    followPoint: GeoPoint? = null,
    onLongPress: (GeoPoint) -> Unit = {},
    onTap: (GeoPoint) -> Unit = {},
) {
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                setUseDataConnection(true)
                controller.setZoom(zoom)
                center?.let { controller.setCenter(OsmGeoPoint(it.latitude, it.longitude)) }

                val eventsReceiver = object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: OsmGeoPoint?): Boolean {
                        p?.let { onTap(GeoPoint(it.latitude, it.longitude)) }
                        return true
                    }

                    override fun longPressHelper(p: OsmGeoPoint?): Boolean {
                        p?.let { onLongPress(GeoPoint(it.latitude, it.longitude)) }
                        return true
                    }
                }
                overlays.add(MapEventsOverlay(eventsReceiver))

                // Bind osmdroid's onResume/onPause to the composition's lifecycle.
                lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
                    override fun onResume(owner: LifecycleOwner) = onResume()
                    override fun onPause(owner: LifecycleOwner) = onPause()
                })
            }
        },
        update = { view ->
            // Rebuild everything except the (first) MapEventsOverlay.
            val events = view.overlays.firstOrNull { it is MapEventsOverlay }
            view.overlays.clear()
            events?.let { view.overlays.add(it) }

            if (path.size >= 2) {
                val line = Polyline(view).apply {
                    setPoints(path.map { OsmGeoPoint(it.latitude, it.longitude) })
                    outlinePaint.strokeWidth = 8f
                }
                view.overlays.add(line)
            }

            pins.forEach { pin ->
                val marker = Marker(view).apply {
                    position = OsmGeoPoint(pin.point.latitude, pin.point.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = pin.label
                    pin.numbered?.let { subDescription = "Waypoint $it" }
                }
                view.overlays.add(marker)
            }

            center?.let {
                view.controller.setCenter(OsmGeoPoint(it.latitude, it.longitude))
            }
            followPoint?.let {
                view.controller.animateTo(OsmGeoPoint(it.latitude, it.longitude))
            }
            view.invalidate()
        },
    )
}
