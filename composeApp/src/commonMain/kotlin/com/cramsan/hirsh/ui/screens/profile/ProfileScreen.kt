package com.cramsan.hirsh.ui.screens.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cramsan.hirsh.model.toDisplayLabel
import com.cramsan.hirsh.ui.components.BadgeTone
import com.cramsan.hirsh.ui.components.KeyValueRow
import com.cramsan.hirsh.ui.components.StatusBadge
import com.cramsan.hirsh.ui.theme.HissAccent
import com.cramsan.hirsh.ui.theme.HissAccentWash
import com.cramsan.hirsh.ui.theme.HissFaint
import com.cramsan.hirsh.ui.theme.HissInk2
import com.cramsan.hirsh.ui.theme.HissRadiusDefault
import com.cramsan.hirsh.ui.theme.HissSuccess
import org.koin.compose.viewmodel.koinViewModel

private val FieldFontSize = 13.sp
private val fieldShape = RoundedCornerShape(HissRadiusDefault)

@Composable
fun ProfileScreen(
    onSignedOut: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val session = uiState.session

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp).testTag("profile_scroll_container"),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column {
            Text(
                "CUENTA ›",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = HissInk2,
            )
            Text(
                "Mi perfil",
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
            )
        }

        if (session != null) {
            AccountCard(displayName = session.displayName, username = session.username, role = session.role.toDisplayLabel())
        }

        PasswordCard(uiState = uiState, viewModel = viewModel)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.signOut(); onSignedOut() }, modifier = Modifier.testTag("profile_sign_out_button")) {
                Text("Cerrar sesion")
            }
        }
    }
}

@Composable
private fun AccountCard(displayName: String, username: String, role: String) {
    Surface(
        shape = RoundedCornerShape(HissRadiusDefault),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, HissFaint),
        modifier = Modifier.width(500.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = HissAccentWash,
                    border = BorderStroke(1.5.dp, HissAccent),
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = initialsOf(displayName),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = HissAccent,
                        )
                    }
                }
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(username, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = HissInk2)
                }
                StatusBadge(text = role, tone = BadgeTone.Neutral)
            }
            KeyValueRow("Nombre completo", displayName)
            KeyValueRow("Usuario") {
                Text(username, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium)
            }
            KeyValueRow("Rol", role)
            Text(
                "Nombre, usuario y rol son gestionados por un administrador",
                fontSize = 11.sp,
                color = HissInk2,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun PasswordCard(uiState: ProfileUiState, viewModel: ProfileViewModel) {
    Surface(
        shape = RoundedCornerShape(HissRadiusDefault),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, HissFaint),
        modifier = Modifier.width(500.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FormSectionCaption("Cambiar contrasena")
            OutlinedTextField(
                value = uiState.currentPassword,
                onValueChange = viewModel::onCurrentPasswordChange,
                label = { RequiredFieldLabel("Contrasena actual") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
                shape = fieldShape,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.newPassword,
                onValueChange = viewModel::onNewPasswordChange,
                label = { RequiredFieldLabel("Nueva contrasena") },
                placeholder = { Text("minimo 8 caracteres", fontSize = FieldFontSize) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
                shape = fieldShape,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChange,
                label = { RequiredFieldLabel("Confirmar nueva contrasena") },
                placeholder = { Text("repetir nueva contrasena", fontSize = FieldFontSize) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
                shape = fieldShape,
                modifier = Modifier.fillMaxWidth(),
            )
            uiState.error?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = FieldFontSize)
            }
            if (uiState.updated) {
                Text("Contrasena actualizada.", color = HissSuccess, fontSize = FieldFontSize)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = viewModel::updatePassword,
                    shape = fieldShape,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text("Actualizar contrasena", fontSize = FieldFontSize, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun FormSectionCaption(text: String) {
    Column {
        Text(
            text.uppercase(),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = 0.8.sp,
            fontWeight = FontWeight.Medium,
            color = HissAccent,
        )
        HorizontalDivider(modifier = Modifier.padding(top = 6.dp), color = HissFaint)
    }
}

@Composable
private fun RequiredFieldLabel(text: String) {
    Text(
        buildAnnotatedString {
            append(text)
            withStyle(SpanStyle(color = HissAccent)) { append(" *") }
        },
        fontSize = 12.sp,
    )
}

private fun initialsOf(name: String): String = name.split(' ').mapNotNull { it.firstOrNull() }.take(2).joinToString("")
