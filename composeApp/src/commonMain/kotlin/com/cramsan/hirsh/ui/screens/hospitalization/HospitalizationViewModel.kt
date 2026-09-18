package com.cramsan.hirsh.ui.screens.hospitalization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.network.ApiError
import com.cramsan.hirsh.network.ApiException
import com.cramsan.hirsh.repository.HospitalizationRepository
import com.cramsan.hirsh.repository.PatientRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HospitalizationUiState(
    val isLoading: Boolean = true,
    val patient: Patient? = null,
    val hospitalizacion: Hospitalizacion? = null,
    val isDischarging: Boolean = false,
    val error: String? = null,
)

private data class RequestedIds(val patientId: String, val hospId: String)

@OptIn(ExperimentalCoroutinesApi::class)
class HospitalizationViewModel(
    private val patientRepository: PatientRepository,
    private val hospitalizationRepository: HospitalizationRepository,
) : ViewModel() {

    private val requestedIds = MutableStateFlow<RequestedIds?>(null)
    private val dischargingState = MutableStateFlow(false)
    private val errorState = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HospitalizationUiState> = requestedIds
        .flatMapLatest { ids ->
            if (ids == null) {
                flowOf(HospitalizationUiState(isLoading = true))
            } else {
                combine(
                    patientRepository.getPatient(ids.patientId),
                    hospitalizationRepository.getHospitalization(ids.patientId, ids.hospId),
                    dischargingState,
                    errorState,
                ) { patient, hospitalizacion, isDischarging, error ->
                    HospitalizationUiState(
                        isLoading = false,
                        patient = patient,
                        hospitalizacion = hospitalizacion,
                        isDischarging = isDischarging,
                        error = error,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HospitalizationUiState())

    fun load(patientId: String, hospId: String) {
        errorState.value = null
        requestedIds.value = RequestedIds(patientId, hospId)
    }

    fun discharge() {
        val ids = requestedIds.value ?: return
        if (dischargingState.value) {
            return
        }
        dischargingState.value = true
        errorState.value = null
        viewModelScope.launch {
            try {
                // Read fresh rather than off uiState.value -- uiState's upstream flow only runs
                // while something collects it (WhileSubscribed(5_000)), so uiState.value can
                // still be the pre-load default here if nothing is observing it yet.
                val jpaVersion = hospitalizationRepository.getHospitalization(ids.patientId, ids.hospId)
                    .first()?.jpaVersion ?: return@launch
                hospitalizationRepository.discharge(ids.hospId, jpaVersion)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                errorState.value = if (e.error is ApiError.Conflict) {
                    "Esta hospitalizacion fue modificada por otro usuario. Recarga la pagina para ver los cambios recientes."
                } else {
                    "No se pudo dar de alta al paciente"
                }
            } catch (e: Exception) {
                errorState.value = "No se pudo dar de alta al paciente"
            } finally {
                dischargingState.update { false }
            }
        }
    }
}
