package com.cramsan.hirsh.ui.screens.hospitalization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cramsan.hirsh.model.AppliedScale
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Permission
import com.cramsan.hirsh.model.can
import com.cramsan.hirsh.repository.ClinicalNoteRepository
import com.cramsan.hirsh.repository.EpisodeRepository
import com.cramsan.hirsh.repository.PatientRepository
import com.cramsan.hirsh.repository.ScaleRepository
import com.cramsan.hirsh.repository.SessionRepository
import com.cramsan.hirsh.ui.components.LoadError
import com.cramsan.hirsh.ui.components.toLoadError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class HospitalizationUiState(
    val isLoading: Boolean = true,
    val patient: Patient? = null,
    val hospitalizacion: Hospitalizacion? = null,
    val historiaClinica: HistoriaClinica = HistoriaClinica(),
    val evoluciones: List<Evolucion> = emptyList(),
    val loadError: LoadError? = null,
    val scales: AppliedScalesSection = AppliedScalesSection.Hidden,
)

/** The stay's applied scales (HCEM-80), kept apart so a failure there never takes down the rest of the stay. */
sealed interface AppliedScalesSection {
    /** The user can't see test results. */
    data object Hidden : AppliedScalesSection

    data class Loaded(val scales: List<AppliedScale>) : AppliedScalesSection

    data object Failed : AppliedScalesSection
}

private data class RequestedIds(val patientId: String, val hospId: String)

@OptIn(ExperimentalCoroutinesApi::class)
class HospitalizationViewModel(
    private val patientRepository: PatientRepository,
    private val episodeRepository: EpisodeRepository,
    private val clinicalNoteRepository: ClinicalNoteRepository,
    private val scaleRepository: ScaleRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val requestedIds = MutableStateFlow<RequestedIds?>(null)

    /** Bumped by [retry] to re-run the reads for the same stay. */
    private val attempt = MutableStateFlow(0)

    val uiState: StateFlow<HospitalizationUiState> = combine(requestedIds, attempt) { ids, _ -> ids }
        .flatMapLatest { ids ->
            if (ids == null) {
                flowOf(HospitalizationUiState(isLoading = true))
            } else {
                combine(
                    patientRepository.getPatient(ids.patientId),
                    episodeRepository.getHospitalization(ids.patientId, ids.hospId),
                    clinicalNoteRepository.getHistoriaClinica(ids.hospId),
                    clinicalNoteRepository.getEvoluciones(ids.hospId),
                    appliedScales(ids.hospId),
                ) { patient, hospitalizacion, historiaClinica, evoluciones, scales ->
                    HospitalizationUiState(
                        isLoading = false,
                        patient = patient,
                        hospitalizacion = hospitalizacion,
                        historiaClinica = historiaClinica,
                        evoluciones = evoluciones,
                        scales = scales.takeIf { hospitalizacion != null } ?: AppliedScalesSection.Hidden,
                    )
                }
                    .onStart { emit(HospitalizationUiState(isLoading = true)) }
                    .catch { emit(HospitalizationUiState(isLoading = false, loadError = it.toLoadError())) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HospitalizationUiState())

    private fun appliedScales(hospId: String): Flow<AppliedScalesSection> =
        if (sessionRepository.session.value?.can(Permission.TEST_READ) == true) {
            scaleRepository.getAppliedScales(hospId)
                .map<List<AppliedScale>, AppliedScalesSection> { AppliedScalesSection.Loaded(it) }
                .catch { emit(AppliedScalesSection.Failed) }
        } else {
            flowOf(AppliedScalesSection.Hidden)
        }

    fun load(patientId: String, hospId: String) {
        requestedIds.value = RequestedIds(patientId, hospId)
    }

    fun retry() {
        attempt.update { it + 1 }
    }
}
