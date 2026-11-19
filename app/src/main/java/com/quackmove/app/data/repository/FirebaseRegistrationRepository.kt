
package com.quackmove.app.data.repository

import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

// =====================================
// RESULTADO DE REGISTRO
// =====================================

data class RegistrationResult(
    val uid: String,
    val email: String,
    val profileSaved: Boolean,
    val profileError: String? = null
)

// =====================================
// ERROR DE REGISTRO
// =====================================
//
// Error ya traducido para el usuario. Así el ViewModel
// no necesita importar clases de Firebase (M6).

class RegistrationException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

// =====================================
// CONTRATO DEL REPOSITORIO
// =====================================

// Cuenta permanente con la que el usuario tiene sesión.
data class RegisteredAccount(
    val uid: String,
    val email: String,
    val username: String
)

interface RegistrationRepository {

    // Cuenta registrada actual (null si no hay sesión
    // o si la sesión es anónima).
    suspend fun currentAccount(): RegisteredAccount?

    // Cierra la sesión de la cuenta registrada.
    suspend fun signOut()

    suspend fun register(
        username: String,
        email: String,
        password: String
    ): RegistrationResult

    suspend fun retrySaveProfile(
        username: String
    ): RegistrationResult
}

// =====================================
// IMPLEMENTACION FIREBASE
// =====================================

class FirebaseRegistrationRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : RegistrationRepository {

    // =====================================
    // REGISTRAR CUENTA REAL
    // =====================================

    override suspend fun register(
        username: String,
        email: String,
        password: String
    ): RegistrationResult {

        val cleanUsername = username.trim()
        val cleanEmail = email.trim()

        require(cleanUsername.length >= 3) {
            "El nombre de usuario debe tener al menos 3 caracteres."
        }

        require(cleanEmail.isNotBlank()) {
            "Introduce un correo electrónico válido."
        }

        require(password.length >= 8) {
            "La contraseña debe tener al menos 8 caracteres."
        }

        val currentUser = auth.currentUser

        // =================================
        // PRESERVAR VIAJES ANONIMOS
        // =================================
        //
        // Si existe un usuario anónimo,
        // lo convertimos a cuenta permanente.
        //
        // Esto conserva su UID, el mismo
        // que FirestoreTripRepository utiliza
        // como ownerId de sus viajes.

        val user = try {

            if (currentUser?.isAnonymous == true) {

                val credential =
                    EmailAuthProvider.getCredential(
                        cleanEmail,
                        password
                    )

                withTimeout(20000L) {
                    currentUser
                        .linkWithCredential(credential)
                        .await()
                }.user

            } else {

                if (currentUser != null) {

                    throw IllegalStateException(
                        "Ya existe una sesión con cuenta registrada. " +
                                "Cierra esa sesión antes de crear otra cuenta."
                    )
                }

                withTimeout(20000L) {
                    auth.createUserWithEmailAndPassword(
                        cleanEmail,
                        password
                    ).await()
                }.user
            }

        } catch (timeout: TimeoutCancellationException) {

            throw registrationError(
                "register",
                timeout,
                "Firebase tardó demasiado en responder. " +
                        "Comprueba la conexión e inténtalo de nuevo."
            )

        } catch (exception: CancellationException) {

            throw exception

        } catch (exception: FirebaseException) {

            // El ViewModel no conoce las clases de Firebase (M6):
            // aquí se traducen a un mensaje para el usuario.
            throw registrationError(
                "register",
                exception,
                friendlyAuthMessage(exception)
            )
        }

        val registeredUser = user
            ?: throw IllegalStateException(
                "Firebase no devolvió el usuario registrado."
            )

        val registeredEmail =
            registeredUser.email ?: cleanEmail

        // =================================
        // GUARDAR PERFIL EN FIRESTORE
        // =================================
        //
        // La cuenta ya existe en Authentication.
        // Si Firestore falla, no creamos
        // otra cuenta ni perdemos el UID.

        return try {

            saveProfile(
                uid = registeredUser.uid,
                username = cleanUsername,
                email = registeredEmail
            )

            RegistrationResult(
                uid = registeredUser.uid,
                email = registeredEmail,
                profileSaved = true
            )

        } catch (timeout: TimeoutCancellationException) {

            RepositoryErrorReporter.record("saveProfile", timeout)

            RegistrationResult(
                uid = registeredUser.uid,
                email = registeredEmail,
                profileSaved = false,
                profileError = "Firestore tardó demasiado en responder."
            )

        } catch (exception: CancellationException) {

            throw exception

        } catch (exception: Exception) {

            RepositoryErrorReporter.record("saveProfile", exception)

            RegistrationResult(
                uid = registeredUser.uid,
                email = registeredEmail,
                profileSaved = false,
                profileError =
                    exception.message
                        ?: "No se pudo guardar el perfil."
            )
        }
    }

