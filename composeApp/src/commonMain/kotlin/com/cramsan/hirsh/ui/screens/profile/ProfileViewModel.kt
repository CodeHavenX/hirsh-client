package com.cramsan.hirsh.ui.screens.profile

import androidx.lifecycle.ViewModel
import com.cramsan.hirsh.repository.AuthRepository

class ProfileViewModel(private val authRepository: AuthRepository) : ViewModel() {

    fun signOut() {
        authRepository.logout()
    }
}
