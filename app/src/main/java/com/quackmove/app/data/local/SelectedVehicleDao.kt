package com.quackmove.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SelectedVehicleDao {

    // Observar el carro guardado.
    @Query("SELECT * FROM selected_vehicle WHERE id = 1 LIMIT 1")
    fun observeSelectedVehicle(): Flow<SelectedVehicleEntity?>

    // Guardar o reemplazar el carro elegido.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSelectedVehicle(vehicle: SelectedVehicleEntity)
}
