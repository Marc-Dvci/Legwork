package app.legwork.ui.components

import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.legwork.core.Format
import app.legwork.data.Mission
import app.legwork.location.Fix
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import kotlin.math.ln

private const val SRC_MISSIONS = "missions"
private const val SRC_ME = "me"
private const val SRC_TARGET = "target"
private const val LAYER_PINS = "mission-pins"
private const val LAYER_LABELS = "mission-labels"

/**
 * A MapLibre map with reward pins for every mission, a dot for the user and an optional
 * radius ring around a target mission. Tiles come from OpenFreeMap, so no map API key ships in the APK.
 */
@Composable
fun MissionMap(
    styleUrl: String,
    missions: List<Mission>,
    fix: Fix?,
    target: Mission? = null,
    modifier: Modifier = Modifier,
    followUser: Boolean = false,
    onMissionTap: (String) -> Unit = {},
    onMapTap: ((Double, Double) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(context) }
    val holder = remember { MapHolder() }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); mapView.onDestroy() }
    }

    AndroidView(factory = {
        mapView.apply {
            getMapAsync { map ->
                map.uiSettings.isCompassEnabled = false
                map.uiSettings.isLogoEnabled = false
                map.uiSettings.isAttributionEnabled = true
                map.uiSettings.setAttributionMargins(16, 0, 0, 16)
                map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
                    holder.install(map, style)
                    holder.update(missions, fix, target, first = true)
                    map.addOnMapClickListener { latLng ->
                        val p = map.projection.toScreenLocation(latLng)
                        val rect = RectF(p.x - 28f, p.y - 28f, p.x + 28f, p.y + 28f)
                        val hits = map.queryRenderedFeatures(rect, LAYER_PINS)
                        if (hits.isNotEmpty()) {
                            hits.first().getStringProperty("address")?.let(onMissionTap); true
                        } else {
                            onMapTap?.invoke(latLng.latitude, latLng.longitude); onMapTap != null
                        }
                    }
                }
            }
        }
    }, modifier = modifier)

    LaunchedEffect(missions, fix, target, followUser) {
        holder.update(missions, fix, target, first = false, follow = followUser)
    }
}

private class MapHolder {
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var centered = false

    private var lastTarget: Mission? = null

    fun install(map: MapLibreMap, style: Style) {
        this.map = map; this.style = style
        map.addOnCameraIdleListener { drawRing() }
        style.addSource(GeoJsonSource(SRC_TARGET))
        style.addSource(GeoJsonSource(SRC_MISSIONS))
        style.addSource(GeoJsonSource(SRC_ME))
        style.addLayer(
            CircleLayer("target-ring", SRC_TARGET).withProperties(
                PropertyFactory.circleRadius(Expression.get("px")),
                PropertyFactory.circleColor("#FF5A1F"),
                PropertyFactory.circleOpacity(0.12f),
                PropertyFactory.circleStrokeColor("#FF5A1F"),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleStrokeOpacity(0.7f),
            )
        )
        style.addLayer(
            CircleLayer("mission-halo", SRC_MISSIONS).withProperties(
                PropertyFactory.circleRadius(22f),
                PropertyFactory.circleColor("#0E9F6E"),
                PropertyFactory.circleOpacity(0.18f),
            )
        )
        style.addLayer(
            CircleLayer(LAYER_PINS, SRC_MISSIONS).withProperties(
                PropertyFactory.circleRadius(16f),
                PropertyFactory.circleColor(
                    Expression.switchCase(
                        Expression.eq(Expression.get("target"), true), Expression.literal("#FF5A1F"),
                        Expression.literal("#0E9F6E"),
                    )
                ),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(2.5f),
            )
        )
        style.addLayer(
            SymbolLayer(LAYER_LABELS, SRC_MISSIONS).withProperties(
                PropertyFactory.textField(Expression.get("label")),
                PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
                PropertyFactory.textSize(11.5f),
                PropertyFactory.textColor("#FFFFFF"),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textIgnorePlacement(true),
            )
        )
        style.addLayer(
            CircleLayer("me-halo", SRC_ME).withProperties(
                PropertyFactory.circleRadius(18f),
                PropertyFactory.circleColor("#2563EB"),
                PropertyFactory.circleOpacity(0.18f),
            )
        )
        style.addLayer(
            CircleLayer("me-dot", SRC_ME).withProperties(
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleColor("#2563EB"),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(2.5f),
            )
        )
    }

    /** Radius ring drawn in pixels at the current zoom; redrawn whenever the camera settles. */
    private fun drawRing() {
        val map = map ?: return
        val style = style ?: return
        val target = lastTarget
        if (target == null) {
            style.getSourceAs<GeoJsonSource>(SRC_TARGET)?.setGeoJson(FeatureCollection.fromFeatures(emptyList())); return
        }
        val metersPerPx = map.projection.getMetersPerPixelAtLatitude(target.lat)
        val px = (target.radiusM / metersPerPx).toFloat().coerceIn(6f, 600f)
        val f = Feature.fromGeometry(Point.fromLngLat(target.lon, target.lat)).apply { addNumberProperty("px", px) }
        style.getSourceAs<GeoJsonSource>(SRC_TARGET)?.setGeoJson(FeatureCollection.fromFeatures(listOf(f)))
    }

    fun update(missions: List<Mission>, fix: Fix?, target: Mission?, first: Boolean, follow: Boolean = false) {
        val map = map ?: return
        val style = style ?: return
        val features = missions.map { m ->
            Feature.fromGeometry(Point.fromLngLat(m.lon, m.lat)).apply {
                addStringProperty("address", m.address)
                addStringProperty("label", Format.usdcShort(m.reward))
                addBooleanProperty("target", target?.address == m.address)
            }
        }
        style.getSourceAs<GeoJsonSource>(SRC_MISSIONS)?.setGeoJson(FeatureCollection.fromFeatures(features))
        style.getSourceAs<GeoJsonSource>(SRC_ME)?.setGeoJson(
            FeatureCollection.fromFeatures(
                if (fix == null) emptyList() else listOf(Feature.fromGeometry(Point.fromLngLat(fix.lon, fix.lat)))
            )
        )
        lastTarget = target
        drawRing()

        if (target != null && fix != null && (first || !centered)) {
            val d = app.legwork.core.Geo.distanceM(fix.lat, fix.lon, target.lat, target.lon)
            val mid = LatLng((fix.lat + target.lat) / 2, (fix.lon + target.lon) / 2)
            val zoom = (17.0 - ln(d.coerceAtLeast(60.0) / 140.0) / ln(2.0)).coerceIn(11.0, 17.0)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(mid, zoom))
            centered = true
        } else if (target != null && (first || !centered)) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(target.lat, target.lon), 16.0)); centered = true
        } else if (!centered && fix != null) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(fix.lat, fix.lon), 14.5)); centered = true
        } else if (!centered && missions.isNotEmpty()) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(missions[0].lat, missions[0].lon), 13.5)); centered = true
        } else if (follow && fix != null) {
            map.easeCamera(CameraUpdateFactory.newLatLng(LatLng(fix.lat, fix.lon)), 500)
        }
    }
}
