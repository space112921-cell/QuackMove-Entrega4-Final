
package com.quackmove.app.feature.home

import android.view.ViewGroup

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

import androidx.compose.material3.*

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

import com.quackmove.app.data.repository.PlaceSearchResult
import com.quackmove.app.data.repository.RoutePoint
import com.quackmove.app.feature.vehicle.MyCarCard
import com.quackmove.app.feature.vehicle.VehicleCatalogSheet
import com.quackmove.app.feature.vehicle.VehicleSpecsSheet
import com.quackmove.app.feature.vehicle.VehicleUiState

import kotlinx.coroutines.launch

import org.maplibre.android.maps.MapView

import java.util.Locale
import kotlin.math.roundToInt


// =====================================
// COLORES QUACKMOVE
// =====================================

private val QMDark = Color(0xFF101928)
private val QMPanel = Color(0xFF142039)
private val QMCard = Color(0xFF1D2C46)
private val QMLine = Color(0xFF405174)

private val QMAccent = Color(0xFF8066F5)
private val QMAccentSoft = Color(0xFFAAA0FF)

private val QMWhite = Color(0xFFF5F6FF)
private val QMGray = Color(0xFFB6C2DF)

private val QMWarning = Color(0xFFFFB86B)
private val QMRed = Color(0xFFFF355D)

// =====================================
// MODOS DE PANTALLA
// =====================================

private enum class HomePanelMode {
    HOME,
    SEARCH,
    PLANNING,
    PREVIEW,
    NAVIGATING
}

private enum class SearchTarget {
    ORIGIN,
    DESTINATION,
    STOP
}

// =====================================
// FORMATO DE NUMEROS
// =====================================

private fun qmNumber(
    value: Double
): String {

    return String.format(
        Locale.US,
        "%.1f",
        value
    )
}

private fun qmTime(
    minutesTotal: Int
): String {

    val hours = minutesTotal / 60
    val minutes = minutesTotal % 60

    return if (hours > 0) {
        "${hours} h ${minutes} min"
    } else {
        "${minutes} min"
    }
}

