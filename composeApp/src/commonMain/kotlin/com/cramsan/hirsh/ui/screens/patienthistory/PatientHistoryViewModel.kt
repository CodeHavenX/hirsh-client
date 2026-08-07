package com.cramsan.hirsh.ui.screens.patienthistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.repository.PatientRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** One changed field, flattened out of a [com.cramsan.hirsh.model.PatientChangeLogEntry] -- mirrors patient-history.html's `rows = getPatientChangeLog(...).flatMap(...)`. */
data class ChangeHistoryRow(
    val fecha: String,
    val hora: String,
    val changedBy: String,
    val label: String,
    val oldValue: String,
    val newValue: String,
)

data class PatientHistoryUiState(
    val isLoading: Boolean = true,
    val patient: Patient? = null,
    val rows: List<ChangeHistoryRow> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class PatientHistoryViewModel(private val patientRepository: PatientRepository) : ViewModel() {

    private val requestedId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PatientHistoryUiState> = requestedId
        .flatMapLatest { id ->
            val patientId = id ?: return@flatMapLatest flowOf(PatientHistoryUiState(isLoading = true))
            combine(
                patientRepository.getPatient(patientId),
                patientRepository.getChangeLog(patientId),
            ) { patient, changeLog ->
                val rows = changeLog.flatMap { entry ->
                    entry.fields.map { field ->
                        ChangeHistoryRow(
                            fecha = entry.fecha,
                            hora = entry.hora,
                            changedBy = entry.changedBy,
                            label = field.label,
                            oldValue = field.oldValue,
                            newValue = field.newValue,
                        )
                    }
                }
                PatientHistoryUiState(isLoading = false, patient = patient, rows = rows)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PatientHistoryUiState())

    fun load(patientId: String) {
        requestedId.value = patientId
    }
}
