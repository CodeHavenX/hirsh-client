package com.cramsan.hirsh.ui.screens.accounts

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.Account
import com.cramsan.hirsh.model.AccountStatus
import com.cramsan.hirsh.model.Role
import com.cramsan.hirsh.repository.AccountRepository
import com.cramsan.hirsh.ui.preview.Preview
import com.cramsan.hirsh.ui.theme.HirshTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

private val previewAccounts = listOf(
    Account(name = "Dr. Anita Patel", username = "apatel", role = Role.DOCTOR, status = AccountStatus.ACTIVE, lastLogin = "Hoy 08:12"),
    Account(name = "Dr. Marco Reyes", username = "mreyes", role = Role.DOCTOR, status = AccountStatus.ACTIVE, lastLogin = "Ayer 17:40"),
    Account(name = "Dr. Sara Lin", username = "slin", role = Role.DOCTOR, status = AccountStatus.ACTIVE, lastLogin = "28 May 2026"),
    Account(name = "Administrador", username = "admin", role = Role.ADMIN, status = AccountStatus.ACTIVE, lastLogin = "Hoy 07:55"),
    Account(name = "Dr. Tom Veer", username = "tveer", role = Role.DOCTOR, status = AccountStatus.INACTIVE, lastLogin = "—"),
)

private class PreviewAccountRepository(accounts: List<Account>) : AccountRepository {
    private val _accounts = MutableStateFlow(accounts)
    override val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    override fun getAccount(username: String): Flow<Account?> =
        accounts.map { list -> list.find { it.username == username } }

    override suspend fun updateLastLogin(username: String, lastLogin: String) = Unit

    override suspend fun addAccount(name: String, username: String): Account {
        val created = Account(name = name, username = username, role = Role.DOCTOR, status = AccountStatus.ACTIVE, lastLogin = "—")
        _accounts.update { it + created }
        return created
    }

    override suspend fun updateAccount(originalUsername: String, name: String, username: String) {
        _accounts.update { list -> list.map { if (it.username == originalUsername) it.copy(name = name, username = username) else it } }
    }

    override suspend fun deactivate(username: String) {
        _accounts.update { list -> list.map { if (it.username == username) it.copy(status = AccountStatus.INACTIVE) else it } }
    }

    override suspend fun reactivate(username: String) {
        _accounts.update { list -> list.map { if (it.username == username) it.copy(status = AccountStatus.ACTIVE) else it } }
    }
}

@Preview
@Composable
private fun AccountsScreenPreview() {
    HirshTheme {
        AccountsScreen(viewModel = AccountsViewModel(PreviewAccountRepository(previewAccounts)))
    }
}

@Preview
@Composable
private fun AccountsScreenAddDialogPreview() {
    HirshTheme {
        val viewModel = AccountsViewModel(PreviewAccountRepository(previewAccounts))
        viewModel.openAddDialog()
        AccountsScreen(viewModel = viewModel)
    }
}

@Preview
@Composable
private fun AccountsScreenDeactivateDialogPreview() {
    HirshTheme {
        val viewModel = AccountsViewModel(PreviewAccountRepository(previewAccounts))
        viewModel.openDeactivateDialog(previewAccounts.first())
        AccountsScreen(viewModel = viewModel)
    }
}
