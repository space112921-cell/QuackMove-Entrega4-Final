package com.quackmove.app.feature.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView

// =====================================
// VISTA PREVIA DEL MAPA
// =====================================
//
// Antes se cargaba un WebView desde http://127.0.0.1:8765/map,
// que solo existía si chargers_proxy.py corría en la computadora
// del equipo. En un teléfono instalado desde App Distribution
// mostraba un error 404 (hallazgo 3 del cross-review).
//
// Ahora se usa MapLibre con los mosaicos públicos de OpenFreeMap,
// igual que el mapa principal: no requiere servidor ni API key.

private const val PREVIEW_MAP_STYLE =
    "https://tiles.openfreemap.org/styles/bright"

private val PREVIEW_CENTER = LatLng(
    14.6349,
    -90.5069
)

private const val PREVIEW_ZOOM = 12.0

@Composable
fun RouteMapPreview(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val mapView = remember {

        MapLibre.getInstance(context.applicationContext)

        MapView(context).apply {

            onCreate(null)

            getMapAsync { map ->

                // Solo es una vista previa dentro de una pantalla
                // con scroll: se desactivan los gestos del mapa.
                map.uiSettings.setAllGesturesEnabled(false)

                map.cameraPosition = CameraPosition.Builder()
                    .target(PREVIEW_CENTER)
                    .zoom(PREVIEW_ZOOM)
                    .build()

                map.setStyle(PREVIEW_MAP_STYLE)
            }
        }
    }

    // El MapView necesita seguir el ciclo de vida de la pantalla.
    DisposableEffect(lifecycle, mapView) {

        var started = false
        var resumed = false

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    mapView.onStart()
                    started = true
                }

                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                    resumed = true
                }

                Lifecycle.Event.ON_PAUSE -> {
                    mapView.onPause()
                    resumed = false
                }

                Lifecycle.Event.ON_STOP -> {
                    mapView.onStop()
                    started = false
                }

                else -> Unit
            }
        }

        lifecycle.addObserver(observer)

        onDispose {
            lifecycle.removeObserver(observer)

            if (resumed) mapView.onPause()
            if (started) mapView.onStop()

            mapView.onDestroy()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { mapView }
    )
}
