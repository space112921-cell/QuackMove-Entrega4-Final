
package com.quackmove.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope

import com.quackmove.app.data.repository.RoutePoint
import com.quackmove.app.data.repository.SmartChargingPlan
import com.quackmove.app.data.repository.SmartChargingRepository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// =====================================
// ESTADO INMUTABLE
// =====================================

data class SmartChargingUiState(
    val isLoading: Boolean = false,
    val plan: SmartChargingPlan? = null,
    val errorMessage: String? = null
)

// =====================================
// VIEWMODEL
// =====================================

class SmartChargingViewModel(
    private val repository: SmartChargingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SmartChargingUiState()
    )

    val uiState: StateFlow<SmartChargingUiState> =
        _uiState.asStateFlow()

    private var calculation: Job? = null

    // =====================================
    // CALCULAR RUTA
    // =====================================

    fun calculate(
        origin: RoutePoint,
        destination: RoutePoint,
        departurePercent: Float
    ) {

        calculation?.cancel()

        _uiState.value = SmartChargingUiState(
            isLoading = true
        )

        calculation = viewModelScope.launch {

            try {

                val result = repository.plan(
                    origin,
                    destination,
                    departurePercent
                )

                _uiState.value = SmartChargingUiState(
                    plan = result
                )

            } catch (
                cancelled: CancellationException
            ) {

                throw cancelled

            } catch (exception: Exception) {

                _uiState.value = SmartChargingUiState(
                    errorMessage =
                        exception.message
                            ?: "No se pudo calcular " +
                            "el recorrido con recargas."
                )
            }
        }
    }

    // =====================================
    // LIMPIAR CALCULO
    // =====================================

    fun clear() {

        calculation?.cancel()
        calculation = null

        _uiState.value = SmartChargingUiState()
    }
}

// =====================================
// FACTORY PARA INYECTAR REPOSITORY
// =====================================

class SmartChargingViewModelFactory(
    private val repository: SmartChargingRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {

        if (
            modelClass.isAssignableFrom(
                SmartChargingViewModel::class.java
            )
        ) {

            @Suppress("UNCHECKED_CAST")
            return SmartChargingViewModel(
                repository
            ) as T
        }

        throw IllegalArgumentException(
            "ViewModel no reconocido: ${modelClass.name}"
        )
    }
}
