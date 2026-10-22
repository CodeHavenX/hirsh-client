package com.cramsan.hirsh.ui.screens.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cramsan.hirsh.model.Account
import com.cramsan.hirsh.model.AccountDocumentType
import com.cramsan.hirsh.model.AccountProfile
import com.cramsan.hirsh.model.AccountRole
import com.cramsan.hirsh.model.NewAccount
import com.cramsan.hirsh.network.ApiError
import com.cramsan.hirsh.network.ApiException
import com.cramsan.hirsh.repository.AccountRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/** The create dialog's fields; see [NewAccount] for what they become. */
data class AddAccountForm(
    val firstName: String = "",
    val lastName: String = "",
    val secondLastName: String = "",
    val username: String = "",
    val documentType: AccountDocumentType = AccountDocumentType.NID,
    val documentNumber: String = "",
    val role: AccountRole? = null,
    val profile: AccountProfile = AccountProfile(),
)

sealed class AccountDialog {
    data object None : AccountDialog()
    data class Add(
        val form: AddAccountForm = AddAccountForm(),
        val tempPassword: String,
        val error: String? = null,
        val isSaving: Boolean = false,
    ) : AccountDialog()

    /** Only [profile] is editable: the API can't rename an account or change its username (HISS-653). */
    data class Edit(
        val account: Account,
        val profile: AccountProfile = account.profile,
        val error: String? = null,
        val isSaving: Boolean = false,
    ) : AccountDialog()

    /** [temporaryPassword] is the backend's generated one, shown once after the reset succeeds. */
    data class Reset(
        val account: Account,
        val isSaving: Boolean = false,
        val temporaryPassword: String? = null,
        val error: String? = null,
    ) : AccountDialog()
    data class Deactivate(val account: Account, val isSaving: Boolean = false) : AccountDialog()
}

data class AccountsUiState(
    val accounts: List<Account> = emptyList(),
    val roles: List<AccountRole> = emptyList(),
    val dialog: AccountDialog = AccountDialog.None,
    /** A failed load or status change; dialog actions report their own errors inside the dialog. */
    val error: String? = null,
)

private const val REQUIRED_FIELDS_ERROR = "Completa los campos requeridos"

