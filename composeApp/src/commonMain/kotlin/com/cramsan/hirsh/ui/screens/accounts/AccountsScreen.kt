package com.cramsan.hirsh.ui.screens.accounts

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cramsan.hirsh.model.Account
import com.cramsan.hirsh.model.AccountDocumentType
import com.cramsan.hirsh.model.AccountProfile
import com.cramsan.hirsh.model.AccountRole
import com.cramsan.hirsh.model.AccountStatus
import com.cramsan.hirsh.model.isAdmin
import com.cramsan.hirsh.model.rolesLabel
import com.cramsan.hirsh.model.toDisplayLabel
import com.cramsan.hirsh.ui.components.BadgeTone
import com.cramsan.hirsh.ui.components.DataTable
import com.cramsan.hirsh.ui.components.DataTableColumn
import com.cramsan.hirsh.ui.components.FieldFontSize
import com.cramsan.hirsh.ui.components.RequiredFieldLabel
import com.cramsan.hirsh.ui.components.SelectField
import com.cramsan.hirsh.ui.components.StatusBadge
import com.cramsan.hirsh.ui.components.fieldShape
import com.cramsan.hirsh.ui.theme.HissAccent
import com.cramsan.hirsh.ui.theme.HissInk2
import com.cramsan.hirsh.ui.theme.HissRadiusDefault
import com.cramsan.hirsh.ui.theme.HissRadiusTag
import com.cramsan.hirsh.ui.theme.HissWarn
import com.cramsan.hirsh.util.formatDateTime
import org.koin.compose.viewmodel.koinViewModel

private val CellFontSize = 13.sp

@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    AccountsScreenContent(
        uiState = uiState,
        onOpenAddDialog = viewModel::openAddDialog,
        onOpenEditDialog = viewModel::openEditDialog,
        onOpenResetDialog = viewModel::openResetDialog,
        onOpenDeactivateDialog = viewModel::openDeactivateDialog,
        onReactivate = viewModel::reactivate,
        onCloseDialog = viewModel::closeDialog,
        onAddFormChange = viewModel::onAddFormChange,
        onEditProfileChange = viewModel::onEditProfileChange,
        onConfirmAdd = viewModel::confirmAdd,
        onConfirmEdit = viewModel::confirmEdit,
        onConfirmReset = viewModel::confirmReset,
        onConfirmDeactivate = viewModel::confirmDeactivate,
    )
}

/** All rendering lives here, taking [uiState] as plain data, so `*Previews.kt` never needs a real ViewModel. */
@Composable
internal fun AccountsScreenContent(
    uiState: AccountsUiState,
    onOpenAddDialog: () -> Unit,
    onOpenEditDialog: (Account) -> Unit,
    onOpenResetDialog: (Account) -> Unit,
    onOpenDeactivateDialog: (Account) -> Unit,
    onReactivate: (Account) -> Unit,
    onCloseDialog: () -> Unit,
    onAddFormChange: (AddAccountForm) -> Unit,
    onEditProfileChange: (AccountProfile) -> Unit,
    onConfirmAdd: () -> Unit,
    onConfirmEdit: () -> Unit,
    onConfirmReset: () -> Unit,
    onConfirmDeactivate: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).testTag("screen_scroll_container"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Cuentas de usuario",
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onOpenAddDialog,
                shape = RoundedCornerShape(HissRadiusDefault),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier.testTag("accounts_add_button"),
            ) {
                Text("+ Agregar cuenta", fontSize = CellFontSize, fontWeight = FontWeight.Medium)
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
                fontSize = 8.sp,
                letterSpacing = 0.48.sp,
                color = HissAccent,
                modifier = Modifier
                    .border(1.dp, HissAccent, RoundedCornerShape(HissRadiusTag))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
            Text(
                "Visible solo para administradores",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = HissAccent,
            )
        }

        uiState.error?.let {
            Text(it, fontSize = 12.sp, color = HissWarn, modifier = Modifier.padding(top = 8.dp).testTag("accounts_error"))
        }

        Column(modifier = Modifier.padding(top = 12.dp)) {
            DataTable(
                columns = accountColumns(
                    onEdit = onOpenEditDialog,
                    onReset = onOpenResetDialog,
                    onDeactivate = onOpenDeactivateDialog,
                    onReactivate = onReactivate,
                ),
                rows = uiState.accounts,
                rowAlpha = { account -> if (account.status == AccountStatus.ACTIVE) 1f else 0.5f },
                rowTestTag = { account -> "account_row_${account.username}" },
            )
        }
    }

    when (val dialog = uiState.dialog) {
        is AccountDialog.None -> Unit
        is AccountDialog.Add -> AddAccountDialog(dialog, uiState.roles, onCloseDialog, onAddFormChange, onConfirmAdd)
        is AccountDialog.Edit -> EditAccountDialog(dialog, onCloseDialog, onEditProfileChange, onConfirmEdit)
        is AccountDialog.Reset -> ResetPasswordDialog(dialog, onCloseDialog, onConfirmReset)
        is AccountDialog.Deactivate -> DeactivateAccountDialog(dialog, onCloseDialog, onConfirmDeactivate)
    }
}

