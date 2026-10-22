package com.cramsan.hirsh.ui.screens.discharge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cramsan.hirsh.model.DischargeCondition
import com.cramsan.hirsh.model.DischargeDetails
import com.cramsan.hirsh.model.DischargeType
import com.cramsan.hirsh.model.EpisodeStatus
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Icd10Code
import com.cramsan.hirsh.model.NoteStatus
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.network.ApiError
import com.cramsan.hirsh.network.ApiException
import com.cramsan.hirsh.repository.ClinicalNoteRepository
import com.cramsan.hirsh.repository.EpisodeRepository
import com.cramsan.hirsh.repository.Icd10Repository
import com.cramsan.hirsh.repository.PatientRepository
import com.cramsan.hirsh.ui.components.LoadError
import com.cramsan.hirsh.ui.components.toLoadError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

data class DischargeUiState(
    val isLoading: Boolean = true,
    val patient: Patient? = null,
    val hospitalizacion: Hospitalizacion? = null,
    val type: DischargeType? = null,
    val condition: DischargeCondition? = null,
    /** Text typed into the diagnosis search; cleared once a [diagnosis] is picked. */
    val diagnosisQuery: String = "",
    val diagnosisResults: List<Icd10Code> = emptyList(),
    /**
     * The query [diagnosisResults] answer, or null before any search has finished. Empty results
     * only mean "no match" when this equals the trimmed [diagnosisQuery]; otherwise a search is
     * still pending (debounce or request).
     */
    val diagnosisResultsQuery: String? = null,
    val diagnosis: Icd10Code? = null,
    val nextAppointmentDate: String = "",
    val followUpPlan: String = "",
    val error: String? = null,
    val isSaving: Boolean = false,
    val discharged: Boolean = false,
    val loadError: LoadError? = null,
    /** The stay's clinical notes still awaiting a signature; discharge is blocked until it's empty. */
    val unsignedNotes: List<String> = emptyList(),
)

/** One-letter searches match most of the catalog; the debounce avoids a request per keystroke. */
internal const val MIN_QUERY_LENGTH = 2
private const val SEARCH_DEBOUNCE_MS = 300L

private const val CONFLICT_ERROR =
    "No se pudo dar de alta: la hospitalizacion fue modificada por otro usuario, o tiene interconsultas " +
        "pendientes o una nota de ingreso sin firmar. Recarga la pagina e intenta de nuevo."
private const val GENERIC_ERROR = "No se pudo dar de alta al paciente"
internal const val UNSIGNED_NOTES_ERROR = "Firma la historia clinica y las evoluciones pendientes antes de dar de alta"

/**
 * The real discharge form (HISS-633): `DischargeEpisodeRequest` needs a discharge type, the
 * patient's condition and a catalogued ICD-10 diagnosis, which the old one-tap button never
 * collected. The end date isn't asked for -- like admission's start date, the server stamps it.
 */
