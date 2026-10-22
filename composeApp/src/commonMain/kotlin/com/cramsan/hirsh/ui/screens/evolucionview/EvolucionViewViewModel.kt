package com.cramsan.hirsh.ui.screens.evolucionview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.NoteStatus
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Permission
import com.cramsan.hirsh.model.can
import com.cramsan.hirsh.network.ApiError
import com.cramsan.hirsh.network.ApiException
import com.cramsan.hirsh.repository.ClinicalNoteRepository
import com.cramsan.hirsh.repository.EpisodeRepository
import com.cramsan.hirsh.repository.PatientRepository
import com.cramsan.hirsh.repository.SessionRepository
import com.cramsan.hirsh.ui.components.LoadError
import com.cramsan.hirsh.ui.components.toLoadError
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.launch

enum class EvolucionViewTab { EVOLUCION, EXAMENES }

data class EvolucionViewUiState(
    val isLoading: Boolean = true,
    val patient: Patient? = null,
    val hospitalizacion: Hospitalizacion? = null,
    val evolucion: Evolucion? = null,
    val selectedTab: EvolucionViewTab = EvolucionViewTab.EVOLUCION,
    val showPrintPreview: Boolean = false,
    val loadError: LoadError? = null,
    /** A draft left by someone without signing rights (or a signature that failed on "Guardar"). */
    val canSign: Boolean = false,
    val isSigning: Boolean = false,
    val error: String? = null,
)

internal const val EVO_SIGN_FORBIDDEN_ERROR =
    "Tu cuenta no puede firmar: necesita el permiso de firma y un numero de colegiatura (CMP)"
internal const val EVO_SIGN_ERROR = "No se pudo firmar la evolucion"

private data class RequestedIds(val patientId: String, val hospId: String, val evoId: String)

/** View-only state, grouped so the main combine stays within its five typed flows. */
private data class ViewControls(
    val selectedTab: EvolucionViewTab = EvolucionViewTab.EVOLUCION,
    val showPrintPreview: Boolean = false,
    val isSigning: Boolean = false,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class EvolucionViewViewModel(
    private val patientRepository: PatientRepository,
    private val episodeRepository: EpisodeRepository,
    private val clinicalNoteRepository: ClinicalNoteRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val requestedIds = MutableStateFlow<RequestedIds?>(null)
    private val controls = MutableStateFlow(ViewControls())

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
                    controls,
                    sessionRepository.session,
                ) { patient, hospitalizacion, evolucion, view, session ->
                    // Notes are keyed by hospId alone, so only shown once the stay resolved for this
                    // patient -- a hospId belonging to a different patient never leaks its
                    // evoluciones through here (HISS-106's C5 tightening).
                    val shown = evolucion.takeIf { hospitalizacion != null }
                    EvolucionViewUiState(
                        isLoading = false,
                        patient = patient,
                        hospitalizacion = hospitalizacion,
                        evolucion = shown,
                        selectedTab = view.selectedTab,
                        showPrintPreview = view.showPrintPreview,
                        canSign = session?.can(Permission.DOCUMENT_SIGN) == true && shown?.status == NoteStatus.DRAFT,
                        isSigning = view.isSigning,
                        error = view.error,
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
        controls.update { it.copy(selectedTab = tab) }
    }

    fun openPrintPreview() {
        controls.update { it.copy(showPrintPreview = true) }
    }

    fun closePrintPreview() {
        controls.update { it.copy(showPrintPreview = false) }
    }

    fun sign() {
        val ids = requestedIds.value ?: return
        val state = uiState.value
        if (!state.canSign || state.isSigning) return
        controls.update { it.copy(isSigning = true, error = null) }
        viewModelScope.launch {
            try {
                clinicalNoteRepository.signEvolucion(ids.hospId, ids.evoId)
                controls.update { it.copy(isSigning = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = if ((e as? ApiException)?.error == ApiError.Forbidden) EVO_SIGN_FORBIDDEN_ERROR else EVO_SIGN_ERROR
                controls.update { it.copy(isSigning = false, error = message) }
            }
        }
    }
}
