
package com.quackmove.app.feature.registration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope

import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

import com.quackmove.app.data.repository.RegistrationRepository
import com.quackmove.app.domain.RegistrationValidator

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// =====================================
// ESTADO INMUTABLE DE REGISTRO
// =====================================

data class RegistrationUiState(

    val username: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",

    val acceptedTerms: Boolean = false,

    val showPassword: Boolean = false,
    val showConfirmation: Boolean = false,

    val isLoading: Boolean = false,

    val registrationComplete: Boolean = false,

    // Authentication creada, pero
    // puede faltar guardar el perfil.
    val accountCreated: Boolean = false,

    val firebaseUid: String? = null,

    val errorMessage: String? = null,

    val successMessage: String? = null

) {

    // =================================
    // VALIDACIONES EXISTENTES
    // =================================

    val validUsername: Boolean
        get() = RegistrationValidator
            .validUsername(username)

    val validEmail: Boolean
        get() = RegistrationValidator
            .validEmail(email)

    val validPassword: Boolean
        get() = RegistrationValidator
            .validPassword(password)

    val validConfirmation: Boolean
        get() = RegistrationValidator
            .validConfirmation(
                password,
                confirmPassword
            )

    val validForm: Boolean
        get() =
            validUsername &&
                    validEmail &&
                    validPassword &&
                    validConfirmation &&
                    acceptedTerms &&
                    !isLoading &&
                    !accountCreated
}

// =====================================
// REGISTRATION VIEWMODEL
// =====================================

