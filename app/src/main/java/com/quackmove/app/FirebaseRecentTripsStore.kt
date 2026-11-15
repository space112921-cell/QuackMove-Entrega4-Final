
package com.quackmove.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.quackmove.app.feature.home.RoutePlanResult

object FirebaseRecentTripsStore {

    fun saveTrip(
        plan: RoutePlanResult,
        destination: RoutePoint,
        originName: String,
        departureBattery: Float,
        onResult: (Result<String>) -> Unit
    ) {

        val user = FirebaseAuth
            .getInstance()
            .currentUser

        if (user == null) {

            onResult(
                Result.failure(
                    IllegalStateException(
                        "No hay una sesión de Firebase activa."
                    )
                )
            )

            return
        }

        if (!destination.isValid()) {

            onResult(
                Result.failure(
                    IllegalArgumentException(
                        "Las coordenadas del destino no son válidas."
                    )
                )
            )

            return
        }

        val firestore = FirebaseFirestore.getInstance()

        val tripsCollection = firestore
            .collection("users")
            .document(user.uid)
            .collection("plannedTrips")

        val document = tripsCollection.document()

        val data = hashMapOf<String, Any?>(
            "id" to document.id,
            "userId" to user.uid,
            "originName" to originName,
            "destinationName" to destination.name,
            "destinationLatitude" to destination.latitude,
            "destinationLongitude" to destination.longitude,
            "distanceKm" to plan.route.distanceKm,
            "drivingMinutes" to plan.route.drivingMinutes,
            "departureBatteryPercent" to
                    departureBattery.toDouble(),
            "estimatedArrivalBatteryPercent" to
                    plan.battery.estimatedArrivalPercent,
            "energyRequiredKwh" to
                    plan.battery.energyRequiredKwh,
            "batteryUsedPercent" to
                    plan.battery.batteryUsedPercent,
            "needsCharging" to
                    !plan.battery.canCompleteTrip,
            "createdAtMillis" to
                    System.currentTimeMillis(),
            "tripStatus" to "PLANNED",
            "source" to "QUACKMOVE"
        )

        document.set(data)
            .addOnSuccessListener {

                onResult(
                    Result.success(document.id)
                )
            }
            .addOnFailureListener { error ->

                onResult(
                    Result.failure(error)
                )
            }
    }
}


