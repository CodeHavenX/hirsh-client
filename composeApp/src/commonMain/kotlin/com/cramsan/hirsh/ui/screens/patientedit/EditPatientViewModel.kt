package com.cramsan.hirsh.ui.screens.patientedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cramsan.hirsh.model.Allergy
import com.cramsan.hirsh.model.AllergyType
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Severity
import com.cramsan.hirsh.model.Sex
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
    val allergies: List<Allergy> = emptyList(),
    /** The add/edit allergy form currently open, if any -- see [AllergyDraft]. */
    val allergyDraft: AllergyDraft? = null,
    /** The allergy whose "Quitar" is awaiting its inline confirmation. */
    val confirmingDeleteId: String? = null,
    val allergyError: String? = null,
    val isAllergyBusy: Boolean = false,
    val error: String? = null,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
)

/**
 * The open allergy form. [editingId] null means a new allergy; otherwise only [severity]/
 * [observations] are editable -- the real `PatchAllergyRequest` can't change the agent itself
 * (see [Allergy]'s doc comment).
 */
data class AllergyDraft(
    val editingId: String? = null,
    val allergyType: AllergyType? = null,
    val description: String = "",
    val severity: Severity? = null,
    val observations: String = "",
    /**
     * The edited allergy's severity as loaded. Once graded, a PATCH can't reset it back to
     * ungraded (a null field means "unchanged" server-side), so the form doesn't offer that.
     */
    val originalSeverity: Severity? = null,
) {
    val isNew: Boolean get() = editingId == null
}

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
                    allergies = patient.allergies,
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
     * [documentNumber]/[birthDate]/[sex] have no setters -- the real `PatchPatientRequest` doesn't
     * accept the identity fields at all (HISS-622, confirmed against the backend source: they're
     * immutable post-registration), so `EditPatientScreen` renders them read-only rather than
     * showing an editable field that silently wouldn't save. Allergies aren't part of this save
     * either: each add/edit/remove below is its own immediate call against the allergies
     * sub-resource (HISS-623), so "Guardar cambios" only ever covers demographics.
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

    fun onStartAddAllergy() = _uiState.update {
        it.copy(allergyDraft = AllergyDraft(), confirmingDeleteId = null, allergyError = null)
    }

    fun onStartEditAllergy(allergy: Allergy) = _uiState.update {
        it.copy(
            allergyDraft = AllergyDraft(
                editingId = allergy.id,
                allergyType = allergy.allergyType,
                description = allergy.description,
                severity = allergy.severity,
                observations = allergy.observations,
                originalSeverity = allergy.severity,
            ),
            confirmingDeleteId = null,
            allergyError = null,
        )
    }

    fun onAllergyTypeChange(value: AllergyType) = updateDraft { if (it.isNew) it.copy(allergyType = value) else it }
    fun onAllergyDescriptionChange(value: String) = updateDraft { if (it.isNew) it.copy(description = value) else it }
    fun onAllergySeverityChange(value: Severity?) = updateDraft { it.copy(severity = value) }
    fun onAllergyObservationsChange(value: String) = updateDraft { it.copy(observations = value) }

    fun onCancelAllergyDraft() = _uiState.update { it.copy(allergyDraft = null, allergyError = null) }

    /** Saves the open [AllergyDraft] immediately -- independent of [save]. */
    fun saveAllergyDraft() {
        val state = _uiState.value
        val patientId = state.patient?.id ?: return
        val draft = state.allergyDraft ?: return
        if (state.isAllergyBusy) return
        val type = draft.allergyType
        if (type == null || draft.description.isBlank()) {
            _uiState.update { it.copy(allergyError = "Indica el tipo y la descripcion de la alergia") }
            return
        }

        runAllergyCall(failureMessage = "No se pudo guardar la alergia") {
            if (draft.isNew) {
                val added = patientRepository.addAllergy(patientId, type, draft.description, draft.severity, draft.observations)
                _uiState.update { it.copy(allergies = listOf(added) + it.allergies, allergyDraft = null) }
            } else {
                val editingId = draft.editingId ?: return@runAllergyCall
                val updated = patientRepository.updateAllergy(patientId, editingId, draft.severity, draft.observations)
                _uiState.update { current ->
                    current.copy(allergies = current.allergies.map { if (it.id == updated.id) updated else it }, allergyDraft = null)
                }
            }
        }
    }

    fun onRequestDeleteAllergy(allergy: Allergy) = _uiState.update {
        it.copy(confirmingDeleteId = allergy.id, allergyDraft = null, allergyError = null)
    }

    fun onCancelDeleteAllergy() = _uiState.update { it.copy(confirmingDeleteId = null) }

    fun confirmDeleteAllergy() {
        val state = _uiState.value
        val patientId = state.patient?.id ?: return
        val allergyId = state.confirmingDeleteId ?: return
        if (state.isAllergyBusy) return

        runAllergyCall(failureMessage = "No se pudo quitar la alergia") {
            patientRepository.deleteAllergy(patientId, allergyId)
            _uiState.update { current ->
                current.copy(allergies = current.allergies.filterNot { it.id == allergyId }, confirmingDeleteId = null)
            }
        }
    }

    private fun updateDraft(transform: (AllergyDraft) -> AllergyDraft) = _uiState.update { state ->
        state.allergyDraft?.let { state.copy(allergyDraft = transform(it)) } ?: state
    }

    /**
     * Shared busy/error handling for the allergy calls. On failure the draft (or pending delete)
     * stays open so nothing typed is lost; a 404 means someone else already removed it.
     */
    private fun runAllergyCall(failureMessage: String, call: suspend () -> Unit) {
        _uiState.update { it.copy(isAllergyBusy = true, allergyError = null) }
        viewModelScope.launch {
            try {
                call()
                _uiState.update { it.copy(isAllergyBusy = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                val message = if (e.error is ApiError.NotFound) {
                    "Esta alergia ya no existe. Recarga la pagina para ver la lista actual."
                } else {
                    failureMessage
                }
                _uiState.update { it.copy(isAllergyBusy = false, allergyError = message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isAllergyBusy = false, allergyError = failureMessage) }
            }
        }
    }

    private fun nowFechaHora(): Pair<String, String> {
        val now = clock.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return formatDate(now.date) to formatTime(now.time)
    }
}
