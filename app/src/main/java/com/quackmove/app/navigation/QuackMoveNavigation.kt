
package com.quackmove.app.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.quackmove.app.feature.chargers.ChargersScreen
import com.quackmove.app.feature.registration.RegistrationScreen
import com.quackmove.app.feature.routes.RoutesScreen

object QuackMoveRoutes {
    const val MAP = "mapa"
    const val REGISTRATION = "registro"

    const val CHARGERS = "cargadores"
    const val CHARGERS_LOCATION =
        "cargadores/{latitude}/{longitude}"

    const val ROUTES = "rutas"
    const val ROUTE_DESTINATION = "rutas/{destination}"
}

@Composable
fun QuackMoveNavigation(
    navController: NavHostController,
    mapScreen: @Composable (
        onOpenRegistration: () -> Unit,
        onOpenChargers: (Double, Double) -> Unit,
        onOpenRoute: (String) -> Unit
    ) -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = QuackMoveRoutes.MAP
    ) {

        // Pantalla del mapa.
        composable(QuackMoveRoutes.MAP) { backStackEntry ->
            mapScreen(
                {
                    if (backStackEntry.isResumed()) {
                        navController.navigate(
                            QuackMoveRoutes.REGISTRATION
                        )
                    }
                },
                { latitude, longitude ->
                    if (backStackEntry.isResumed()) {
                        navController.navigate(
                            "${QuackMoveRoutes.CHARGERS}/" +
                                    "$latitude/$longitude"
                        )
                    }
                },
                { destination ->
                    if (backStackEntry.isResumed()) {
                        navController.navigate(
                            "${QuackMoveRoutes.ROUTES}/" +
                                    Uri.encode(destination)
                        )
                    }
                }
            )
        }

        // Registro.
        composable(QuackMoveRoutes.REGISTRATION) { backStackEntry ->
            RegistrationScreen(
                onBack = {
                    navController.popIfResumed(backStackEntry)
                }
            )
        }

        // Cargadores con ubicación dinámica.
        composable(
            route = QuackMoveRoutes.CHARGERS_LOCATION,
            arguments = listOf(
                navArgument("latitude") {
                    type = NavType.StringType
                },
                navArgument("longitude") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->

            val latitude = backStackEntry.arguments
                ?.getString("latitude")
                ?.toDoubleOrNull()

            val longitude = backStackEntry.arguments
                ?.getString("longitude")
                ?.toDoubleOrNull()

            ChargersScreen(
                latitude = latitude,
                longitude = longitude,
                onBack = {
                    navController.popIfResumed(backStackEntry)
                }
            )
        }

        // Pantalla de rutas.
        composable(
            route = QuackMoveRoutes.ROUTE_DESTINATION,
            arguments = listOf(
                navArgument("destination") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->

            val destination = backStackEntry.arguments
                ?.getString("destination")
                ?: "Destino seleccionado"

            RoutesScreen(
                destination = destination,
                onBack = {
                    navController.popIfResumed(backStackEntry)
                }
            )
        }
    }
}

// =====================================
// PROTECCION CONTRA DOBLE TOQUE
// =====================================
//
// Durante la animacion de salida la pantalla deja de estar
// RESUMED. Si el usuario toca dos veces "Volver al mapa",
// el segundo toque se ignora y ya no se saca el mapa de la
// pila (lo que dejaba la pantalla en blanco).

private fun NavBackStackEntry.isResumed(): Boolean =
    lifecycle.currentState == Lifecycle.State.RESUMED

private fun NavHostController.popIfResumed(
    entry: NavBackStackEntry
) {
    if (entry.isResumed()) {
        popBackStack()
    }
}
