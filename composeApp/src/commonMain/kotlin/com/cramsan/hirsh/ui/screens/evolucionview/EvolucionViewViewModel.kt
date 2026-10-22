package com.cramsan.hirsh.ui.screens.evolucionview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.repository.ClinicalNoteRepository
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

enum class EvolucionViewTab { EVOLUCION, EXAMENES }

data class EvolucionViewUiState(
    val isLoading: Boolean = true,
    val patient: Patient? = null,
    val hospitalizacion: Hospitalizacion? = null,
    val evolucion: Evolucion? = null,
    val selectedTab: EvolucionViewTab = EvolucionViewTab.EVOLUCION,
    val showPrintPreview: Boolean = false,
    val loadError: LoadError? = null,
)

private data class RequestedIds(val patientId: String, val hospId: String, val evoId: String)

@OptIn(ExperimentalCoroutinesApi::class)
class EvolucionViewViewModel(
    private val patientRepository: PatientRepository,
    private val episodeRepository: EpisodeRepository,
    private val clinicalNoteRepository: ClinicalNoteRepository,
) : ViewModel() {

    private val requestedIds = MutableStateFlow<RequestedIds?>(null)
    private val selectedTab = MutableStateFlow(EvolucionViewTab.EVOLUCION)
    private val showPrintPreview = MutableStateFlow(false)

    /** Bumped by [retry] to re-run the reads for the same note. */
    private val attempt = MutableStateFlow(0)

    val uiState: StateFlow<EvolucionViewUiState> = combine(requestedIds, attempt) { ids, _ -> ids }
        .flatMapLatest { ids ->
            if (ids == null) {
                flowOf(EvolucionViewUiState(isLoading = true))
            } else {
                combine(
                    patientRepository.getPatient(ids.patientId),
                    episodeRepository.getHospitalization(ids.patientId, ids.hospId),
                    clinicalNoteRepository.getEvolucion(ids.hospId, ids.evoId),
                    selectedTab,
                    showPrintPreview,
                ) { patient, hospitalizacion, evolucion, tab, printPreview ->
                    EvolucionViewUiState(
                        isLoading = false,
                        patient = patient,
                        hospitalizacion = hospitalizacion,
                        // Notes are keyed by hospId alone, so only shown once the stay resolved for this
                        // patient -- a hospId belonging to a different patient never leaks its
                        // evoluciones through here (HISS-106's C5 tightening).
                        evolucion = evolucion.takeIf { hospitalizacion != null },
                        selectedTab = tab,
                        showPrintPreview = printPreview,
                    )
                }
                    .onStart { emit(EvolucionViewUiState(isLoading = true)) }
                    .catch { emit(EvolucionViewUiState(isLoading = false, loadError = it.toLoadError())) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EvolucionViewUiState())

    fun load(patientId: String, hospId: String, evoId: String) {
        requestedIds.value = RequestedIds(patientId, hospId, evoId)
    }

    fun retry() {
        attempt.update { it + 1 }
    }

    fun selectTab(tab: EvolucionViewTab) {
        selectedTab.value = tab
    }

    fun openPrintPreview() {
        showPrintPreview.value = true
    }

    fun closePrintPreview() {
        showPrintPreview.value = false
    }
}