// =====================================
// MAP SCREEN
// =====================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    mapView: MapView,
    onMapStyleChange: (Boolean) -> Unit,
    onReturnToCenter: () -> Unit,
    onOpenRegistration: () -> Unit,
    onOpenChargers: () -> Unit,
    onShowChargersOnMap: () -> Unit,
    onOpenRoute: (String) -> Unit,

    recentDestinations: List<RoutePoint> = emptyList(),

    onCalculateRoute: (
        RoutePoint?,
        RoutePoint,
        Float,
        (Result<RoutePlanResult>) -> Unit
    ) -> Unit,

    onContinueTrip: () -> Unit,

    onFinishTrip: (
        RoutePlanResult,
        RoutePoint,
        Float,
        String
    ) -> Unit,

    // =====================================
    // MI CARRO (CATALOGO)
    // =====================================

    vehicleState: VehicleUiState,
    onVehicleSelected: (String) -> Unit,
    onVehicleBatteryChange: (Float) -> Unit,
    onSaveVehicle: () -> Unit,
    onReloadVehicleCatalog: () -> Unit,

    // =====================================
    // MIS VIAJES (FIRESTORE)
    // =====================================

    savedTripsState: SavedTripsUiState,
    onRetrySavedTrips: () -> Unit
) {

    val scope = rememberCoroutineScope()

    val drawerState = rememberDrawerState(
        DrawerValue.Closed
    )

    val sheetState =
        rememberBottomSheetScaffoldState()

    // =====================================
    // MODO ACTUAL
    // =====================================

    var mode by rememberSaveable {
        mutableStateOf(HomePanelMode.HOME)
    }

    // =====================================
    // CORRECCION DEL SCROLL
    // =====================================
    //
    // Cada modo tiene su desplazamiento
    // reiniciado.
    //
    // Al entrar a NAVIGATING, comenzamos
    // desde arriba para mostrar inmediatamente
    // tiempo, bateria y botones.
    //
    // No conservamos la posicion de scroll
    // de PREVIEW o PLANNING.

    val panelScrollState = remember(mode) {
        ScrollState(0)
    }

    LaunchedEffect(mode) {

        panelScrollState.scrollTo(0)
    }

    // =====================================
    // BUSQUEDA
    // =====================================

    var searchTarget by remember {
        mutableStateOf(SearchTarget.DESTINATION)
    }

    var searchText by rememberSaveable {
        mutableStateOf("")
    }

    // =====================================
    // ORIGEN Y DESTINO
    // =====================================

    var origin by rememberSaveable {
        mutableStateOf("Tu ubicación")
    }

    var destination by rememberSaveable {
        mutableStateOf("")
    }

    var stop by rememberSaveable {
        mutableStateOf("")
    }

    var selectedOrigin by remember {
        mutableStateOf<RoutePoint?>(null)
    }

    var selectedDestination by remember {
        mutableStateOf<RoutePoint?>(null)
    }

    var selectedStop by remember {
        mutableStateOf<RoutePoint?>(null)
    }

    var showStop by rememberSaveable {
        mutableStateOf(false)
    }

    // =====================================
    // VEHICULO
    // =====================================

    // El carro y la batería viven en VehicleViewModel
    // (guardados en Room), no en el Composable.
    val batteryPercent = vehicleState.batteryPercent

    var showVehicleCatalog by rememberSaveable {
        mutableStateOf(false)
    }

    var showVehicleSpecs by rememberSaveable {
        mutableStateOf(false)
    }

    // =====================================
    // RESULTADO DEL VIAJE
    // =====================================

    var routePlan by remember {
        mutableStateOf<RoutePlanResult?>(null)
    }

    var calculatingRoute by remember {
        mutableStateOf(false)
    }

    var routeError by remember {
        mutableStateOf<String?>(null)
    }

    var darkMap by rememberSaveable {
        mutableStateOf(true)
    }

    // =====================================
    // CONTROL DEL PANEL
    // =====================================

    fun expandPanel() {

        scope.launch {

            sheetState.bottomSheetState.expand()
        }
    }

    fun collapsePanel() {

        scope.launch {

            sheetState.bottomSheetState.partialExpand()
        }
    }

    fun resetCalculation() {

        routePlan = null
        routeError = null
    }

    // =====================================
    // ABRIR BUSQUEDA
    // =====================================

    fun openSearch(
        target: SearchTarget
    ) {

        searchTarget = target

        searchText = when (target) {

            SearchTarget.ORIGIN -> {

                if (origin == "Tu ubicación") {
                    ""
                } else {
                    origin
                }
            }

            SearchTarget.DESTINATION -> {
                destination
            }

            SearchTarget.STOP -> {
                stop
            }
        }

        mode = HomePanelMode.SEARCH
    }

    // =====================================
    // SELECCIONAR LUGAR
    // =====================================

    fun selectPoint(
        point: RoutePoint
    ) {

        when (searchTarget) {

            SearchTarget.ORIGIN -> {

                origin = point.name
                selectedOrigin = point
            }

            SearchTarget.DESTINATION -> {

                destination = point.name
                selectedDestination = point
            }

            SearchTarget.STOP -> {

                stop = point.name
                selectedStop = point
                showStop = true
            }
        }

        resetCalculation()

        mode = HomePanelMode.PLANNING

        expandPanel()
    }

    // =====================================
    // CERRAR BUSQUEDA
    // =====================================

    fun closeSearch() {

        mode = if (
            destination.isNotBlank() ||
            origin != "Tu ubicación" ||
            showStop
        ) {

            HomePanelMode.PLANNING

        } else {

            HomePanelMode.HOME
        }
    }

    // =====================================
    // CALCULAR RUTA
    // =====================================

    fun calculateTrip() {

        val point = selectedDestination ?: return

        if (calculatingRoute) {
            return
        }

        if (showStop) {

            routeError =
                "Las paradas todavía no están " +
                        "integradas en este cálculo."

            return
        }

        resetCalculation()

        calculatingRoute = true

        mode = HomePanelMode.PREVIEW

        expandPanel()

        onCalculateRoute(
            selectedOrigin,
            point,
            batteryPercent
        ) { result ->

            calculatingRoute = false

            result.fold(
                onSuccess = { plan ->

                    routePlan = plan
                    routeError = null
                },

                onFailure = { error ->

                    routePlan = null

                    routeError =
                        error.message
                            ?: "No se pudo calcular la ruta."
                }
            )
        }
    }

    // =====================================
    // CONTINUAR
    // =====================================

    fun continueTrip() {

        if (routePlan == null) {
            return
        }

        // Acercar el mapa a la posicion inicial.
        onContinueTrip()

        // Cambiar el contenido del panel.
        // El scroll se reinicia automaticamente
        // al cambiar el modo.
        mode = HomePanelMode.NAVIGATING

        scope.launch {

            panelScrollState.scrollTo(0)

            sheetState.bottomSheetState
                .partialExpand()
        }
    }

    // =====================================
    // FINALIZAR Y GUARDAR
    // =====================================

    fun finishTrip() {

        val completedPlan = routePlan

        val completedDestination =
            selectedDestination

        if (
            completedPlan != null &&
            completedDestination != null
        ) {

            onFinishTrip(
                completedPlan,
                completedDestination,
                batteryPercent,
                origin
            )

        } else {

            routeError =
                "No hay un viaje válido para finalizar."

            return
        }

        // Limpiar viaje actual.
        origin = "Tu ubicación"
        destination = ""
        stop = ""

        selectedOrigin = null
        selectedDestination = null
        selectedStop = null

        showStop = false
        searchText = ""

        routePlan = null
        routeError = null
        calculatingRoute = false

        // Conservar Mi carro y bateria.
        mode = HomePanelMode.HOME

        collapsePanel()
    }

    // =====================================
    // BOTON ATRAS
    // =====================================

    BackHandler(
        enabled =
            mode == HomePanelMode.SEARCH ||
                    mode == HomePanelMode.NAVIGATING
    ) {

        when (mode) {

            HomePanelMode.SEARCH -> {

                closeSearch()
            }

            HomePanelMode.NAVIGATING -> {

                collapsePanel()
            }

            else -> Unit
        }
    }

    // =====================================
    // EDITAR MI CARRO
    // =====================================

    if (showVehicleCatalog) {

        VehicleCatalogSheet(
            state = vehicleState,

            onVehicleSelected = { vehicleId ->

                onVehicleSelected(vehicleId)

                resetCalculation()

                showVehicleCatalog = false
            },

            onRetry = onReloadVehicleCatalog,

            onDismiss = {

                showVehicleCatalog = false
            }
        )
    }

    val specsVehicle = vehicleState.selectedVehicle

    if (showVehicleSpecs && specsVehicle != null) {

        VehicleSpecsSheet(
            vehicle = specsVehicle,

            onDismiss = {

                showVehicleSpecs = false
            }
        )
    }

    // =====================================
    // CONTENEDOR GENERAL
    // =====================================

    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = false,

            drawerContent = {

                ModalDrawerSheet(
                    drawerContainerColor = QMPanel,
                    modifier = Modifier.width(290.dp)
                ) {

                    Text(
                        text = "QuackMove",
                        color = QMWhite,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(24.dp)
                    )

                    HorizontalDivider(
                        color = QMLine
                    )

                    NavigationDrawerItem(
                        label = {
                            Text("Inicio")
                        },

                        selected =
                            mode == HomePanelMode.HOME,

                        icon = {
                            Icon(
                                Icons.Default.Home,
                                contentDescription = null
                            )
                        },

                        onClick = {

                            mode = HomePanelMode.HOME

                            scope.launch {
                                drawerState.close()
                            }
                        }
                    )

                    NavigationDrawerItem(
                        label = {
                            Text("Mi carro")
                        },

                        selected = false,

                        icon = {
                            Icon(
                                Icons.Default.DirectionsCar,
                                contentDescription = null
                            )
                        },

                        onClick = {

                            showVehicleCatalog = true

                            scope.launch {
                                drawerState.close()
                            }
                        }
                    )

                    NavigationDrawerItem(
                        label = {
                            Text("Buscar destino")
                        },

                        selected = false,

                        icon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null
                            )
                        },

                        onClick = {

                            scope.launch {
                                drawerState.close()
                            }

                            openSearch(
                                SearchTarget.DESTINATION
                            )
                        }
                    )

                    NavigationDrawerItem(
                        label = {
                            Text("Cargadores")
                        },

                        selected = false,

                        icon = {
                            Icon(
                                Icons.Default.EvStation,
                                contentDescription = null
                            )
                        },

                        onClick = {

                            scope.launch {
                                drawerState.close()
                            }

                            onOpenChargers()
                        }
                    )

                    NavigationDrawerItem(
                        label = {
                            Text("Mi cuenta")
                        },

                        selected = false,

                        icon = {
                            Icon(
                                Icons.Default.PersonAdd,
                                contentDescription = null
                            )
                        },

                        onClick = {

                            scope.launch {
                                drawerState.close()
                            }

                            onOpenRegistration()
                        }
                    )
                }
            }
        ) {

            // =====================================
            // PANEL INFERIOR
            // =====================================

            BottomSheetScaffold(
                scaffoldState = sheetState,

                sheetPeekHeight =
                    if (mode == HomePanelMode.NAVIGATING) {
                        200.dp
                    } else {
                        100.dp
                    },

                sheetContainerColor = QMPanel,

                sheetContentColor = QMWhite,

                sheetShape = RoundedCornerShape(
                    topStart = 28.dp,
                    topEnd = 28.dp
                ),

                sheetDragHandle = {

                    BottomSheetDefaults.DragHandle(
                        color = QMLine
                    )
                },

                sheetContent = {

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(
                                max = if (
                                    mode == HomePanelMode.NAVIGATING
                                ) {
                                    640.dp
                                } else {
                                    580.dp
                                }
                            )
                            .navigationBarsPadding()

                            // CORREGIDO:
                            // No reutilizamos el scroll
                            // del panel anterior.
                            .verticalScroll(panelScrollState)

                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                                bottom = 20.dp
                            ),

                        verticalArrangement =
                            Arrangement.spacedBy(14.dp)
                    ) {

                        when (mode) {

                            // =========================
                            // HOME
                            // =========================

                            HomePanelMode.HOME,
                            HomePanelMode.SEARCH -> {

                                Surface(
                                    color = QMCard,

                                    shape =
                                        RoundedCornerShape(26.dp),

                                    onClick = {

                                        openSearch(
                                            SearchTarget.DESTINATION
                                        )
                                    }
                                ) {

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(58.dp)
                                            .padding(
                                                horizontal = 16.dp
                                            ),

                                        verticalAlignment =
                                            Alignment.CenterVertically,

                                        horizontalArrangement =
                                            Arrangement.spacedBy(12.dp)
                                    ) {

                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = null,
                                            tint = QMAccentSoft
                                        )

                                        Text(
                                            "¿A dónde vas?",
                                            color = QMWhite,
                                            fontSize = 17.sp,
                                            modifier = Modifier.weight(1f)
                                        )

                                        Icon(
                                            Icons.Default.ArrowForward,
                                            contentDescription = null,
                                            tint = QMGray
                                        )
                                    }
                                }

                                MyCarCard(
                                    state = vehicleState,

                                    onOpenCatalog = {

                                        showVehicleCatalog = true
                                    },

                                    onOpenSpecs = {

                                        showVehicleSpecs = true
                                    },

                                    onBatteryChange = { value ->

                                        onVehicleBatteryChange(value)

                                        resetCalculation()
                                    },

                                    onSave = onSaveVehicle
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),

                                    verticalAlignment =
                                        Alignment.CenterVertically,

                                    horizontalArrangement =
                                        Arrangement.SpaceBetween
                                ) {

                                    Text(
                                        "Recientes",
                                        color = QMWhite,
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Text(
                                        if (recentDestinations.size == 1) "1 destino"
                                        else "${recentDestinations.size} destinos",
                                        color = QMGray,
                                        fontSize = 12.sp
                                    )
                                }

                                if (recentDestinations.isEmpty()) {

                                    Text(
                                        "Todavía no hay destinos recientes.",

                                        color = QMGray,
                                        fontSize = 13.sp,

                                        modifier =
                                            Modifier.padding(12.dp)
                                    )

                                } else {

                                    recentDestinations
                                        .take(8)
                                        .forEach { point ->

                                            QMRecentRow(
                                                point = point,

                                                onClick = {

                                                    searchTarget =
                                                        SearchTarget.DESTINATION

                                                    selectPoint(point)
                                                }
                                            )

                                            HorizontalDivider(
                                                color = QMLine
                                            )
                                        }
                                }

                                SavedTripsSection(
                                    state = savedTripsState,
                                    onRetry = onRetrySavedTrips
                                )
                            }

                            // =========================
                            // PLANIFICAR VIAJE
                            // =========================

                            HomePanelMode.PLANNING -> {

                                QMHeader(
                                    title = "Planificar viaje",

                                    onBack = {

                                        mode = HomePanelMode.HOME
                                    }
                                )

                                QMLocationRow(
                                    title = "Origen",
                                    value = origin,
                                    icon = Icons.Default.MyLocation,

                                    onClick = {

                                        openSearch(
                                            SearchTarget.ORIGIN
                                        )
                                    }
                                )

                                if (origin != "Tu ubicación") {

                                    TextButton(
                                        onClick = {

                                            origin = "Tu ubicación"

                                            selectedOrigin = null

                                            resetCalculation()
                                        }
                                    ) {

                                        Text(
                                            "Usar mi ubicación",
                                            color = QMAccentSoft
                                        )
                                    }
                                }

                                QMLocationRow(
                                    title = "Destino",

                                    value = destination.ifBlank {
                                        "Seleccionar destino"
                                    },

                                    icon = Icons.Default.Place,

                                    onClick = {

                                        openSearch(
                                            SearchTarget.DESTINATION
                                        )
                                    }
                                )

                                if (showStop) {

                                    QMLocationRow(
                                        title = "Parada",

                                        value = stop.ifBlank {
                                            "Seleccionar parada"
                                        },

                                        icon =
                                            Icons.Default.AddLocation,

                                        onClick = {

                                            openSearch(
                                                SearchTarget.STOP
                                            )
                                        }
                                    )
                                }

                                Row(
                                    horizontalArrangement =
                                        Arrangement.spacedBy(8.dp)
                                ) {

                                    OutlinedButton(
                                        onClick = {

                                            if (showStop) {

                                                showStop = false
                                                stop = ""
                                                selectedStop = null

                                            } else {

                                                showStop = true

                                                openSearch(
                                                    SearchTarget.STOP
                                                )
                                            }

                                            resetCalculation()
                                        },

                                        colors =
                                            ButtonDefaults.outlinedButtonColors(
                                                contentColor = QMAccentSoft
                                            )
                                    ) {

                                        Icon(
                                            if (showStop) {
                                                Icons.Default.Close
                                            } else {
                                                Icons.Default.Add
                                            },

                                            contentDescription = null,

                                            modifier =
                                                Modifier.size(18.dp)
                                        )

                                        Spacer(Modifier.width(5.dp))

                                        Text(
                                            if (showStop) {
                                                "Quitar"
                                            } else {
                                                "Parada"
                                            }
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {

                                            val previousOrigin = origin
                                            val previousPoint =
                                                selectedOrigin

                                            if (
                                                selectedOrigin != null &&
                                                selectedDestination != null
                                            ) {

                                                origin = destination

                                                selectedOrigin =
                                                    selectedDestination

                                                destination =
                                                    previousOrigin

                                                selectedDestination =
                                                    previousPoint

                                                resetCalculation()
                                            }
                                        },

                                        enabled =
                                            selectedOrigin != null &&
                                                    selectedDestination != null,

                                        colors =
                                            ButtonDefaults.outlinedButtonColors(
                                                contentColor = QMAccentSoft
                                            )
                                    ) {

                                        Icon(
                                            Icons.Default.SwapVert,

                                            contentDescription = null,

                                            modifier =
                                                Modifier.size(18.dp)
                                        )

                                        Spacer(Modifier.width(5.dp))

                                        Text("Invertir")
                                    }
                                }

                                Surface(
                                    color = QMCard,
                                    shape = RoundedCornerShape(16.dp)
                                ) {

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),

                                        horizontalArrangement =
                                            Arrangement.SpaceBetween,

                                        verticalAlignment =
                                            Alignment.CenterVertically
                                    ) {

                                        Text(
                                            "Batería inicial",
                                            color = QMWhite
                                        )

                                        Text(
                                            "${batteryPercent.roundToInt()}%",

                                            color = QMAccentSoft,

                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Button(
                                    onClick = {

                                        calculateTrip()
                                    },

                                    enabled =
                                        selectedDestination != null &&
                                                !calculatingRoute &&
                                                (
                                                        origin == "Tu ubicación" ||
                                                                selectedOrigin != null
                                                        ) &&
                                                !showStop,

                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp),

                                    shape = RoundedCornerShape(18.dp),

                                    colors =
                                        ButtonDefaults.buttonColors(
                                            containerColor = QMAccent
                                        )
                                ) {

                                    Text("Calcular ruta")
                                }

                                if (showStop) {

                                    Text(
                                        "Las paradas intermedias " +
                                                "todavía no están habilitadas.",

                                        color = QMWarning,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            // =========================
                            // RESULTADO DEL VIAJE
                            // =========================

                            HomePanelMode.PREVIEW -> {

                                QMHeader(
                                    title = "Tu viaje",

                                    onBack = {

                                        mode =
                                            HomePanelMode.PLANNING
                                    }
                                )

                                QMSummaryText(
                                    title = "Origen",
                                    value = origin
                                )

                                QMSummaryText(
                                    title = "Destino",
                                    value = destination
                                )

                                HorizontalDivider(
                                    color = QMLine
                                )

                                when {

                                    calculatingRoute -> {

                                        Row(
                                            verticalAlignment =
                                                Alignment.CenterVertically,

                                            horizontalArrangement =
                                                Arrangement.spacedBy(12.dp)
                                        ) {

                                            CircularProgressIndicator(
                                                modifier =
                                                    Modifier.size(25.dp),

                                                strokeWidth = 3.dp,

                                                color = QMAccent
                                            )

                                            Text(
                                                "Calculando recorrido...",
                                                color = QMWhite
                                            )
                                        }
                                    }

                                    routeError != null -> {

                                        Text(
                                            routeError.orEmpty(),

                                            color = QMWarning,
                                            fontSize = 14.sp
                                        )

                                        OutlinedButton(
                                            onClick = {

                                                mode =
                                                    HomePanelMode.PLANNING
                                            },

                                            modifier =
                                                Modifier.fillMaxWidth()
                                        ) {

                                            Text("Intentar nuevamente")
                                        }
                                    }

                                    routePlan != null -> {

                                        val plan = routePlan!!

                                        QMTripMetrics(plan)

                                        QMTripConsumption(plan)

                                        QMTripBatteryMessage(plan)

                                        Text(
                                            qmVehicleEstimateText(
                                                vehicleState
                                            ),

                                            color = QMGray,
                                            fontSize = 11.sp
                                        )

                                        Button(
                                            onClick = {

                                                continueTrip()
                                            },

                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(52.dp),

                                            shape =
                                                RoundedCornerShape(16.dp),

                                            colors =
                                                ButtonDefaults.buttonColors(
                                                    containerColor = QMAccent
                                                )
                                        ) {

                                            Icon(
                                                Icons.Default.Navigation,
                                                contentDescription = null
                                            )

                                            Spacer(Modifier.width(8.dp))

                                            Text(
                                                "Continuar",
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = {

                                                mode =
                                                    HomePanelMode.PLANNING
                                            },

                                            modifier =
                                                Modifier.fillMaxWidth()
                                        ) {

                                            Text("Modificar recorrido")
                                        }
                                    }

                                    else -> {

                                        Text(
                                            "No hay una ruta calculada.",
                                            color = QMGray
                                        )
                                    }
                                }
                            }

                            // =========================
                            // MODO CONDUCCION
                            // =========================

                            HomePanelMode.NAVIGATING -> {

                                val plan = routePlan

                                if (plan != null) {

                                    // =====================
                                    // PRIMERO:
                                    // TIEMPO Y BATERIA
                                    // =====================

                                    Row(
                                        modifier =
                                            Modifier.fillMaxWidth(),

                                        horizontalArrangement =
                                            Arrangement.SpaceBetween,

                                        verticalAlignment =
                                            Alignment.CenterVertically
                                    ) {

                                        Column(
                                            verticalArrangement =
                                                Arrangement.spacedBy(4.dp)
                                        ) {

                                            Text(
                                                "Tiempo estimado",
                                                color = QMGray,
                                                fontSize = 12.sp
                                            )

                                            Text(
                                                qmTime(
                                                    plan.route.drivingMinutes
                                                ),

                                                color = QMWhite,
                                                fontSize = 24.sp,

                                                fontWeight =
                                                    FontWeight.Bold
                                            )
                                        }

                                        Column(
                                            horizontalAlignment =
                                                Alignment.End,

                                            verticalArrangement =
                                                Arrangement.spacedBy(4.dp)
                                        ) {

                                            Text(
                                                "Batería al llegar",
                                                color = QMGray,
                                                fontSize = 12.sp
                                            )

                                            Text(
                                                plan.battery
                                                    .estimatedArrivalPercent
                                                    ?.let {
                                                        "${qmNumber(it)}%"
                                                    }
                                                    ?: "No alcanza",

                                                color = if (
                                                    plan.battery
                                                        .canCompleteTrip
                                                ) {
                                                    QMAccentSoft
                                                } else {
                                                    QMWarning
                                                },

                                                fontSize = 23.sp,

                                                fontWeight =
                                                    FontWeight.Bold
                                            )
                                        }
                                    }

                                    // =====================
                                    // SEGUNDO:
                                    // VIAJE Y FINALIZAR
                                    // =====================

                                    Row(
                                        modifier =
                                            Modifier.fillMaxWidth(),

                                        horizontalArrangement =
                                            Arrangement.spacedBy(10.dp)
                                    ) {

                                        OutlinedButton(
                                            onClick = {

                                                // Subir la hoja
                                                // sin cambiar de modo.
                                                expandPanel()
                                            },

                                            modifier =
                                                Modifier.weight(1f),

                                            border = BorderStroke(
                                                1.dp,
                                                QMLine
                                            ),

                                            shape =
                                                RoundedCornerShape(15.dp),

                                            colors =
                                                ButtonDefaults
                                                    .outlinedButtonColors(
                                                        contentColor = QMWhite
                                                    )
                                        ) {

                                            Icon(
                                                Icons.Default.KeyboardArrowUp,
                                                contentDescription = null,
                                                modifier =
                                                    Modifier.size(18.dp)
                                            )

                                            Spacer(Modifier.width(4.dp))

                                            Text("Viaje")
                                        }

                                        Button(
                                            onClick = {

                                                finishTrip()
                                            },

                                            modifier =
                                                Modifier.weight(1f),

                                            shape =
                                                RoundedCornerShape(15.dp),

                                            colors =
                                                ButtonDefaults.buttonColors(
                                                    containerColor = QMRed,
                                                    contentColor = QMWhite
                                                )
                                        ) {

                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = null,
                                                modifier =
                                                    Modifier.size(17.dp)
                                            )

                                            Spacer(Modifier.width(4.dp))

                                            Text("Finalizar")
                                        }
                                    }

                                    // =====================
                                    // DETALLES DEBAJO
                                    // =====================

                                    HorizontalDivider(
                                        color = QMLine
                                    )

                                    Text(
                                        "Tu viaje",
                                        color = QMWhite,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    QMSummaryText(
                                        title = "Origen",
                                        value = origin
                                    )

                                    QMSummaryText(
                                        title = "Destino",
                                        value = destination
                                    )

                                    HorizontalDivider(
                                        color = QMLine
                                    )

                                    QMTripMetrics(plan)

                                    QMTripConsumption(plan)

                                    QMTripBatteryMessage(plan)

                                    Text(
                                        qmVehicleEstimateText(
                                            vehicleState
                                        ),

                                        color = QMGray,
                                        fontSize = 11.sp
                                    )

                                    OutlinedButton(
                                        onClick = {

                                            // Volver a mostrar el
                                            // panel pequeño.
                                            collapsePanel()

                                            // Preparar el scroll
                                            // para la próxima vez.
                                            scope.launch {
                                                panelScrollState
                                                    .animateScrollTo(0)
                                            }
                                        },

                                        modifier =
                                            Modifier.fillMaxWidth(),

                                        colors =
                                            ButtonDefaults
                                                .outlinedButtonColors(
                                                    contentColor =
                                                        QMAccentSoft
                                                )
                                    ) {

                                        Icon(
                                            Icons.Default.KeyboardArrowDown,
                                            contentDescription = null
                                        )

                                        Spacer(Modifier.width(6.dp))

                                        Text("Volver al mapa")
                                    }
                                }
                            }
                        }
                    }
                }

            ) { _ ->

                // =====================================
                // MAPLIBRE
                // =====================================

                Box(
                    modifier = Modifier.fillMaxSize()
                ) {

                    AndroidView(
                        factory = {

                            (mapView.parent as? ViewGroup)
                                ?.removeView(mapView)

                            mapView
                        },

                        modifier = Modifier.fillMaxSize()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(16.dp),

                        horizontalArrangement =
                            Arrangement.SpaceBetween
                    ) {

                        QMMapCircle(
                            icon = Icons.Default.Menu,
                            description = "Menú"
                        ) {

                            scope.launch {
                                drawerState.open()
                            }
                        }

                        Column(
                            verticalArrangement =
                                Arrangement.spacedBy(10.dp)
                        ) {

                            QMMapCircle(
                                icon = Icons.Default.Layers,
                                description = "Cambiar estilo"
                            ) {

                                darkMap = !darkMap

                                onMapStyleChange(darkMap)
                            }

                            QMMapCircle(
                                icon = Icons.Default.FlashOn,
                                description = "Cargadores"
                            ) {

                                onShowChargersOnMap()
                            }

                            QMMapCircle(
                                icon = Icons.Default.MyLocation,
                                description = "Mi ubicación"
                            ) {

                                onReturnToCenter()
                            }
                        }
                    }
                }
            }
        }

        // =====================================
        // BUSCADOR
        // =====================================

        if (mode == HomePanelMode.SEARCH) {

            QMWazeSearch(
                text = searchText,

                onTextChange = {

                    searchText = it
                },

                onBack = {

                    closeSearch()
                },

                onPlaceSelected = { place ->

                    selectPoint(
                        place.toRoutePoint()
                    )
                },

                recentDestinations =
                    recentDestinations,

                onRecentSelected = { point ->

                    selectPoint(point)
                },

                onChargers = onOpenChargers
            )
        }
    }
}

