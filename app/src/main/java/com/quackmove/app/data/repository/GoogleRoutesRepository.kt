
package com.quackmove.app.data.repository

import android.util.Log

import com.google.firebase.auth.FirebaseAuth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

import org.json.JSONObject

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

import kotlin.math.ceil

// =====================================
// MODELO DE RUTA
// =====================================

data class RouteSummary(
    val origin: String,
    val destination: String,
    val distanceKm: Double,
    val drivingMinutes: Int,

    // Geometría para dibujar en MapLibre.
    val encodedPolyline: String = "",

    // Coordenadas de referencia.
    val originLatitude: Double? = null,
    val originLongitude: Double? = null,
    val destinationLatitude: Double? = null,
    val destinationLongitude: Double? = null
)

// =====================================
// GOOGLE ROUTES REPOSITORY
// =====================================

object GoogleRoutesRepository : RoutesRepository {

    private const val TAG = "QuackMoveRoutes"

    private const val ROUTES_URL =
        "https://quackmove-api.space-112921.workers.dev/route"

    // =====================================
    // MÉTODO ANTIGUO
    // =====================================

    @Deprecated(
        "Es necesario seleccionar origen y destino"
    )
    override suspend fun fetchRoute(): RouteSummary {
        throw IOException(
            "Selecciona un origen y un destino " +
                    "antes de calcular la ruta."
        )
    }

    // =====================================
    // CALCULAR RUTA REAL
    // =====================================

    override suspend fun fetchRoute(
        origin: RoutePoint,
        destination: RoutePoint
    ): RouteSummary = withContext(Dispatchers.IO) {

        require(origin.isValid()) {
            "El origen no tiene coordenadas válidas"
        }

        require(destination.isValid()) {
            "El destino no tiene coordenadas válidas"
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
                "No se pudo obtener el token de Firebase"
            )

        // =====================================
        // PREPARAR JSON
        // =====================================

        val requestBody = JSONObject().apply {

            put(
                "origin",
                JSONObject().apply {
                    put("latitude", origin.latitude)
                    put("longitude", origin.longitude)
                }
            )

            put(
                "destination",
                JSONObject().apply {
                    put("latitude", destination.latitude)
                    put("longitude", destination.longitude)
                }
            )
        }

        // =====================================
        // CONEXIÓN CON CLOUDFLARE
        // =====================================

        val connection = URL(ROUTES_URL)
            .openConnection() as HttpURLConnection

        try {

            connection.requestMethod = "POST"

            connection.connectTimeout = 10000
            connection.readTimeout = 30000

            connection.doOutput = true

            connection.setRequestProperty(
                "Content-Type",
                "application/json; charset=utf-8"
            )

            connection.setRequestProperty(
                "Authorization",
                "Bearer $token"
            )

            // =====================================
            // ENVIAR COORDENADAS
            // =====================================

            connection.outputStream.use { output ->

                output.write(
                    requestBody.toString()
                        .toByteArray(Charsets.UTF_8)
                )
            }

            // =====================================
            // VERIFICAR RESPUESTA
            // =====================================

            val statusCode = connection.responseCode

            if (statusCode != HttpURLConnection.HTTP_OK) {

                val errorBody = connection.errorStream
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()

                Log.e(
                    TAG,
                    "Error HTTP $statusCode: $errorBody"
                )

                val details = runCatching {

                    val json = JSONObject(errorBody)

                    json.optString("details")
                        .takeIf { it.isNotBlank() }
                        ?: json.optString("error")

                }.getOrNull()

                throw IOException(
                    "Error HTTP $statusCode" +
                            if (!details.isNullOrBlank()) {
                                ": $details"
                            } else {
                                ""
                            }
                )
            }

            // =====================================
            // LEER JSON DE GOOGLE ROUTES
            // =====================================

            val response = connection.inputStream
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }

            val json = JSONObject(response)

            val routes = json.optJSONArray("routes")
                ?: throw IOException(
                    "El servidor no devolvió rutas"
                )

            if (routes.length() == 0) {
                throw IOException(
                    "No se encontró una ruta entre " +
                            "los puntos seleccionados"
                )
            }

            val firstRoute = routes.optJSONObject(0)
                ?: throw IOException(
                    "La respuesta de ruta es inválida"
                )

            // =====================================
            // DISTANCIA
            // =====================================

            if (!firstRoute.has("distanceMeters")) {
                throw IOException(
                    "La ruta no contiene distancia"
                )
            }

            val distanceMeters = firstRoute
                .optDouble(
                    "distanceMeters",
                    Double.NaN
                )

            if (
                !distanceMeters.isFinite() ||
                distanceMeters < 0.0
            ) {
                throw IOException(
                    "La distancia recibida es inválida"
                )
            }

            val distanceKm =
                distanceMeters / 1000.0

            // =====================================
            // DURACIÓN
            // =====================================

            // Google devuelve valores como "1234s".
            val durationText = firstRoute
                .optString("duration")
                .removeSuffix("s")

            val durationSeconds = durationText
                .toDoubleOrNull()
                ?: throw IOException(
                    "La duración de ruta es inválida"
                )

            if (
                !durationSeconds.isFinite() ||
                durationSeconds < 0.0
            ) {
                throw IOException(
                    "La duración recibida es inválida"
                )
            }

            val drivingMinutes =
                ceil(durationSeconds / 60.0)
                    .toInt()

            // =====================================
            // GEOMETRÍA DE RUTA
            // =====================================

            val encodedPolyline = firstRoute
                .optJSONObject("polyline")
                ?.optString("encodedPolyline")
                .orEmpty()

            if (encodedPolyline.isBlank()) {
                Log.w(
                    TAG,
                    "Google Routes no devolvió polyline"
                )
            }

            Log.d(
                TAG,
                "Ruta calculada correctamente: " +
                        "$distanceKm km, " +
                        "$drivingMinutes minutos"
            )

            // =====================================
            // DEVOLVER RESULTADO
            // =====================================

            RouteSummary(
                origin = origin.name,
                destination = destination.name,
                distanceKm = distanceKm,
                drivingMinutes = drivingMinutes,
                encodedPolyline = encodedPolyline,

                originLatitude = origin.latitude,
                originLongitude = origin.longitude,

                destinationLatitude =
                    destination.latitude,

                destinationLongitude =
                    destination.longitude
            )

        } finally {

            connection.disconnect()
        }
    }
}
