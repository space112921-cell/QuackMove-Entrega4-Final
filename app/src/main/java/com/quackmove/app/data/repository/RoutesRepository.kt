
package com.quackmove.app.data.repository

// Coordenadas de un punto seleccionado.
data class RoutePoint(
    val name: String,
    val latitude: Double,
    val longitude: Double
) {
    fun isValid(): Boolean {
        return name.isNotBlank() &&
                latitude.isFinite() &&
                longitude.isFinite() &&
                latitude in -90.0..90.0 &&
                longitude in -180.0..180.0
    }
}

// Contrato para obtener rutas reales.
interface RoutesRepository {

    // Método anterior, conservado temporalmente
    // para evitar errores en otros archivos.
    @Deprecated(
        "Utiliza fetchRoute(origin, destination)"
    )
    suspend fun fetchRoute(): RouteSummary

    // Nuevo método con origen y destino reales.
    suspend fun fetchRoute(
        origin: RoutePoint,
        destination: RoutePoint
    ): RouteSummary
}