// =====================================
// METRICAS DEL VIAJE
// =====================================

@Composable
private fun QMTripMetrics(
    plan: RoutePlanResult
) {

    Row(
        modifier = Modifier.fillMaxWidth(),

        horizontalArrangement =
            Arrangement.spacedBy(8.dp)
    ) {

        QMMetricCard(
            title = "Tiempo",

            value = qmTime(
                plan.route.drivingMinutes
            ),

            icon = Icons.Default.Schedule,

            modifier = Modifier.weight(1f)
        )

        QMMetricCard(
            title = "Distancia",

            value =
                "${qmNumber(plan.route.distanceKm)} km",

            icon = Icons.Default.Route,

            modifier = Modifier.weight(1f)
        )

        QMMetricCard(
            title = "Al llegar",

            value = plan.battery
                .estimatedArrivalPercent
                ?.let {
                    "${qmNumber(it)}%"
                }
                ?: "No alcanza",

            icon = Icons.Default.BatteryChargingFull,

            modifier = Modifier.weight(1f)
        )
    }
}

// =====================================
// CONSUMO ESTIMADO
// =====================================

@Composable
private fun QMTripConsumption(
    plan: RoutePlanResult
) {

    Surface(
        color = QMCard,
        shape = RoundedCornerShape(16.dp)
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),

            verticalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {

            Text(
                "Consumo estimado",
                color = QMGray,
                fontSize = 13.sp
            )

            Text(
                "${qmNumber(plan.battery.energyRequiredKwh)} kWh",

                color = QMWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                "Batería consumida: " +
                        "${qmNumber(plan.battery.batteryUsedPercent)}%",

                color = QMGray,
                fontSize = 12.sp
            )
        }
    }
}

