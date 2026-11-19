
package com.quackmove.app.data.repository

import com.google.firebase.auth.FirebaseAuth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await

import org.json.JSONObject

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

// =====================================
// MODELO DE RESULTADO
// =====================================

data class PlaceSearchResult(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
) {

    fun toRoutePoint(): RoutePoint {
        return RoutePoint(
            name = name,
            latitude = latitude,
            longitude = longitude
        )
    }
}

// =====================================
// CONTRATO DE BUSQUEDA DE LUGARES
// =====================================

interface PlacesRepository {

    suspend fun searchPlaces(
        query: String,
        userLatitude: Double?,
        userLongitude: Double?
    ): List<PlaceSearchResult>
}

// =====================================
// REPOSITORIO DE GOOGLE PLACES
// =====================================

object GooglePlacesRepository : PlacesRepository {

    private const val PLACES_URL =
        "https://quackmove-api.space-112921.workers.dev/places"

    // =====================================
    // BUSCAR DESTINOS
    // =====================================

    override suspend fun searchPlaces(
        query: String,
        userLatitude: Double?,
        userLongitude: Double?
    ): List<PlaceSearchResult> =
        withErrorReporting(
            "GooglePlacesRepository.searchPlaces",
            Dispatchers.IO
        ) {

            val searchText = query.trim()

            if (searchText.length < 2) {
                return@withErrorReporting emptyList()
            }

            // =====================================
            // FIREBASE AUTH
            // =====================================

            val auth = FirebaseAuth.getInstance()

            val user = auth.currentUser
                ?: auth.signInAnonymously()
                    .await()
                    .user
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
            // PREPARAR PETICIÓN
            // =====================================

            val body = JSONObject().apply {

                put("query", searchText)

                if (
                    userLatitude != null &&
                    userLongitude != null &&
                    userLatitude.isFinite() &&
                    userLongitude.isFinite() &&
                    userLatitude in -90.0..90.0 &&
                    userLongitude in -180.0..180.0
                ) {

                    put(
                        "location",
                        JSONObject().apply {
                            put(
                                "latitude",
                                userLatitude
                            )

                            put(
                                "longitude",
                                userLongitude
                            )
                        }
                    )
                }
            }

            val connection =
                URL(PLACES_URL)
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

                // =====================================
                // ENVIAR BÚSQUEDA
                // =====================================

                connection.outputStream.use { output ->

                    output.write(
                        body.toString()
                            .toByteArray(
                                Charsets.UTF_8
                            )
                    )
                }

                // =====================================
                // LEER RESPUESTA HTTP
                // =====================================

                val statusCode =
                    connection.responseCode

                if (
                    statusCode !=
                    HttpURLConnection.HTTP_OK
                ) {

                    val errorBody =
                        connection.errorStream
                            ?.bufferedReader(
                                Charsets.UTF_8
                            )
                            ?.use {
                                it.readText()
                            }
                            .orEmpty()

                    val details = runCatching {

                        val errorJson =
                            JSONObject(errorBody)

                        errorJson
                            .optString("details")
                            .takeIf {
                                it.isNotBlank()
                            }
                            ?: errorJson.optString(
                                "error"
                            )

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
                // LEER LUGARES
                // =====================================

                val response =
                    connection.inputStream
                        .bufferedReader(
                            Charsets.UTF_8
                        )
                        .use {
                            it.readText()
                        }

                val json = JSONObject(response)

                val places = json.optJSONArray(
                    "places"
                ) ?: return@withErrorReporting emptyList()

                // =====================================
                // CONVERTIR RESULTADOS
                // =====================================

                (0 until places.length())
                    .mapNotNull { index ->

                        val place = places
                            .optJSONObject(index)
                            ?: return@mapNotNull null

                        val name = place
                            .optJSONObject("displayName")
                            ?.optString("text")
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?: return@mapNotNull null

                        val location = place
                            .optJSONObject("location")
                            ?: return@mapNotNull null

                        val latitude = location.optDouble(
                            "latitude",
                            Double.NaN
                        )

                        val longitude = location.optDouble(
                            "longitude",
                            Double.NaN
                        )

                        if (
                            !latitude.isFinite() ||
                            !longitude.isFinite() ||
                            latitude !in -90.0..90.0 ||
                            longitude !in -180.0..180.0
                        ) {
                            return@mapNotNull null
                        }

                        PlaceSearchResult(
                            id = place.optString("id"),
                            name = name,
                            address = place
                                .optString(
                                    "formattedAddress"
                                ),
                            latitude = latitude,
                            longitude = longitude
                        )
                    }
                    .distinctBy {
                        if (it.id.isNotBlank()) {
                            it.id
                        } else {
                            "${it.latitude},${it.longitude}"
                        }
                    }

            } finally {

                connection.disconnect()
            }
        }
}
