
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

import com.quackmove.app.data.repository.GooglePlacesRepository
import com.quackmove.app.data.repository.PlaceSearchResult

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private val FieldPanel = Color(0xFF1D2C46)
private val FieldText = Color(0xFFF5F6FF)
private val FieldSecondary = Color(0xFFB6C2DF)
private val FieldGreen = Color(0xFF43E6B3)

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

    var results by remember {
        mutableStateOf<List<PlaceSearchResult>>(emptyList())
    }

    var isLoading by remember {
        mutableStateOf(false)
    }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    var selectedPlace by remember {
        mutableStateOf<PlaceSearchResult?>(null)
    }

    var isFocusedSearch by remember {
        mutableStateOf(false)
    }

    // Buscar con retraso para evitar consultas
    // por cada tecla presionada.
    LaunchedEffect(
        value,
        selectedPlace,
        userLatitude,
        userLongitude
    ) {

        results = emptyList()
        errorMessage = null

        val query = value.trim()

        if (
            query.length < 3 ||
            selectedPlace?.name == value
        ) {
            isLoading = false
            return@LaunchedEffect
        }

        isLoading = false

        delay(500)

        isLoading = true

        try {

            results = GooglePlacesRepository.searchPlaces(
                query = query,
                userLatitude = userLatitude,
                userLongitude = userLongitude
            )

        } catch (exception: CancellationException) {

            throw exception

        } catch (exception: Exception) {

            results = emptyList()

            errorMessage =
                exception.message
                    ?: "No se encontraron lugares"

        } finally {

            isLoading = false
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {

        OutlinedTextField(
            value = value,

            onValueChange = { newValue ->

                selectedPlace = null
                isFocusedSearch = true
                onValueChange(newValue)
            },

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

                if (isLoading) {

                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = FieldGreen
                    )

                } else if (selectedPlace != null) {

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
            results.isNotEmpty() &&
            selectedPlace == null
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
                        items = results,
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

                                    selectedPlace = place

                                    results = emptyList()
                                    isFocusedSearch = false

                                    onValueChange(place.name)

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

        errorMessage?.let { message ->

            Text(
                text = message,
                color = Color(0xFFFFB86B),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
