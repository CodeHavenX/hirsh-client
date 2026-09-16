package com.cramsan.hirsh.ui.screens.accounts

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.Account
import com.cramsan.hirsh.model.AccountStatus
import com.cramsan.hirsh.model.Role
import com.cramsan.hirsh.ui.preview.Preview
import com.cramsan.hirsh.ui.theme.HirshTheme

private val previewAccounts = listOf(
    Account(name = "Dr. Anita Patel", username = "apatel", role = Role.DOCTOR, status = AccountStatus.ACTIVE, lastLogin = "Hoy 08:12"),
    Account(name = "Dr. Marco Reyes", username = "mreyes", role = Role.DOCTOR, status = AccountStatus.ACTIVE, lastLogin = "Ayer 17:40"),
    Account(name = "Dr. Sara Lin", username = "slin", role = Role.DOCTOR, status = AccountStatus.ACTIVE, lastLogin = "28 May 2026"),
    Account(name = "Administrador", username = "admin", role = Role.ADMIN, status = AccountStatus.ACTIVE, lastLogin = "Hoy 07:55"),
    Account(name = "Dr. Tom Veer", username = "tveer", role = Role.DOCTOR, status = AccountStatus.INACTIVE, lastLogin = "—"),
)

// Only the base (no dialog open) state is captured here -- accounts.html's own
// modals are all `modal-scrim hidden` by default, so that's the state that's
// actually comparable to the mock. An open AlertDialog also isn't capturable by
// Roborazzi on desktop: Compose Multiplatform's Dialog opens a separate native
// window, which produces two semantics roots and fails captureRoboImage's
// single-root assertion. Dialog content/behavior is covered by
// AccountsViewModelTest instead.
@Preview
@Composable
private fun AccountsScreenPreview() {
    HirshTheme {
        AccountsScreenContent(
            uiState = AccountsUiState(accounts = previewAccounts),
            onOpenAddDialog = {},
            onOpenEditDialog = {},
            onOpenResetDialog = {},
            onOpenDeactivateDialog = {},
            onReactivate = {},
            onCloseDialog = {},
            onAddNameChange = {},
            onAddUsernameChange = {},
            onEditNameChange = {},
            onEditUsernameChange = {},
            onConfirmAdd = {},
            onConfirmEdit = {},
            onConfirmReset = {},
            onConfirmDeactivate = {},
        )
    }
}
