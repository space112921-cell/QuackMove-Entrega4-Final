package com.quackmove.app.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.quackmove.app.data.local.QuackMoveDatabase
import com.quackmove.app.domain.TripRepository
import com.quackmove.app.domain.VehicleRepository

// =====================================
// CREACION DE REPOSITORIOS
// =====================================
//
// Las pantallas piden aquí sus Repository para pasarlos al
// ViewModel por constructor (M7), sin importar Firebase ni
// Room en los Composables ni en los ViewModels (M6).

object QuackMoveDependencies {

    val registrationRepository: RegistrationRepository by lazy {
        FirebaseRegistrationRepository(
            auth = FirebaseAuth.getInstance(),
            firestore = FirebaseFirestore.getInstance()
        )
    }

    @Volatile
    private var vehicleRepository: VehicleRepository? = null

    fun vehicleRepository(
        context: Context
    ): VehicleRepository {

        return vehicleRepository ?: synchronized(this) {

            vehicleRepository ?: RoomVehicleRepository(
                dao = QuackMoveDatabase
                    .getDatabase(context.applicationContext)
                    .selectedVehicleDao()
            ).also { repository ->
                vehicleRepository = repository
            }
        }
    }

    // Viajes guardados solo en el teléfono (Room).
    fun localTripRepository(
        context: Context
    ): TripRepository {

        return RoomTripRepository(
            QuackMoveDatabase
                .getDatabase(context.applicationContext)
                .localTripDao()
        )
    }
}
