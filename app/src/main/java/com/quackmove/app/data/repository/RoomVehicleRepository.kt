package com.quackmove.app.data.repository

import com.quackmove.app.data.local.SelectedVehicleDao
import com.quackmove.app.data.local.SelectedVehicleEntity
import com.quackmove.app.domain.Vehicle
import com.quackmove.app.domain.VehicleRepository
import com.quackmove.app.domain.VehicleSelection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

// =====================================
// REPOSITORY DE MI CARRO
// =====================================
//
// El catálogo es fijo (VehicleCatalog) y el carro elegido
// se guarda en Room para que sobreviva al cerrar la app.

class RoomVehicleRepository(
    private val dao: SelectedVehicleDao,
    private val catalog: List<Vehicle> = VehicleCatalog.vehicles
) : VehicleRepository {

    override suspend fun getCatalog(): List<Vehicle> = withErrorReporting(
        "RoomVehicleRepository.getCatalog"
    ) {
        catalog.sortedWith(
            compareBy<Vehicle> { it.brand }.thenBy { it.model }
        )
    }

    override fun observeSelection(): Flow<VehicleSelection?> {

        return dao.observeSelectedVehicle()
            .map { entity ->

                entity?.let { saved ->

                    // Si el carro ya no existe en el catálogo,
                    // se trata como si no hubiera selección.
                    catalog
                        .firstOrNull { it.id == saved.vehicleId }
                        ?.let { vehicle ->
                            VehicleSelection(
                                vehicle = vehicle,
                                batteryPercent =
                                    saved.batteryPercent.coerceIn(0f, 100f)
                            )
                        }
                }
            }
            .catch { exception ->

                RepositoryErrorReporter.record(
                    "RoomVehicleRepository.observeSelection",
                    exception
                )

                throw exception
            }
    }

    override suspend fun saveSelection(
        vehicleId: String,
        batteryPercent: Float
    ) = withErrorReporting(
        "RoomVehicleRepository.saveSelection"
    ) {

        require(catalog.any { it.id == vehicleId }) {
            "El carro elegido no está en el catálogo."
        }

        require(batteryPercent in 0f..100f) {
            "La batería debe estar entre 0 y 100 %."
        }

        dao.saveSelectedVehicle(
            SelectedVehicleEntity(
                vehicleId = vehicleId,
                batteryPercent = batteryPercent
            )
        )
    }
}
