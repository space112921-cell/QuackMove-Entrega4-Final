
package com.quackmove.app.feature.registration

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff

import androidx.compose.material3.*

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.quackmove.app.data.repository.FirebaseRegistrationRepository

// =====================================
// COLORES DE QUACKMOVE
// =====================================

private val RegistrationBackground =
    Color(0xFF101928)

private val RegistrationText =
    Color(0xFFF5F6FF)

private val RegistrationSecondaryText =
    Color(0xFFB6C2DF)

private val RegistrationPurple =
    Color(0xFF8066F5)

private val RegistrationGreen =
    Color(0xFF43E6B3)

private val RegistrationError =
    Color(0xFFFFB86B)

// =====================================
// PANTALLA DE REGISTRO
// =====================================

@Composable
fun RegistrationScreen(
    onBack: () -> Unit = {}
) {

    // =================================
    // INYECCION DEL REPOSITORY
    // =================================

    val factory = remember {

        RegistrationViewModelFactory(
            repository = FirebaseRegistrationRepository(
                auth = FirebaseAuth.getInstance(),
                firestore = FirebaseFirestore.getInstance()
            )
        )
    }

    val model: RegistrationViewModel = viewModel(
        factory = factory
    )

    // =================================
    // OBSERVAR STATEFLOW
    // =================================

    val state by model.uiState
        .collectAsStateWithLifecycle()

    val fieldsEnabled =
        !state.isLoading &&
                !state.accountCreated

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = RegistrationBackground
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(
                    horizontal = 24.dp,
                    vertical = 24.dp
                ),

            horizontalAlignment =
                Alignment.CenterHorizontally,

            verticalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {

            // =================================
            // VOLVER
            // =================================

            TextButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.Start)
            ) {

                Text(
                    "← Volver",
                    color = RegistrationPurple
                )
            }

            Spacer(
                Modifier.height(8.dp)
            )

            // =================================
            // TITULO
            // =================================

            Text(
                text = "Regístrate",
                color = RegistrationText,
                style = MaterialTheme
                    .typography.headlineLarge
            )

            Text(
                text = "Crea tu cuenta en QuackMove.",
                color = RegistrationSecondaryText,
                fontSize = 15.sp
            )

            Spacer(
                Modifier.height(12.dp)
            )

            // =================================
            // NOMBRE DE USUARIO
            // =================================

            OutlinedTextField(
                value = state.username,

                onValueChange =
                    model::updateUsername,

                enabled = fieldsEnabled,

                label = {
                    Text("Nombre de usuario")
                },

                singleLine = true,

                modifier = Modifier.fillMaxWidth(),

                isError =
                    state.username.isNotEmpty() &&
                            !state.validUsername,

                supportingText = {

                    if (
                        state.username.isNotEmpty() &&
                        !state.validUsername
                    ) {

                        Text(
                            "Debe tener al menos 3 caracteres"
                        )
                    }
                },

                shape = RoundedCornerShape(14.dp)
            )

            // =================================
            // CORREO ELECTRONICO
            // =================================

            OutlinedTextField(
                value = state.email,

                onValueChange =
                    model::updateEmail,

                enabled = fieldsEnabled,

                label = {
                    Text("Correo electrónico")
                },

                singleLine = true,

                modifier = Modifier.fillMaxWidth(),

                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email
                ),

                isError =
                    state.email.isNotEmpty() &&
                            !state.validEmail,

                supportingText = {

                    if (
                        state.email.isNotEmpty() &&
                        !state.validEmail
                    ) {

                        Text(
                            "Ingresa un correo electrónico válido"
                        )
                    }
                },

                shape = RoundedCornerShape(14.dp)
            )

            // =================================
            // CONTRASEÑA
            // =================================

            OutlinedTextField(
                value = state.password,

                onValueChange =
                    model::updatePassword,

                enabled = fieldsEnabled,

                label = {
                    Text("Contraseña")
                },

                singleLine = true,

                modifier = Modifier.fillMaxWidth(),

                visualTransformation =
                    if (state.showPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },

                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password
                ),

                trailingIcon = {

                    IconButton(
                        onClick =
                            model::toggleShowPassword
                    ) {

                        Icon(
                            imageVector =
                                if (state.showPassword) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },

                            contentDescription =
                                "Mostrar u ocultar contraseña"
                        )
                    }
                },

                isError =
                    state.password.isNotEmpty() &&
                            !state.validPassword,

                supportingText = {

                    if (
                        state.password.isNotEmpty() &&
                        !state.validPassword
                    ) {

                        Text(
                            "Debe tener al menos 8 caracteres"
                        )
                    }
                },

                shape = RoundedCornerShape(14.dp)
            )

            // =================================
            // CONFIRMACION DE CONTRASEÑA
            // =================================

            OutlinedTextField(
                value = state.confirmPassword,

                onValueChange =
                    model::updateConfirmPassword,

                enabled = fieldsEnabled,

                label = {
                    Text("Confirmar contraseña")
                },

                singleLine = true,

                modifier = Modifier.fillMaxWidth(),

                visualTransformation =
                    if (state.showConfirmation) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },

                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password
                ),

                trailingIcon = {

                    IconButton(
                        onClick =
                            model::toggleShowConfirmation
                    ) {

                        Icon(
                            imageVector =
                                if (state.showConfirmation) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },

                            contentDescription =
                                "Mostrar u ocultar confirmación"
                        )
                    }
                },

                isError =
                    state.confirmPassword.isNotEmpty() &&
                            !state.validConfirmation,

                supportingText = {

                    if (
                        state.confirmPassword.isNotEmpty() &&
                        !state.validConfirmation
                    ) {

                        Text(
                            "Las contraseñas no coinciden"
                        )
                    }
                },

                shape = RoundedCornerShape(14.dp)
            )

            // =================================
            // TERMINOS
            // =================================

            Row(
                modifier = Modifier.fillMaxWidth(),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Checkbox(
                    checked = state.acceptedTerms,

                    enabled = fieldsEnabled,

                    onCheckedChange =
                        model::updateAcceptedTerms
                )

                Text(
                    text = "Acepto los términos y condiciones",
                    color = RegistrationSecondaryText,
                    fontSize = 13.sp
                )
            }

            // =================================
            // ERROR DE FIREBASE
            // =================================

            state.errorMessage?.let { error ->

                Text(
                    text = error,
                    color = RegistrationError,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // =================================
            // MENSAJE DE EXITO
            // =================================

            if (state.registrationComplete) {

                Text(
                    text = state.successMessage
                        ?: "Cuenta creada correctamente.",

                    color = RegistrationGreen,

                    fontSize = 14.sp,

                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Tu perfil ya está asociado " +
                            "a tu cuenta de Firebase.",

                    color = RegistrationSecondaryText,

                    fontSize = 13.sp
                )
            }

            // =================================
            // INDICADOR DE CARGA
            // =================================

            if (state.isLoading) {

                CircularProgressIndicator(
                    color = RegistrationPurple
                )

                Text(
                    text = "Comunicando con Firebase...",
                    color = RegistrationSecondaryText,
                    fontSize = 13.sp
                )
            }

            // =================================
            // BOTON CREAR CUENTA
            // =================================

            if (!state.accountCreated) {

                Button(
                    onClick = model::submit,

                    enabled = state.validForm,

                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),

                    shape = RoundedCornerShape(14.dp),

                    colors = ButtonDefaults.buttonColors(
                        containerColor = RegistrationPurple
                    )
                ) {

                    Text(
                        text = "Crear cuenta",
                        fontSize = 16.sp
                    )
                }
            }

            // =================================
            // REINTENTAR PERFIL
            // =================================

            if (
                state.accountCreated &&
                !state.registrationComplete
            ) {

                Button(
                    onClick =
                        model::retrySaveProfile,

                    enabled = !state.isLoading,

                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),

                    shape = RoundedCornerShape(14.dp),

                    colors = ButtonDefaults.buttonColors(
                        containerColor = RegistrationPurple
                    )
                ) {

                    Text(
                        "Reintentar guardar perfil"
                    )
                }
            }

            // =================================
            // VOLVER AL MAPA
            // =================================

            if (state.registrationComplete) {

                Button(
                    onClick = onBack,

                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),

                    shape = RoundedCornerShape(14.dp),

                    colors = ButtonDefaults.buttonColors(
                        containerColor = RegistrationPurple
                    )
                ) {

                    Text(
                        "Continuar a QuackMove"
                    )
                }
            }

            Spacer(
                Modifier.height(12.dp)
            )
        }
    }
}
