package com.cramsan.hirsh.ui.screens.patienthistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cramsan.hirsh.model.AccessLogEntry
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.network.ApiError
import com.cramsan.hirsh.network.ApiException
import com.cramsan.hirsh.repository.AccessLogRepository
import com.cramsan.hirsh.repository.PatientRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Why the trail couldn't be shown -- a missing permission reads differently from a failed request. */
enum class AccessLogError { FORBIDDEN, FAILED }

data class PatientHistoryUiState(
    val isLoading: Boolean = true,
    val patient: Patient? = null,
    /** The most recent entries, newest first -- only the first page (see [AccessLogRepository]). */
    val entries: List<AccessLogEntry> = emptyList(),
    val totalEntries: Long = 0,
    val error: AccessLogError? = null,
) {
    /** More entries exist server side than [entries] shows. */
    val isTruncated: Boolean get() = totalEntries > entries.size
}

private sealed interface AccessLogResult {
    data class Loaded(val entries: List<AccessLogEntry>, val totalEntries: Long) : AccessLogResult
    data class Failed(val error: AccessLogError) : AccessLogResult
}

/**
 * Backs the patient's access-log screen (HISS-624): who opened this record, when, and what they
 * did, from the real audit trail. The backend keeps no field-level before/after values, so this
 * replaced the prototype's field-diff change history.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PatientHistoryViewModel(
    private val patientRepository: PatientRepository,
    private val accessLogRepository: AccessLogRepository,
) : ViewModel() {

    private val requestedId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PatientHistoryUiState> = requestedId
        .flatMapLatest { id ->
            val patientId = id ?: return@flatMapLatest flowOf(PatientHistoryUiState(isLoading = true))
            combine(patientRepository.getPatient(patientId), accessLog(patientId)) { patient, result ->
                when (result) {
                    is AccessLogResult.Loaded -> PatientHistoryUiState(
                        isLoading = false,
                        patient = patient,
                        entries = result.entries,
                        totalEntries = result.totalEntries,
                    )
                    is AccessLogResult.Failed -> PatientHistoryUiState(isLoading = false, patient = patient, error = result.error)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PatientHistoryUiState())

    private fun accessLog(patientId: String): Flow<AccessLogResult> =
        accessLogRepository.getPatientAccessLog(patientId)
            .map<_, AccessLogResult> { AccessLogResult.Loaded(it.entries, it.totalEntries) }
            .catch { e ->
                val error = if (e is ApiException && e.error is ApiError.Forbidden) AccessLogError.FORBIDDEN else AccessLogError.FAILED
                emit(AccessLogResult.Failed(error))
            }

    fun load(patientId: String) {
        requestedId.value = patientId
    }
}
