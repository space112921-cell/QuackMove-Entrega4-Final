
package com.quackmove.app.data.repository

import com.quackmove.app.data.model.ChargingStation
import com.quackmove.app.domain.BatteryEstimator
import com.quackmove.app.domain.BatteryEstimate
import com.quackmove.app.feature.routes.RoutePolylineDecoder
import org.maplibre.android.geometry.LatLng
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.roundToInt

// =====================================
// MODELO DE PARADA INTELIGENTE
// =====================================

data class SmartChargingStop(
    val point: RoutePoint,
    val distanceFromPreviousKm: Double,
    val estimatedArrivalPercent: Double,
    val reportedAvailability: Boolean?
)

// =====================================
// MODELO DEL PLAN COMPLETO
// =====================================

data class SmartChargingPlan(
    val route: RouteSummary,
    val battery: BatteryEstimate,
    val stops: List<SmartChargingStop>
)

// =====================================
// REPOSITORIO DE CARGA INTELIGENTE
// =====================================

class SmartChargingRepository(
    private val fetchRoute:
    suspend (RoutePoint, RoutePoint) -> RouteSummary,
    private val fetchStations:
    suspend (Double, Double) -> List<ChargingStation>
) {

    companion object {

        const val BATTERY_KWH = 60.0
        const val CONSUMPTION_KWH_PER_100_KM = 18.0
        const val RESERVE_PERCENT = 10.0
        const val CHARGE_TO_PERCENT = 80.0

        private const val MAX_STOPS = 3
        private const val MAX_SEARCHES_PER_STOP = 3
        private const val MAX_CANDIDATES_PER_STOP = 5

        private const val MAX_CORRIDOR_KM = 25.0
        private const val MAX_EXTRA_DRIVING_KM = 30.0
    }

    // =====================================
    // CANDIDATO VERIFICADO
    // =====================================

    private data class ReachableCandidate(
        val station: ChargingStation,
        val point: RoutePoint,
        val routeToStation: RouteSummary,
        val arrivalPercent: Double,
        val progressKm: Double
    )

    // =====================================
    // PLANIFICAR VIAJE
    // =====================================

    suspend fun plan(
        origin: RoutePoint,
        destination: RoutePoint,
        departurePercent: Float
    ): SmartChargingPlan {

        require(origin.isValid() && destination.isValid()) {
            "Selecciona un origen y un destino válidos."
        }

        require(departurePercent in 0f..100f) {
            "El porcentaje de batería debe estar entre 0 y 100."
        }

        var currentPoint = origin
        var currentPercent = departurePercent.toDouble()

        val usedStations = mutableSetOf<String>()
        val stops = mutableListOf<SmartChargingStop>()
        val legs = mutableListOf<RouteSummary>()

        // Hasta tres estaciones intermedias.
        // El último tramo termina en el destino.

        repeat(MAX_STOPS + 1) { legIndex ->

            val directRoute = fetchRoute(
                currentPoint,
                destination
            )

            requireValidRoute(directRoute)

            // =================================
            // COMPROBAR SI ALCANZA LA BATERIA
            // =================================

            val directBattery = BatteryEstimator.calculate(
                distanceKm = directRoute.distanceKm,
                usableBatteryKwh = BATTERY_KWH,
                consumptionKwhPer100Km =
                    CONSUMPTION_KWH_PER_100_KM,
                departurePercent = currentPercent,
                minimumArrivalPercent = RESERVE_PERCENT
            )

            if (directBattery.canCompleteTrip) {

                legs.add(directRoute)

                val lastLegArrival =
                    directBattery.estimatedArrivalPercent
                        ?: (
                                currentPercent -
                                        percentUsed(
                                            directRoute.distanceKm
                                        )
                                )

                // Combinar todos los tramos.
                val wholeRoute = mergeLegs(legs)

                val totalPercentUsed =
                    percentUsed(wholeRoute.distanceKm)

                val totalEnergyKwh =
                    energyKwh(wholeRoute.distanceKm)

                // Llegada después de las recargas.
                // Consumo total de todos los tramos.

                val finalBattery = directBattery.copy(
                    energyRequiredKwh = totalEnergyKwh,
                    batteryUsedPercent = totalPercentUsed,
                    estimatedArrivalPercent = lastLegArrival,
                    canCompleteTrip = true,
                    missingEnergyKwh = 0.0
                )

                return SmartChargingPlan(
                    route = wholeRoute,
                    battery = finalBattery,
                    stops = stops.toList()
                )
            }

            // =================================
            // LIMITE DE PARADAS
            // =================================

            if (legIndex >= MAX_STOPS) {

                throw IllegalStateException(
                    "No pudimos encontrar una ruta " +
                            "que respete el 10% de reserva " +
                            "con un máximo de $MAX_STOPS paradas."
                )
            }

            // =================================
            // BUSCAR SIGUIENTE CARGADOR
            // =================================

            val candidate = findReachableCharger(
                origin = currentPoint,
                directRoute = directRoute,
                currentPercent = currentPercent,
                usedStations = usedStations
            ) ?: throw IllegalStateException(
                "La batería no alcanza hasta el destino " +
                        "y no encontramos una estación " +
                        "verificablemente alcanzable junto " +
                        "a la ruta. Comprueba una alternativa " +
                        "de carga antes de viajar."
            )

            // =================================
            // AGREGAR TRAMO A LA ESTACION
            // =================================

            legs.add(candidate.routeToStation)

            stops.add(
                SmartChargingStop(
                    point = candidate.point,
                    distanceFromPreviousKm =
                        candidate.routeToStation.distanceKm,
                    estimatedArrivalPercent =
                        candidate.arrivalPercent,
                    reportedAvailability =
                        candidate.station.available
                )
            )

            usedStations.add(
                stationKey(candidate.station)
            )

            // En la estación se supone que
            // se recarga hasta el 80%.

            currentPoint = candidate.point
            currentPercent = CHARGE_TO_PERCENT
        }

        error("No se pudo completar el plan de carga.")
    }

    // =====================================
    // BUSCAR CARGADOR ALCANZABLE
    // =====================================

    private suspend fun findReachableCharger(
        origin: RoutePoint,
        directRoute: RouteSummary,
        currentPercent: Double,
        usedStations: Set<String>
    ): ReachableCandidate? {

        val points = RoutePolylineDecoder.decode(
            directRoute.encodedPolyline
        )

        if (points.size < 2) {
            return null
        }

        // Distancia máxima posible conservando
        // la reserva del 10%.

        val usableKm =
            (currentPercent - RESERVE_PERCENT) /
                    percentUsed(1.0)

        if (usableKm <= 8.0) {
            return null
        }

        // =================================
        // BUSCAR A LO LARGO DEL RECORRIDO
        // =================================

        val anchorsKm = listOf(
            0.78,
            0.56,
            0.90
        )
            .take(MAX_SEARCHES_PER_STOP)
            .map { fraction ->
                min(
                    usableKm * fraction,
                    directRoute.distanceKm * 0.92
                )
            }
            .filter {
                it >= 5.0
            }
            .distinct()

        val stations = mutableListOf<ChargingStation>()

        // Las consultas se limitan para
        // controlar la cantidad de llamadas
        // a Google Places.

        for (distanceKm in anchorsKm) {

            val anchor = pointAlongRoute(
                points,
                fraction =
                    distanceKm / directRoute.distanceKm
            )

            try {

                stations.addAll(
                    fetchStations(
                        anchor.latitude,
                        anchor.longitude
                    )
                )

            } catch (
                cancellation:
                kotlinx.coroutines.CancellationException
            ) {

                throw cancellation

            } catch (_: Exception) {

                // Si un punto falla, se prueban
                // los demás puntos de búsqueda.
            }
        }

        // =================================
        // FILTRAR ESTACIONES
        // =================================

        val candidates = stations
            .distinctBy(::stationKey)
            .filter { station ->

                val latitude = station.latitude
                val longitude = station.longitude

                latitude != null &&
                        longitude != null &&
                        latitude.isFinite() &&
                        longitude.isFinite() &&
                        latitude in -90.0..90.0 &&
                        longitude in -180.0..180.0 &&
                        station.available != false &&
                        stationKey(station) !in usedStations
            }
            .mapNotNull { station ->

                val lat =
                    station.latitude ?: return@mapNotNull null

                val lon =
                    station.longitude ?: return@mapNotNull null

                val projected = progressAlongRoute(
                    points,
                    directRoute.distanceKm,
                    LatLng(lat, lon)
                )

                if (
                    projected.second > MAX_CORRIDOR_KM ||
                    projected.first < 5.0 ||
                    projected.first > usableKm
                ) {

                    null

                } else {

                    station to projected.first
                }
            }
            .sortedByDescending {
                it.second
            }
            .take(MAX_CANDIDATES_PER_STOP)

        // =================================
        // VALIDAR CON GOOGLE ROUTES
        // =================================

        val reachable = mutableListOf<ReachableCandidate>()

        for ((station, projectedKm) in candidates) {

            val point = RoutePoint(
                name = station.name,
                latitude = station.latitude ?: continue,
                longitude = station.longitude ?: continue
            )

            val routeToStation = try {

                fetchRoute(origin, point)

            } catch (
                cancellation:
                kotlinx.coroutines.CancellationException
            ) {

                throw cancellation

            } catch (_: Exception) {

                continue
            }

            if (
                routeToStation.distanceKm <= 0.0 ||
                routeToStation.distanceKm > usableKm ||
                routeToStation.encodedPolyline.isBlank()
            ) {

                continue
            }

            // No aceptar grandes desvíos
            // respecto de la ruta original.

            val extraKm = max(
                0.0,
                routeToStation.distanceKm - projectedKm
            )

            if (extraKm > MAX_EXTRA_DRIVING_KM) {
                continue
            }

            // Comprobar energía con distancia
            // real de Google Routes.

            val battery = BatteryEstimator.calculate(
                distanceKm = routeToStation.distanceKm,
                usableBatteryKwh = BATTERY_KWH,
                consumptionKwhPer100Km =
                    CONSUMPTION_KWH_PER_100_KM,
                departurePercent = currentPercent,
                minimumArrivalPercent = RESERVE_PERCENT
            )

            if (!battery.canCompleteTrip) {
                continue
            }

            reachable.add(
                ReachableCandidate(
                    station = station,
                    point = point,
                    routeToStation = routeToStation,
                    arrivalPercent =
                        battery.estimatedArrivalPercent
                            ?: (
                                    currentPercent -
                                            percentUsed(
                                                routeToStation.distanceKm
                                            )
                                    ),
                    progressKm = projectedKm
                )
            )
        }

        // Elegir una estación que permita
        // avanzar sin perder la reserva.

        return reachable.maxByOrNull {
            it.progressKm
        }
    }

    // =====================================
    // VALIDAR RUTA
    // =====================================

    private fun requireValidRoute(
        route: RouteSummary
    ) {

        if (
            route.distanceKm <= 0.0 ||
            !route.distanceKm.isFinite() ||
            route.encodedPolyline.isBlank() ||
            RoutePolylineDecoder.decode(
                route.encodedPolyline
            ).size < 2
        ) {

            throw IllegalStateException(
                "Google Routes devolvió una ruta incompleta."
            )
        }
    }

    // =====================================
    // COMBINAR TRAMOS
    // =====================================

    private fun mergeLegs(
        legs: List<RouteSummary>
    ): RouteSummary {

        if (legs.size == 1) {
            return legs.first()
        }

        val allPoints = mutableListOf<LatLng>()

        for ((index, leg) in legs.withIndex()) {

            val points = RoutePolylineDecoder.decode(
                leg.encodedPolyline
            )

            if (points.size < 2) {

                throw IllegalStateException(
                    "Un tramo no tiene geometría válida."
                )
            }

            allPoints.addAll(
                if (index == 0) {
                    points
                } else {
                    points.drop(1)
                }
            )
        }

        return legs.first().copy(
            destination = legs.last().destination,
            distanceKm = legs.sumOf {
                it.distanceKm
            },
            drivingMinutes = legs.sumOf {
                it.drivingMinutes
            },
            encodedPolyline = encodePolyline(allPoints)
        )
    }

    // =====================================
    // FORMULAS DE ENERGIA
    // =====================================

    private fun energyKwh(
        kilometers: Double
    ): Double {

        return kilometers *
                CONSUMPTION_KWH_PER_100_KM / 100.0
    }

    private fun percentUsed(
        kilometers: Double
    ): Double {

        return energyKwh(kilometers) /
                BATTERY_KWH * 100.0
    }

    // =====================================
    // IDENTIFICACION DE ESTACION
    // =====================================

    private fun stationKey(
        station: ChargingStation
    ): String {

        return "${station.latitude}:" +
                "${station.longitude}:" +
                station.name.trim()
    }

    // =====================================
    // PUNTO A UNA FRACCION DE LA RUTA
    // =====================================

    private fun pointAlongRoute(
        points: List<LatLng>,
        fraction: Double
    ): LatLng {

        val distances = points.zipWithNext(
            ::distanceKm
        )

        val target = distances.sum() *
                fraction.coerceIn(0.0, 1.0)

        var traversed = 0.0

        for (i in distances.indices) {

            val length = distances[i]

            if (
                traversed + length >= target &&
                length > 0.0
            ) {

                val t =
                    (target - traversed) / length

                return LatLng(
                    points[i].latitude +
                            t * (
                            points[i + 1].latitude -
                                    points[i].latitude
                            ),
                    points[i].longitude +
                            t * (
                            points[i + 1].longitude -
                                    points[i].longitude
                            )
                )
            }

            traversed += length
        }

        return points.last()
    }

    // =====================================
    // PROGRESO Y DISTANCIA A LA RUTA
    // =====================================

    private fun progressAlongRoute(
        points: List<LatLng>,
        roadKm: Double,
        position: LatLng
    ): Pair<Double, Double> {

        val lengths = points.zipWithNext(
            ::distanceKm
        )

        val totalGeometry = lengths.sum()

        if (totalGeometry <= 0.0) {

            return 0.0 to Double.POSITIVE_INFINITY
        }

        var covered = 0.0
        var bestDistance = Double.POSITIVE_INFINITY
        var bestProgress = 0.0

        for (i in lengths.indices) {

            val a = points[i]
            val b = points[i + 1]

            val latitudeWeight = cos(
                Math.toRadians(position.latitude)
            )

            val dx =
                (b.longitude - a.longitude) *
                        latitudeWeight

            val dy =
                b.latitude - a.latitude

            val px =
                (position.longitude - a.longitude) *
                        latitudeWeight

            val py =
                position.latitude - a.latitude

            val lengthSquared =
                dx * dx + dy * dy

            val t = if (lengthSquared == 0.0) {

                0.0

            } else {

                (
                        (px * dx + py * dy) /
                                lengthSquared
                        ).coerceIn(0.0, 1.0)
            }

            val projected = LatLng(
                a.latitude +
                        t * (b.latitude - a.latitude),
                a.longitude +
                        t * (b.longitude - a.longitude)
            )

            val distance = distanceKm(
                projected,
                position
            )

            if (distance < bestDistance) {

                bestDistance = distance

                bestProgress =
                    (covered + t * lengths[i]) /
                            totalGeometry * roadKm
            }

            covered += lengths[i]
        }

        return bestProgress to bestDistance
    }

    // =====================================
    // DISTANCIA GEOGRAFICA
    // =====================================

    private fun distanceKm(
        a: LatLng,
        b: LatLng
    ): Double {

        val earthRadiusKm = 6371.0

        val lat = Math.toRadians(
            b.latitude - a.latitude
        )

        val lon = Math.toRadians(
            b.longitude - a.longitude
        )

        val x = sin(lat / 2).pow(2.0) +
                cos(Math.toRadians(a.latitude)) *
                cos(Math.toRadians(b.latitude)) *
                sin(lon / 2).pow(2.0)

        return earthRadiusKm *
                2.0 *
                asin(min(1.0, sqrt(x)))
    }

    // =====================================
    // CODIFICAR POLYLINE COMBINADA
    // =====================================

    private fun encodePolyline(
        points: List<LatLng>
    ): String = buildString {

        var previousLat = 0
        var previousLon = 0

        for (point in points) {

            val lat = (
                    point.latitude * 100000.0
                    ).roundToInt()

            val lon = (
                    point.longitude * 100000.0
                    ).roundToInt()

            appendEncodedNumber(
                lat - previousLat
            )

            appendEncodedNumber(
                lon - previousLon
            )

            previousLat = lat
            previousLon = lon
        }
    }

    private fun StringBuilder.appendEncodedNumber(
        number: Int
    ) {

        var value = if (number < 0) {

            (number shl 1).inv()

        } else {

            number shl 1
        }

        while (value >= 0x20) {

            append(
                (
                        (0x20 or (value and 0x1F)) +
                                63
                        ).toChar()
            )

            value = value ushr 5
        }

        append((value + 63).toChar())
    }
}