@OptIn(FlowPreview::class)
class DischargeViewModel(
    private val patientRepository: PatientRepository,
    private val episodeRepository: EpisodeRepository,
    private val clinicalNoteRepository: ClinicalNoteRepository,
    private val icd10Repository: Icd10Repository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DischargeUiState())
    val uiState: StateFlow<DischargeUiState> = _uiState.asStateFlow()

    private val diagnosisQuery = MutableStateFlow("")

    private var patientId: String? = null
    private var hospId: String? = null

    init {
        viewModelScope.launch {
            diagnosisQuery.debounce(SEARCH_DEBOUNCE_MS).collectLatest { query ->
                val trimmed = query.trim()
                val results = if (trimmed.length < MIN_QUERY_LENGTH) {
                    emptyList()
                } else {
                    try {
                        icd10Repository.search(trimmed)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
                _uiState.update { it.copy(diagnosisResults = results, diagnosisResultsQuery = trimmed) }
            }
        }
    }

    fun load(patientId: String, hospId: String) {
        if (this.patientId == patientId && this.hospId == hospId) return
        this.patientId = patientId
        this.hospId = hospId
        _uiState.value = DischargeUiState(isLoading = true)
        fetch(patientId, hospId)
    }

    fun retry() {
        val patientId = patientId ?: return
        val hospId = hospId ?: return
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        fetch(patientId, hospId)
    }

    private fun fetch(patientId: String, hospId: String) {
        viewModelScope.launch {
            try {
                val patient = patientRepository.getPatient(patientId).first()
                val hospitalizacion = episodeRepository.getHospitalization(patientId, hospId).first()
                val unsignedNotes = if (hospitalizacion?.status == EpisodeStatus.ACTIVE) unsignedNotes(hospId) else emptyList()
                _uiState.update {
                    it.copy(isLoading = false, patient = patient, hospitalizacion = hospitalizacion, unsignedNotes = unsignedNotes)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, loadError = e.toLoadError()) }
            }
        }
    }

    /**
     * The backend refuses to discharge over an unsigned Historia Clinica (a bare 409), and HCEM-74
     * settled that pending evoluciones must be signed first too; checking here names exactly what is
     * missing instead of failing at the end of the form.
     */
    private suspend fun unsignedNotes(hospId: String): List<String> {
        val historia = clinicalNoteRepository.getHistoriaClinica(hospId).first()
        val evoluciones = clinicalNoteRepository.getEvoluciones(hospId).first()
        return listOfNotNull("Historia clinica".takeIf { historia.status == NoteStatus.DRAFT }) +
            evoluciones.filter { it.status == NoteStatus.DRAFT }.map { "Evolucion del ${it.fecha} · ${it.hora}" }
    }

    fun onTypeChange(value: DischargeType) = _uiState.update { it.copy(type = value, error = null) }
    fun onConditionChange(value: DischargeCondition) = _uiState.update { it.copy(condition = value, error = null) }
    fun onNextAppointmentDateChange(value: String) = _uiState.update { it.copy(nextAppointmentDate = value, error = null) }
    fun onFollowUpPlanChange(value: String) = _uiState.update { it.copy(followUpPlan = value, error = null) }

    fun onDiagnosisQueryChange(value: String) {
        _uiState.update { it.copy(diagnosisQuery = value, error = null) }
        diagnosisQuery.value = value
    }

    fun onDiagnosisSelected(code: Icd10Code) {
        _uiState.update { it.copy(diagnosis = code, diagnosisQuery = "", diagnosisResults = emptyList(), diagnosisResultsQuery = null, error = null) }
        diagnosisQuery.value = ""
    }

    fun onDiagnosisCleared() = _uiState.update { it.copy(diagnosis = null) }

    /**
     * Type, condition and diagnosis are required, as on `DischargeEpisodeRequest`; the follow-up
     * plan and next appointment are optional, but a next appointment must be a real `dd/MM/yyyy`
     * date. The version sent is read fresh, so a stale screen can't discharge over a newer edit.
     */
    fun discharge() {
        val state = _uiState.value
        val patientId = patientId ?: return
        val hospId = hospId ?: return
        if (state.isSaving || state.hospitalizacion?.status != EpisodeStatus.ACTIVE) return
        if (state.unsignedNotes.isNotEmpty()) {
            _uiState.update { it.copy(error = UNSIGNED_NOTES_ERROR) }
            return
        }
        val type = state.type
        val condition = state.condition
        val diagnosis = state.diagnosis
        if (type == null || condition == null || diagnosis == null) {
            _uiState.update { it.copy(error = "Completa los campos requeridos") }
            return
        }
        val nextAppointment = state.nextAppointmentDate.trim().ifEmpty { null }
        if (nextAppointment != null && !nextAppointment.isDisplayDate()) {
            _uiState.update { it.copy(error = "La proxima cita debe tener el formato dd/mm/aaaa") }
            return
        }

        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val jpaVersion = episodeRepository.getHospitalization(patientId, hospId).first()?.jpaVersion
                    ?: throw ApiException(ApiError.NotFound(hospId))
                episodeRepository.discharge(
                    hospId = hospId,
                    details = DischargeDetails(
                        type = type,
                        condition = condition,
                        diagnosisIcd10 = diagnosis.code,
                        followUpPlan = state.followUpPlan.trim().ifEmpty { null },
                        nextAppointmentDate = nextAppointment,
                    ),
                    jpaVersion = jpaVersion,
                )
                _uiState.update { it.copy(isSaving = false, discharged = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                val message = when (e.error) {
                    is ApiError.Conflict -> CONFLICT_ERROR
                    is ApiError.NotFound -> "No se encontro la hospitalizacion o el diagnostico CIE-10 elegido"
                    else -> GENERIC_ERROR
                }
                _uiState.update { it.copy(isSaving = false, error = message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = GENERIC_ERROR) }
            }
        }
    }
}

private fun String.isDisplayDate(): Boolean {
    val parts = split("/")
    if (parts.size != 3 || parts[2].length != 4) return false
    val (day, month, year) = parts.map { it.toIntOrNull() ?: return false }
    return runCatching { LocalDate(year, month, day) }.isSuccess
}
