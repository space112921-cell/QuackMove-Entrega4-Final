
package com.quackmove.app.data.repository

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions

import kotlinx.coroutines.CancellationException
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
// CONTRATO DEL REPOSITORIO
// =====================================

interface RegistrationRepository {

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

        } catch (exception: CancellationException) {

            throw exception
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

        } catch (exception: CancellationException) {

            throw exception

        } catch (exception: Exception) {

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

        } catch (exception: CancellationException) {

            throw exception

        } catch (exception: Exception) {

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
}
