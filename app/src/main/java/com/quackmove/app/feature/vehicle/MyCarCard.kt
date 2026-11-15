package com.quackmove.app.feature.vehicle

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.quackmove.app.domain.Vehicle
import com.quackmove.app.ui.theme.QuackBackground
import com.quackmove.app.ui.theme.QuackBorder
import com.quackmove.app.ui.theme.QuackMint
import com.quackmove.app.ui.theme.QuackPurpleLight
import com.quackmove.app.ui.theme.QuackSurface
import com.quackmove.app.ui.theme.QuackSurfaceVariant
import com.quackmove.app.ui.theme.QuackTextPrimary
import com.quackmove.app.ui.theme.QuackTextSecondary

import java.util.Locale
import kotlin.math.roundToInt

// =====================================
// TARJETA "MI CARRO"
// =====================================
//
// Composable de contenido: recibe el estado y avisa
// los eventos con callbacks (M5).

@Composable
fun MyCarCard(
    state: VehicleUiState,
    onOpenCatalog: () -> Unit,
    onOpenSpecs: () -> Unit,
    onBatteryChange: (Float) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = QuackSurfaceVariant,
        shape = RoundedCornerShape(28.dp)
    ) {

        Column(
            modifier = Modifier.padding(
                start = 22.dp,
                end = 16.dp,
                top = 18.dp,
                bottom = 18.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ---------- Nombre + engranaje ----------

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpenCatalog)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(
                        text = state.selectedVehicle?.displayName
                            ?: "Elige tu carro",
                        color = QuackTextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(Modifier.width(6.dp))

                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Abrir catálogo de carros",
                        tint = QuackTextPrimary
                    )
                }

                IconButton(
                    onClick = onOpenSpecs,
                    enabled = state.selectedVehicle != null
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Ver datos del carro",
                        tint = if (state.selectedVehicle != null) {
                            QuackTextPrimary
                        } else {
                            QuackBorder
                        }
                    )
                }
            }

            // ---------- Imagen del carro ----------

            VehicleImage(
                vehicle = state.selectedVehicle,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            )

            // ---------- Batería ----------

            Row(verticalAlignment = Alignment.CenterVertically) {

                Icon(
                    Icons.Default.Battery5Bar,
                    contentDescription = null,
                    tint = QuackTextPrimary,
                    modifier = Modifier
                        .size(26.dp)
                        .rotate(90f)
                )

                Spacer(Modifier.width(8.dp))

                Text(
                    "${state.batteryPercent.roundToInt()}%",
                    color = QuackTextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            BatterySlider(
                value = state.batteryPercent,
                onValueChange = onBatteryChange
            )

            state.errorMessage?.let { error ->
                Text(error, color = Color(0xFFFF8A8A), fontSize = 13.sp)
            }

            // ---------- Botones ----------

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                Button(
                    onClick = onOpenCatalog,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = QuackBorder,
                        contentColor = QuackTextPrimary
                    )
                ) {
                    Text(
                        if (state.selectedVehicle == null) "Elegir" else "Reemplazar",
                        fontSize = 17.sp
                    )
                }

                Button(
                    onClick = onSave,
                    enabled = state.canSave,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = QuackTextPrimary,
                        contentColor = QuackBackground,
                        disabledContainerColor = QuackSurface,
                        disabledContentColor = QuackTextSecondary
                    )
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = QuackBackground
                        )
                    } else {
                        Text(
                            if (state.hasUnsavedChanges || state.savedVehicleId == null) {
                                "Guardar"
                            } else {
                                "Guardado"
                            },
                            fontSize = 17.sp
                        )
                    }
                }
            }
        }
    }
}

// =====================================
// IMAGEN DEL CARRO
// =====================================
//
// Por ahora sin fotos: se muestra un ícono. Cuando llegue el
// catálogo de imágenes, aquí se cambia el ícono por la foto
// del modelo usando vehicle.id.

