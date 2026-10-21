package com.cramsan.hirsh.ui.screens.admision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.ServiceRef
import com.cramsan.hirsh.model.UserRef
import com.cramsan.hirsh.repository.HospitalizationRepository
import com.cramsan.hirsh.repository.PatientRepository
import com.cramsan.hirsh.repository.PlaceholderCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdmisionUiState(
    val isLoading: Boolean = true,
    val patient: Patient? = null,
    val service: ServiceRef? = null,
    val cama: String = "",
    val attendingPhysician: UserRef? = null,
    val motivo: String = "",
    val error: String? = null,
    val isSaving: Boolean = false,
    val createdHospitalizationId: String? = null,
)

/**
 * fechaIngreso/horaIngreso are never part of this state -- unlike the
 * prototype's editable date/time inputs, [HospitalizationRepository.addHospitalization]
 * stamps them from its own Clock, so there's nothing for this form to collect
 * or submit for them (see HISS-301's plan notes).
 */
class AdmisionViewModel(
    private val patientRepository: PatientRepository,
    private val hospitalizationRepository: HospitalizationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdmisionUiState())
    val uiState: StateFlow<AdmisionUiState> = _uiState.asStateFlow()

    private var patientId: String? = null

    fun load(patientId: String) {
        if (this.patientId == patientId) return
        this.patientId = patientId
        _uiState.value = AdmisionUiState(isLoading = true)
        viewModelScope.launch {
            val patient = patientRepository.getPatient(patientId).first()
            _uiState.update { it.copy(isLoading = false, patient = patient) }
        }
    }

    fun onServicioChange(value: ServiceRef) = _uiState.update { it.copy(service = value, error = null) }
    fun onCamaChange(value: String) = _uiState.update { it.copy(cama = value, error = null) }
    fun onMedicoResponsableChange(value: UserRef) = _uiState.update { it.copy(attendingPhysician = value, error = null) }
    fun onMotivoChange(value: String) = _uiState.update { it.copy(motivo = value, error = null) }

    /**
     * Mirrors the prototype's registerHospitalization(): service, cama, and
     * attending physician block submit; motivo does not -- a blank motivo is
     * saved as no admission reason (the backend's `admissionReason` is optional).
     */
    fun register() {
        val state = _uiState.value
        val id = patientId
        state.patient ?: return
        if (state.isSaving) {
            return
        }
        val service = state.service
        val physician = state.attendingPhysician
        if (service == null || state.cama.isBlank() || physician == null || id == null) {
            _uiState.update { it.copy(error = "Completa los campos requeridos") }
            return
        }

        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val created = hospitalizationRepository.addHospitalization(
                    patientId = id,
                    service = service,
                    bed = PlaceholderCatalog.bed(state.cama.trim()),
                    attendingPhysician = physician,
                    admissionReason = state.motivo.ifBlank { null },
                )
                _uiState.update { it.copy(isSaving = false, createdHospitalizationId = created.id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = "No se pudo registrar la hospitalizacion") }
            }
        }
    }
}
