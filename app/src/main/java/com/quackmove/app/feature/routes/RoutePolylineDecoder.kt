
package com.quackmove.app.feature.routes

import org.maplibre.android.geometry.LatLng

/**
 * Convierte una polilínea codificada de Google Routes
 * en puntos geográficos que entiende MapLibre.
 *
 * Google utiliza precisión de 5 decimales
 * para el formato Encoded Polyline estándar.
 */
object RoutePolylineDecoder {

    fun decode(encoded: String): List<LatLng> {

        if (encoded.isBlank()) {
            return emptyList()
        }

        val points = mutableListOf<LatLng>()

        var index = 0
        var latitude = 0L
        var longitude = 0L

        fun nextValue(): Long {

            var result = 0L
            var shift = 0

            while (true) {

                if (index >= encoded.length) {
                    throw IllegalArgumentException(
                        "Polilínea incompleta"
                    )
                }

                val value = encoded[index++].code - 63

                if (value !in 0..63) {
                    throw IllegalArgumentException(
                        "Polilínea con caracteres inválidos"
                    )
                }

                result = result or
                        ((value.toLong() and 0x1fL) shl shift)

                if (value < 0x20) {
                    break
                }

                shift += 5

                if (shift > 60) {
                    throw IllegalArgumentException(
                        "Polilínea inválida"
                    )
                }
            }

            return if ((result and 1L) != 0L) {
                (result shr 1).inv()
            } else {
                result shr 1
            }
        }

        while (index < encoded.length) {

            latitude += nextValue()
            longitude += nextValue()

            val lat = latitude / 100000.0
            val lon = longitude / 100000.0

            if (
                lat !in -90.0..90.0 ||
                lon !in -180.0..180.0
            ) {
                throw IllegalArgumentException(
                    "Coordenadas fuera de rango"
                )
            }

            points.add(
                LatLng(lat, lon)
            )
        }

        return points
    }
}
