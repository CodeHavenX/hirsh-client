package com.cramsan.hirsh.ui.screens.patientlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.repository.PatientRepository
import com.cramsan.hirsh.ui.components.LoadError
import com.cramsan.hirsh.ui.components.toLoadError
import kotlinx.coroutines.CancellationException
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
    val loadError: LoadError? = null,
)

class PatientListViewModel(private val patientRepository: PatientRepository) : ViewModel() {

    private val query = MutableStateFlow("")
    private val isRefreshing = MutableStateFlow(true)
    private val loadError = MutableStateFlow<LoadError?>(null)

    init {
        refresh()
    }

    val uiState: StateFlow<PatientListUiState> = combine(
        patientRepository.patients,
        query,
        isRefreshing,
        loadError,
    ) { patients, query, isRefreshing, loadError ->
        val filtered = if (query.isBlank()) {
            patients
        } else {
            patients.filter { patient ->
                patient.fullName.contains(query, ignoreCase = true) ||
                    patient.documentNumber.contains(query, ignoreCase = true) ||
                    patient.medicalRecordNumber.contains(query, ignoreCase = true)
            }
        }
        // A list already cached by an earlier visit shows straight away while it refreshes.
        PatientListUiState(
            isLoading = isRefreshing && patients.isEmpty(),
            patients = filtered,
            query = query,
            loadError = loadError,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PatientListUiState())

    fun onQueryChange(newQuery: String) {
        query.value = newQuery
    }

    fun retry() = refresh()

    // No-op for InMemoryPatientRepository (already fully populated); for KtorPatientRepository
    // this is what actually fetches the list from the real backend -- see
    // PatientRepository.refresh()'s own doc comment for why this is an explicit call here
    // rather than the repository launching its own background work.
    private fun refresh() {
        isRefreshing.value = true
        loadError.value = null
        viewModelScope.launch {
            try {
                patientRepository.refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loadError.value = e.toLoadError()
            } finally {
                isRefreshing.value = false
            }
        }
    }
}
