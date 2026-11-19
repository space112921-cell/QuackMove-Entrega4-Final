package com.quackmove.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.quackmove.app.domain.SavedTrip
import com.quackmove.app.ui.theme.QuackBorder
import com.quackmove.app.ui.theme.QuackMint
import com.quackmove.app.ui.theme.QuackPurpleLight
import com.quackmove.app.ui.theme.QuackTextPrimary
import com.quackmove.app.ui.theme.QuackTextSecondary

import java.util.Locale
import kotlin.math.roundToInt

// =====================================
// MIS VIAJES (LEIDOS DE FIRESTORE)
// =====================================
//
// Composable de contenido: recibe el estado del TripViewModel
// y avisa el evento "Reintentar" con un callback.

private const val MAX_TRIPS_SHOWN = 5

@Composable
fun SavedTripsSection(
    state: SavedTripsUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Text(
                "Mis viajes",
                color = QuackTextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )

            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = QuackMint
                )
            } else {
                Text(
                    if (state.trips.size == 1) "1 guardado"
                    else "${state.trips.size} guardados",
                    color = QuackTextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        when {

            // Error sin datos previos: mensaje + reintentar.
            state.errorMessage != null && state.trips.isEmpty() -> {

                Text(
                    state.errorMessage,
                    color = QuackTextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                TextButton(onClick = onRetry) {
                    Text("Reintentar", color = QuackPurpleLight)
                }
            }

            // Primera carga.
            state.isLoading && state.trips.isEmpty() -> {

                Text(
                    "Cargando tus viajes…",
                    color = QuackTextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }

            // Vacío.
            state.trips.isEmpty() -> {

                Text(
                    "Cuando finalices un viaje, aparecerá aquí.",
                    color = QuackTextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }

            else -> {

                // Hay datos; si además falló la última
                // actualización, se avisa sin ocultarlos.
                state.errorMessage?.let { message ->
                    Text(
                        message,
                        color = QuackTextSecondary,
                        fontSize = 12.sp
                    )
                }

                state.trips
                    .take(MAX_TRIPS_SHOWN)
                    .forEach { trip ->
                        SavedTripRow(trip)
                        HorizontalDivider(color = QuackBorder)
                    }
            }
        }
    }
}

@Composable
private fun SavedTripRow(trip: SavedTrip) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Icon(
            Icons.Default.CloudDone,
            contentDescription = null,
            tint = QuackMint,
            modifier = Modifier.size(22.dp)
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {

            Text(
                trip.destination,
                color = QuackTextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                "Desde ${trip.origin} · " +
                        "${String.format(Locale.US, "%.1f", trip.distanceKm)} km · " +
                        "${trip.drivingMinutes} min · " +
                        "salió con ${trip.departureBatteryPercent.roundToInt()}%",
                color = QuackTextSecondary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
