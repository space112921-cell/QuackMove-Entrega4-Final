
package com.quackmove.app.feature.chargers

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope

import com.quackmove.app.data.model.ChargingStation
import com.quackmove.app.data.repository.ChargingRepository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// =====================================
// ESTADO DE LA PANTALLA DE CARGADORES
// =====================================

data class ChargersUiState(

    val stations: List<ChargingStation> = emptyList(),

    val isLoading: Boolean = false,

    val errorMessage: String? = null,

    val availableOnly: Boolean = false,

    val ccs2Only: Boolean = false,

    val fastOnly: Boolean = false

) {

    // Existen estaciones con información
    // de disponibilidad.
    val hasAvailability: Boolean
        get() = stations.any {
            it.available != null
        }

    // Existen estaciones con información
    // del tipo de conector.
    val hasConnectorInfo: Boolean
        get() = stations.any {
            !it.connector.isNullOrBlank()
        }

    // Existen estaciones con información
    // de potencia.
    val hasPowerInfo: Boolean
        get() = stations.any {
            it.powerKw != null
        }

    // =====================================
    // FILTROS
    // =====================================

    val filteredStations: List<ChargingStation>
        get() = stations.filter { station ->

            val matchesAvailability =
                !availableOnly ||
                        station.available == true

            val matchesConnector =
                !ccs2Only ||
                        station.connector.equals(
                            "CCS2",
                            ignoreCase = true
                        )

            val matchesPower =
                !fastOnly ||
                        (station.powerKw ?: 0) >= 50

            matchesAvailability &&
                    matchesConnector &&
                    matchesPower
        }
}

// =====================================
// VIEWMODEL PRINCIPAL
// =====================================

class ChargersViewModel(

    private val repository: ChargingRepository

) : ViewModel() {

    companion object {
        private const val TAG = "QuackMoveChargers"
    }

    // =====================================
    // ESTADO REACTIVO
    // =====================================

    private val _uiState = MutableStateFlow(
        ChargersUiState()
    )

    val uiState = _uiState.asStateFlow()

    // =====================================
    // COORDENADAS GPS
    // =====================================

    private var searchLatitude: Double? = null

    private var searchLongitude: Double? = null

    // Trabajo de consulta actual.
    private var searchJob: Job? = null

    // =====================================
    // RECIBIR UBICACIÓN
    // =====================================

    fun setSearchLocation(
        latitude: Double,
        longitude: Double
    ) {

        if (
            !latitude.isFinite() ||
            latitude !in -90.0..90.0 ||
            !longitude.isFinite() ||
            longitude !in -180.0..180.0
        ) {

            _uiState.update { state ->

                state.copy(
                    errorMessage = "Ubicación GPS inválida",
                    isLoading = false
                )
            }

            return
        }

        // Evitar repetir una consulta si
        // las coordenadas no cambiaron.
        if (
            searchLatitude == latitude &&
            searchLongitude == longitude
        ) {
            return
        }

        searchLatitude = latitude
        searchLongitude = longitude

        Log.d(
            TAG,
            "Nueva ubicación GPS: $latitude, $longitude"
        )

        refreshStations()
    }

    // =====================================
    // ACTUALIZAR ESTACIONES
    // =====================================

    fun refreshStations() {

        val latitude = searchLatitude
        val longitude = searchLongitude

        if (
            latitude == null ||
            longitude == null
        ) {

            _uiState.update { state ->

                state.copy(
                    isLoading = false,
                    errorMessage =
                        "No se recibió una ubicación GPS. " +
                                "Regresa al mapa y vuelve a abrir Cargadores."
                )
            }

            return
        }

        // Cancelar la consulta anterior si existe.
        searchJob?.cancel()

        searchJob = viewModelScope.launch {

            _uiState.update { state ->

                state.copy(
                    isLoading = true,
                    errorMessage = null
                )
            }

            try {

                Log.d(
                    TAG,
                    "Consultando estaciones cercanas"
                )

                val result = repository.fetchStations(
                    latitude = latitude,
                    longitude = longitude
                )

                Log.d(
                    TAG,
                    "Estaciones recibidas: ${result.size}"
                )

                _uiState.update { state ->

                    state.copy(
                        stations = result,
                        isLoading = false,
                        errorMessage = null
                    )
                }

            } catch (exception: CancellationException) {

                throw exception

            } catch (exception: Exception) {

                // Registrar la excepción completa.
                Log.e(
                    TAG,
                    "Error al consultar cargadores",
                    exception
                )

                val details =
                    exception.message
                        ?: "Error desconocido"

                _uiState.update { state ->

                    state.copy(
                        isLoading = false,
                        errorMessage =
                            "Error al buscar cargadores: $details"
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
    // FILTRO: DISPONIBLES
    // =====================================

    fun toggleAvailableOnly() {

        _uiState.update { state ->

            if (state.hasAvailability) {

                state.copy(
                    availableOnly =
                        !state.availableOnly
                )

            } else {

                state
            }
        }
    }

    // =====================================
    // FILTRO: CCS2
    // =====================================

    fun toggleCcs2Only() {

        _uiState.update { state ->

            if (state.hasConnectorInfo) {

                state.copy(
                    ccs2Only =
                        !state.ccs2Only
                )

            } else {

                state
            }
        }
    }

    // =====================================
    // FILTRO: CARGA RÁPIDA
    // =====================================

    fun toggleFastOnly() {

        _uiState.update { state ->

            if (state.hasPowerInfo) {

                state.copy(
                    fastOnly =
                        !state.fastOnly
                )

            } else {

                state
            }
        }
    }

    // =====================================
    // LIMPIAR RECURSOS
    // =====================================

    override fun onCleared() {

        searchJob?.cancel()

        super.onCleared()
    }
}

// =====================================
// FACTORY DEL VIEWMODEL
// =====================================

class ChargersViewModelFactory(

    private val repository: ChargingRepository

) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {

        if (
            modelClass.isAssignableFrom(
                ChargersViewModel::class.java
            )
        ) {

            @Suppress("UNCHECKED_CAST")
            return ChargersViewModel(
                repository
            ) as T
        }

        throw IllegalArgumentException(
            "ViewModel desconocido: ${modelClass.name}"
        )
    }
}
