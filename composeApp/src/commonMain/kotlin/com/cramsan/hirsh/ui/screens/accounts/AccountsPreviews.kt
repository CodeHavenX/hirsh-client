package com.cramsan.hirsh.ui.screens.accounts

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.Account
import com.cramsan.hirsh.model.AccountRole
import com.cramsan.hirsh.model.AccountStatus
import com.cramsan.hirsh.ui.preview.PreviewResponsive
import com.cramsan.hirsh.ui.theme.HirshTheme
import kotlin.time.Instant

private val physician = AccountRole("ATTENDING_PHYSICIAN", "Attending Physician")

private fun previewAccount(
    id: String,
    name: String,
    username: String,
    role: AccountRole = physician,
    status: AccountStatus = AccountStatus.ACTIVE,
    lastAccess: String? = null,
) = Account(id, name, username, listOf(role), status, lastAccess?.let(Instant::parse))

private val previewAccounts = listOf(
    previewAccount("acc_1", "Dr. Anita Patel", "apatel", lastAccess = "2026-06-18T13:12:00Z"),
    previewAccount("acc_2", "Dr. Marco Reyes", "mreyes", lastAccess = "2026-06-17T22:40:00Z"),
    previewAccount("acc_3", "Dr. Sara Lin", "slin", lastAccess = "2026-05-28T15:00:00Z"),
    previewAccount(
        "acc_4",
        "Administrador",
        "admin",
        role = AccountRole("SYSTEM_ADMIN", "System Administrator"),
        lastAccess = "2026-06-18T12:55:00Z",
    ),
    previewAccount("acc_5", "Dr. Tom Veer", "tveer", status = AccountStatus.INACTIVE),
    // LOCKED (HISS-651): repeated failed logins -- shown like an inactive account, with "Reactivar".
    previewAccount(
        "acc_6",
        "Ps. Lucia Paredes",
        "lparedes",
        role = AccountRole("PSYCHOLOGIST", "Psychologist"),
        status = AccountStatus.LOCKED,
        lastAccess = "2026-06-16T14:05:00Z",
    ),
)

// Only the base (no dialog open) state is captured here -- accounts.html's own
// modals are all `modal-scrim hidden` by default, so that's the state that's
// actually comparable to the mock. An open AlertDialog also isn't capturable by
// Roborazzi on desktop: Compose Multiplatform's Dialog opens a separate native
// window, which produces two semantics roots and fails captureRoboImage's
// single-root assertion. Dialog content/behavior is covered by
// AccountsViewModelTest instead.
@PreviewResponsive
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
            onAddFormChange = {},
            onEditProfileChange = {},
            onConfirmAdd = {},
            onConfirmEdit = {},
            onConfirmReset = {},
            onConfirmDeactivate = {},
        )
    }
}
