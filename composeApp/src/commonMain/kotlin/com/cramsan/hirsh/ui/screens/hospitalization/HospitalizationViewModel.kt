package com.cramsan.hirsh.ui.screens.hospitalization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Patient
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

data class HospitalizationUiState(
    val isLoading: Boolean = true,
    val patient: Patient? = null,
    val hospitalizacion: Hospitalizacion? = null,
)

private data class RequestedIds(val patientId: String, val hospId: String)

@OptIn(ExperimentalCoroutinesApi::class)
class HospitalizationViewModel(
    private val patientRepository: PatientRepository,
    private val hospitalizationRepository: HospitalizationRepository,
) : ViewModel() {

    private val requestedIds = MutableStateFlow<RequestedIds?>(null)

    val uiState: StateFlow<HospitalizationUiState> = requestedIds
        .flatMapLatest { ids ->
            if (ids == null) {
                flowOf(HospitalizationUiState(isLoading = true))
            } else {
                combine(
                    patientRepository.getPatient(ids.patientId),
                    hospitalizationRepository.getHospitalization(ids.patientId, ids.hospId),
                ) { patient, hospitalizacion ->
                    HospitalizationUiState(
                        isLoading = false,
                        patient = patient,
                        hospitalizacion = hospitalizacion,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HospitalizationUiState())

    fun load(patientId: String, hospId: String) {
        requestedIds.value = RequestedIds(patientId, hospId)
    }
}