private fun accountColumns(
    onEdit: (Account) -> Unit,
    onReset: (Account) -> Unit,
    onDeactivate: (Account) -> Unit,
    onReactivate: (Account) -> Unit,
): List<DataTableColumn<Account>> = listOf(
    DataTableColumn(label = "Nombre", weight = 1.6f) { account ->
        Text(account.name, fontSize = CellFontSize, fontWeight = FontWeight.SemiBold)
    },
    DataTableColumn(label = "Usuario", weight = 1f) { account ->
        Text(account.username, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
    },
    DataTableColumn(label = "Rol", weight = 1.2f) { account -> Text(account.rolesLabel, fontSize = CellFontSize) },
    DataTableColumn(label = "Estado", weight = 1f) { account ->
        StatusBadge(
            text = account.status.toDisplayLabel(),
            tone = if (account.status == AccountStatus.ACTIVE) BadgeTone.Done else BadgeTone.Off,
        )
    },
    DataTableColumn(label = "Ultimo acceso", weight = 1.2f) { account ->
        Text(account.lastAccess?.let { formatDateTime(it) } ?: "—", fontSize = CellFontSize)
    },
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
        account.isAdmin -> Text("—", fontSize = CellFontSize, color = HissInk2)
        account.status == AccountStatus.ACTIVE -> Row(verticalAlignment = Alignment.CenterVertically) {
            ActionLink("Editar", HissAccent, "account_edit_${account.username}") { onEdit(account) }
            ActionSeparator()
            ActionLink("Reset clave", HissAccent, "account_reset_${account.username}") { onReset(account) }
            ActionSeparator()
            ActionLink("Desactivar", HissWarn, "account_deactivate_${account.username}") { onDeactivate(account) }
        }
        else -> ActionLink("Reactivar", HissAccent, "account_reactivate_${account.username}") { onReactivate(account) }
    }
}

