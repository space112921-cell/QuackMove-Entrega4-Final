package com.quackmove.app.data.repository

import com.quackmove.app.domain.Vehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleCatalogTest {

    // Cada carro debe tener un id único: se usa como key
    // en la LazyColumn y como dato guardado en Room.
    @Test
    fun catalogIdsAreUnique() {

        val ids = VehicleCatalog.vehicles.map { it.id }

        assertEquals(ids.size, ids.toSet().size)
    }

    // Datos realistas: batería y consumo dentro de rangos
    // posibles para un carro eléctrico.
    @Test
    fun catalogSpecsAreRealistic() {

        VehicleCatalog.vehicles.forEach { vehicle ->

            assertTrue(
                vehicle.displayName,
                vehicle.usableBatteryKwh in 20.0..120.0
            )

            assertTrue(
                vehicle.displayName,
                vehicle.consumptionKwhPer100Km in 10.0..30.0
            )
        }
    }

    // Autonomía = batería útil / consumo × 100.
    @Test
    fun estimatedRangeUsesBatteryAndConsumption() {

        val vehicle = Vehicle(
            id = "test",
            brand = "Marca",
            model = "Modelo",
            usableBatteryKwh = 60.0,
            consumptionKwhPer100Km = 15.0
        )

        assertEquals(400, vehicle.estimatedRangeKm)
        assertEquals("Marca Modelo", vehicle.displayName)
    }
}
