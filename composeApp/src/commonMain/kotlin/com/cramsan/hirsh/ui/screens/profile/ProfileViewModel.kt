package com.cramsan.hirsh.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cramsan.hirsh.model.Session
import com.cramsan.hirsh.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class ProfileUiState(
    val session: Session? = null,
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val error: String? = null,
    val updated: Boolean = false,
)

private data class PasswordForm(
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val error: String? = null,
    val updated: Boolean = false,
)

class ProfileViewModel(private val sessionRepository: SessionRepository) : ViewModel() {

    private val formState = MutableStateFlow(PasswordForm())

    val uiState: StateFlow<ProfileUiState> = combine(sessionRepository.session, formState) { session, form ->
        ProfileUiState(
            session = session,
            currentPassword = form.currentPassword,
            newPassword = form.newPassword,
            confirmPassword = form.confirmPassword,
            error = form.error,
            updated = form.updated,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    fun onCurrentPasswordChange(value: String) =
        formState.update { it.copy(currentPassword = value, error = null, updated = false) }
    fun onNewPasswordChange(value: String) =
        formState.update { it.copy(newPassword = value, error = null, updated = false) }
    fun onConfirmPasswordChange(value: String) =
        formState.update { it.copy(confirmPassword = value, error = null, updated = false) }

    /**
     * No backend to persist against yet -- validates, then clears the form and
     * flips [ProfileUiState.updated] as a local confirmation. Never calls
     * [SessionRepository] or any repository; there's nothing to write through.
     */
    fun updatePassword() {
        val form = formState.value
        if (form.currentPassword.isBlank() || form.newPassword.isBlank() || form.confirmPassword.isBlank()) {
            formState.update { it.copy(error = "Completa los campos requeridos") }
            return
        }
        if (form.newPassword.length < MIN_PASSWORD_LENGTH) {
            formState.update { it.copy(error = "La nueva contrasena debe tener al menos 8 caracteres") }
            return
        }
        if (form.newPassword != form.confirmPassword) {
            formState.update { it.copy(error = "Las contrasenas no coinciden") }
            return
        }
        formState.update { PasswordForm(updated = true) }
    }

    fun signOut() {
        sessionRepository.logout()
    }

    private companion object {
        const val MIN_PASSWORD_LENGTH = 8
    }
}
