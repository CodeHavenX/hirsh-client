package com.cramsan.hirsh.ui.screens.evolucionview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.repository.HospitalizationRepository
import com.cramsan.hirsh.repository.PatientRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

enum class EvolucionViewTab { EVOLUCION, EXAMENES }

data class EvolucionViewUiState(
    val isLoading: Boolean = true,
    val patient: Patient? = null,
    val hospitalizacion: Hospitalizacion? = null,
    val evolucion: Evolucion? = null,
    val selectedTab: EvolucionViewTab = EvolucionViewTab.EVOLUCION,
    val showPrintPreview: Boolean = false,
)

private data class RequestedIds(val patientId: String, val hospId: String, val evoId: String)

@OptIn(ExperimentalCoroutinesApi::class)
class EvolucionViewViewModel(
    private val patientRepository: PatientRepository,
    private val hospitalizationRepository: HospitalizationRepository,
) : ViewModel() {

    private val requestedIds = MutableStateFlow<RequestedIds?>(null)
    private val selectedTab = MutableStateFlow(EvolucionViewTab.EVOLUCION)
    private val showPrintPreview = MutableStateFlow(false)

    val uiState: StateFlow<EvolucionViewUiState> = requestedIds
        .flatMapLatest { ids ->
            if (ids == null) {
                flowOf(EvolucionViewUiState(isLoading = true))
            } else {
                combine(
                    patientRepository.getPatient(ids.patientId),
                    hospitalizationRepository.getHospitalization(ids.patientId, ids.hospId),
                    selectedTab,
                    showPrintPreview,
                ) { patient, hospitalizacion, tab, printPreview ->
                    EvolucionViewUiState(
                        isLoading = false,
                        patient = patient,
                        hospitalizacion = hospitalizacion,
                        // Derived from the already patient-validated hospitalizacion, not a separate
                        // getEvolucion(hospId, evoId) call -- guarantees the full patient->hospitalizacion->
                        // evolucion chain is validated together (a hospId belonging to a different patient
                        // never leaks its evoluciones through here, per HISS-106's C5 tightening).
                        evolucion = hospitalizacion?.evoluciones?.find { it.id == ids.evoId },
                        selectedTab = tab,
                        showPrintPreview = printPreview,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EvolucionViewUiState())

    fun load(patientId: String, hospId: String, evoId: String) {
        requestedIds.value = RequestedIds(patientId, hospId, evoId)
    }

    fun selectTab(tab: EvolucionViewTab) {
        selectedTab.value = tab
    }

    fun openPrintPreview() {
        showPrintPreview.value = true
    }

    fun closePrintPreview() {
        showPrintPreview.value = false
    }
}
