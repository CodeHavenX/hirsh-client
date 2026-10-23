package com.cramsan.hirsh.ui.screens.scaleapply

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cramsan.hirsh.model.EpisodeStatus
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.ScaleDefinition
import com.cramsan.hirsh.model.ScaleScore
import com.cramsan.hirsh.model.ScaleSummary
import com.cramsan.hirsh.network.ApiError
import com.cramsan.hirsh.network.ApiException
import com.cramsan.hirsh.repository.ClinicalNoteRepository
import com.cramsan.hirsh.repository.EpisodeRepository
import com.cramsan.hirsh.repository.PatientRepository
import com.cramsan.hirsh.repository.ScaleRepository
import com.cramsan.hirsh.ui.components.LoadError
import com.cramsan.hirsh.ui.components.toLoadError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ApplyScaleUiState(
    val isLoading: Boolean = true,
    val loadError: LoadError? = null,
    val patient: Patient? = null,
    val hospitalizacion: Hospitalizacion? = null,
    val evolucion: Evolucion? = null,
    val scales: List<ScaleSummary> = emptyList(),
    /** The scale being answered; null while picking one. */
    val scale: ScaleDefinition? = null,
    val isLoadingScale: Boolean = false,
    /** Item id to the chosen option's value. */
    val answers: Map<String, Int> = emptyMap(),
    val observations: String = "",
    /** The server's score for [answers], as of the last preview. */
    val score: ScaleScore? = null,
    val showAlertConfirmation: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
) {
    val answeredCount: Int get() = scale?.questions?.count { it.id in answers } ?: 0
    val isComplete: Boolean get() = scale != null && answeredCount == scale.questions.size
}

internal const val SCALE_INCOMPLETE_ERROR = "Responde todos los items antes de guardar"
internal const val SCALE_SAVE_ERROR = "No se pudo guardar la escala"
internal const val SCALE_CLOSED_STAY_ERROR = "La hospitalizacion ya no esta activa: no se pueden aplicar escalas"
internal const val SCALE_LOAD_ERROR = "No se pudo cargar la escala"
internal const val SCALE_PREVIEW_ERROR = "No se pudo calcular el puntaje"

/** Answers are scored after this long without another change, so a quick run of taps sends one preview. */
internal const val SCALE_PREVIEW_DEBOUNCE_MILLIS = 300L

private data class RequestedIds(val patientId: String, val hospId: String, val evoId: String)

class ApplyScaleViewModel(
    private val patientRepository: PatientRepository,
    private val episodeRepository: EpisodeRepository,
    private val clinicalNoteRepository: ClinicalNoteRepository,
    private val scaleRepository: ScaleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ApplyScaleUiState())
    val uiState: StateFlow<ApplyScaleUiState> = _uiState.asStateFlow()
    private var loadedIds: RequestedIds? = null
    private var previewJob: Job? = null

    fun load(patientId: String, hospId: String, evoId: String) {
        val ids = RequestedIds(patientId, hospId, evoId)
        if (loadedIds == ids) return
        loadedIds = ids
        _uiState.value = ApplyScaleUiState()
        fetch(ids)
    }

    fun retry() {
        val ids = loadedIds ?: return
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        fetch(ids)
    }

    private fun fetch(ids: RequestedIds) {
        viewModelScope.launch {
            try {
                val patient = patientRepository.getPatient(ids.patientId).first()
                val hospitalizacion = episodeRepository.getHospitalization(ids.patientId, ids.hospId).first()
                // Only an evolucion of a stay that resolved for this patient (HISS-106's C5 rule).
                val evolucion = hospitalizacion?.let { clinicalNoteRepository.getEvolucion(ids.hospId, ids.evoId).first() }
                val stayIsActive = hospitalizacion?.status == EpisodeStatus.ACTIVE
                val scales = if (evolucion != null && stayIsActive) scaleRepository.getScales() else emptyList()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        patient = patient,
                        hospitalizacion = hospitalizacion,
                        evolucion = evolucion,
                        scales = scales,
                        error = SCALE_CLOSED_STAY_ERROR.takeIf { evolucion != null && !stayIsActive },
                    )
                }
                // Nothing to pick between when the hospital offers a single scale.
                scales.singleOrNull()?.let { selectScale(it.id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, loadError = e.toLoadError()) }
            }
        }
    }

    fun selectScale(scaleId: String) {
        previewJob?.cancel()
        _uiState.update { it.copy(isLoadingScale = true, scale = null, answers = emptyMap(), score = null, error = null) }
        viewModelScope.launch {
            try {
                val scale = scaleRepository.getScale(scaleId)
                _uiState.update { it.copy(isLoadingScale = false, scale = scale) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingScale = false, error = SCALE_LOAD_ERROR) }
            }
        }
    }

    /** Back to the scale list; the answers given so far are dropped. */
    fun changeScale() {
        previewJob?.cancel()
        _uiState.update { it.copy(scale = null, answers = emptyMap(), score = null, error = null) }
    }

    fun answer(questionId: String, value: Int) {
        val scale = _uiState.value.scale ?: return
        _uiState.update { it.copy(answers = it.answers + (questionId to value), error = null) }
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            delay(SCALE_PREVIEW_DEBOUNCE_MILLIS)
            try {
                val score = scaleRepository.previewScore(scale.id, _uiState.value.answers)
                _uiState.update { it.copy(score = score) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = SCALE_PREVIEW_ERROR) }
            }
        }
    }

    fun onObservationsChange(value: String) = _uiState.update { it.copy(observations = value) }

    /**
     * Scores the final answers first rather than trusting the last preview, which may still be
     * pending: answers that raise the suicide-risk alert are only saved after [confirmSave].
     */
    fun save() = submit(confirmed = false)

    fun confirmSave() = submit(confirmed = true)

    fun dismissAlertConfirmation() = _uiState.update { it.copy(showAlertConfirmation = false) }

    private fun submit(confirmed: Boolean) {
        val state = _uiState.value
        val ids = loadedIds ?: return
        val scale = state.scale ?: return
        if (state.isSaving || state.evolucion == null) return
        if (state.hospitalizacion?.status != EpisodeStatus.ACTIVE) {
            _uiState.update { it.copy(error = SCALE_CLOSED_STAY_ERROR) }
            return
        }
        if (!state.isComplete) {
            _uiState.update { it.copy(error = SCALE_INCOMPLETE_ERROR) }
            return
        }
        previewJob?.cancel()
        _uiState.update { it.copy(isSaving = true, showAlertConfirmation = false, error = null) }
        viewModelScope.launch {
            try {
                if (!confirmed) {
                    val score = scaleRepository.previewScore(scale.id, state.answers)
                    if (score.raisesSuicideAlert) {
                        _uiState.update { it.copy(isSaving = false, score = score, showAlertConfirmation = true) }
                        return@launch
                    }
                }
                scaleRepository.applyScale(ids.hospId, ids.evoId, scale.id, state.answers, state.observations)
                _uiState.update { it.copy(isSaving = false, saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = if ((e as? ApiException)?.error is ApiError.Conflict) SCALE_CLOSED_STAY_ERROR else SCALE_SAVE_ERROR
                _uiState.update { it.copy(isSaving = false, error = message) }
            }
        }
    }
}
