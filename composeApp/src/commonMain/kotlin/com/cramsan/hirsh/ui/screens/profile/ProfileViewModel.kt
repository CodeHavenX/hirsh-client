package com.cramsan.hirsh.ui.screens.profile

import androidx.lifecycle.ViewModel
import com.cramsan.hirsh.repository.SessionRepository

class ProfileViewModel(private val sessionRepository: SessionRepository) : ViewModel() {

    fun signOut() {
        sessionRepository.logout()
    }
}
