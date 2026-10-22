package com.cramsan.hirsh.ui.screens.patientrecord

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cramsan.hirsh.model.EpisodeStatus
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.repository.EpisodeRepository
import com.cramsan.hirsh.repository.PatientRepository
import com.cramsan.hirsh.ui.components.LoadError
import com.cramsan.hirsh.ui.components.toLoadError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class PatientRecordUiState(
    val isLoading: Boolean = true,
    val patient: Patient? = null,
    val hospitalizations: List<Hospitalizacion> = emptyList(),
    val loadError: LoadError? = null,
) {
    /**
     * The backend allows one open inpatient stay per patient. Read off [hospitalizations] rather
     * than the patient's `activeEpisode`, which is a snapshot from when the record was opened --
     * the episode list is updated in place by admission and discharge.
     */
    val canAdmit: Boolean get() = hospitalizations.none { it.status == EpisodeStatus.ACTIVE }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PatientRecordViewModel(
    private val patientRepository: PatientRepository,
    private val episodeRepository: EpisodeRepository,
) : ViewModel() {

    private val requestedId = MutableStateFlow<String?>(null)

    /** Bumped by [retry] to re-run the reads for the same patient. */
    private val attempt = MutableStateFlow(0)

    val uiState: StateFlow<PatientRecordUiState> = combine(requestedId, attempt) { id, _ -> id }
        .flatMapLatest { id ->
            val patientId = id ?: return@flatMapLatest flowOf(PatientRecordUiState(isLoading = true))
            combine(
                patientRepository.getPatient(patientId),
                episodeRepository.getHospitalizations(patientId),
            ) { patient, hospitalizations ->
                PatientRecordUiState(
                    isLoading = false,
                    patient = patient,
                    hospitalizations = hospitalizations,
                )
            }
                .onStart { emit(PatientRecordUiState(isLoading = true)) }
                .catch { emit(PatientRecordUiState(isLoading = false, loadError = it.toLoadError())) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PatientRecordUiState())

    fun load(patientId: String) {
        requestedId.value = patientId
    }

    fun retry() {
        attempt.update { it + 1 }
    }
}
