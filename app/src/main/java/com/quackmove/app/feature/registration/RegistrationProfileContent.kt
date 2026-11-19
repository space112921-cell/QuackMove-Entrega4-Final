package com.quackmove.app.feature.registration

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.quackmove.app.data.repository.RegisteredAccount
import com.quackmove.app.ui.theme.QuackBackground
import com.quackmove.app.ui.theme.QuackBorder
import com.quackmove.app.ui.theme.QuackMint
import com.quackmove.app.ui.theme.QuackPurple
import com.quackmove.app.ui.theme.QuackPurpleLight
import com.quackmove.app.ui.theme.QuackSurfaceVariant
import com.quackmove.app.ui.theme.QuackTextPrimary
import com.quackmove.app.ui.theme.QuackTextSecondary

// =====================================
// PERFIL DEL USUARIO REGISTRADO
// =====================================
//
// Se muestra en lugar del formulario cuando ya hay una cuenta
// con sesión. Composable de contenido: recibe el estado y avisa
// los eventos con callbacks.

@Composable
fun RegistrationProfileContent(
    account: RegisteredAccount,
    successMessage: String?,
    errorMessage: String?,
    showRetryProfile: Boolean,
    isBusy: Boolean,
    onRetryProfile: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit
) {

    // Estado solo de UI: si el diálogo de confirmación está abierto.
    var showSignOutDialog by rememberSaveable { mutableStateOf(false) }

    if (showSignOutDialog) {

        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("¿Cerrar sesión?") },
            text = {
                Text(
                    "Todavía no hay pantalla para iniciar sesión, así que " +
                            "no podrás volver a entrar a esta cuenta desde la app " +
                            "y sus viajes dejarán de mostrarse en este teléfono."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutDialog = false
                        onSignOut()
                    }
                ) {
                    Text("Cerrar sesión", color = ProfileWarning)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancelar", color = QuackPurpleLight)
                }
            },
            containerColor = QuackSurfaceVariant,
            titleContentColor = QuackTextPrimary,
            textContentColor = QuackTextSecondary
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = QuackBackground
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            TextButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.Start)
            ) {
                Text("← Volver", color = QuackPurpleLight)
            }

            Spacer(Modifier.height(8.dp))

            // ---------- Avatar con la inicial ----------

            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(QuackPurple),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = account.username.take(1).uppercase(),
                    color = QuackTextPrimary,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = account.username,
                color = QuackTextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Tu cuenta de QuackMove",
                color = QuackTextSecondary,
                fontSize = 15.sp
            )

            // ---------- Datos de la cuenta ----------

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = QuackSurfaceVariant,
                shape = RoundedCornerShape(20.dp)
            ) {

                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {

                    ProfileRow(
                        label = "Correo electrónico",
                        value = account.email,
                        icon = { Icon(Icons.Default.Email, null, tint = QuackPurpleLight) }
                    )

                    ProfileRow(
                        label = "Tus viajes",
                        value = "Se guardan en la nube con esta cuenta",
                        icon = { Icon(Icons.Default.CloudDone, null, tint = QuackMint) }
                    )
                }
            }

            successMessage?.let { message ->
                Text(
                    text = message,
                    color = QuackMint,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            errorMessage?.let { message ->
                Text(
                    text = message,
                    color = ProfileWarning,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ---------- Acciones ----------

            if (showRetryProfile) {

                Button(
                    onClick = onRetryProfile,
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = QuackPurple)
                ) {
                    Text("Reintentar guardar perfil")
                }
            }

            Button(
                onClick = onBack,
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = QuackPurple)
            ) {
                Text("Continuar a QuackMove", fontSize = 16.sp)
            }

            OutlinedButton(
                onClick = { showSignOutDialog = true },
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, QuackBorder)
            ) {

                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = QuackPurpleLight
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = QuackTextSecondary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Cerrar sesión", color = QuackTextSecondary, fontSize = 16.sp)
                }
            }
        }
    }
}

// =====================================
// CARGANDO SESION
// =====================================

@Composable
fun RegistrationCheckingContent() {

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = QuackBackground
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = QuackPurple)
        }
    }
}

@Composable
private fun ProfileRow(
    label: String,
    value: String,
    icon: @Composable () -> Unit
) {

    Row(verticalAlignment = Alignment.CenterVertically) {

        icon()

        Spacer(Modifier.width(14.dp))

        Column {
            Text(label, color = QuackTextSecondary, fontSize = 13.sp)
            Text(
                value,
                color = QuackTextPrimary,
                fontSize = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private val ProfileWarning = Color(0xFFFFB86B)
