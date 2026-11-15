
package com.quackmove.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Color as AndroidColor
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

import androidx.compose.ui.unit.dp

import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

import com.quackmove.app.data.local.QuackMoveDatabase

import com.quackmove.app.data.repository.DemoDestinationsRepository
import com.quackmove.app.data.repository.FirestoreTripRepository
import com.quackmove.app.data.repository.GoogleChargingRepository
import com.quackmove.app.data.repository.GoogleRoutesRepository
import com.quackmove.app.data.repository.RecentTripsStore
import com.quackmove.app.data.repository.TripStorageRepository
import com.quackmove.app.data.repository.RoutePoint
import com.quackmove.app.data.repository.RouteSummary
import com.quackmove.app.data.repository.SmartChargingPlan
import com.quackmove.app.data.repository.SmartChargingRepository
import com.quackmove.app.data.repository.SmartChargingStop

import com.quackmove.app.domain.SavedTrip

import com.quackmove.app.feature.home.HomeViewModel
import com.quackmove.app.feature.home.HomeViewModelFactory
import com.quackmove.app.feature.home.MapScreen
import com.quackmove.app.feature.home.RoutePlanResult
import com.quackmove.app.feature.home.TripViewModel
import com.quackmove.app.feature.home.TripViewModelFactory
import com.quackmove.app.feature.home.SmartChargingViewModel
import com.quackmove.app.feature.home.SmartChargingViewModelFactory

import com.quackmove.app.feature.routes.RoutePolylineDecoder

import com.quackmove.app.navigation.QuackMoveNavigation
import com.quackmove.app.ui.theme.QuackMoveTheme

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import java.util.Locale

import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

// =====================================
// ESTILOS DEL MAPA
// =====================================

private const val DARK_MAP_STYLE =
    "https://tiles.openfreemap.org/styles/dark"

private const val LIGHT_MAP_STYLE =
    "https://tiles.openfreemap.org/styles/bright"

private val INITIAL_MAP_CENTER = LatLng(
    14.5269,
    -90.5877
)

// =====================================
// MAIN ACTIVITY
// =====================================

class MainActivity : ComponentActivity() {

    // =====================================
    // HOME VIEWMODEL
    // =====================================

    private val homeViewModel: HomeViewModel by viewModels {
        HomeViewModelFactory(
            destinationsRepository = DemoDestinationsRepository,
            routesRepository = GoogleRoutesRepository
        )
    }

    // =====================================
    // SMART CHARGING VIEWMODEL
    // =====================================

    private val smartChargingViewModel:
            SmartChargingViewModel by viewModels {

        SmartChargingViewModelFactory(
            repository = SmartChargingRepository(

                fetchRoute = { origin, destination ->

                    GoogleRoutesRepository.fetchRoute(
                        origin,
                        destination
                    )
                },

                fetchStations = { latitude, longitude ->

                    GoogleChargingRepository.fetchStations(
                        latitude,
                        longitude
                    )
                }
            )
        )
    }

    // =====================================
    // PROPUESTA DE PARADAS
    // =====================================

    private val pendingSmartPlan =
        mutableStateOf<SmartChargingPlan?>(null)

    private var pendingSmartCallback:
            ((Result<RoutePlanResult>) -> Unit)? = null

    private val acceptedStops =
        mutableListOf<SmartChargingStop>()

    // =====================================
    // MAPLIBRE
    // =====================================

    private lateinit var mapView: MapView

    private var mapInstance: MapLibreMap? = null

    private var activeRoutePolyline: Polyline? = null

    private val routeMarkers =
        mutableListOf<Marker>()

    private var lastCalculatedRoute: RouteSummary? = null

    private var routeJob: Job? = null

    // =====================================
    // CARGADORES CERCANOS
    // =====================================

    private val chargingMarkers =
        mutableListOf<Marker>()

    private var chargersVisible = false

    private var chargersLoading = false

    private var chargersJob: Job? = null

    // =====================================
    // TRIP VIEWMODEL
    // =====================================

