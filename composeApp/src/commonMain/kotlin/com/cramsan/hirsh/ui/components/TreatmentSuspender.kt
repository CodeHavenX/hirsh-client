package com.cramsan.hirsh.ui.components

import com.cramsan.hirsh.model.ActiveTreatment
import com.cramsan.hirsh.network.ApiError
import com.cramsan.hirsh.network.ApiException
import com.cramsan.hirsh.repository.TreatmentRepository
import com.cramsan.hirsh.util.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** The patient's "Tratamiento activo" panel (HCEM-11), kept apart so a failure there never takes down the screen. */
sealed interface ActiveTreatmentsSection {
    data object Loading : ActiveTreatmentsSection

    data class Loaded(val treatments: List<ActiveTreatment>) : ActiveTreatmentsSection

    data object Failed : ActiveTreatmentsSection
}

fun TreatmentRepository.activeTreatmentsSection(patientId: String): Flow<ActiveTreatmentsSection> =
    getActiveTreatments(patientId)
        .map<List<ActiveTreatment>, ActiveTreatmentsSection> { ActiveTreatmentsSection.Loaded(it) }
        .onStart { emit(ActiveTreatmentsSection.Loading) }
        .catch { emit(ActiveTreatmentsSection.Failed) }

/** The suspend dialog's state while it is open. */
data class TreatmentSuspension(
    val treatment: ActiveTreatment,
    val reason: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
)

internal const val SUSPENSION_REASON_REQUIRED_ERROR = "Indica el motivo de la suspension"
internal const val SUSPENSION_CONFLICT_ERROR = "El tratamiento ya no esta activo o empezo despues de hoy"
internal const val SUSPENSION_ERROR = "No se pudo suspender el tratamiento"

/**
 * Suspending a treatment from the patient record or a stay, which share the dialog. Owned by the
 * screen's view model and run in its scope; the suspension is dated today, and the panel refreshes
 * through [TreatmentRepository.getActiveTreatments] once it succeeds.
 */
class TreatmentSuspender(
    private val treatmentRepository: TreatmentRepository,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<TreatmentSuspension?>(null)
    val state: StateFlow<TreatmentSuspension?> = _state.asStateFlow()

    fun open(treatment: ActiveTreatment) {
        _state.value = TreatmentSuspension(treatment)
    }

    fun onReasonChange(reason: String) = _state.update { it?.copy(reason = reason, error = null) }

    fun dismiss() {
        if (_state.value?.isSaving == true) return
        _state.value = null
    }

    fun confirm() {
        val suspension = _state.value ?: return
        if (suspension.isSaving) return
        val reason = suspension.reason.trim()
        if (reason.isEmpty()) {
            _state.update { it?.copy(error = SUSPENSION_REASON_REQUIRED_ERROR) }
            return
        }
        _state.update { it?.copy(isSaving = true, error = null) }
        scope.launch {
            try {
                val today = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                treatmentRepository.suspend(suspension.treatment.id, reason, today)
                _state.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = if ((e as? ApiException)?.error is ApiError.Conflict) SUSPENSION_CONFLICT_ERROR else SUSPENSION_ERROR
                _state.update { it?.copy(isSaving = false, error = message) }
            }
        }
    }
}
