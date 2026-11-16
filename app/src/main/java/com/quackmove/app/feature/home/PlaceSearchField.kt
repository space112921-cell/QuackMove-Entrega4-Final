
package com.quackmove.app.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

import com.quackmove.app.data.repository.GooglePlacesRepository
import com.quackmove.app.data.repository.PlaceSearchResult

private val FieldPanel = Color(0xFF1D2C46)
private val FieldText = Color(0xFFF5F6FF)
private val FieldSecondary = Color(0xFFB6C2DF)
private val FieldGreen = Color(0xFF43E6B3)

// =====================================
// CAMPO CON SU VIEWMODEL
// =====================================

@Composable
fun PlaceSearchField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onPlaceSelected: (PlaceSearchResult) -> Unit,
    modifier: Modifier = Modifier,
    userLatitude: Double? = null,
    userLongitude: Double? = null
) {

    val model: PlaceSearchViewModel = viewModel(
        key = "place-search-$label",
        factory = PlaceSearchViewModelFactory(GooglePlacesRepository)
    )

    val state by model.uiState.collectAsStateWithLifecycle()

    // Avisar al ViewModel cada vez que cambia el texto.
    // El ViewModel espera 500 ms antes de buscar.
    LaunchedEffect(value, userLatitude, userLongitude) {
        model.onQueryChanged(value, userLatitude, userLongitude)
    }

    PlaceSearchFieldContent(
        label = label,
        value = value,
        state = state,
        onValueChange = onValueChange,
        onPlaceSelected = { place ->
            model.onPlaceSelected(place)
            onValueChange(place.name)
            onPlaceSelected(place)
        },
        modifier = modifier
    )
}

// =====================================
// CONTENIDO: RECIBE ESTADO Y CALLBACKS
// =====================================

@Composable
fun PlaceSearchFieldContent(
    label: String,
    value: String,
    state: PlaceSearchUiState,
    onValueChange: (String) -> Unit,
    onPlaceSelected: (PlaceSearchResult) -> Unit,
    modifier: Modifier = Modifier
) {

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {

        OutlinedTextField(
            value = value,

            onValueChange = onValueChange,

            modifier = Modifier.fillMaxWidth(),

            label = {
                Text(label)
            },

            placeholder = {
                Text("Buscar lugar o dirección")
            },

            leadingIcon = {

                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = FieldGreen
                )
            },

            trailingIcon = {

                if (state.isLoading) {

                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = FieldGreen
                    )

                } else if (state.selectedPlace != null) {

                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = FieldGreen
                    )
                }
            },

            singleLine = true,

            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = FieldText,
                unfocusedTextColor = FieldText,
                focusedBorderColor = FieldGreen,
                unfocusedBorderColor = FieldSecondary,
                focusedLabelColor = FieldGreen,
                unfocusedLabelColor = FieldSecondary
            )
        )

        if (
            state.results.isNotEmpty() &&
            state.selectedPlace == null
        ) {

            Surface(
                color = FieldPanel,
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 4.dp
            ) {

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                ) {

                    items(
                        items = state.results,
                        key = { place ->
                            place.id.ifBlank {
                                "${place.latitude},${place.longitude}"
                            }
                        }
                    ) { place ->

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {

                                    onPlaceSelected(place)
                                }
                                .padding(14.dp),
                            horizontalArrangement =
                                Arrangement.spacedBy(12.dp)
                        ) {

                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = FieldGreen
                            )

                            Column {

                                Text(
                                    text = place.name,
                                    color = FieldText,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Text(
                                    text = place.address,
                                    color = FieldSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        HorizontalDivider(
                            color = FieldSecondary.copy(
                                alpha = 0.15f
                            )
                        )
                    }
                }
            }
        }

        state.errorMessage?.let { message ->

            Text(
                text = message,
                color = Color(0xFFFFB86B),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