    private val tripViewModel: TripViewModel by viewModels {

        TripViewModelFactory(
            TripStorageRepository(
                localDao = QuackMoveDatabase
                    .getDatabase(applicationContext)
                    .localTripDao(),

                remoteRepository =
                    FirestoreTripRepository(
                        auth = FirebaseAuth.getInstance(),
                        firestore =
                            FirebaseFirestore.getInstance()
                    )
            )
        )
    }

    // =====================================
    // GPS
    // =====================================

    private lateinit var locationManager: LocationManager

    private val locationHandler =
        Handler(Looper.getMainLooper())

    private var locationListener: LocationListener? = null

    private var locationTimeout: Runnable? = null

    private var pendingLocationAction:
            ((Double, Double) -> Unit)? = null

    private var pendingLocationError:
            ((String) -> Unit)? = null

    // =====================================
    // PERMISOS DE UBICACION
    // =====================================

    private val locationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val granted =
                permissions[
                    Manifest.permission.ACCESS_FINE_LOCATION
                ] == true ||
                        permissions[
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ] == true

            if (granted) {

                requestDeviceLocation()

            } else {

                failLocationRequest(
                    "Permite el acceso a la ubicación."
                )
            }
        }

    // =====================================
    // ON CREATE
    // =====================================

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        locationManager = getSystemService(
            Context.LOCATION_SERVICE
        ) as LocationManager

        MapLibre.getInstance(this)

        mapView = MapView(this)

        mapView.onCreate(savedInstanceState)

        mapView.getMapAsync { map ->

            mapInstance = map

            map.setStyle(DARK_MAP_STYLE)

            map.cameraPosition =
                CameraPosition.Builder()
                    .target(INITIAL_MAP_CENTER)
                    .zoom(15.0)
                    .build()

            map.uiSettings.isScrollGesturesEnabled = true
            map.uiSettings.isZoomGesturesEnabled = true
            map.uiSettings.isRotateGesturesEnabled = true
            map.uiSettings.isCompassEnabled = false

            map.setAllowConcurrentMultipleOpenInfoWindows(
                false
            )

            map.setOnMarkerClickListener { marker ->

                map.deselectMarkers()
                map.selectMarker(marker)

                true
            }
        }

        // =====================================
        // COMPOSE
        // =====================================

        setContent {

            QuackMoveTheme {

                val navController =
                    rememberNavController()

                val recentDestinations = remember {

                    mutableStateListOf<RoutePoint>().apply {

                        addAll(
                            RecentTripsStore
                                .loadRecentDestinations(
                                    this@MainActivity
                                )
                        )
                    }
                }

                QuackMoveNavigation(
                    navController = navController,

                    mapScreen = {
                            onOpenRegistration,
                            onOpenChargers,
                            onOpenRoute ->

                        MapScreen(
                            mapView = mapView,

                            // =========================
                            // ESTILO DEL MAPA
                            // =========================

                            onMapStyleChange = { dark ->

                                val style = if (dark) {
                                    DARK_MAP_STYLE
                                } else {
                                    LIGHT_MAP_STYLE
                                }

                                mapInstance?.setStyle(style) {

                                    lastCalculatedRoute?.let { route ->

                                        drawRouteOnMap(
                                            route = route,
                                            adjustCamera = false
                                        )
                                    }
                                }
                            },

                            // =========================
                            // CENTRAR EN GPS
                            // =========================

                            onReturnToCenter = {

                                centerMapOnDeviceLocation()
                            },

                            // =========================
                            // REGISTRO
                            // =========================

                            onOpenRegistration =
                                onOpenRegistration,

                            // =========================
                            // PANTALLA DE CARGADORES
                            // =========================

                            onOpenChargers = {

                                openWithDeviceLocation(
                                    action = {
                                            latitude, longitude ->

                                        onOpenChargers(
                                            latitude,
                                            longitude
                                        )
                                    }
                                )
                            },

                            // =========================
                            // CARGADORES EN EL MAPA
                            // =========================

                            onShowChargersOnMap = {

                                toggleChargingMarkers()
                            },

                            // =========================
                            // NAVEGACION
                            // =========================

                            onOpenRoute = onOpenRoute,

                            // =========================
                            // RECIENTES
                            // =========================

                            recentDestinations =
                                recentDestinations,

                            // =========================
                            // CALCULO INTELIGENTE
                            // =========================

                            onCalculateRoute = {
                                    origin,
                                    destination,
                                    battery,
                                    onResult ->

                                calculateElectricTrip(
                                    selectedOrigin = origin,
                                    destination = destination,
                                    departureBattery = battery,
                                    onResult = onResult
                                )
                            },

                            // =========================
                            // INICIAR CONDUCCION
                            // =========================

                            onContinueTrip = {

                                enterDrivingView()
                            },

                            // =========================
                            // FINALIZAR VIAJE
                            // =========================

                            onFinishTrip = {
                                    plan,
                                    destination,
                                    battery,
                                    originName ->

                                // ---------------------
                                // RECENTES
                                // ---------------------

                                RecentTripsStore.saveTrip(
                                    context = this@MainActivity,
                                    destination = destination,
                                    originName = originName,

                                    distanceKm =
                                        plan.route.distanceKm,

                                    drivingMinutes =
                                        plan.route.drivingMinutes,

                                    departureBatteryPercent =
                                        battery,

                                    arrivalBatteryPercent =
                                        plan.battery
                                            .estimatedArrivalPercent
                                )

                                recentDestinations.clear()

                                recentDestinations.addAll(
                                    RecentTripsStore
                                        .loadRecentDestinations(
                                            this@MainActivity
                                        )
                                )

                                // ---------------------
                                // ROOM Y FIREBASE
                                // ---------------------

                                tripViewModel.saveTrip(
                                    SavedTrip(
                                        id = "",

                                        origin = originName,

                                        destination =
                                            destination.name,

                                        distanceKm =
                                            plan.route.distanceKm,

                                        drivingMinutes =
                                            plan.route.drivingMinutes,

                                        departureBatteryPercent =
                                            battery.toDouble()
                                    )
                                )

                                finishCurrentTrip()
                            }
                        )

                        // =========================
                        // DIALOGO DE PARADAS
                        // =========================

                        pendingSmartPlan.value?.let { suggestion ->

                            AlertDialog(
                                onDismissRequest = {

                                    rejectSmartChargingPlan()
                                },

                                title = {

                                    Text(
                                        "Paradas de carga recomendadas"
                                    )
                                },

                                text = {

                                    Column(
                                        verticalArrangement =
                                            Arrangement.spacedBy(8.dp)
                                    ) {

                                        Text(
                                            "La batería no alcanza " +
                                                    "con 10% de reserva. " +
                                                    "Encontramos " +
                                                    "${suggestion.stops.size} " +
                                                    "parada(s) verificadas " +
                                                    "con Google Routes."
                                        )

                                        suggestion.stops.forEachIndexed {
                                                index, stop ->

                                            Text(
                                                "${index + 1}. " +
                                                        "${stop.point.name} " +
                                                        "(llegas con " +
                                                        "${formatPercent(stop.estimatedArrivalPercent)}%). " +
                                                        "Recargar hasta 80%."
                                            )
                                        }

                                        Spacer(
                                            modifier =
                                                androidx.compose.ui.Modifier
                                                    .height(4.dp)
                                        )

                                        Text(
                                            "Distancia: " +
                                                    "${formatDistance(suggestion.route.distanceKm)} km. " +
                                                    "El tiempo no incluye " +
                                                    "la recarga. " +
                                                    "Confirma conectores, horario " +
                                                    "y disponibilidad antes de salir."
                                        )
                                    }
                                },

                                confirmButton = {

                                    TextButton(
                                        onClick = {

                                            acceptSmartChargingPlan()
                                        }
                                    ) {

                                        Text("Agregar a mi ruta")
                                    }
                                },

                                dismissButton = {

                                    TextButton(
                                        onClick = {

                                            rejectSmartChargingPlan()
                                        }
                                    ) {

                                        Text("Cancelar")
                                    }
                                }
                            )
                        }
                    }
                )
            }
        }
    }

    // =====================================
    // CENTRAR MAPA EN GPS
    // =====================================

    private fun centerMapOnDeviceLocation() {

        openWithDeviceLocation(
            action = { latitude, longitude ->

                mapInstance?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(
                            latitude,
                            longitude
                        ),
                        16.0
                    )
                )
            }
        )
    }

    // =====================================
    // CALCULAR RUTA INTELIGENTE
    // =====================================

    private fun calculateElectricTrip(
        selectedOrigin: RoutePoint?,
        destination: RoutePoint,
        departureBattery: Float,
        onResult: (Result<RoutePlanResult>) -> Unit
    ) {

        routeJob?.cancel()
        routeJob = null

        smartChargingViewModel.clear()

        pendingSmartPlan.value = null
        pendingSmartCallback = null
        acceptedStops.clear()

        if (!destination.isValid()) {

            onResult(
                Result.failure(
                    IllegalArgumentException(
                        "Destino sin coordenadas válidas."
                    )
                )
            )

            return
        }

        fun calculateFrom(
            origin: RoutePoint
        ) {

            if (!origin.isValid()) {

                onResult(
                    Result.failure(
                        IllegalArgumentException(
                            "Origen sin coordenadas válidas."
                        )
                    )
                )

                return
            }

            // =========================
            // CONSULTAS EN VIEWMODEL
            // =========================

            smartChargingViewModel.calculate(
                origin = origin,
                destination = destination,
                departurePercent = departureBattery
            )

            routeJob = lifecycleScope.launch {

                try {

                    val state =
                        smartChargingViewModel.uiState.first {
                                current ->

                            !current.isLoading &&
                                    (
                                            current.plan != null ||
                                                    current.errorMessage != null
                                            )
                        }

                    val recommendation = state.plan

                    if (recommendation == null) {

                        // =========================
                        // NO HAY RUTA SEGURA
                        // =========================

                        onResult(
                            Result.failure(
                                IllegalStateException(
                                    state.errorMessage
                                        ?: "No se pudo planificar el viaje."
                                )
                            )
                        )

                    } else if (
                        recommendation.stops.isEmpty()
                    ) {

                        // =========================
                        // RUTA DIRECTA
                        // =========================

                        drawRouteOnMap(
                            recommendation.route
                        )

                        onResult(
                            Result.success(
                                RoutePlanResult(
                                    route =
                                        recommendation.route,
                                    battery =
                                        recommendation.battery
                                )
                            )
                        )

                    } else {

                        // =========================
                        // PROPONER PARADAS
                        // =========================

                        pendingSmartCallback = onResult

                        pendingSmartPlan.value =
                            recommendation
                    }

                } catch (
                    cancelled: CancellationException
                ) {

                    throw cancelled

                } catch (error: Exception) {

                    onResult(
                        Result.failure(error)
                    )
                }
            }
        }

        // =====================================
        // ORIGEN MANUAL O GPS
        // =====================================

        if (selectedOrigin != null) {

            calculateFrom(selectedOrigin)

        } else {

            openWithDeviceLocation(
                action = { latitude, longitude ->

                    calculateFrom(
                        RoutePoint(
                            name = "Tu ubicación",
                            latitude = latitude,
                            longitude = longitude
                        )
                    )
                },

                onError = { message ->

                    onResult(
                        Result.failure(
                            IllegalStateException(message)
                        )
                    )
                }
            )
        }
    }

    // =====================================
    // ACEPTAR PARADAS RECOMENDADAS
    // =====================================

    private fun acceptSmartChargingPlan() {

        val proposed =
            pendingSmartPlan.value ?: return

        val callback =
            pendingSmartCallback ?: return

        pendingSmartPlan.value = null
        pendingSmartCallback = null

        acceptedStops.clear()
        acceptedStops.addAll(proposed.stops)

        try {

            drawRouteOnMap(proposed.route)

            callback(
                Result.success(
                    RoutePlanResult(
                        route = proposed.route,
                        battery = proposed.battery
                    )
                )
            )

        } catch (error: Exception) {

            acceptedStops.clear()

            callback(
                Result.failure(error)
            )
        }
    }

    // =====================================
    // RECHAZAR PARADAS
    // =====================================

    private fun rejectSmartChargingPlan() {

        val callback =
            pendingSmartCallback

        pendingSmartPlan.value = null
        pendingSmartCallback = null

        acceptedStops.clear()

        callback?.invoke(
            Result.failure(
                IllegalStateException(
                    "No se agregó la parada. " +
                            "Modifica el viaje o carga " +
                            "el vehículo antes de continuar."
                )
            )
        )
    }

    // =====================================
    // FORMATO DE METRICAS
    // =====================================

    private fun formatPercent(
        value: Double
    ): String {

        return String.format(
            Locale.US,
            "%.0f",
            value
        )
    }

    private fun formatDistance(
        value: Double
    ): String {

        return String.format(
            Locale.US,
            "%.1f",
            value
        )
    }

    // =====================================
    // DIBUJAR RUTA MORADA
    // =====================================

    @Suppress("DEPRECATION")
    private fun drawRouteOnMap(
        route: RouteSummary,
        adjustCamera: Boolean = true
    ) {

        val map = mapInstance
            ?: throw IllegalStateException(
                "El mapa no está disponible."
            )

        val points = RoutePolylineDecoder.decode(
            route.encodedPolyline
        )

        if (points.size < 2) {

            throw IllegalStateException(
                "La ruta no tiene suficientes puntos."
            )
        }

        clearRoutePolyline()
        clearRouteMarkers()

        activeRoutePolyline = map.addPolyline(
            PolylineOptions()
                .addAll(points)
                .color(
                    AndroidColor.rgb(
                        128,
                        102,
                        245
                    )
                )
                .width(7f)
        )

        // =================================
        // ORIGEN AZUL
        // =================================

        addRouteMarker(
            points.first(),
            "Origen",
            AndroidColor.rgb(
                38,
                149,
                255
            )
        )

        // =================================
        // PARADAS VERDES
        // =================================

        acceptedStops.forEachIndexed { index, stop ->

            addRouteMarker(
                position = LatLng(
                    stop.point.latitude,
                    stop.point.longitude
                ),

                title =
                    "Parada ${index + 1}: ${stop.point.name}",

                color = AndroidColor.rgb(
                    67,
                    230,
                    179
                )
            )
        }

        // =================================
        // DESTINO MORADO
        // =================================

        addRouteMarker(
            points.last(),
            "Destino",
            AndroidColor.rgb(
                128,
                102,
                245
            )
        )

        lastCalculatedRoute = route

        if (adjustCamera) {

            showCompleteRoute()
        }
    }

    // =====================================
    // ICONOS ORIGEN / DESTINO / PARADA
    // =====================================

    private fun createRouteMarkerBitmap(
        fillColor: Int
    ): Bitmap {

        val bitmap = Bitmap.createBitmap(
            72,
            72,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)

        val paint = Paint(
            Paint.ANTI_ALIAS_FLAG
        )

        paint.color = AndroidColor.WHITE

        canvas.drawCircle(
            36f,
            36f,
            25f,
            paint
        )

        paint.color = fillColor

        canvas.drawCircle(
            36f,
            36f,
            19f,
            paint
        )

        paint.color = AndroidColor.WHITE

        canvas.drawCircle(
            36f,
            36f,
            5f,
            paint
        )

        return bitmap
    }

    // =====================================
    // AGREGAR MARCADOR
    // =====================================

    @Suppress("DEPRECATION")
    private fun addRouteMarker(
        position: LatLng,
        title: String,
        color: Int
    ) {

        val map = mapInstance ?: return

        val icon =
            IconFactory.getInstance(this)
                .fromBitmap(
                    createRouteMarkerBitmap(color)
                )

        val marker = map.addMarker(
            MarkerOptions()
                .position(position)
                .title(title)
                .icon(icon)
        )

        routeMarkers.add(marker)
    }

    // =====================================
    // LIMPIAR MARCADORES DE RUTA
    // =====================================

    @Suppress("DEPRECATION")
    private fun clearRouteMarkers() {

        val map = mapInstance

        if (map != null) {

            routeMarkers.forEach { marker ->

                map.removeMarker(marker)
            }
        }

        routeMarkers.clear()
    }

    // =====================================
    // LIMPIAR POLYLINE
    // =====================================

    @Suppress("DEPRECATION")
    private fun clearRoutePolyline() {

        val map = mapInstance

        if (map != null) {

            activeRoutePolyline?.let { line ->

                map.removePolyline(line)
            }
        }

        activeRoutePolyline = null
    }

    // =====================================
    // MOSTRAR RECORRIDO COMPLETO
    // =====================================

    private fun showCompleteRoute() {

        val route = lastCalculatedRoute ?: return
        val map = mapInstance ?: return

        val points = RoutePolylineDecoder.decode(
            route.encodedPolyline
        )

        if (points.size < 2) {
            return
        }

        val boundsBuilder =
            LatLngBounds.Builder()

        points.forEach { point ->

            boundsBuilder.include(point)
        }

        map.animateCamera(
            CameraUpdateFactory.newLatLngBounds(
                boundsBuilder.build(),
                70
            ),
            1000
        )
    }

    // =====================================
    // ENTRAR AL MODO CONDUCCION
    // =====================================

    private fun enterDrivingView() {

        val route = lastCalculatedRoute ?: return
        val map = mapInstance ?: return

        val points = RoutePolylineDecoder.decode(
            route.encodedPolyline
        )

        if (points.isEmpty()) {
            return
        }

        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                points.first(),
                17.0
            ),
            1000
        )
    }

    // =====================================
    // FINALIZAR VIAJE
    // =====================================

    private fun finishCurrentTrip() {

        routeJob?.cancel()
        routeJob = null

        homeViewModel.clearTrip()
        smartChargingViewModel.clear()

        pendingSmartPlan.value = null
        pendingSmartCallback = null

        acceptedStops.clear()

        clearRouteMarkers()
        clearRoutePolyline()

        lastCalculatedRoute = null

        showMessage(
            "Viaje finalizado. Guardado solicitado."
        )

        mapInstance?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                INITIAL_MAP_CENTER,
                13.0
            ),
            700
        )
    }

    // =====================================
    // ICONO VERDE DE CARGADORES CERCANOS
    // =====================================

    private fun createChargingMarkerBitmap(): Bitmap {

        val bitmap = Bitmap.createBitmap(
            96,
            96,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)

        val paint = Paint(
            Paint.ANTI_ALIAS_FLAG
        )

        paint.color = AndroidColor.rgb(
            67,
            230,
            179
        )

        canvas.drawCircle(
            48f,
            48f,
            35f,
            paint
        )

        paint.color = AndroidColor.rgb(
            17,
            24,
            39
        )

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f

        canvas.drawCircle(
            48f,
            48f,
            35f,
            paint
        )

        paint.style = Paint.Style.FILL
        paint.color = AndroidColor.WHITE

        val lightning = Path().apply {

            moveTo(53f, 19f)
            lineTo(32f, 52f)
            lineTo(46f, 52f)
            lineTo(39f, 77f)
            lineTo(66f, 40f)
            lineTo(51f, 40f)
            close()
        }

        canvas.drawPath(
            lightning,
            paint
        )

        return bitmap
    }

    // =====================================
    // MOSTRAR / OCULTAR CARGADORES CERCANOS
    // =====================================

    private fun toggleChargingMarkers() {

        if (chargersLoading) {

            showMessage(
                "La búsqueda está en proceso."
            )

            return
        }

        if (chargersVisible) {

            hideChargingMarkers()

        } else {

            chargersLoading = true

            openWithDeviceLocation(
                action = { latitude, longitude ->

                    loadChargingMarkers(
                        latitude,
                        longitude
                    )
                },

                onError = {

                    chargersLoading = false
                }
            )
        }
    }

    // =====================================
    // CONSULTAR CARGADORES CERCANOS
    // =====================================

    private fun loadChargingMarkers(
        latitude: Double,
        longitude: Double
    ) {

        chargersJob?.cancel()

        chargersJob = lifecycleScope.launch {

            try {

                showMessage(
                    "Buscando cargadores cercanos..."
                )

                val stations =
                    GoogleChargingRepository.fetchStations(
                        latitude,
                        longitude
                    )

                val map = mapInstance

                if (map == null) {

                    showMessage(
                        "El mapa no está disponible."
                    )

                    return@launch
                }

                clearChargingMarkers()

                val icon =
                    IconFactory.getInstance(
                        this@MainActivity
                    ).fromBitmap(
                        createChargingMarkerBitmap()
                    )

                stations.forEach { station ->

                    val lat = station.latitude
                    val lon = station.longitude

                    if (
                        lat != null &&
                        lon != null &&
                        lat.isFinite() &&
                        lon.isFinite() &&
                        lat in -90.0..90.0 &&
                        lon in -180.0..180.0
                    ) {

                        val marker = map.addMarker(
                            MarkerOptions()
                                .position(
                                    LatLng(lat, lon)
                                )
                                .title(station.name)
                                .snippet(
                                    listOf(
                                        station.location,
                                        station.distance
                                    )
                                        .filter {
                                            it.isNotBlank()
                                        }
                                        .joinToString(" | ")
                                )
                                .icon(icon)
                        )

                        chargingMarkers.add(marker)
                    }
                }

                chargersVisible =
                    chargingMarkers.isNotEmpty()

                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(
                            latitude,
                            longitude
                        ),
                        12.5
                    )
                )

                if (chargingMarkers.isEmpty()) {

                    showMessage(
                        "No se encontraron cargadores cercanos."
                    )

                } else {

                    showMessage(
                        "${chargingMarkers.size} cargadores mostrados."
                    )
                }

            } catch (
                exception: CancellationException
            ) {

                throw exception

            } catch (exception: Exception) {

                showMessage(
                    "Error al consultar cargadores: " +
                            (
                                    exception.message
                                        ?: "Desconocido"
                                    )
                )

            } finally {

                chargersLoading = false
                chargersJob = null
            }
        }
    }

    // =====================================
    // LIMPIAR CARGADORES CERCANOS
    // =====================================

    @Suppress("DEPRECATION")
    private fun clearChargingMarkers() {

        val map = mapInstance

        if (map != null) {

            map.deselectMarkers()

            chargingMarkers.forEach { marker ->

                map.removeMarker(marker)
            }
        }

        chargingMarkers.clear()

        chargersVisible = false
    }

    // =====================================
    // OCULTAR CARGADORES CERCANOS
    // =====================================

    private fun hideChargingMarkers() {

        chargersJob?.cancel()
        chargersJob = null

        clearChargingMarkers()

        chargersLoading = false

        showMessage(
            "Cargadores ocultos"
        )
    }

    // =====================================
    // COMPROBAR PERMISOS GPS
    // =====================================

    private fun hasLocationPermission(): Boolean {

        val fine =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        val coarse =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        return fine || coarse
    }

    // =====================================
    // EJECUTAR ACCION CON GPS
    // =====================================

    private fun openWithDeviceLocation(
        action: (Double, Double) -> Unit,
        onError: ((String) -> Unit)? = null
    ) {

        if (pendingLocationAction != null) {

            val message =
                "Ya se está obteniendo ubicación GPS."

            showMessage(message)

            onError?.invoke(message)

            return
        }

        pendingLocationAction = action
        pendingLocationError = onError

        if (!hasLocationPermission()) {

            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )

            return
        }

        requestDeviceLocation()
    }

    // =====================================
    // ERROR DE UBICACION
    // =====================================

    private fun failLocationRequest(
        message: String
    ) {

        val callback =
            pendingLocationError

        stopLocationUpdates()

        pendingLocationAction = null
        pendingLocationError = null

        chargersLoading = false

        showMessage(message)

        callback?.invoke(message)
    }

    // =====================================
    // OBTENER UBICACION REAL
    // =====================================

    @SuppressLint("MissingPermission")
    private fun requestDeviceLocation() {

        if (!hasLocationPermission()) {

            failLocationRequest(
                "No hay permiso de ubicación."
            )

            return
        }

        stopLocationUpdates()

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        ).filter { provider ->

            try {

                locationManager.isProviderEnabled(provider)

            } catch (exception: Exception) {

                false
            }
        }

        if (providers.isEmpty()) {

            failLocationRequest(
                "Activa el GPS del dispositivo."
            )

            return
        }

        val recentLocation =
            providers.mapNotNull { provider ->

                try {

                    locationManager.getLastKnownLocation(
                        provider
                    )

                } catch (
                    exception: SecurityException
                ) {

                    null
                }
            }
                .filter { location ->

                    val ageNanos =
                        SystemClock.elapsedRealtimeNanos() -
                                location.elapsedRealtimeNanos

                    ageNanos >= 0L &&
                            ageNanos <= 30_000_000_000L &&
                            location.hasAccuracy() &&
                            location.accuracy <= 1000f
                }
                .maxByOrNull {

                    it.elapsedRealtimeNanos
                }

        if (recentLocation != null) {

            finishLocationSearch(recentLocation)

            return
        }

        showMessage(
            "Obteniendo ubicación GPS..."
        )

        val listener = object : LocationListener {

            override fun onLocationChanged(
                location: Location
            ) {

                finishLocationSearch(location)
            }

            override fun onProviderDisabled(
                provider: String
            ) = Unit

            override fun onProviderEnabled(
                provider: String
            ) = Unit
        }

        locationListener = listener

        var requested = false

        for (provider in providers) {

            if (!hasLocationPermission()) {
                break
            }

            try {

                locationManager.requestLocationUpdates(
                    provider,
                    0L,
                    0f,
                    listener,
                    Looper.getMainLooper()
                )

                requested = true

            } catch (
                exception: SecurityException
            ) {

                // Permiso retirado.

            } catch (
                exception: IllegalArgumentException
            ) {

                // Proveedor no disponible.
            }
        }

        if (!requested) {

            failLocationRequest(
                "No se pudo iniciar el GPS."
            )

            return
        }

        val timeout = Runnable {

            if (locationListener === listener) {

                failLocationRequest(
                    "No se obtuvo ubicación GPS. " +
                            "Intenta nuevamente."
                )
            }
        }

        locationTimeout = timeout

        locationHandler.postDelayed(
            timeout,
            15000L
        )
    }

    // =====================================
    // PROCESAR UBICACION GPS
    // =====================================

    private fun finishLocationSearch(
        location: Location
    ) {

        if (pendingLocationAction == null) {
            return
        }

        val latitude = location.latitude
        val longitude = location.longitude

        if (
            !latitude.isFinite() ||
            !longitude.isFinite() ||
            latitude !in -90.0..90.0 ||
            longitude !in -180.0..180.0
        ) {

            failLocationRequest(
                "Coordenadas GPS inválidas."
            )

            return
        }

        val callback =
            pendingLocationAction

        stopLocationUpdates()

        pendingLocationAction = null
        pendingLocationError = null

        callback?.invoke(
            latitude,
            longitude
        )
    }

    // =====================================
    // DETENER GPS
    // =====================================

    private fun stopLocationUpdates() {

        locationListener?.let { listener ->

            locationManager.removeUpdates(listener)
        }

        locationListener = null

        locationTimeout?.let { timeout ->

            locationHandler.removeCallbacks(timeout)
        }

        locationTimeout = null
    }

    // =====================================
    // MENSAJES
    // =====================================

    private fun showMessage(
        message: String
    ) {

        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show()
    }

    // =====================================
    // CICLO DE VIDA MAPLIBRE
    // =====================================

    override fun onStart() {

        super.onStart()

        mapView.onStart()
    }

    override fun onResume() {

        super.onResume()

        mapView.onResume()
    }

    override fun onPause() {

        mapView.onPause()

        super.onPause()
    }

    override fun onStop() {

        stopLocationUpdates()

        pendingLocationAction = null
        pendingLocationError = null

        chargersJob?.cancel()
        chargersJob = null

        chargersLoading = false

        routeJob?.cancel()
        routeJob = null

        mapView.onStop()

        super.onStop()
    }

    override fun onLowMemory() {

        super.onLowMemory()

        mapView.onLowMemory()
    }

    override fun onSaveInstanceState(
        outState: Bundle
    ) {

        mapView.onSaveInstanceState(
            outState
        )

        super.onSaveInstanceState(
            outState
        )
    }

    override fun onDestroy() {

        stopLocationUpdates()

        chargersJob?.cancel()
        routeJob?.cancel()

        locationHandler.removeCallbacksAndMessages(
            null
        )

        clearRouteMarkers()
        clearRoutePolyline()
        clearChargingMarkers()

        lastCalculatedRoute = null
        mapInstance = null

        mapView.onDestroy()

        super.onDestroy()
    }
}
