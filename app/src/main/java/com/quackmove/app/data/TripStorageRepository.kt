
package com.quackmove.app.data.repository

import android.util.Log

import com.google.firebase.crashlytics.FirebaseCrashlytics

import com.quackmove.app.data.local.LocalTripDao
import com.quackmove.app.data.local.LocalTripEntity
import com.quackmove.app.domain.SavedTrip

import java.util.UUID

import kotlinx.coroutines.CancellationException

// =====================================
// REPOSITORY DE ALMACENAMIENTO
// ROOM + FIREBASE + CRASHLYTICS
// =====================================

class TripStorageRepository(
    private val localDao: LocalTripDao,
    private val remoteRepository: FirestoreTripRepository
) {

    // =====================================
    // GUARDAR LOCALMENTE EN ROOM
    // =====================================

    suspend fun saveLocal(
        trip: SavedTrip
    ): String {

        try {

            val id = UUID.randomUUID().toString()

            val localTrip = LocalTripEntity(
                id = id,
                origin = trip.origin,
                destination = trip.destination,
                distanceKm = trip.distanceKm,
                drivingMinutes = trip.drivingMinutes,
                departureBatteryPercent =
                    trip.departureBatteryPercent,
                savedAtMillis = System.currentTimeMillis()
            )

            localDao.saveTrip(localTrip)

            Log.d(
                "QuackMoveRoom",
                "Viaje guardado localmente: $id"
            )

            return id

        } catch (
            exception: CancellationException
        ) {

            throw exception

        } catch (exception: Exception) {

            reportNonFatal(
                operation = "save_local_trip",
                exception = exception
            )

            // El ViewModel seguirá mostrando
            // el error sin cerrar la app.
            throw exception
        }
    }

    // =====================================
    // GUARDAR REMOTAMENTE EN FIRESTORE
    // =====================================

    suspend fun saveRemote(
        trip: SavedTrip
    ): String {

        try {

            val documentId =
                remoteRepository.saveTrip(trip)

            Log.d(
                "QuackMoveFirebase",
                "Viaje sincronizado con Firestore"
            )

            return documentId

        } catch (
            exception: CancellationException
        ) {

            throw exception

        } catch (exception: Exception) {

            reportNonFatal(
                operation = "save_remote_trip",
                exception = exception
            )

            // No afecta el viaje que ya
            // se haya guardado en Room.
            throw exception
        }
    }

    // =====================================
    // LEER VIAJES DE FIRESTORE
    // =====================================

    suspend fun getRemoteTrips(): List<SavedTrip> {

        try {

            return remoteRepository.getTrips()

        } catch (
            exception: CancellationException
        ) {

            throw exception

        } catch (exception: Exception) {

            reportNonFatal(
                operation = "get_remote_trips",
                exception = exception
            )

            throw exception
        }
    }

    // =====================================
    // REPORTAR ERRORES NO FATALES
    // =====================================

    private fun reportNonFatal(
        operation: String,
        exception: Exception
    ) {

        Log.e(
            "QuackMoveCrashlytics",
            "Error en Repository: $operation",
            exception
        )

        try {

            val crashlytics =
                FirebaseCrashlytics.getInstance()

            // Registramos la operación,
            // sin incluir nombres, correos,
            // contraseñas ni datos del viaje.
            crashlytics.log(
                "TripStorageRepository: $operation"
            )

            // Enviar excepción no fatal.
            // Esto NO provoca un cierre.
            crashlytics.recordException(
                exception
            )

        } catch (reportingError: Exception) {

            // Un fallo del sistema de monitoreo
            // no debe ocultar el error original
            // ni provocar otro cierre.
            Log.e(
                "QuackMoveCrashlytics",
                "No se pudo registrar el error",
                reportingError
            )
        }
    }
}