class RegistrationViewModel(
    private val repository: RegistrationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        RegistrationUiState()
    )

    val uiState: StateFlow<RegistrationUiState> =
        _uiState.asStateFlow()

    // =====================================
    // ACTUALIZAR NOMBRE
    // =====================================

    fun updateUsername(value: String) {

        if (_uiState.value.isLoading ||
            _uiState.value.accountCreated
        ) {
            return
        }

        _uiState.update {
            it.copy(
                username = value,
                registrationComplete = false,
                errorMessage = null
            )
        }
    }

    // =====================================
    // ACTUALIZAR CORREO
    // =====================================

    fun updateEmail(value: String) {

        if (_uiState.value.isLoading ||
            _uiState.value.accountCreated
        ) {
            return
        }

        _uiState.update {
            it.copy(
                email = value,
                registrationComplete = false,
                errorMessage = null
            )
        }
    }

    // =====================================
    // ACTUALIZAR CONTRASEÑA
    // =====================================

    fun updatePassword(value: String) {

        if (_uiState.value.isLoading ||
            _uiState.value.accountCreated
        ) {
            return
        }

        _uiState.update {
            it.copy(
                password = value,
                registrationComplete = false,
                errorMessage = null
            )
        }
    }

    // =====================================
    // CONFIRMAR CONTRASEÑA
    // =====================================

    fun updateConfirmPassword(value: String) {

        if (_uiState.value.isLoading ||
            _uiState.value.accountCreated
        ) {
            return
        }

        _uiState.update {
            it.copy(
                confirmPassword = value,
                registrationComplete = false,
                errorMessage = null
            )
        }
    }

    // =====================================
    // TERMINOS
    // =====================================

    fun updateAcceptedTerms(value: Boolean) {

        if (_uiState.value.isLoading ||
            _uiState.value.accountCreated
        ) {
            return
        }

        _uiState.update {
            it.copy(
                acceptedTerms = value,
                registrationComplete = false,
                errorMessage = null
            )
        }
    }

    // =====================================
    // MOSTRAR CONTRASEÑA
    // =====================================

    fun toggleShowPassword() {

        _uiState.update {
            it.copy(
                showPassword = !it.showPassword
            )
        }
    }

    // =====================================
    // MOSTRAR CONFIRMACION
    // =====================================

    fun toggleShowConfirmation() {

        _uiState.update {
            it.copy(
                showConfirmation = !it.showConfirmation
            )
        }
    }

    // =====================================
    // CREAR CUENTA EN FIREBASE
    // =====================================

    fun submit() {

        val state = _uiState.value

        if (!state.validForm) {
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                successMessage = null
            )
        }

        viewModelScope.launch {

            try {

                val result = repository.register(
                    username = state.username,
                    email = state.email,
                    password = state.password
                )

                _uiState.update { current ->

                    current.copy(
                        isLoading = false,

                        accountCreated = true,
                        registrationComplete =
                            result.profileSaved,

                        firebaseUid = result.uid,

                        // Borrar las credenciales del
                        // estado después del registro.
                        password = "",
                        confirmPassword = "",

                        successMessage =
                            if (result.profileSaved) {
                                "Cuenta y perfil guardados en Firebase."
                            } else {
                                null
                            },

                        errorMessage =
                            if (result.profileSaved) {
                                null
                            } else {
                                "La cuenta se creó, pero " +
                                        "no se guardó el perfil. " +
                                        "Puedes reintentarlo."
                            }
                    )
                }

            } catch (
                exception: CancellationException
            ) {

                throw exception

            } catch (exception: Exception) {

                _uiState.update { current ->

                    current.copy(
                        isLoading = false,
                        registrationComplete = false,
                        errorMessage =
                            getFriendlyError(exception)
                    )
                }
            }
        }
    }

    // =====================================
    // REINTENTAR PERFIL FIRESTORE
    // =====================================

    fun retrySaveProfile() {

        val state = _uiState.value

        if (!state.accountCreated ||
            state.isLoading ||
            state.registrationComplete
        ) {
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {

            try {

                val result =
                    repository.retrySaveProfile(
                        username = state.username
                    )

                _uiState.update { current ->

                    current.copy(
                        isLoading = false,

                        firebaseUid = result.uid,

                        registrationComplete =
                            result.profileSaved,

                        successMessage =
                            if (result.profileSaved) {
                                "Perfil guardado correctamente."
                            } else {
                                null
                            },

                        errorMessage =
                            if (result.profileSaved) {
                                null
                            } else {
                                "No se pudo guardar el perfil. " +
                                        "Comprueba la conexión y las reglas de Firestore."
                            }
                    )
                }

            } catch (
                exception: CancellationException
            ) {

                throw exception

            } catch (exception: Exception) {

                _uiState.update { current ->

                    current.copy(
                        isLoading = false,
                        errorMessage =
                            getFriendlyError(exception)
                    )
                }
            }
        }
    }

    // =====================================
    // MENSAJES DE ERROR
    // =====================================

    private fun getFriendlyError(
        exception: Exception
    ): String {

        return when (exception) {

            is FirebaseAuthUserCollisionException -> {
                "Este correo ya está registrado. " +
                        "Utiliza otra dirección o inicia sesión."
            }

            is FirebaseAuthWeakPasswordException -> {
                "Firebase rechazó la contraseña. " +
                        "Utiliza una contraseña más segura."
            }

            is FirebaseAuthException -> {

                when (exception.errorCode) {

                    "ERROR_EMAIL_ALREADY_IN_USE" -> {
                        "Este correo ya está registrado."
                    }

                    "ERROR_INVALID_EMAIL" -> {
                        "El correo electrónico no es válido."
                    }

                    "ERROR_NETWORK_REQUEST_FAILED" -> {
                        "No hay conexión. Comprueba internet."
                    }

                    "ERROR_OPERATION_NOT_ALLOWED" -> {
                        "Habilita Email/Password en Firebase Authentication."
                    }

                    else -> {
                        "No se pudo crear la cuenta. " +
                                "Comprueba Firebase Authentication."
                    }
                }
            }

            is IllegalArgumentException -> {
                exception.message
                    ?: "Los datos introducidos no son válidos."
            }

            is IllegalStateException -> {
                exception.message
                    ?: "No se pudo completar el registro."
            }

            else -> {
                "Error al conectar con Firebase. " +
                        "Comprueba internet e inténtalo nuevamente."
            }
        }
    }
}

// =====================================
// FACTORY
// =====================================

class RegistrationViewModelFactory(
    private val repository: RegistrationRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {

        if (
            modelClass.isAssignableFrom(
                RegistrationViewModel::class.java
            )
        ) {

            @Suppress("UNCHECKED_CAST")
            return RegistrationViewModel(
                repository = repository
            ) as T
        }

        throw IllegalArgumentException(
            "ViewModel desconocido: ${modelClass.name}"
        )
    }
}
