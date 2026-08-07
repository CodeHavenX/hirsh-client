package com.cramsan.hirsh.ui.screens.patientrecord

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.PatientChangeLogEntry
import com.cramsan.hirsh.repository.HospitalizationRepository
import com.cramsan.hirsh.repository.PatientRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class PatientRecordUiState(
    val isLoading: Boolean = true,
    val patient: Patient? = null,
    val hospitalizations: List<Hospitalizacion> = emptyList(),
    val lastChange: PatientChangeLogEntry? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PatientRecordViewModel(
    private val patientRepository: PatientRepository,
    private val hospitalizationRepository: HospitalizationRepository,
) : ViewModel() {

    private val requestedId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PatientRecordUiState> = requestedId
        .flatMapLatest { id ->
            val patientId = id ?: return@flatMapLatest flowOf(PatientRecordUiState(isLoading = true))
            combine(
                patientRepository.getPatient(patientId),
                patientRepository.getChangeLog(patientId),
                hospitalizationRepository.getHospitalizations(patientId),
            ) { patient, changeLog, hospitalizations ->
                PatientRecordUiState(
                    isLoading = false,
                    patient = patient,
                    hospitalizations = hospitalizations,
                    lastChange = changeLog.firstOrNull(),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PatientRecordUiState())

    fun load(patientId: String) {
        requestedId.value = patientId
    }
}