@Composable
private fun ActionSeparator() {
    Text(" · ", fontSize = CellFontSize, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun ActionLink(text: String, color: Color, testTag: String, onClick: () -> Unit) {
    Text(
        text,
        fontSize = CellFontSize,
        color = color,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable(onClick = onClick).testTag(testTag),
    )
}

@Composable
private fun AddAccountDialog(
    dialog: AccountDialog.Add,
    roles: List<AccountRole>,
    onCloseDialog: () -> Unit,
    onFormChange: (AddAccountForm) -> Unit,
    onConfirmAdd: () -> Unit,
) {
    val form = dialog.form
    AlertDialog(
        onDismissRequest = onCloseDialog,
        title = { Text("Agregar cuenta", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                DialogField(form.firstName, { onFormChange(form.copy(firstName = it)) }, "Nombres", "account_add_first_name_field", required = true)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DialogField(
                        form.lastName,
                        { onFormChange(form.copy(lastName = it)) },
                        "Apellido paterno",
                        "account_add_last_name_field",
                        required = true,
                        modifier = Modifier.weight(1f),
                    )
                    DialogField(
                        form.secondLastName,
                        { onFormChange(form.copy(secondLastName = it)) },
                        "Apellido materno",
                        "account_add_second_last_name_field",
                        modifier = Modifier.weight(1f),
                    )
                }
                DialogField(form.username, { onFormChange(form.copy(username = it)) }, "Usuario", "account_add_username_field", required = true)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SelectField(
                        label = { Text("Tipo de documento") },
                        options = AccountDocumentType.entries.map { it.toDisplayLabel() },
                        selected = form.documentType.toDisplayLabel(),
                        onSelect = { label ->
                            AccountDocumentType.entries.firstOrNull { it.toDisplayLabel() == label }
                                ?.let { onFormChange(form.copy(documentType = it)) }
                        },
                        testTag = "account_add_document_type_field",
                        modifier = Modifier.weight(1f),
                    )
                    DialogField(
                        form.documentNumber,
                        { onFormChange(form.copy(documentNumber = it)) },
                        "Numero de documento",
                        "account_add_document_number_field",
                        required = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                SelectField(
                    label = { RequiredFieldLabel("Rol") },
                    options = roles.map { it.name },
                    selected = form.role?.name.orEmpty(),
                    onSelect = { name -> roles.firstOrNull { it.name == name }?.let { onFormChange(form.copy(role = it)) } },
                    testTag = "account_add_role_field",
                )
                ProfileFields(form.profile, { onFormChange(form.copy(profile = it)) }, tagPrefix = "account_add")
                OutlinedTextField(
                    value = dialog.tempPassword,
                    onValueChange = {},
                    label = { RequiredFieldLabel("Contraseña temporal") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
                    shape = fieldShape,
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Se le pedira cambiar la contraseña en el primer inicio de sesion.",
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
            Button(
                onClick = onConfirmAdd,
                enabled = !dialog.isSaving,
                modifier = Modifier.testTag("account_add_confirm_button"),
            ) { Text("Crear cuenta") }
        },
        dismissButton = {
            TextButton(onClick = onCloseDialog, modifier = Modifier.testTag("account_add_cancel_button")) { Text("Cancelar") }
        },
    )
}

@Composable
private fun EditAccountDialog(
    dialog: AccountDialog.Edit,
    onCloseDialog: () -> Unit,
    onProfileChange: (AccountProfile) -> Unit,
    onConfirmEdit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCloseDialog,
        title = { Text("Editar cuenta · ${dialog.account.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Usuario: ${dialog.account.username}. El nombre y el usuario no se pueden cambiar.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = HissInk2,
                )
                ProfileFields(dialog.profile, onProfileChange, tagPrefix = "account_edit")
                if (dialog.error != null) {
                    Text(dialog.error, fontSize = 12.sp, color = HissWarn)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmEdit,
                enabled = !dialog.isSaving,
                modifier = Modifier.testTag("account_edit_confirm_button"),
            ) { Text("Guardar cambios") }
        },
        dismissButton = {
            TextButton(onClick = onCloseDialog, modifier = Modifier.testTag("account_edit_cancel_button")) { Text("Cancelar") }
        },
    )
}

/** The optional contact and registry fields both dialogs share; see [AccountProfile]. */
@Composable
private fun ProfileFields(profile: AccountProfile, onChange: (AccountProfile) -> Unit, tagPrefix: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DialogField(profile.email, { onChange(profile.copy(email = it)) }, "Correo", "${tagPrefix}_email_field", modifier = Modifier.weight(1f))
        DialogField(profile.phone, { onChange(profile.copy(phone = it)) }, "Telefono", "${tagPrefix}_phone_field", modifier = Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DialogField(
            profile.medicalLicense,
            { onChange(profile.copy(medicalLicense = it)) },
            "CMP",
            "${tagPrefix}_cmp_field",
            modifier = Modifier.weight(1f),
        )
        DialogField(
            profile.specialistRegistry,
            { onChange(profile.copy(specialistRegistry = it)) },
            "RNE",
            "${tagPrefix}_rne_field",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DialogField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    testTag: String,
    required: Boolean = false,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { if (required) RequiredFieldLabel(label) else Text(label) },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
        shape = fieldShape,
        singleLine = true,
        modifier = modifier.testTag(testTag),
    )
}

@Composable
private fun ResetPasswordDialog(
    dialog: AccountDialog.Reset,
    onCloseDialog: () -> Unit,
    onConfirmReset: () -> Unit,
) {
    val password = dialog.temporaryPassword
    AlertDialog(
        onDismissRequest = onCloseDialog,
        title = { Text("Reset contraseña · ${dialog.account.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (password == null) {
                    Text(
                        "Se generara una nueva contraseña temporal y la actual dejara de funcionar.",
                        fontSize = 13.sp,
                        color = HissInk2,
                    )
                } else {
                    OutlinedTextField(
                        value = password,
                        onValueChange = {},
                        label = { Text("Nueva contraseña temporal") },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
                        shape = fieldShape,
                        readOnly = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("account_reset_password_field"),
                    )
                    Text(
                        "Entregala al usuario: no se volvera a mostrar. Debera cambiarla en su proximo inicio de sesion.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = HissInk2,
                    )
                }
                if (dialog.error != null) {
                    Text(dialog.error, fontSize = 12.sp, color = HissWarn)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmReset,
                enabled = !dialog.isSaving,
                modifier = Modifier.testTag("account_reset_confirm_button"),
            ) { Text(if (password == null) "Confirmar reset" else "Listo") }
        },
        dismissButton = {
            if (password == null) {
                TextButton(onClick = onCloseDialog, modifier = Modifier.testTag("account_reset_cancel_button")) { Text("Cancelar") }
            }
        },
    )
}

@Composable
private fun DeactivateAccountDialog(
    dialog: AccountDialog.Deactivate,
    onCloseDialog: () -> Unit,
    onConfirmDeactivate: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCloseDialog,
        title = { Text("Desactivar cuenta", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "¿Desactivar la cuenta de ${dialog.account.name}? El usuario no podra iniciar sesion. " +
                    "Sus evoluciones e historias clinicas previas se mantendran intactas y visibles. " +
                    "Un administrador puede reactivar la cuenta en cualquier momento.",
                fontSize = 13.sp,
                color = HissInk2,
            )
        },
        confirmButton = {
            OutlinedButton(
                onClick = onConfirmDeactivate,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = HissWarn),
                modifier = Modifier.testTag("account_deactivate_confirm_button"),
            ) { Text("Desactivar") }
        },
        dismissButton = {
            TextButton(onClick = onCloseDialog, modifier = Modifier.testTag("account_deactivate_cancel_button")) { Text("Cancelar") }
        },
    )
}
