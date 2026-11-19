
package com.quackmove.app.data.repository

import com.quackmove.app.data.model.ChargingStation

interface ChargingRepository {

    suspend fun fetchStations(
        latitude: Double,
        longitude: Double
    ): List<ChargingStation>
}
