package com.cramsan.hirsh.ui.screens.patientedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.model.summary
import com.cramsan.hirsh.repository.assembleFullName
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
    val firstName: String = "",
    val lastName: String = "",
    val secondLastName: String = "",
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
                    firstName = patient.firstName,
                    lastName = patient.lastName,
                    secondLastName = patient.secondLastName,
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

    fun onFirstNameChange(value: String) = _uiState.update { it.copy(firstName = value) }
    fun onLastNameChange(value: String) = _uiState.update { it.copy(lastName = value) }
    fun onSecondLastNameChange(value: String) = _uiState.update { it.copy(secondLastName = value) }
    fun onPhoneChange(value: String) = _uiState.update { it.copy(phone = value) }
    fun onBloodTypeChange(value: String) = _uiState.update { it.copy(bloodType = value) }

    /**
     * [documentNumber]/[birthDate]/[sex] and [allergies] have no setters -- the real
     * `PatchPatientRequest` doesn't accept the identity fields at all (HISS-622, confirmed
     * against the backend source: they're immutable post-registration), and allergies is a
     * separate sub-resource this repository doesn't reconcile on update yet (HISS-623's job).
     * `EditPatientScreen` renders all four read-only rather than showing an editable field that
     * silently wouldn't save.
     */
    fun save() {
        val state = _uiState.value
        val original = state.patient ?: return
        if (state.isSaving) {
            return
        }
        if (state.firstName.isBlank() || state.lastName.isBlank() || state.phone.isBlank()) {
            _uiState.update { it.copy(error = "Completa los campos requeridos") }
            return
        }

        val changedBy = sessionRepository.session.value?.username.orEmpty()
        val (fecha, hora) = nowFechaHora()
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val newValues = original.copy(
                    firstName = state.firstName,
                    lastName = state.lastName,
                    secondLastName = state.secondLastName,
                    fullName = assembleFullName(state.firstName, state.lastName, state.secondLastName),
                    phone = state.phone,
                    bloodType = state.bloodType,
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
