
package com.quackmove.app.feature.home

import android.util.Log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope

import com.quackmove.app.data.repository.TripStorageRepository
import com.quackmove.app.domain.SavedTrip

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// =====================================
// ESTADO DE GUARDADO DE VIAJES
// =====================================

data class TripSaveUiState(
    val isSaving: Boolean = false,
    val roomSaved: Boolean = false,
    val firebaseSaved: Boolean = false,
    val roomError: String? = null,
    val firebaseError: String? = null
)

// =====================================
// ESTADO DE VIAJES LEIDOS DE FIRESTORE
// =====================================

data class SavedTripsUiState(
    val isLoading: Boolean = false,
    val trips: List<SavedTrip> = emptyList(),
    val errorMessage: String? = null
)

// =====================================
// TRIP VIEWMODEL
// =====================================

class TripViewModel(
    private val repository: TripStorageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        TripSaveUiState()
    )

    val uiState: StateFlow<TripSaveUiState> =
        _uiState.asStateFlow()

    private val _savedTrips = MutableStateFlow(
        SavedTripsUiState()
    )

    // Viajes del usuario leídos de la colección "trips" (M9).
    val savedTrips: StateFlow<SavedTripsUiState> =
        _savedTrips.asStateFlow()

    init {
        loadSavedTrips()
    }

    // =====================================
    // LEER VIAJES DE FIRESTORE
    // =====================================

    fun loadSavedTrips() {

        if (_savedTrips.value.isLoading) return

        _savedTrips.update {
            it.copy(isLoading = true, errorMessage = null)
        }

        viewModelScope.launch {

            try {

                val trips = repository.getRemoteTrips()

                _savedTrips.value = SavedTripsUiState(
                    trips = trips
                )

            } catch (
                exception: CancellationException
            ) {

                throw exception

            } catch (exception: Exception) {

                // Se conservan los viajes que ya se
                // mostraban; solo se avisa del error.
                _savedTrips.update {
                    it.copy(
                        isLoading = false,
                        errorMessage =
                            "No se pudieron cargar tus viajes. " +
                                    "Comprueba la conexión."
                    )
                }
            }
        }
    }

    // =====================================
    // GUARDAR VIAJE
    // =====================================

    fun saveTrip(trip: SavedTrip) {

        viewModelScope.launch {

            _uiState.value = TripSaveUiState(
                isSaving = true
            )

            // =================================
            // 1. GUARDAR EN ROOM
            // =================================

            var roomSaved = false
            var roomError: String? = null

            try {

                val id = withContext(Dispatchers.IO) {

                    repository.saveLocal(trip)
                }

                roomSaved = true

                Log.d(
                    "QuackMoveRoom",
                    "Guardado en Room: $id"
                )

            } catch (
                exception: CancellationException
            ) {

                throw exception

            } catch (exception: Exception) {

                roomError =
                    exception.message
                        ?: "Error local desconocido"

                Log.e(
                    "QuackMoveRoom",
                    "No se pudo guardar",
                    exception
                )
            }

            _uiState.update { current ->

                current.copy(
                    roomSaved = roomSaved,
                    roomError = roomError
                )
            }

            // =================================
            // 2. GUARDAR EN FIREBASE
            // =================================

            var firebaseSaved = false
            var firebaseError: String? = null

            try {

                val id = repository.saveRemote(trip)

                firebaseSaved = true

                // Mostrar el viaje recién guardado.
                loadSavedTrips()

                Log.d(
                    "QuackMoveFirebase",
                    "Guardado en Firebase: $id"
                )

            } catch (
                exception: CancellationException
            ) {

                throw exception

            } catch (exception: Exception) {

                firebaseError =
                    exception.message
                        ?: "Error remoto desconocido"

                Log.e(
                    "QuackMoveFirebase",
                    "No se pudo guardar",
                    exception
                )
            }

            // =================================
            // 3. ACTUALIZAR RESULTADO
            // =================================

            _uiState.update { current ->

                current.copy(
                    isSaving = false,
                    firebaseSaved = firebaseSaved,
                    firebaseError = firebaseError
                )
            }
        }
    }
}

// =====================================
// FACTORY DEL VIEWMODEL
// =====================================

class TripViewModelFactory(
    private val repository: TripStorageRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {

        if (
            modelClass.isAssignableFrom(
                TripViewModel::class.java
            )
        ) {

            @Suppress("UNCHECKED_CAST")
            return TripViewModel(
                repository = repository
            ) as T
        }

        throw IllegalArgumentException(
            "ViewModel desconocido: ${modelClass.name}"
        )
    }
}