// =====================================
// MENSAJE DE BATERIA
// =====================================

@Composable
private fun QMTripBatteryMessage(
    plan: RoutePlanResult
) {

    if (plan.battery.canCompleteTrip) {

        Row(
            verticalAlignment =
                Alignment.CenterVertically,

            horizontalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {

            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = QMAccentSoft
            )

            Text(
                "Tu batería alcanza para " +
                        "llegar con la reserva prevista.",

                color = QMWhite,
                fontSize = 13.sp
            )
        }

    } else {

        Row(
            verticalAlignment =
                Alignment.CenterVertically,

            horizontalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {

            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = QMWarning
            )

            Text(
                "Necesitas recargar durante el viaje.",

                color = QMWarning,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        Text(
            "Energía adicional estimada: " +
                    "${qmNumber(plan.battery.missingEnergyKwh)} kWh",

            color = QMGray,
            fontSize = 12.sp
        )

        Text(
            "Las estaciones de carga sobre " +
                    "el recorrido se agregarán después.",

            color = QMGray,
            fontSize = 12.sp
        )
    }
}

// =====================================
// BUSQUEDA ESTILO WAZE
// =====================================

@Composable
private fun QMWazeSearch(
    text: String,
    onTextChange: (String) -> Unit,
    onBack: () -> Unit,
    onPlaceSelected: (PlaceSearchResult) -> Unit,
    recentDestinations: List<RoutePoint>,
    onRecentSelected: (RoutePoint) -> Unit,
    onChargers: () -> Unit
) {

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = QMPanel
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(16.dp),

            verticalArrangement =
                Arrangement.spacedBy(18.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {

                IconButton(
                    modifier = Modifier.padding(top = 8.dp),
                    onClick = onBack
                ) {

                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Volver",
                        tint = QMWhite
                    )
                }

                PlaceSearchField(
                    label = "¿A dónde vas?",
                    value = text,
                    onValueChange = onTextChange,
                    onPlaceSelected = onPlaceSelected,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                QMShortcut(
                    title = "Guardados",
                    icon = Icons.Default.Bookmark,
                    modifier = Modifier.weight(1f)
                )

                QMShortcut(
                    title = "Casa",
                    icon = Icons.Default.Home,
                    modifier = Modifier.weight(1f)
                )

                QMShortcut(
                    title = "Trabajo",
                    icon = Icons.Default.Work,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = QMLine)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onChargers()
                    }
                    .padding(vertical = 12.dp),

                verticalAlignment =
                    Alignment.CenterVertically,

                horizontalArrangement =
                    Arrangement.spacedBy(14.dp)
            ) {

                Icon(
                    Icons.Default.EvStation,
                    contentDescription = null,
                    tint = QMAccentSoft,
                    modifier = Modifier.size(28.dp)
                )

                Column(
                    modifier = Modifier.weight(1f)
                ) {

                    Text(
                        "Estaciones de carga",
                        color = QMWhite,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "Buscar cargadores cercanos",
                        color = QMGray,
                        fontSize = 12.sp
                    )
                }

                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = QMGray
                )
            }

            HorizontalDivider(color = QMLine)

            Text(
                "Recientes",
                color = QMGray,
                fontSize = 14.sp
            )

            if (recentDestinations.isEmpty()) {

                Text(
                    "Todavía no hay destinos recientes.",
                    color = QMGray,
                    fontSize = 13.sp
                )

            } else {

                recentDestinations
                    .take(12)
                    .forEach { point ->

                        QMRecentRow(
                            point = point,

                            onClick = {

                                onRecentSelected(point)
                            }
                        )

                        HorizontalDivider(
                            color = QMLine
                        )
                    }
            }
        }
    }
}

