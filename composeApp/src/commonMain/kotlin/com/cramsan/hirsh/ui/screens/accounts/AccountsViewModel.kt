package com.cramsan.hirsh.ui.screens.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cramsan.hirsh.model.Account
import com.cramsan.hirsh.repository.AccountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

sealed class AccountDialog {
    data object None : AccountDialog()
    data class Add(
        val name: String = "",
        val username: String = "",
        val tempPassword: String,
        val error: String? = null,
        val isSaving: Boolean = false,
    ) : AccountDialog()
    data class Edit(
        val originalUsername: String,
        val name: String,
        val username: String,
        val error: String? = null,
        val isSaving: Boolean = false,
    ) : AccountDialog()
    data class Reset(val account: Account, val tempPassword: String) : AccountDialog()
    data class Deactivate(val account: Account, val isSaving: Boolean = false) : AccountDialog()
}

data class AccountsUiState(
    val accounts: List<Account> = emptyList(),
    val dialog: AccountDialog = AccountDialog.None,
)

class AccountsViewModel(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val dialogState = MutableStateFlow<AccountDialog>(AccountDialog.None)

    val uiState: StateFlow<AccountsUiState> = combine(
        accountRepository.accounts,
        dialogState,
    ) { accounts, dialog ->
        AccountsUiState(accounts = accounts, dialog = dialog)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    fun openAddDialog() {
        dialogState.value = AccountDialog.Add(tempPassword = generateTempPassword())
    }

    fun openEditDialog(account: Account) {
        dialogState.value = AccountDialog.Edit(
            originalUsername = account.username,
            name = account.name,
            username = account.username,
        )
    }

    fun openResetDialog(account: Account) {
        dialogState.value = AccountDialog.Reset(account, tempPassword = generateTempPassword())
    }

    fun openDeactivateDialog(account: Account) {
        dialogState.value = AccountDialog.Deactivate(account)
    }

    fun closeDialog() {
        dialogState.value = AccountDialog.None
    }

    fun onAddNameChange(value: String) = updateAddDialog { it.copy(name = value, error = null) }
    fun onAddUsernameChange(value: String) = updateAddDialog { it.copy(username = value, error = null) }
    fun onEditNameChange(value: String) = updateEditDialog { it.copy(name = value, error = null) }
    fun onEditUsernameChange(value: String) = updateEditDialog { it.copy(username = value, error = null) }

    fun confirmAdd() {
        val dialog = dialogState.value as? AccountDialog.Add ?: return
        if (dialog.isSaving) {
            return
        }
        if (dialog.name.isBlank() || dialog.username.isBlank()) {
            updateAddDialog { it.copy(error = "Completa los campos requeridos") }
            return
        }
        updateAddDialog { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            accountRepository.addAccount(dialog.name, dialog.username)
            dialogState.value = AccountDialog.None
        }
    }

    fun confirmEdit() {
        val dialog = dialogState.value as? AccountDialog.Edit ?: return
        if (dialog.isSaving) {
            return
        }
        if (dialog.name.isBlank() || dialog.username.isBlank()) {
            updateEditDialog { it.copy(error = "Completa los campos requeridos") }
            return
        }
        updateEditDialog { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            accountRepository.updateAccount(dialog.originalUsername, dialog.name, dialog.username)
            dialogState.value = AccountDialog.None
        }
    }

    /** No repository call -- the temp password shown is a cosmetic, non-persisted placeholder (see HISS-401's own note). */
    fun confirmReset() {
        dialogState.value = AccountDialog.None
    }

    fun confirmDeactivate() {
        val dialog = dialogState.value as? AccountDialog.Deactivate ?: return
        if (dialog.isSaving) {
            return
        }
        dialogState.value = dialog.copy(isSaving = true)
        viewModelScope.launch {
            accountRepository.deactivate(dialog.account.username)
            dialogState.value = AccountDialog.None
        }
    }

    /** Direct action, no confirm dialog -- matches the mock's own reactivateAccount(), which has none either. */
    fun reactivate(account: Account) {
        viewModelScope.launch {
            accountRepository.reactivate(account.username)
        }
    }

    private fun updateAddDialog(transform: (AccountDialog.Add) -> AccountDialog.Add) = dialogState.update {
        if (it is AccountDialog.Add) transform(it) else it
    }

    private fun updateEditDialog(transform: (AccountDialog.Edit) -> AccountDialog.Edit) = dialogState.update {
        if (it is AccountDialog.Edit) transform(it) else it
    }
}

private const val TEMP_PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"

private fun generateTempPassword(): String {
    val first = (1..3).map { TEMP_PASSWORD_CHARS.random(Random) }.joinToString("")
    val second = (1..4).map { TEMP_PASSWORD_CHARS.random(Random) }.joinToString("")
    return "$first-$second"
}
