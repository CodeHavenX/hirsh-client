package com.cramsan.hirsh.ui.screens.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cramsan.hirsh.model.Account
import com.cramsan.hirsh.model.AccountStatus
import com.cramsan.hirsh.model.Role
import com.cramsan.hirsh.model.toDisplayLabel
import com.cramsan.hirsh.ui.components.BadgeTone
import com.cramsan.hirsh.ui.components.DataTable
import com.cramsan.hirsh.ui.components.DataTableColumn
import com.cramsan.hirsh.ui.components.StatusBadge
import com.cramsan.hirsh.ui.theme.HissAccent
import com.cramsan.hirsh.ui.theme.HissInk2
import com.cramsan.hirsh.ui.theme.HissRadiusDefault
import com.cramsan.hirsh.ui.theme.HissWarn
import org.koin.compose.viewmodel.koinViewModel

private val CellFontSize = 13.sp

@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Cuentas de medicos",
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = viewModel::openAddDialog,
                shape = RoundedCornerShape(HissRadiusDefault),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text("+ Agregar medico", fontSize = CellFontSize, fontWeight = FontWeight.Medium)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        ) {
            Text(
                "ADMIN",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = HissAccent,
            )
            Text(
                "Visible solo para administradores",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = HissAccent,
            )
        }

        Column(modifier = Modifier.padding(top = 12.dp)) {
            DataTable(
                columns = accountColumns(
                    onEdit = viewModel::openEditDialog,
                    onReset = viewModel::openResetDialog,
                    onDeactivate = viewModel::openDeactivateDialog,
                    onReactivate = viewModel::reactivate,
                ),
                rows = uiState.accounts,
                rowAlpha = { account -> if (account.status == AccountStatus.INACTIVE) 0.5f else 1f },
            )
        }
    }

    when (val dialog = uiState.dialog) {
        is AccountDialog.None -> Unit
        is AccountDialog.Add -> AddAccountDialog(dialog, viewModel)
        is AccountDialog.Edit -> EditAccountDialog(dialog, viewModel)
        is AccountDialog.Reset -> ResetPasswordDialog(dialog, viewModel::confirmReset)
        is AccountDialog.Deactivate -> DeactivateAccountDialog(dialog, viewModel)
    }
}

private fun accountColumns(
    onEdit: (Account) -> Unit,
    onReset: (Account) -> Unit,
    onDeactivate: (Account) -> Unit,
    onReactivate: (Account) -> Unit,
): List<DataTableColumn<Account>> = listOf(
    DataTableColumn(label = "Nombre", weight = 2f) { account ->
        Text(account.name, fontSize = CellFontSize, fontWeight = FontWeight.SemiBold)
    },
    DataTableColumn(label = "Usuario", weight = 1f) { account ->
        Text(account.username, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
    },
    DataTableColumn(label = "Rol", weight = 0.8f) { account -> Text(account.role.toDisplayLabel(), fontSize = CellFontSize) },
    DataTableColumn(label = "Estado", weight = 1f) { account ->
        StatusBadge(
            text = account.status.toDisplayLabel(),
            tone = if (account.status == AccountStatus.ACTIVE) BadgeTone.Done else BadgeTone.Off,
        )
    },
    DataTableColumn(label = "Ultimo acceso", weight = 1.2f) { account -> Text(account.lastLogin, fontSize = CellFontSize) },
    DataTableColumn(label = "Acciones", weight = 2f) { account ->
        AccountActions(account, onEdit, onReset, onDeactivate, onReactivate)
    },
)

@Composable
private fun AccountActions(
    account: Account,
    onEdit: (Account) -> Unit,
    onReset: (Account) -> Unit,
    onDeactivate: (Account) -> Unit,
    onReactivate: (Account) -> Unit,
) {
    when {
        account.role == Role.ADMIN -> Text("—", fontSize = CellFontSize, color = HissInk2)
        account.status == AccountStatus.ACTIVE -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionLink("Editar", HissAccent) { onEdit(account) }
            ActionLink("Reset clave", HissAccent) { onReset(account) }
            ActionLink("Desactivar", HissWarn) { onDeactivate(account) }
        }
        else -> ActionLink("Reactivar", HissAccent) { onReactivate(account) }
    }
}

@Composable
private fun ActionLink(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 12.sp,
        color = color,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun AddAccountDialog(dialog: AccountDialog.Add, viewModel: AccountsViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::closeDialog,
        title = { Text("Agregar medico", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = dialog.name,
                    onValueChange = viewModel::onAddNameChange,
                    label = { Text("Nombre completo *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dialog.username,
                    onValueChange = viewModel::onAddUsernameChange,
                    label = { Text("Usuario *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dialog.tempPassword,
                    onValueChange = {},
                    label = { Text("Contraseña temporal *") },
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Rol fijo: Medico. Se le pedira cambiar la contraseña en el primer inicio de sesion.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = HissInk2,
                )
                if (dialog.error != null) {
                    Text(dialog.error, fontSize = 12.sp, color = HissWarn)
                }
            }
        },
        confirmButton = {
            Button(onClick = viewModel::confirmAdd) { Text("Crear cuenta") }
        },
        dismissButton = {
            TextButton(onClick = viewModel::closeDialog) { Text("Cancelar") }
        },
    )
}

@Composable
private fun EditAccountDialog(dialog: AccountDialog.Edit, viewModel: AccountsViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::closeDialog,
        title = { Text("Editar medico · ${dialog.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = dialog.name,
                    onValueChange = viewModel::onEditNameChange,
                    label = { Text("Nombre completo *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dialog.username,
                    onValueChange = viewModel::onEditUsernameChange,
                    label = { Text("Usuario *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (dialog.error != null) {
                    Text(dialog.error, fontSize = 12.sp, color = HissWarn)
                }
            }
        },
        confirmButton = {
            Button(onClick = viewModel::confirmEdit) { Text("Guardar cambios") }
        },
        dismissButton = {
            TextButton(onClick = viewModel::closeDialog) { Text("Cancelar") }
        },
    )
}

@Composable
private fun ResetPasswordDialog(dialog: AccountDialog.Reset, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onConfirm,
        title = { Text("Reset contraseña · ${dialog.account.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = dialog.tempPassword,
                    onValueChange = {},
                    label = { Text("Nueva contraseña temporal") },
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "El medico debera cambiar esta contraseña en su proximo inicio de sesion.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = HissInk2,
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Confirmar reset") }
        },
        dismissButton = {
            TextButton(onClick = onConfirm) { Text("Cancelar") }
        },
    )
}

@Composable
private fun DeactivateAccountDialog(dialog: AccountDialog.Deactivate, viewModel: AccountsViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::closeDialog,
        title = { Text("Desactivar cuenta", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "¿Desactivar la cuenta de ${dialog.account.name}? El medico no podra iniciar sesion. " +
                    "Sus evoluciones e historias clinicas previas se mantendran intactas y visibles. " +
                    "Un administrador puede reactivar la cuenta en cualquier momento.",
                fontSize = 13.sp,
                color = HissInk2,
            )
        },
        confirmButton = {
            OutlinedButton(
                onClick = viewModel::confirmDeactivate,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = HissWarn),
            ) { Text("Desactivar") }
        },
        dismissButton = {
            TextButton(onClick = viewModel::closeDialog) { Text("Cancelar") }
        },
    )
}
