package com.cramsan.hirsh.ui.screens.hospitalization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.repository.ClinicalNoteRepository
import com.cramsan.hirsh.repository.EpisodeRepository
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
    val historiaClinica: HistoriaClinica = HistoriaClinica(),
    val evoluciones: List<Evolucion> = emptyList(),
)

private data class RequestedIds(val patientId: String, val hospId: String)

@OptIn(ExperimentalCoroutinesApi::class)
class HospitalizationViewModel(
    private val patientRepository: PatientRepository,
    private val episodeRepository: EpisodeRepository,
    private val clinicalNoteRepository: ClinicalNoteRepository,
) : ViewModel() {

    private val requestedIds = MutableStateFlow<RequestedIds?>(null)

    val uiState: StateFlow<HospitalizationUiState> = requestedIds
        .flatMapLatest { ids ->
            if (ids == null) {
                flowOf(HospitalizationUiState(isLoading = true))
            } else {
                combine(
                    patientRepository.getPatient(ids.patientId),
                    episodeRepository.getHospitalization(ids.patientId, ids.hospId),
                    clinicalNoteRepository.getHistoriaClinica(ids.hospId),
                    clinicalNoteRepository.getEvoluciones(ids.hospId),
                ) { patient, hospitalizacion, historiaClinica, evoluciones ->
                    HospitalizationUiState(
                        isLoading = false,
                        patient = patient,
                        hospitalizacion = hospitalizacion,
                        historiaClinica = historiaClinica,
                        evoluciones = evoluciones,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HospitalizationUiState())

    fun load(patientId: String, hospId: String) {
        requestedIds.value = RequestedIds(patientId, hospId)
    }
}
