package com.quackmove.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// Carro elegido en "Mi carro". Solo existe una fila (id = 1):
// así el carro y su batería sobreviven a cerrar la app (M11).
@Entity(tableName = "selected_vehicle")
data class SelectedVehicleEntity(

    @PrimaryKey
    val id: Int = SINGLE_ROW_ID,

    val vehicleId: String,

    val batteryPercent: Float,

    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    companion object {
        const val SINGLE_ROW_ID = 1
    }
}