@Composable
private fun VehicleImage(
    vehicle: Vehicle?,
    modifier: Modifier = Modifier
) {

    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterEnd
    ) {
        Icon(
            Icons.Default.DirectionsCar,
            contentDescription = vehicle?.displayName,
            tint = if (vehicle != null) QuackTextSecondary else QuackBorder,
            modifier = Modifier
                .padding(end = 12.dp)
                .size(120.dp)
        )
    }
}

// =====================================
// SLIDER DE BATERIA
// =====================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatterySlider(
    value: Float,
    onValueChange: (Float) -> Unit
) {

    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = 0f..100f,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Batería actual"
            },
        thumb = {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(QuackBackground)
                    .border(3.dp, QuackMint, CircleShape)
            )
        },
        track = { sliderState ->

            val range = sliderState.valueRange
            val fraction = (
                    (sliderState.value - range.start) /
                            (range.endInclusive - range.start)
                    ).coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .clip(RoundedCornerShape(50))
                    .background(QuackSurface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(QuackMint)
                )
            }
        }
    )
}

// =====================================
// CATALOGO DE CARROS
// =====================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleCatalogSheet(
    state: VehicleUiState,
    onVehicleSelected: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = QuackSurface,
        contentColor = QuackTextPrimary
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {

            Text(
                "Catálogo de carros",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                "Elige tu modelo para calcular el consumo del viaje.",
                color = QuackTextSecondary,
                fontSize = 13.sp
            )

            Spacer(Modifier.height(12.dp))

            when {

                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = QuackMint)
                    }
                }

                state.catalog.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            state.errorMessage ?: "No hay carros en el catálogo.",
                            color = QuackTextSecondary
                        )
                        TextButton(onClick = onRetry) {
                            Text("Reintentar", color = QuackPurpleLight)
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 480.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = state.catalog,
                            key = { vehicle -> vehicle.id }
                        ) { vehicle ->
                            VehicleCatalogItem(
                                vehicle = vehicle,
                                isSelected =
                                    vehicle.id == state.selectedVehicle?.id,
                                onClick = { onVehicleSelected(vehicle.id) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun VehicleCatalogItem(
    vehicle: Vehicle,
    isSelected: Boolean,
    onClick: () -> Unit
) {

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        color = QuackSurfaceVariant,
        shape = RoundedCornerShape(18.dp),
        border = if (isSelected) BorderStroke(2.dp, QuackMint) else null
    ) {

        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                Icons.Default.DirectionsCar,
                contentDescription = null,
                tint = QuackTextSecondary,
                modifier = Modifier.size(36.dp)
            )

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {

                Text(
                    vehicle.displayName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                Text(
                    "${formatKwh(vehicle.usableBatteryKwh)} kWh · " +
                            "≈ ${vehicle.estimatedRangeKm} km de autonomía",
                    color = QuackTextSecondary,
                    fontSize = 13.sp
                )
            }

            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Seleccionado",
                    tint = QuackMint
                )
            }
        }
    }
}

// =====================================
// DATOS DEL CARRO (ENGRANAJE)
// =====================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleSpecsSheet(
    vehicle: Vehicle,
    onDismiss: () -> Unit
) {

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = QuackSurface,
        contentColor = QuackTextPrimary
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Text(
                vehicle.displayName,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            SpecRow("Batería útil", "${formatKwh(vehicle.usableBatteryKwh)} kWh")
            HorizontalDivider(color = QuackBorder)
            SpecRow(
                "Consumo estimado",
                "${formatKwh(vehicle.consumptionKwhPer100Km)} kWh/100 km"
            )
            HorizontalDivider(color = QuackBorder)
            SpecRow("Autonomía estimada", "≈ ${vehicle.estimatedRangeKm} km")

            Text(
                "Valores aproximados de uso real. Se usan para estimar " +
                        "la batería al llegar y las paradas de carga.",
                color = QuackTextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = QuackTextSecondary)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

private fun formatKwh(value: Double): String =
    String.format(Locale.US, "%.1f", value)
