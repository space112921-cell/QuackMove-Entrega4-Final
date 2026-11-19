
package com.quackmove.app.feature.routes

import android.util.Log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope

import com.quackmove.app.data.repository.RoutePoint
import com.quackmove.app.data.repository.RouteSummary
import com.quackmove.app.data.repository.RoutesRepository

import com.quackmove.app.domain.BatteryEstimate
import com.quackmove.app.domain.BatteryEstimator

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// =====================================
// ESTADO DEL PLANIFICADOR
// =====================================

data class RoutesUiState(

    val route: RouteSummary? = null,

    val isLoading: Boolean = false,

    val errorMessage: String? = null,

    // Origen y destino seleccionados
    val originName: String = "",
    val destinationName: String = "",

    // Datos provisionales del vehículo
    val batteryCapacity: String = "60",
    val consumption: String = "18",
    val departureBattery: String = "80",
    val minimumReserve: String = "10",

    // Resultado de autonomía
    val batteryEstimate: BatteryEstimate? = null,

    val validationError: String? = null
)

// =====================================
// ROUTES VIEWMODEL
// =====================================

class RoutesViewModel(
    private val routesRepository: RoutesRepository
) : ViewModel() {

    companion object {
        private const val TAG = "QuackMoveRoutes"
    }

    private val _uiState = MutableStateFlow(
        RoutesUiState()
    )

    val uiState = _uiState.asStateFlow()

    // Coordenadas seleccionadas por el usuario
    private var originPoint: RoutePoint? = null
    private var destinationPoint: RoutePoint? = null

    private var routeJob: Job? = null

    // No usamos init { refreshRoute() }.
    // Esperamos a que el usuario elija su viaje.

    // =====================================
    // SELECCIONAR ORIGEN Y DESTINO
    // =====================================

    fun setRoutePoints(
        origin: RoutePoint,
        destination: RoutePoint
    ) {

        if (!origin.isValid()) {

            _uiState.update { state ->
                state.copy(
                    errorMessage =
                        "El origen seleccionado no es válido."
                )
            }

            return
        }

        if (!destination.isValid()) {

            _uiState.update { state ->
                state.copy(
                    errorMessage =
                        "El destino seleccionado no es válido."
                )
            }

            return
        }

        routeJob?.cancel()
        routeJob = null

        originPoint = origin
        destinationPoint = destination

        _uiState.update { state ->
            state.copy(
                originName = origin.name,
                destinationName = destination.name,
                route = null,
                batteryEstimate = null,
                isLoading = false,
                errorMessage = null,
                validationError = null
            )
        }

        Log.d(
            TAG,
            "Ruta seleccionada: " +
                    "${origin.name} -> ${destination.name}"
        )
    }

    // =====================================
    // CALCULAR RUTA REAL
    // =====================================

    fun calculateRoute() {

        val origin = originPoint
        val destination = destinationPoint

        if (origin == null || destination == null) {

            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    errorMessage =
                        "Selecciona un origen y un destino " +
                                "con coordenadas antes de calcular."
                )
            }

            return
        }

        routeJob?.cancel()

        routeJob = viewModelScope.launch {

            _uiState.update { state ->
                state.copy(
                    isLoading = true,
                    errorMessage = null
                )
            }

            try {

                val result = routesRepository.fetchRoute(
                    origin = origin,
                    destination = destination
                )

                Log.d(
                    TAG,
                    "Ruta calculada: " +
                            "${result.distanceKm} km, " +
                            "${result.drivingMinutes} min"
                )

                _uiState.update { state ->
                    state.copy(
                        route = result,
                        isLoading = false,
                        errorMessage = null
                    )
                }

                calculateBattery()

            } catch (exception: CancellationException) {

                throw exception

            } catch (exception: Exception) {

                Log.e(
                    TAG,
                    "No se pudo calcular la ruta",
                    exception
                )

                _uiState.update { state ->
                    state.copy(
                        route = null,
                        batteryEstimate = null,
                        isLoading = false,
                        errorMessage =
                            "No se pudo calcular la ruta: " +
                                    (
                                            exception.message
                                                ?: "Error desconocido"
                                            )
                    )
                }

            } finally {

                _uiState.update { state ->
                    state.copy(
                        isLoading = false
                    )
                }
            }
        }
    }

    // =====================================
    // ACTUALIZAR RUTA
    // =====================================

    fun refreshRoute() {
        calculateRoute()
    }

    // =====================================
    // CAMBIAR CAPACIDAD DE BATERÍA
    // =====================================

    fun updateBatteryCapacity(value: String) {

        _uiState.update { state ->
            state.copy(
                batteryCapacity = value
            )
        }

        calculateBattery()
    }

    // =====================================
    // CAMBIAR CONSUMO
    // =====================================

    fun updateConsumption(value: String) {

        _uiState.update { state ->
            state.copy(
                consumption = value
            )
        }

        calculateBattery()
    }

    // =====================================
    // CAMBIAR BATERÍA INICIAL
    // =====================================

    fun updateDepartureBattery(value: String) {

        _uiState.update { state ->
            state.copy(
                departureBattery = value
            )
        }

        calculateBattery()
    }

    // =====================================
    // CAMBIAR RESERVA MÍNIMA
    // =====================================

    fun updateMinimumReserve(value: String) {

        _uiState.update { state ->
            state.copy(
                minimumReserve = value
            )
        }

        calculateBattery()
    }

    // =====================================
    // CONVERSIÓN NUMÉRICA
    // =====================================

    private fun parseNumber(
        value: String
    ): Double? {

        return value
            .trim()
            .replace(",", ".")
            .toDoubleOrNull()
    }

    // =====================================
    // CALCULAR BATERÍA NECESARIA
    // =====================================

    private fun calculateBattery() {

        val state = _uiState.value

        val currentRoute = state.route

        if (currentRoute == null) {

            _uiState.update {
                it.copy(
                    batteryEstimate = null,
                    validationError = null
                )
            }

            return
        }

        val capacity = parseNumber(
            state.batteryCapacity
        )

        val energyConsumption = parseNumber(
            state.consumption
        )

        val departure = parseNumber(
            state.departureBattery
        )

        val reserve = parseNumber(
            state.minimumReserve
        )

        if (
            capacity == null ||
            energyConsumption == null ||
            departure == null ||
            reserve == null
        ) {

            _uiState.update {
                it.copy(
                    batteryEstimate = null,
                    validationError =
                        "Introduce valores numéricos válidos."
                )
            }

            return
        }

        if (
            !capacity.isFinite() ||
            !energyConsumption.isFinite() ||
            !departure.isFinite() ||
            !reserve.isFinite() ||
            capacity <= 0.0 ||
            energyConsumption <= 0.0 ||
            departure !in 0.0..100.0 ||
            reserve !in 0.0..100.0
        ) {

            _uiState.update {
                it.copy(
                    batteryEstimate = null,
                    validationError =
                        "Revisa los datos del vehículo. " +
                                "La capacidad y el consumo deben " +
                                "ser positivos, y los porcentajes " +
                                "estar entre 0 y 100."
                )
            }

            return
        }

        try {

            val estimate = BatteryEstimator.calculate(

                distanceKm = currentRoute.distanceKm,

                usableBatteryKwh = capacity,

                consumptionKwhPer100Km =
                    energyConsumption,

                departurePercent = departure,

                minimumArrivalPercent = reserve
            )

            _uiState.update {
                it.copy(
                    batteryEstimate = estimate,
                    validationError = null
                )
            }

        } catch (exception: IllegalArgumentException) {

            Log.e(
                TAG,
                "Error calculando autonomía",
                exception
            )

            _uiState.update {
                it.copy(
                    batteryEstimate = null,
                    validationError =
                        "Los datos del vehículo no son válidos."
                )
            }
        }
    }

    // =====================================
    // LIMPIAR RECURSOS
    // =====================================

    override fun onCleared() {

        routeJob?.cancel()

        super.onCleared()
    }
}

// =====================================
// FACTORY
// =====================================

class RoutesViewModelFactory(
    private val repository: RoutesRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {

        if (
            modelClass.isAssignableFrom(
                RoutesViewModel::class.java
            )
        ) {

            @Suppress("UNCHECKED_CAST")
            return RoutesViewModel(
                routesRepository = repository
            ) as T
        }

        throw IllegalArgumentException(
            "ViewModel desconocido: ${modelClass.name}"
        )
    }
}
