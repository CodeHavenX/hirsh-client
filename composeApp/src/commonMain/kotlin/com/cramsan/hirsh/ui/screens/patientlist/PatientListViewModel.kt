package com.cramsan.hirsh.ui.screens.patientlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.repository.PatientRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PatientListUiState(
    val isLoading: Boolean = true,
    val patients: List<Patient> = emptyList(),
    val query: String = "",
)

class PatientListViewModel(private val patientRepository: PatientRepository) : ViewModel() {

    private val query = MutableStateFlow("")

    init {
        // No-op for InMemoryPatientRepository (already fully populated); for KtorPatientRepository
        // this is what actually fetches the list from the real backend -- see
        // PatientRepository.refresh()'s own doc comment for why this is an explicit call here
        // rather than the repository launching its own background work.
        viewModelScope.launch { patientRepository.refresh() }
    }

    val uiState: StateFlow<PatientListUiState> = combine(patientRepository.patients, query) { patients, query ->
        val filtered = if (query.isBlank()) {
            patients
        } else {
            patients.filter { patient ->
                patient.fullName.contains(query, ignoreCase = true) ||
                    patient.documentNumber.contains(query, ignoreCase = true) ||
                    patient.medicalRecordNumber.contains(query, ignoreCase = true)
            }
        }
        PatientListUiState(isLoading = false, patients = filtered, query = query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PatientListUiState())

    fun onQueryChange(newQuery: String) {
        query.value = newQuery
    }
}
