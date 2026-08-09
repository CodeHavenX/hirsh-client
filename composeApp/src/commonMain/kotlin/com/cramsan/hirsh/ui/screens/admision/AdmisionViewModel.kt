package com.cramsan.hirsh.ui.screens.admision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.repository.HospitalizationRepository
import com.cramsan.hirsh.repository.PatientRepository
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
    val servicio: String = "",
    val cama: String = "",
    val medicoResponsable: String = "",
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

    fun onServicioChange(value: String) = _uiState.update { it.copy(servicio = value) }
    fun onCamaChange(value: String) = _uiState.update { it.copy(cama = value) }
    fun onMedicoResponsableChange(value: String) = _uiState.update { it.copy(medicoResponsable = value) }
    fun onMotivoChange(value: String) = _uiState.update { it.copy(motivo = value) }

    /**
     * Mirrors the prototype's registerHospitalization(): servicio, cama, and
     * medicoResponsable block submit; motivo does not -- a blank motivo is
     * saved as "—" the same way the prototype's own
     * `document.getElementById('adm-motivo').value || '—'` does.
     */
    fun register() {
        val state = _uiState.value
        val id = patientId
        if (state.isSaving) {
            return
        }
        if (state.servicio.isBlank() || state.cama.isBlank() || state.medicoResponsable.isBlank() || id == null) {
            _uiState.update { it.copy(error = "Completa los campos requeridos") }
            return
        }

        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val created = hospitalizationRepository.addHospitalization(
                    patientId = id,
                    servicio = state.servicio,
                    cama = state.cama,
                    medicoResponsable = state.medicoResponsable,
                    motivoIngreso = state.motivo.ifBlank { "—" },
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
