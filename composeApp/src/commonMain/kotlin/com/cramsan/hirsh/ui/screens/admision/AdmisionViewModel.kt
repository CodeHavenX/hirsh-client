package com.cramsan.hirsh.ui.screens.admision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cramsan.hirsh.model.BedRef
import com.cramsan.hirsh.model.EpisodeStatus
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.ServiceRef
import com.cramsan.hirsh.model.UserRef
import com.cramsan.hirsh.network.ApiError
import com.cramsan.hirsh.network.ApiException
import com.cramsan.hirsh.repository.AdmissionCatalogRepository
import com.cramsan.hirsh.repository.EpisodeRepository
import com.cramsan.hirsh.repository.PatientRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdmisionUiState(
    val isLoading: Boolean = true,
    val patient: Patient? = null,
    val services: List<ServiceRef> = emptyList(),
    val physicians: List<UserRef> = emptyList(),
    /** [service]'s available beds; empty until a service is picked. */
    val beds: List<BedRef> = emptyList(),
    val isLoadingBeds: Boolean = false,
    val service: ServiceRef? = null,
    val bed: BedRef? = null,
    val attendingPhysician: UserRef? = null,
    val motivo: String = "",
    val error: String? = null,
    val isSaving: Boolean = false,
    val createdHospitalizationId: String? = null,
    /**
     * Whether the patient already has an [EpisodeStatus.ACTIVE] stay -- the backend allows only
     * one open inpatient episode and answers a second admission with `409` (HISS-632).
     */
    val hasActiveHospitalization: Boolean = false,
)

private const val ACTIVE_HOSPITALIZATION_ERROR = "El paciente ya tiene una hospitalizacion activa"
private const val BED_UNAVAILABLE_ERROR = "La cama elegida ya no esta disponible"

/**
 * fechaIngreso/horaIngreso are never part of this state -- unlike the
 * prototype's editable date/time inputs, [EpisodeRepository.addHospitalization]
 * stamps them from its own Clock, so there's nothing for this form to collect
 * or submit for them (see HISS-301's plan notes).
 */
class AdmisionViewModel(
    private val patientRepository: PatientRepository,
    private val episodeRepository: EpisodeRepository,
    private val catalogRepository: AdmissionCatalogRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdmisionUiState())
    val uiState: StateFlow<AdmisionUiState> = _uiState.asStateFlow()

    private var patientId: String? = null
    private var bedsJob: Job? = null

    fun load(patientId: String) {
        if (this.patientId == patientId) return
        this.patientId = patientId
        _uiState.value = AdmisionUiState(isLoading = true)
        viewModelScope.launch {
            try {
                // coroutineScope, so a failed picker load surfaces in the catch below instead of
                // escaping through the launch as an uncaught async failure.
                coroutineScope {
                    val services = async { catalogRepository.services() }
                    val physicians = async { catalogRepository.attendingPhysicians() }
                    val patient = patientRepository.getPatient(patientId).first()
                    val hasActiveHospitalization = patient != null &&
                        episodeRepository.getHospitalizations(patientId).first().any { it.status == EpisodeStatus.ACTIVE }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            patient = patient,
                            hasActiveHospitalization = hasActiveHospitalization,
                            services = services.await(),
                            physicians = physicians.await(),
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "No se pudo cargar el formulario de admision") }
            }
        }
    }

    /** Picking a service clears the bed: beds belong to one service, and the backend refuses a mismatch. */
    fun onServicioChange(value: ServiceRef) {
        if (value == _uiState.value.service) return
        _uiState.update { it.copy(service = value, bed = null, beds = emptyList(), error = null) }
        loadBeds(value)
    }

    fun onCamaChange(value: BedRef) = _uiState.update { it.copy(bed = value, error = null) }
    fun onMedicoResponsableChange(value: UserRef) = _uiState.update { it.copy(attendingPhysician = value, error = null) }
    fun onMotivoChange(value: String) = _uiState.update { it.copy(motivo = value, error = null) }

    /**
     * Mirrors the prototype's registerHospitalization(): service, cama, and
     * attending physician block submit; motivo does not -- a blank motivo is
     * saved as no admission reason (the backend's `admissionReason` is optional).
     *
     * A patient with an active stay is refused here rather than left to the backend's `409`,
     * which is still mapped to the same message in case the stay was opened after [load].
     */
    fun register() {
        val state = _uiState.value
        val id = patientId
        state.patient ?: return
        if (state.isSaving) {
            return
        }
        if (state.hasActiveHospitalization) {
            _uiState.update { it.copy(error = ACTIVE_HOSPITALIZATION_ERROR) }
            return
        }
        val service = state.service
        val bed = state.bed
        val physician = state.attendingPhysician
        if (service == null || bed == null || physician == null || id == null) {
            _uiState.update { it.copy(error = "Completa los campos requeridos") }
            return
        }

        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val created = episodeRepository.addHospitalization(
                    patientId = id,
                    service = service,
                    bed = bed,
                    attendingPhysician = physician,
                    admissionReason = state.motivo.ifBlank { null },
                )
                _uiState.update { it.copy(isSaving = false, createdHospitalizationId = created.id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                // Only the one-open-stay 409 names a conflicting resource (the open episode); the
                // backend's other 409s on admission are about the bed (occupied, or another service's).
                val error = e.error
                when {
                    error is ApiError.Conflict && error.resourceId.isNotBlank() -> _uiState.update {
                        it.copy(isSaving = false, error = ACTIVE_HOSPITALIZATION_ERROR, hasActiveHospitalization = true)
                    }
                    // Someone else took the bed since the list was loaded: offer the current list instead.
                    error is ApiError.Conflict -> {
                        _uiState.update { it.copy(isSaving = false, error = BED_UNAVAILABLE_ERROR, bed = null) }
                        loadBeds(service, keepError = true)
                    }
                    else -> _uiState.update { it.copy(isSaving = false, error = "No se pudo registrar la hospitalizacion") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = "No se pudo registrar la hospitalizacion") }
            }
        }
    }

    private fun loadBeds(service: ServiceRef, keepError: Boolean = false) {
        bedsJob?.cancel()
        _uiState.update { it.copy(isLoadingBeds = true) }
        bedsJob = viewModelScope.launch {
            try {
                val beds = catalogRepository.availableBeds(service.id)
                _uiState.update { it.copy(beds = beds, isLoadingBeds = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoadingBeds = false, error = if (keepError) it.error else "No se pudieron cargar las camas")
                }
            }
        }
    }
}