class AccountsViewModel(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val dialogState = MutableStateFlow<AccountDialog>(AccountDialog.None)
    private val roles = MutableStateFlow<List<AccountRole>>(emptyList())
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AccountsUiState> = combine(
        accountRepository.accounts,
        roles,
        dialogState,
        error,
    ) { accounts, roles, dialog, error ->
        AccountsUiState(accounts = accounts, roles = roles, dialog = dialog, error = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    init {
        viewModelScope.launch {
            runReporting("No se pudieron cargar las cuentas") {
                accountRepository.refresh()
                roles.value = accountRepository.roles()
            }
        }
    }

    fun openAddDialog() {
        dialogState.value = AccountDialog.Add(tempPassword = generateTempPassword())
    }

    fun openEditDialog(account: Account) {
        dialogState.value = AccountDialog.Edit(account)
    }

    fun openResetDialog(account: Account) {
        dialogState.value = AccountDialog.Reset(account)
    }

    fun openDeactivateDialog(account: Account) {
        dialogState.value = AccountDialog.Deactivate(account)
    }

    fun closeDialog() {
        dialogState.value = AccountDialog.None
    }

    fun onAddFormChange(form: AddAccountForm) = updateDialog<AccountDialog.Add> { it.copy(form = form, error = null) }

    fun onEditProfileChange(profile: AccountProfile) = updateDialog<AccountDialog.Edit> { it.copy(profile = profile, error = null) }

    fun confirmAdd() {
        val dialog = dialogState.value as? AccountDialog.Add ?: return
        if (dialog.isSaving) {
            return
        }
        val form = dialog.form
        val role = form.role
        val required = listOf(form.firstName, form.lastName, form.username, form.documentNumber)
        if (required.any { it.isBlank() } || role == null) {
            updateDialog<AccountDialog.Add> { it.copy(error = REQUIRED_FIELDS_ERROR) }
            return
        }
        updateDialog<AccountDialog.Add> { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val newAccount = NewAccount(
                firstName = form.firstName,
                lastName = form.lastName,
                secondLastName = form.secondLastName,
                username = form.username,
                documentType = form.documentType,
                documentNumber = form.documentNumber,
                roleCode = role.code,
                profile = form.profile,
            )
            try {
                accountRepository.addAccount(newAccount, dialog.tempPassword)
                dialogState.value = AccountDialog.None
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = when ((e as? ApiException)?.error) {
                    is ApiError.Conflict -> "Ya existe una cuenta con ese usuario, documento, CMP o RNE"
                    is ApiError.Validation -> "Revisa los datos ingresados"
                    else -> "No se pudo crear la cuenta"
                }
                updateDialog<AccountDialog.Add> { it.copy(isSaving = false, error = message) }
            }
        }
    }

    fun confirmEdit() {
        val dialog = dialogState.value as? AccountDialog.Edit ?: return
        if (dialog.isSaving) {
            return
        }
        updateDialog<AccountDialog.Edit> { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                accountRepository.updateAccount(dialog.account.id, dialog.profile)
                dialogState.value = AccountDialog.None
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = when ((e as? ApiException)?.error) {
                    is ApiError.Conflict -> "Ese CMP o RNE ya esta registrado en otra cuenta"
                    is ApiError.Validation -> "Revisa los datos ingresados"
                    else -> "No se pudieron guardar los cambios"
                }
                updateDialog<AccountDialog.Edit> { it.copy(isSaving = false, error = message) }
            }
        }
    }

    /** First call resets and shows the new password; once it's shown, the same button closes the dialog. */
    fun confirmReset() {
        val dialog = dialogState.value as? AccountDialog.Reset ?: return
        if (dialog.temporaryPassword != null) {
            dialogState.value = AccountDialog.None
            return
        }
        if (dialog.isSaving) {
            return
        }
        updateDialog<AccountDialog.Reset> { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val password = accountRepository.resetPassword(dialog.account.id)
                updateDialog<AccountDialog.Reset> { it.copy(isSaving = false, temporaryPassword = password) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateDialog<AccountDialog.Reset> { it.copy(isSaving = false, error = "No se pudo restablecer la contraseña") }
            }
        }
    }

    fun confirmDeactivate() {
        val dialog = dialogState.value as? AccountDialog.Deactivate ?: return
        if (dialog.isSaving) {
            return
        }
        dialogState.value = dialog.copy(isSaving = true)
        viewModelScope.launch {
            runReporting("No se pudo desactivar la cuenta") { accountRepository.deactivate(dialog.account.id) }
            dialogState.value = AccountDialog.None
        }
    }

    /** Direct action, no confirm dialog -- matches the mock's own reactivateAccount(), which has none either. */
    fun reactivate(account: Account) {
        viewModelScope.launch {
            runReporting("No se pudo reactivar la cuenta") { accountRepository.reactivate(account.id) }
        }
    }

    /** Runs [block], showing [message] as the screen's error if it fails and clearing a previous one if it succeeds. */
    private suspend fun runReporting(message: String, block: suspend () -> Unit) {
        try {
            block()
            error.value = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error.value = message
        }
    }

    private inline fun <reified T : AccountDialog> updateDialog(crossinline transform: (T) -> T) = dialogState.update {
        if (it is T) transform(it) else it
    }
}

private const val TEMP_PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"

private fun generateTempPassword(): String {
    val first = (1..3).map { TEMP_PASSWORD_CHARS.random(Random) }.joinToString("")
    val second = (1..4).map { TEMP_PASSWORD_CHARS.random(Random) }.joinToString("")
    return "$first-$second"
}