// =====================================
// TARJETA DE METRICAS
// =====================================

@Composable
private fun QMMetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {

    Surface(
        modifier = modifier,
        color = QMCard,
        shape = RoundedCornerShape(14.dp)
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 6.dp,
                    vertical = 12.dp
                ),

            horizontalAlignment =
                Alignment.CenterHorizontally,

            verticalArrangement =
                Arrangement.spacedBy(6.dp)
        ) {

            Icon(
                icon,
                contentDescription = null,
                tint = QMAccentSoft,
                modifier = Modifier.size(20.dp)
            )

            Text(
                value,
                color = QMWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2
            )

            Text(
                title,
                color = QMGray,
                fontSize = 11.sp
            )
        }
    }
}

// =====================================
// ACCESO RAPIDO
// =====================================

@Composable
private fun QMShortcut(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {

    Surface(
        modifier = modifier.height(94.dp),
        shape = RoundedCornerShape(18.dp),
        color = QMCard
    ) {

        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally,

            verticalArrangement =
                Arrangement.Center
        ) {

            Icon(
                icon,
                contentDescription = null,
                tint = QMAccentSoft,
                modifier = Modifier.size(26.dp)
            )

            Spacer(Modifier.height(8.dp))

            Text(
                title,
                color = QMWhite,
                fontSize = 13.sp
            )
        }
    }
}

