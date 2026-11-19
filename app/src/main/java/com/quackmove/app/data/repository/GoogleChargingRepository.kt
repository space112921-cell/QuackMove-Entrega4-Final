
package com.quackmove.app.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.quackmove.app.data.model.ChargingStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object GoogleChargingRepository : ChargingRepository {

    private const val TAG = "QuackMoveEV"

    private const val CHARGERS_URL =
        "https://quackmove-api.space-112921.workers.dev/chargers"

    // =====================================
    // PRUEBA TEMPORAL DE DATOS TÉCNICOS
    // =====================================

    // Cambiar a true únicamente para una prueba
    // autorizada de evChargeOptions.
    // Este campo puede tener costos adicionales.
    private const val TEST_EV_DETAILS = false

    // =====================================
    // CONSULTAR CARGADORES CERCANOS
    // =====================================

    override suspend fun fetchStations(
        latitude: Double,
        longitude: Double
    ): List<ChargingStation> = withErrorReporting(
        "GoogleChargingRepository.fetchStations",
        Dispatchers.IO
    ) {

        require(
            latitude.isFinite() &&
                    latitude in -90.0..90.0
        ) {
            "Latitud inválida"
        }

        require(
            longitude.isFinite() &&
                    longitude in -180.0..180.0
        ) {
            "Longitud inválida"
        }

        // =====================================
        // AUTENTICACIÓN FIREBASE
        // =====================================

        val auth = FirebaseAuth.getInstance()

        val user = auth.currentUser
            ?: auth.signInAnonymously().await().user
            ?: throw IOException(
                "No se pudo iniciar sesión en Firebase"
            )

        val token = user.getIdToken(false)
            .await()
            .token
            ?: throw IOException(
                "No se pudo obtener el token Firebase"
            )

        // =====================================
        // PREPARAR PETICIÓN A CLOUDFLARE
        // =====================================

        val requestBody = JSONObject().apply {

            put(
                "location",
                JSONObject().apply {
                    put("latitude", latitude)
                    put("longitude", longitude)
                }
            )

            if (TEST_EV_DETAILS) {
                put("includeEvDetails", true)
            }
        }

        Log.d(
            TAG,
            "Consultando cargadores cerca de: " +
                    "$latitude, $longitude"
        )

        // =====================================
        // CONEXIÓN HTTPS
        // =====================================

        val connection = URL(CHARGERS_URL)
            .openConnection() as HttpURLConnection

        try {

            connection.requestMethod = "POST"
            connection.connectTimeout = 10000
            connection.readTimeout = 25000
            connection.doOutput = true

            connection.setRequestProperty(
                "Content-Type",
                "application/json; charset=utf-8"
            )

            connection.setRequestProperty(
                "Authorization",
                "Bearer $token"
            )

            connection.outputStream.use { output ->
                output.write(
                    requestBody.toString()
                        .toByteArray(Charsets.UTF_8)
                )
            }

            // =====================================
            // LEER ESTADO HTTP
            // =====================================

            val statusCode = connection.responseCode

            if (statusCode != HttpURLConnection.HTTP_OK) {

                val errorBody = connection.errorStream
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()

                Log.e(
                    TAG,
                    "HTTP $statusCode: $errorBody"
                )

                // No imprimir tokens ni claves API.
                val serverMessage = runCatching {
                    val errorJson = JSONObject(errorBody)

                    errorJson.optString("details")
                        .takeIf { it.isNotBlank() }
                        ?: errorJson.optString("error")
                }.getOrNull()

                throw IOException(
                    "HTTP $statusCode" +
                            if (!serverMessage.isNullOrBlank()) {
                                ": $serverMessage"
                            } else {
                                ""
                            }
                )
            }

            // =====================================
            // LEER RESPUESTA JSON
            // =====================================

            val response = connection.inputStream
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }

            val responseJson = JSONObject(response)

            val evDetailsRequested =
                responseJson.optBoolean(
                    "evDetailsRequested",
                    false
                )

            Log.d(
                TAG,
                "Datos técnicos solicitados: " +
                        evDetailsRequested
            )

            val chargers = responseJson
                .optJSONArray("chargers")
                ?: throw IOException(
                    "Cloudflare no devolvió el arreglo chargers"
                )

            Log.d(
                TAG,
                "Estaciones recibidas: ${chargers.length()}"
            )

            // =====================================
            // PROCESAR CADA ESTACIÓN
            // =====================================

            (0 until chargers.length()).mapNotNull { index ->

                val charger = chargers.optJSONObject(index)
                    ?: return@mapNotNull null

                // Nombre
                val name = charger
                    .optJSONObject("displayName")
                    ?.optString("text")
                    ?.takeIf { it.isNotBlank() }
                    ?: "Estación sin nombre"

                // Dirección
                val address = charger
                    .optString("formattedAddress")
                    .takeIf { it.isNotBlank() }
                    ?: "Dirección desconocida"

                // =====================================
                // DISTANCIA
                // =====================================

                val distanceKm = charger.optDouble(
                    "distanceKm",
                    Double.NaN
                )

                val distanceText =
                    if (
                        distanceKm.isFinite() &&
                        distanceKm >= 0.0
                    ) {
                        String.format(
                            Locale.getDefault(),
                            "%.1f km",
                            distanceKm
                        )
                    } else {
                        ""
                    }

                // =====================================
                // COORDENADAS
                // =====================================

                val location = charger
                    .optJSONObject("location")

                val stationLatitude = location
                    ?.optDouble(
                        "latitude",
                        Double.NaN
                    )
                    ?.takeIf {
                        it.isFinite() &&
                                it in -90.0..90.0
                    }

                val stationLongitude = location
                    ?.optDouble(
                        "longitude",
                        Double.NaN
                    )
                    ?.takeIf {
                        it.isFinite() &&
                                it in -180.0..180.0
                    }

                // =====================================
                // INFORMACIÓN TÉCNICA EV
                // =====================================

                val evDetails = charger
                    .optJSONObject("evDetails")

                if (evDetails != null) {

                    Log.d(
                        TAG,
                        "Estación $index: $name"
                    )

                    Log.d(
                        TAG,
                        "Datos EV: ${evDetails.toString()}"
                    )

                } else if (TEST_EV_DETAILS) {

                    Log.d(
                        TAG,
                        "Estación $index: $name " +
                                "sin datos técnicos"
                    )
                }

                // =====================================
                // INTERPRETAR CONECTORES
                // =====================================

                val connectors = evDetails
                    ?.optJSONArray("connectors")

                var connectorType: String? = null
                var powerKw: Int? = null
                var availability: Boolean? = null

                if (connectors != null) {

                    var maxPower: Double? = null

                    var availableConnectors = 0
                    var knownAvailable = false

                    var connectorCount = 0
                    var knownCount = false

                    for (i in 0 until connectors.length()) {

                        val connector = connectors
                            .optJSONObject(i)
                            ?: continue

                        val type = connector
                            .optString("type")
                            .takeIf {
                                it.isNotBlank()
                            }

                        // Preferir CCS2 para el resumen
                        // cuando esté registrado.
                        if (
                            connectorType == null ||
                            (
                                    type?.contains(
                                        "CCS_COMBO_2",
                                        ignoreCase = true
                                    ) == true
                                    )
                        ) {
                            connectorType = type
                        }

                        val power = connector.optDouble(
                            "maxChargeRateKw",
                            Double.NaN
                        )

                        if (
                            power.isFinite() &&
                            power > 0
                        ) {
                            maxPower = maxOf(
                                maxPower ?: 0.0,
                                power
                            )
                        }

                        if (
                            connector.has("availableCount") &&
                            !connector.isNull("availableCount")
                        ) {
                            availableConnectors +=
                                connector.optInt(
                                    "availableCount",
                                    0
                                )

                            knownAvailable = true
                        }

                        if (
                            connector.has("count") &&
                            !connector.isNull("count")
                        ) {
                            connectorCount +=
                                connector.optInt(
                                    "count",
                                    0
                                )

                            knownCount = true
                        }
                    }

                    powerKw = maxPower
                        ?.toInt()
                        ?.takeIf { it > 0 }

                    availability = when {
                        knownAvailable &&
                                availableConnectors > 0 -> true

                        knownAvailable &&
                                knownCount &&
                                connectorCount > 0 -> false

                        else -> null
                    }
                }

                // Mostrar etiquetas comprensibles.
                val normalizedConnector = when {
                    connectorType == null -> null

                    connectorType.contains(
                        "CCS_COMBO_2",
                        ignoreCase = true
                    ) -> "CCS2"

                    connectorType.contains(
                        "CCS_COMBO_1",
                        ignoreCase = true
                    ) -> "CCS1"

                    connectorType.contains(
                        "CHADEMO",
                        ignoreCase = true
                    ) -> "CHAdeMO"

                    connectorType.contains(
                        "TYPE_2",
                        ignoreCase = true
                    ) -> "Tipo 2"

                    connectorType.contains(
                        "TYPE_1",
                        ignoreCase = true
                    ) -> "Tipo 1"

                    else -> connectorType
                }

                // =====================================
                // CREAR MODELO CHARGING STATION
                // =====================================

                ChargingStation(
                    name = name,
                    location = address,
                    distance = distanceText,
                    connector = normalizedConnector,
                    powerKw = powerKw,
                    available = availability,
                    latitude = stationLatitude,
                    longitude = stationLongitude
                )
            }

        } finally {

            connection.disconnect()
        }
    }
}
