
package com.quackmove.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quackmove.app.data.model.RecentDestination
import com.quackmove.app.data.repository.DemoDestinationsRepository
import com.quackmove.app.data.repository.GoogleRoutesRepository
import com.quackmove.app.data.repository.RoutePoint
import com.quackmove.app.data.repository.RoutesRepository
import com.quackmove.app.domain.BatteryEstimator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// =====================================
// ESTADO DE HOME
// =====================================

data class HomeUiState(
    val searchText: String = "",
    val darkMap: Boolean = true,
    val recentDestinations: List<RecentDestination> = emptyList(),
    val searchResults: List<Pair<String, String>> = emptyList(),

    val selectedOrigin: RoutePoint? = null,
    val selectedDestination: RoutePoint? = null,
    val departureBatteryPercent: Float = 80f,
    val routePlan: RoutePlanResult? = null,

    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

// =====================================
// HOME VIEWMODEL
// =====================================

class HomeViewModel(
    private val destinationsRepository: DemoDestinationsRepository =
        DemoDestinationsRepository,
    private val routesRepository: RoutesRepository =
        GoogleRoutesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HomeUiState(
            recentDestinations =
                destinationsRepository.demoRecentDestinations
        )
    )

    val uiState: StateFlow<HomeUiState> =
        _uiState.asStateFlow()

    private var calculationJob: Job? = null

    // =====================================
    // BUSQUEDA
    // =====================================

    fun updateSearchText(value: String) {
        val search = value.trim()

        val results = if (search.isBlank()) {
            emptyList()
        } else {
            destinationsRepository.searchablePlaces.filter { place ->
                place.first.contains(search, ignoreCase = true) ||
                        place.second.contains(search, ignoreCase = true)
            }
        }

        _uiState.update { current ->
            current.copy(
                searchText = value,
                searchResults = results
            )
        }
    }

    // =====================================
    // TEMA OSCURO
    // =====================================

    fun toggleDarkMap(): Boolean {
        val newDarkMap = !_uiState.value.darkMap

        _uiState.update { current ->
            current.copy(darkMap = newDarkMap)
        }

        return newDarkMap
    }

    // =====================================
    // SELECCIONAR ORIGEN
    // =====================================

    fun setOrigin(origin: RoutePoint?) {
        cancelCalculation()

        _uiState.update { current ->
            current.copy(
                selectedOrigin = origin,
                routePlan = null,
                isLoading = false,
                errorMessage = null
            )
        }
    }

    // =====================================
    // SELECCIONAR DESTINO
    // =====================================

    fun setDestination(destination: RoutePoint?) {
        cancelCalculation()

        _uiState.update { current ->
            current.copy(
                selectedDestination = destination,
                routePlan = null,
                isLoading = false,
                errorMessage = null
            )
        }
    }

    // =====================================
    // ACTUALIZAR BATERIA
    // =====================================

    fun updateDepartureBattery(percent: Float) {
        if (!percent.isFinite()) {
            _uiState.update { current ->
                current.copy(
                    errorMessage =
                        "La batería debe ser un número válido."
                )
            }
            return
        }

        cancelCalculation()

        _uiState.update { current ->
            current.copy(
                departureBatteryPercent =
                    percent.coerceIn(0f, 100f),
                routePlan = null,
                isLoading = false,
                errorMessage = null
            )
        }
    }

    // =====================================
    // CONFIGURAR VIAJE
    // =====================================

    fun setTrip(
        origin: RoutePoint?,
        destination: RoutePoint,
        departureBatteryPercent: Float
    ) {
        cancelCalculation()

        val error = when {
            origin != null && !origin.isValid() ->
                "El origen seleccionado no es válido."

            !destination.isValid() ->
                "El destino seleccionado no es válido."

            !departureBatteryPercent.isFinite() ||
                    departureBatteryPercent !in 0f..100f ->
                "La batería inicial debe estar entre 0 y 100 %."

            else -> null
        }

        if (error != null) {
            _uiState.update { current ->
                current.copy(
                    routePlan = null,
                    isLoading = false,
                    errorMessage = error
                )
            }
            return
        }

        _uiState.update { current ->
            current.copy(
                selectedOrigin = origin,
                selectedDestination = destination,
                departureBatteryPercent = departureBatteryPercent,
                routePlan = null,
                isLoading = false,
                errorMessage = null
            )
        }
    }

    // =====================================
    // CALCULO REAL DE RUTA
    // =====================================

    fun calculateRoute(
        origin: RoutePoint,
        destination: RoutePoint,
        departureBatteryPercent: Float
    ) {
        cancelCalculation()

        val error = when {
            !origin.isValid() ->
                "El origen no tiene coordenadas válidas."

            !destination.isValid() ->
                "El destino no tiene coordenadas válidas."

            !departureBatteryPercent.isFinite() ||
                    departureBatteryPercent !in 0f..100f ->
                "El porcentaje de batería es inválido."

            else -> null
        }

        if (error != null) {
            _uiState.update { current ->
                current.copy(
                    routePlan = null,
                    isLoading = false,
                    errorMessage = error
                )
            }
            return
        }

        _uiState.update { current ->
            current.copy(
                selectedOrigin = origin,
                selectedDestination = destination,
                departureBatteryPercent = departureBatteryPercent,
                routePlan = null,
                isLoading = true,
                errorMessage = null
            )
        }

        calculationJob = viewModelScope.launch {
            try {
                val route = routesRepository.fetchRoute(
                    origin = origin,
                    destination = destination
                )

                if (route.encodedPolyline.isBlank()) {
                    throw IllegalStateException(
                        "No se recibió la geometría de la ruta."
                    )
                }

                // Valores provisionales del vehículo.
                val estimate = BatteryEstimator.calculate(
                    distanceKm = route.distanceKm,
                    usableBatteryKwh = 60.0,
                    consumptionKwhPer100Km = 18.0,
                    departurePercent =
                        departureBatteryPercent.toDouble(),
                    minimumArrivalPercent = 10.0
                )

                val plan = RoutePlanResult(
                    route = route,
                    battery = estimate
                )

                _uiState.update { current ->
                    current.copy(
                        routePlan = plan,
                        isLoading = false,
                        errorMessage = null
                    )
                }

            } catch (exception: CancellationException) {
                throw exception

            } catch (exception: Exception) {
                _uiState.update { current ->
                    current.copy(
                        routePlan = null,
                        isLoading = false,
                        errorMessage =
                            exception.message
                                ?: "No se pudo calcular el viaje."
                    )
                }
            }
        }
    }

    // =====================================
    // LIMPIAR ERRORES
    // =====================================

    fun clearError() {
        _uiState.update { current ->
            current.copy(errorMessage = null)
        }
    }

    // =====================================
    // LIMPIAR VIAJE
    // =====================================

    fun clearTrip() {
        cancelCalculation()

        _uiState.update { current ->
            current.copy(
                selectedOrigin = null,
                selectedDestination = null,
                routePlan = null,
                isLoading = false,
                errorMessage = null
            )
        }
    }

    // =====================================
    // CANCELAR CALCULO
    // =====================================

    private fun cancelCalculation() {
        calculationJob?.cancel()
        calculationJob = null
    }

    // =====================================
    // LIBERAR RECURSOS
    // =====================================

    override fun onCleared() {
        cancelCalculation()
        super.onCleared()
    }
}

// =====================================
// FACTORY
// =====================================

class HomeViewModelFactory(
    private val destinationsRepository: DemoDestinationsRepository,
    private val routesRepository: RoutesRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(
                destinationsRepository = destinationsRepository,
                routesRepository = routesRepository
            ) as T
        }

        throw IllegalArgumentException(
            "ViewModel desconocido: ${modelClass.name}"
        )
    }
}
