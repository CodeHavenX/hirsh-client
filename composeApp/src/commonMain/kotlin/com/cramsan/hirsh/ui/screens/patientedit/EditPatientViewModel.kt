package com.cramsan.hirsh.ui.screens.patientedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.model.singleAllergyFromText
import com.cramsan.hirsh.model.summary
import com.cramsan.hirsh.network.ApiError
import com.cramsan.hirsh.network.ApiException
import com.cramsan.hirsh.repository.PatientRepository
import com.cramsan.hirsh.repository.SessionRepository
import com.cramsan.hirsh.util.Clock
import com.cramsan.hirsh.util.formatDate
import com.cramsan.hirsh.util.formatTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class EditPatientUiState(
    val isLoading: Boolean = true,
    val patient: Patient? = null,
    val fullName: String = "",
    val documentNumber: String = "",
    val birthDate: String = "",
    val phone: String = "",
    val sex: Sex? = null,
    val bloodType: String = "",
    val allergies: String = "",
    val error: String? = null,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
)

/**
 * Pre-fills the form from a one-shot [PatientRepository.getPatient] fetch, not a
 * continuously-collected [kotlinx.coroutines.flow.Flow] like [com.cramsan.hirsh.ui.screens.patientrecord.PatientRecordViewModel] --
 * a background change to the same patient while the user is mid-edit shouldn't
 * silently overwrite their in-progress input.
 */
class EditPatientViewModel(
    private val patientRepository: PatientRepository,
    private val sessionRepository: SessionRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditPatientUiState())
    val uiState: StateFlow<EditPatientUiState> = _uiState.asStateFlow()

    fun load(patientId: String) {
        if (_uiState.value.patient?.id == patientId) return
        _uiState.value = EditPatientUiState(isLoading = true)
        viewModelScope.launch {
            val patient = patientRepository.getPatient(patientId).first()
            if (patient == null) {
                _uiState.update { it.copy(isLoading = false, error = "Paciente no encontrado") }
                return@launch
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    patient = patient,
                    fullName = patient.fullName,
                    documentNumber = patient.documentNumber,
                    birthDate = patient.birthDate,
                    phone = patient.phone,
                    sex = patient.sex,
                    bloodType = patient.bloodType,
                    allergies = patient.allergies.summary(),
                )
            }
        }
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(fullName = value) }
    fun onNationalIdChange(value: String) = _uiState.update { it.copy(documentNumber = value) }
    fun onDateOfBirthChange(value: String) = _uiState.update { it.copy(birthDate = value) }
    fun onPhoneChange(value: String) = _uiState.update { it.copy(phone = value) }
    fun onSexChange(value: Sex) = _uiState.update { it.copy(sex = value) }
    fun onBloodTypeChange(value: String) = _uiState.update { it.copy(bloodType = value) }
    fun onAllergiesChange(value: String) = _uiState.update { it.copy(allergies = value) }

    fun save() {
        val state = _uiState.value
        val original = state.patient ?: return
        val sex = state.sex
        if (state.isSaving) {
            return
        }
        if (state.fullName.isBlank() || state.documentNumber.isBlank() || state.birthDate.isBlank() ||
            state.phone.isBlank() || sex == null
        ) {
            _uiState.update { it.copy(error = "Completa los campos requeridos") }
            return
        }

        val changedBy = sessionRepository.session.value?.username.orEmpty()
        val (fecha, hora) = nowFechaHora()
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val newValues = original.copy(
                    fullName = state.fullName,
                    documentNumber = state.documentNumber,
                    birthDate = state.birthDate,
                    phone = state.phone,
                    sex = sex,
                    bloodType = state.bloodType,
                    allergies = singleAllergyFromText(state.allergies),
                )
                patientRepository.updatePatient(original.id, newValues, changedBy, fecha, hora)
                _uiState.update { it.copy(isSaving = false, saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                val message = if (e.error is ApiError.Conflict) {
                    "Otro usuario actualizo este paciente mientras editabas. Recarga la pagina para ver los cambios recientes."
                } else {
                    "No se pudo guardar los cambios"
                }
                _uiState.update { it.copy(isSaving = false, error = message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = "No se pudo guardar los cambios") }
            }
        }
    }

    private fun nowFechaHora(): Pair<String, String> {
        val now = clock.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return formatDate(now.date) to formatTime(now.time)
    }
}