    // =====================================
    // REINTENTAR PERFIL
    // =====================================

    override suspend fun retrySaveProfile(
        username: String
    ): RegistrationResult {

        val user = auth.currentUser
            ?: throw IllegalStateException(
                "No existe una sesión activa."
            )

        if (user.isAnonymous) {

            throw IllegalStateException(
                "Todavía no se ha creado una cuenta permanente."
            )
        }

        val email = user.email
            ?: throw IllegalStateException(
                "La cuenta no tiene correo electrónico."
            )

        val cleanUsername = username.trim()

        require(cleanUsername.length >= 3) {
            "El nombre debe tener al menos 3 caracteres."
        }

        return try {

            saveProfile(
                uid = user.uid,
                username = cleanUsername,
                email = email
            )

            RegistrationResult(
                uid = user.uid,
                email = email,
                profileSaved = true
            )

        } catch (timeout: TimeoutCancellationException) {

            RepositoryErrorReporter.record("saveProfile", timeout)

            RegistrationResult(
                uid = user.uid,
                email = email,
                profileSaved = false,
                profileError = "Firestore tardó demasiado en responder."
            )

        } catch (exception: CancellationException) {

            throw exception

        } catch (exception: Exception) {

            RepositoryErrorReporter.record("saveProfile", exception)

            RegistrationResult(
                uid = user.uid,
                email = email,
                profileSaved = false,
                profileError =
                    exception.message
                        ?: "No se pudo guardar el perfil."
            )
        }
    }

    // =====================================
    // ESCRITURA DEL PERFIL
    // =====================================

    private suspend fun saveProfile(
        uid: String,
        username: String,
        email: String
    ) {

        val data = mapOf(
            "uid" to uid,
            "username" to username,
            "email" to email,
            "acceptedTerms" to true,
            "createdAt" to FieldValue.serverTimestamp()
        )

        withTimeout(20000L) {

            firestore
                .collection("users")
                .document(uid)
                .set(
                    data,
                    SetOptions.merge()
                )
                .await()
        }
    }

    // =====================================
    // TRADUCCION DE ERRORES DE FIREBASE
    // =====================================

    private fun registrationError(
        operation: String,
        cause: Exception,
        message: String
    ): RegistrationException {

        RepositoryErrorReporter.record(operation, cause)

        return RegistrationException(message, cause)
    }

    private fun friendlyAuthMessage(
        exception: FirebaseException
    ): String {

        return when (exception) {

            is FirebaseAuthUserCollisionException ->
                "Este correo ya está registrado. " +
                        "Utiliza otra dirección."

            is FirebaseAuthWeakPasswordException ->
                "Firebase rechazó la contraseña. " +
                        "Utiliza una contraseña más segura."

            is FirebaseNetworkException ->
                "No hay conexión. Comprueba internet."

            is FirebaseAuthException -> when (exception.errorCode) {

                "ERROR_EMAIL_ALREADY_IN_USE" ->
                    "Este correo ya está registrado."

                "ERROR_INVALID_EMAIL" ->
                    "El correo electrónico no es válido."

                "ERROR_NETWORK_REQUEST_FAILED" ->
                    "No hay conexión. Comprueba internet."

                "ERROR_OPERATION_NOT_ALLOWED" ->
                    "El registro con correo no está habilitado."

                else ->
                    "No se pudo crear la cuenta. Inténtalo de nuevo."
            }

            else ->
                "Error al conectar con Firebase. " +
                        "Comprueba internet e inténtalo nuevamente."
        }
    }

    // =====================================
    // CUENTA ACTUAL
    // =====================================

    override suspend fun currentAccount(): RegisteredAccount? {

        val user = auth.currentUser ?: return null

        if (user.isAnonymous) return null

        val email = user.email ?: return null

        // El nombre de usuario vive en Firestore (users/{uid}).
        // Si no se puede leer, se usa la parte antes de la @.
        val username = try {

            withTimeout(8000L) {
                firestore
                    .collection("users")
                    .document(user.uid)
                    .get()
                    .await()
                    .getString("username")
            }

        } catch (timeout: TimeoutCancellationException) {

            RepositoryErrorReporter.record("currentAccount", timeout)
            null

        } catch (exception: CancellationException) {

            throw exception

        } catch (exception: Exception) {

            RepositoryErrorReporter.record("currentAccount", exception)
            null
        }

        return RegisteredAccount(
            uid = user.uid,
            email = email,
            username = username?.takeIf { it.isNotBlank() }
                ?: email.substringBefore("@")
        )
    }

    // =====================================
    // CERRAR SESION
    // =====================================

    override suspend fun signOut() {

        auth.signOut()
    }
}
