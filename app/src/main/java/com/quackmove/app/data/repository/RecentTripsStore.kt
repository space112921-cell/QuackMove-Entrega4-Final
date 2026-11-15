
package com.quackmove.app.data.repository

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Historial local de viajes planificados.
 *
 * No requiere internet ni llamadas a APIs.
 * Los datos permanecen después de cerrar la app.
 */
data class RecentTrip(
    val destination: RoutePoint,
    val originName: String,
    val distanceKm: Double,
    val drivingMinutes: Int,
    val departureBatteryPercent: Float,
    val arrivalBatteryPercent: Double?,
    val createdAt: Long
)

object RecentTripsStore {

    private const val PREFS_NAME =
        "quackmove_recent_trips"

    private const val KEY_TRIPS = "saved_trips"

    private const val MAX_TRIPS = 30

    /**
     * Guarda un viaje al finalizar la planificación.
     */
    fun saveTrip(
        context: Context,
        destination: RoutePoint,
        originName: String,
        distanceKm: Double,
        drivingMinutes: Int,
        departureBatteryPercent: Float,
        arrivalBatteryPercent: Double?
    ) {

        if (!destination.isValid()) {
            return
        }

        val prefs = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

        val existing = loadTrips(context)

        // Mantener una sola entrada reciente
        // por destino y actualizarla al usarla.
        val filtered = existing.filterNot { trip ->

            trip.destination.latitude ==
                    destination.latitude &&
                    trip.destination.longitude ==
                    destination.longitude
        }

        val newTrip = RecentTrip(
            destination = destination,
            originName = originName,
            distanceKm = distanceKm,
            drivingMinutes = drivingMinutes,
            departureBatteryPercent =
                departureBatteryPercent,
            arrivalBatteryPercent =
                arrivalBatteryPercent,
            createdAt = System.currentTimeMillis()
        )

        val updated = (
                listOf(newTrip) + filtered
                ).take(MAX_TRIPS)

        val jsonArray = JSONArray()

        updated.forEach { trip ->

            val json = JSONObject().apply {

                put(
                    "destinationName",
                    trip.destination.name
                )

                put(
                    "destinationLatitude",
                    trip.destination.latitude
                )

                put(
                    "destinationLongitude",
                    trip.destination.longitude
                )

                put(
                    "originName",
                    trip.originName
                )

                put(
                    "distanceKm",
                    trip.distanceKm
                )

                put(
                    "drivingMinutes",
                    trip.drivingMinutes
                )

                put(
                    "departureBatteryPercent",
                    trip.departureBatteryPercent.toDouble()
                )

                put(
                    "arrivalBatteryPercent",
                    trip.arrivalBatteryPercent
                        ?: JSONObject.NULL
                )

                put(
                    "createdAt",
                    trip.createdAt
                )
            }

            jsonArray.put(json)
        }

        prefs.edit()
            .putString(
                KEY_TRIPS,
                jsonArray.toString()
            )
            .apply()
    }

    /**
     * Recupera los viajes almacenados.
     */
    fun loadTrips(
        context: Context
    ): List<RecentTrip> {

        val prefs = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

        val savedJson = prefs.getString(
            KEY_TRIPS,
            "[]"
        ) ?: "[]"

        val jsonArray = try {
            JSONArray(savedJson)
        } catch (exception: Exception) {
            return emptyList()
        }

        val result = mutableListOf<RecentTrip>()

        for (index in 0 until jsonArray.length()) {

            try {

                val json =
                    jsonArray.getJSONObject(index)

                val destination = RoutePoint(
                    name = json.getString(
                        "destinationName"
                    ),

                    latitude = json.getDouble(
                        "destinationLatitude"
                    ),

                    longitude = json.getDouble(
                        "destinationLongitude"
                    )
                )

                if (!destination.isValid()) {
                    continue
                }

                val arrivalBattery = if (
                    json.isNull("arrivalBatteryPercent")
                ) {
                    null
                } else {
                    json.optDouble(
                        "arrivalBatteryPercent"
                    ).takeIf {
                        it.isFinite()
                    }
                }

                result.add(
                    RecentTrip(
                        destination = destination,

                        originName = json.optString(
                            "originName",
                            "Tu ubicación"
                        ),

                        distanceKm = json.getDouble(
                            "distanceKm"
                        ),

                        drivingMinutes = json.getInt(
                            "drivingMinutes"
                        ),

                        departureBatteryPercent =
                            json.getDouble(
                                "departureBatteryPercent"
                            ).toFloat(),

                        arrivalBatteryPercent =
                            arrivalBattery,

                        createdAt = json.optLong(
                            "createdAt"
                        )
                    )
                )

            } catch (exception: Exception) {

                // Una entrada dañada no debe impedir
                // recuperar los demás viajes.
                continue
            }
        }

        return result.sortedByDescending {
            it.createdAt
        }
    }

    /**
     * Devuelve destinos para el buscador
     * y la sección Recientes.
     */
    fun loadRecentDestinations(
        context: Context
    ): List<RoutePoint> {

        return loadTrips(context).map {
            it.destination
        }
    }

    /**
     * Permite eliminar el historial si
     * posteriormente agregamos esa opción.
     */
    fun clearAll(
        context: Context
    ) {

        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        ).edit()
            .remove(KEY_TRIPS)
            .apply()
    }
}
