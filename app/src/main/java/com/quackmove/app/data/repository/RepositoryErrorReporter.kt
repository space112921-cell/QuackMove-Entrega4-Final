package com.quackmove.app.data.repository

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

// =====================================
// CRASHLYTICS: ERRORES NO FATALES (M13)
// =====================================
//
// Todos los Repository registran aquí los errores
// que capturan. Se envían como no fatales: la app
// no se cierra y el ViewModel sigue mostrando su
// estado de error al usuario.

object RepositoryErrorReporter {

    private const val TAG = "QuackMoveCrashlytics"

    fun record(
        operation: String,
        exception: Throwable
    ) {

        // Cancelar una corrutina no es un error.
        if (exception is CancellationException) return

        Log.e(TAG, "Error en Repository: $operation", exception)

        try {

            val crashlytics = FirebaseCrashlytics.getInstance()

            // Solo el nombre de la operación: sin correos,
            // contraseñas ni datos del viaje.
            crashlytics.setCustomKey("repository_operation", operation)
            crashlytics.log("Repository: $operation")
            crashlytics.recordException(exception)

        } catch (reportingError: Exception) {

            // Un fallo del monitoreo no debe ocultar
            // el error original ni cerrar la app.
            Log.e(TAG, "No se pudo registrar el error", reportingError)
        }
    }
}

// Ejecuta una operación del Repository, registra en
// Crashlytics cualquier error y lo vuelve a lanzar
// para que el ViewModel muestre el estado de error.
internal suspend fun <T> withErrorReporting(
    operation: String,
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> T
): T {

    return try {

        withContext(context, block)

    } catch (timeout: TimeoutCancellationException) {

        // withTimeout lanza una CancellationException. Si se
        // relanzara tal cual, el ViewModel la trataría como una
        // cancelación y se quedaría cargando para siempre (M10).
        val error = IOException(
            "El servidor tardó demasiado en responder. " +
                    "Comprueba la conexión.",
            timeout
        )

        RepositoryErrorReporter.record(operation, error)

        throw error

    } catch (cancelled: CancellationException) {

        throw cancelled

    } catch (exception: Exception) {

        RepositoryErrorReporter.record(operation, exception)

        throw exception
    }
}
