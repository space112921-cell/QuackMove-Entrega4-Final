package com.quackmove.app.feature.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope

import com.quackmove.app.domain.DefaultVehicleSpecs
import com.quackmove.app.domain.Vehicle
import com.quackmove.app.domain.VehicleRepository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import kotlin.math.roundToInt

// =====================================
// ESTADO INMUTABLE DE MI CARRO
// =====================================

data class VehicleUiState(

    val isLoading: Boolean = true,

    val catalog: List<Vehicle> = emptyList(),

    // Carro y batería que se ven en la tarjeta
    // (pueden tener cambios sin guardar).
    val selectedVehicle: Vehicle? = null,
    val batteryPercent: Float = DefaultVehicleSpecs.BATTERY_PERCENT,

    // Lo que está guardado en Room.
    val savedVehicleId: String? = null,
    val savedBatteryPercent: Float? = null,

    val isSaving: Boolean = false,

    val errorMessage: String? = null,
    val message: String? = null
) {

    val hasUnsavedChanges: Boolean
        get() = selectedVehicle != null && (
                selectedVehicle.id != savedVehicleId ||
                        batteryPercent != savedBatteryPercent
                )

    val canSave: Boolean
        get() = hasUnsavedChanges && !isSaving

    // Valores usados para calcular el consumo del viaje.
    val usableBatteryKwh: Double
        get() = selectedVehicle?.usableBatteryKwh
            ?: DefaultVehicleSpecs.USABLE_BATTERY_KWH

    val consumptionKwhPer100Km: Double
        get() = selectedVehicle?.consumptionKwhPer100Km
            ?: DefaultVehicleSpecs.CONSUMPTION_KWH_PER_100_KM
}

// =====================================
// VIEWMODEL
// =====================================

class VehicleViewModel(
    private val repository: VehicleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VehicleUiState())

    val uiState: StateFlow<VehicleUiState> = _uiState.asStateFlow()

    init {
        loadCatalog()
        observeSavedVehicle()
    }

    // =====================================
    // CARGAR CATALOGO
    // =====================================

    fun loadCatalog() {

        _uiState.update {
            it.copy(isLoading = true, errorMessage = null)
        }

        viewModelScope.launch {

            try {

                val catalog = repository.getCatalog()

                _uiState.update {
                    it.copy(isLoading = false, catalog = catalog)
                }

            } catch (cancelled: CancellationException) {

                throw cancelled

            } catch (exception: Exception) {

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "No se pudo cargar el catálogo."
                    )
                }
            }
        }
    }

    // =====================================
    // CARRO GUARDADO EN ROOM
    // =====================================

    private fun observeSavedVehicle() {

        viewModelScope.launch {

            repository.observeSelection()
                .catch {
                    _uiState.update {
                        it.copy(
                            errorMessage =
                                "No se pudo leer el carro guardado."
                        )
                    }
                }
                .collect { saved ->

                    _uiState.update { current ->

                        // Si el usuario está editando, no se
                        // pisan sus cambios sin guardar.
                        val keepEdits = current.hasUnsavedChanges

                        current.copy(
                            savedVehicleId = saved?.vehicle?.id,
                            savedBatteryPercent = saved?.batteryPercent,

                            selectedVehicle =
                                if (keepEdits) current.selectedVehicle
                                else saved?.vehicle ?: current.selectedVehicle,

                            batteryPercent =
                                if (keepEdits) current.batteryPercent
                                else saved?.batteryPercent
                                    ?: current.batteryPercent
                        )
                    }
                }
        }
    }

    // =====================================
    // EVENTOS DE LA TARJETA
    // =====================================

    fun selectVehicle(vehicleId: String) {

        val vehicle = _uiState.value.catalog
            .firstOrNull { it.id == vehicleId }
            ?: return

        _uiState.update {
            it.copy(
                selectedVehicle = vehicle,
                errorMessage = null,
                message = null
            )
        }
    }

    fun changeBattery(percent: Float) {

        val rounded = percent
            .coerceIn(0f, 100f)
            .roundToInt()
            .toFloat()

        _uiState.update {
            it.copy(batteryPercent = rounded, message = null)
        }
    }

    fun save() {

        val state = _uiState.value
        val vehicle = state.selectedVehicle ?: return

        if (!state.canSave) return

        _uiState.update {
            it.copy(isSaving = true, errorMessage = null, message = null)
        }

        viewModelScope.launch {

            try {

                repository.saveSelection(
                    vehicleId = vehicle.id,
                    batteryPercent = state.batteryPercent
                )

                // Room avisa por observeSelection(); aquí
                // solo se marca como guardado.
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        savedVehicleId = vehicle.id,
                        savedBatteryPercent = state.batteryPercent,
                        message = "Carro guardado."
                    )
                }

            } catch (cancelled: CancellationException) {

                throw cancelled

            } catch (exception: Exception) {

                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage =
                            exception.message
                                ?: "No se pudo guardar el carro."
                    )
                }
            }
        }
    }

    fun messageShown() {
        _uiState.update { it.copy(message = null, errorMessage = null) }
    }
}

// =====================================
// FACTORY PARA INYECTAR REPOSITORY
// =====================================

class VehicleViewModelFactory(
    private val repository: VehicleRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {

        if (modelClass.isAssignableFrom(VehicleViewModel::class.java)) {

            @Suppress("UNCHECKED_CAST")
            return VehicleViewModel(repository) as T
        }

        throw IllegalArgumentException(
            "ViewModel no reconocido: ${modelClass.name}"
        )
    }
}
