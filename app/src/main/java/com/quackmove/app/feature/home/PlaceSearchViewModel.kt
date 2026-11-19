package com.quackmove.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope

import com.quackmove.app.data.repository.PlaceSearchResult
import com.quackmove.app.data.repository.PlacesRepository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// =====================================
// ESTADO INMUTABLE DE LA BUSQUEDA
// =====================================

data class PlaceSearchUiState(
    val results: List<PlaceSearchResult> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedPlace: PlaceSearchResult? = null
)

// =====================================
// VIEWMODEL
// =====================================
//
// Antes el Composable PlaceSearchField llamaba directamente
// a GooglePlacesRepository. Ahora la búsqueda vive aquí (M5).

class PlaceSearchViewModel(
    private val repository: PlacesRepository
) : ViewModel() {

    companion object {
        private const val MIN_QUERY_LENGTH = 3
        private const val DEBOUNCE_MILLIS = 500L
    }

    private val _uiState = MutableStateFlow(PlaceSearchUiState())

    val uiState: StateFlow<PlaceSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    private var lastQuery: String? = null

    // =====================================
    // CAMBIO DEL TEXTO
    // =====================================

    fun onQueryChanged(
        text: String,
        userLatitude: Double?,
        userLongitude: Double?
    ) {

        val query = text.trim()

        // Evitar repetir la misma consulta (por ejemplo,
        // al recomponer o al girar la pantalla).
        if (query == lastQuery) return
        lastQuery = query

        searchJob?.cancel()

        val selected = _uiState.value.selectedPlace

        // El texto es el del lugar ya elegido: no se busca de nuevo.
        if (selected != null && selected.name == text) return

        _uiState.update {
            it.copy(
                results = emptyList(),
                errorMessage = null,
                isLoading = false,
                selectedPlace = null
            )
        }

        if (query.length < MIN_QUERY_LENGTH) return

        searchJob = viewModelScope.launch {

            // Esperar a que el usuario deje de escribir.
            delay(DEBOUNCE_MILLIS)

            _uiState.update { it.copy(isLoading = true) }

            try {

                val places = repository.searchPlaces(
                    query = query,
                    userLatitude = userLatitude,
                    userLongitude = userLongitude
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        results = places,
                        errorMessage =
                            if (places.isEmpty()) {
                                "No se encontraron lugares."
                            } else {
                                null
                            }
                    )
                }

            } catch (cancelled: CancellationException) {

                throw cancelled

            } catch (exception: Exception) {

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        results = emptyList(),
                        errorMessage =
                            "No se pudo buscar. Comprueba la conexión."
                    )
                }
            }
        }
    }

    // =====================================
    // LUGAR ELEGIDO
    // =====================================

    fun onPlaceSelected(place: PlaceSearchResult) {

        searchJob?.cancel()

        lastQuery = place.name.trim()

        _uiState.update {
            PlaceSearchUiState(selectedPlace = place)
        }
    }
}

// =====================================
// FACTORY PARA INYECTAR REPOSITORY
// =====================================

class PlaceSearchViewModelFactory(
    private val repository: PlacesRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {

        if (modelClass.isAssignableFrom(PlaceSearchViewModel::class.java)) {

            @Suppress("UNCHECKED_CAST")
            return PlaceSearchViewModel(repository) as T
        }

        throw IllegalArgumentException(
            "ViewModel no reconocido: ${modelClass.name}"
        )
    }
}