// =====================================
// ENCABEZADO
// =====================================

@Composable
private fun QMHeader(
    title: String,
    onBack: () -> Unit
) {

    Row(
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        IconButton(
            onClick = onBack
        ) {

            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "Volver",
                tint = QMWhite
            )
        }

        Text(
            title,
            color = QMWhite,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// =====================================
// DESTINO RECIENTE
// =====================================

@Composable
private fun QMRecentRow(
    point: RoutePoint,
    onClick: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),

        verticalAlignment =
            Alignment.CenterVertically,

        horizontalArrangement =
            Arrangement.spacedBy(14.dp)
    ) {

        Icon(
            Icons.Default.History,
            contentDescription = null,
            tint = QMAccentSoft,
            modifier = Modifier.size(25.dp)
        )

        Text(
            point.name,
            modifier = Modifier.weight(1f),
            color = QMWhite,
            fontSize = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = QMGray
        )
    }
}

// =====================================
// ORIGEN / DESTINO
// =====================================

@Composable
private fun QMLocationRow(
    title: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit
) {

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(17.dp),
        color = QMCard
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp),

            verticalAlignment =
                Alignment.CenterVertically,

            horizontalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {

            Icon(
                icon,
                contentDescription = null,
                tint = QMAccentSoft
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    title,
                    color = QMGray,
                    fontSize = 11.sp
                )

                Text(
                    value,
                    color = QMWhite,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = QMGray
            )
        }
    }
}

