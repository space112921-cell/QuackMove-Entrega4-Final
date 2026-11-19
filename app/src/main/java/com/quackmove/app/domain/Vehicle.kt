package com.quackmove.app.domain

import kotlinx.coroutines.flow.Flow

// =====================================
// VEHICULO DEL CATALOGO
// =====================================

data class Vehicle(
    val id: String,
    val brand: String,
    val model: String,
    val usableBatteryKwh: Double,
    val consumptionKwhPer100Km: Double
) {

    val displayName: String
        get() = "$brand $model"

    // Autonomía estimada con la batería al 100 %.
    val estimatedRangeKm: Int
        get() = (usableBatteryKwh / consumptionKwhPer100Km * 100.0).toInt()
}

// =====================================
// CARRO GUARDADO POR EL USUARIO
// =====================================

data class VehicleSelection(
    val vehicle: Vehicle,
    val batteryPercent: Float
)

// =====================================
// VALORES SI NO HAY CARRO ELEGIDO
// =====================================

object DefaultVehicleSpecs {
    const val USABLE_BATTERY_KWH = 60.0
    const val CONSUMPTION_KWH_PER_100_KM = 18.0
    const val BATTERY_PERCENT = 80f
}

// =====================================
// CONTRATO DEL REPOSITORY
// =====================================

interface VehicleRepository {

    // Catálogo de carros eléctricos disponibles.
    suspend fun getCatalog(): List<Vehicle>

    // Carro y batería guardados (null si nunca se guardó).
    fun observeSelection(): Flow<VehicleSelection?>

    suspend fun saveSelection(
        vehicleId: String,
        batteryPercent: Float
    )
}