// =====================================
// RESUMEN DE VIAJE
// =====================================

@Composable
private fun QMSummaryText(
    title: String,
    value: String
) {

    Column(
        verticalArrangement =
            Arrangement.spacedBy(4.dp)
    ) {

        Text(
            title,
            color = QMGray,
            fontSize = 12.sp
        )

        Text(
            value,
            color = QMWhite,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// =====================================
// BOTONES DEL MAPA
// =====================================

@Composable
private fun QMMapCircle(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit
) {

    Surface(
        modifier = Modifier.size(52.dp),
        shape = CircleShape,
        color = QMDark,

        border = BorderStroke(
            1.dp,
            QMLine
        ),

        shadowElevation = 5.dp,
        onClick = onClick
    ) {

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {

            Icon(
                icon,
                contentDescription = description,
                tint = QMAccentSoft,
                modifier = Modifier.size(25.dp)
            )
        }
    }
}

// =====================================
// TEXTO DE LA ESTIMACION
// =====================================

private fun qmVehicleEstimateText(
    state: VehicleUiState
): String {

    val battery = qmNumber(state.usableBatteryKwh)
    val consumption = qmNumber(state.consumptionKwhPer100Km)

    val source = state.selectedVehicle?.displayName
        ?: "Estimación provisional"

    return "$source: $battery kWh de batería útil, " +
            "$consumption kWh/100 km y 10% de reserva mínima."
}
