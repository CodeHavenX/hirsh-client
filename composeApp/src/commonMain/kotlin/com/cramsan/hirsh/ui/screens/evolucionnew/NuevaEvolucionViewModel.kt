package com.cramsan.hirsh.ui.screens.evolucionnew

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.FlowPreview
import com.cramsan.hirsh.repository.DrugRepository
import com.cramsan.hirsh.model.allergiesMatching
import com.cramsan.hirsh.model.RouteOfAdministration
import com.cramsan.hirsh.model.Prescripcion
import com.cramsan.hirsh.model.Drug
import com.cramsan.hirsh.model.Allergy
import androidx.lifecycle.viewModelScope
import com.cramsan.hirsh.model.DiagnosticoCie10
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.EvolucionResultado
import com.cramsan.hirsh.model.Examen
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Permission
import com.cramsan.hirsh.model.Pronostico
import com.cramsan.hirsh.model.Vitals
import com.cramsan.hirsh.model.can
import com.cramsan.hirsh.model.toDisplayLabel
import com.cramsan.hirsh.repository.ClinicalNoteRepository
import com.cramsan.hirsh.repository.EpisodeRepository
import com.cramsan.hirsh.repository.PatientRepository
import com.cramsan.hirsh.repository.SessionRepository
import com.cramsan.hirsh.ui.components.LoadError
import com.cramsan.hirsh.ui.components.toLoadError
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

enum class EvolucionTab { EVOLUCION, EXAMENES }

data class DxRow(val codigoCie10: String = "", val descripcion: String = "")

data class ExamRow(
    val tipo: String = "Laboratorio",
    val nombre: String = "",
    val resultado: String = "",
    val unidad: String = "",
    val referencia: String = "",
    val fecha: String = "",
)

/** One prescription being written; [query] is the drug search text until a [drug] is picked. */
data class RxRow(
    val drug: Drug? = null,
    val query: String = "",
    val dose: String = "",
    val frequency: String = "",
    val route: RouteOfAdministration? = null,
    val duration: String = "",
    val instructions: String = "",
) {
    val isBlank: Boolean
        get() = drug == null && listOf(query, dose, frequency, duration, instructions).all { it.isBlank() }
}

/** The formulary matches for the prescription row being searched, answering [query]. */
data class DrugSearch(val row: Int, val query: String, val results: List<Drug>)

data class NuevaEvolucionUiState(
    val isLoading: Boolean = true,
    val patient: Patient? = null,
    val hospitalizacion: Hospitalizacion? = null,
    val selectedTab: EvolucionTab = EvolucionTab.EVOLUCION,
    val subjective: String = "",
    val objective: String = "",
    val assessment: String = "",
    val plan: String = "",
    val rx: String = "",
    val pa: String = "",
    val fc: String = "",
    val fr: String = "",
    val temp: String = "",
    val satO2: String = "",
    val fio2: String = "",
    val diagnosticos: List<DxRow> = listOf(DxRow()),
    val pronostico: String = "",
    val resultadoEvolucion: String = "",
    val examenes: List<ExamRow> = listOf(ExamRow()),
    val examenesObs: String = "",
    val prescripciones: List<RxRow> = emptyList(),
    val drugSearch: DrugSearch? = null,
    val openedFecha: String = "",
    val openedHora: String = "",
    val error: String? = null,
    val isSaving: Boolean = false,
    val createdEvolucionId: String? = null,
    val loadError: LoadError? = null,
) {
    /** Per prescription row, the patient's medication allergies its drug matches. */
    val rxAllergies: List<List<Allergy>>
        get() = prescripciones.map { row -> row.drug?.let { patient?.allergiesMatching(it) }.orEmpty() }
}

internal const val REQUIRED_FIELDS_ERROR = "Completa los campos requeridos"
internal const val RX_INCOMPLETE_ERROR = "Cada prescripcion necesita un medicamento del formulario, dosis y frecuencia"

/** One-letter searches match most of the formulary; the debounce avoids a request per keystroke. */
internal const val MIN_DRUG_QUERY_LENGTH = 2
private const val DRUG_SEARCH_DEBOUNCE_MS = 300L

@OptIn(FlowPreview::class)
@Suppress("TooManyFunctions")
class NuevaEvolucionViewModel(
    private val patientRepository: PatientRepository,
    private val episodeRepository: EpisodeRepository,
    private val clinicalNoteRepository: ClinicalNoteRepository,
    private val drugRepository: DrugRepository,
    private val sessionRepository: SessionRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NuevaEvolucionUiState())
    val uiState: StateFlow<NuevaEvolucionUiState> = _uiState.asStateFlow()
    private var loadedIds: Pair<String, String>? = null

    /** The row being searched and its text; null when no search is open. */
    private val drugQuery = MutableStateFlow<Pair<Int, String>?>(null)

    init {
        viewModelScope.launch {
            drugQuery.debounce(DRUG_SEARCH_DEBOUNCE_MS).collectLatest { request ->
                val (row, query) = request ?: return@collectLatest
                val trimmed = query.trim()
                val results = if (trimmed.length < MIN_DRUG_QUERY_LENGTH) {
                    emptyList()
                } else {
                    try {
                        drugRepository.search(trimmed)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
                _uiState.update { it.copy(drugSearch = DrugSearch(row, trimmed, results)) }
            }
        }
    }

    fun load(patientId: String, hospId: String) {
        if (loadedIds == patientId to hospId) return
        loadedIds = patientId to hospId
        val (openedFecha, openedHora) = nowFechaHora()
        _uiState.value = NuevaEvolucionUiState(isLoading = true, openedFecha = openedFecha, openedHora = openedHora)
        fetch(patientId, hospId)
    }

    fun retry() {
        val (patientId, hospId) = loadedIds ?: return
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        fetch(patientId, hospId)
    }

    private fun fetch(patientId: String, hospId: String) {
        viewModelScope.launch {
            try {
                val patient = patientRepository.getPatient(patientId).first()
                val hospitalizacion = episodeRepository.getHospitalization(patientId, hospId).first()
                _uiState.update { it.copy(isLoading = false, patient = patient, hospitalizacion = hospitalizacion) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, loadError = e.toLoadError()) }
            }
        }
    }

    fun selectTab(tab: EvolucionTab) = _uiState.update { it.copy(selectedTab = tab) }

    fun onSubjectiveChange(value: String) = _uiState.update { it.copy(subjective = value, error = null) }
    fun onObjectiveChange(value: String) = _uiState.update { it.copy(objective = value, error = null) }
    fun onAssessmentChange(value: String) = _uiState.update { it.copy(assessment = value) }
    fun onPlanChange(value: String) = _uiState.update { it.copy(plan = value) }
    fun onRxChange(value: String) = _uiState.update { it.copy(rx = value) }
    fun onPaChange(value: String) = _uiState.update { it.copy(pa = value) }
    fun onFcChange(value: String) = _uiState.update { it.copy(fc = value) }
    fun onFrChange(value: String) = _uiState.update { it.copy(fr = value) }
    fun onTempChange(value: String) = _uiState.update { it.copy(temp = value) }
    fun onSatO2Change(value: String) = _uiState.update { it.copy(satO2 = value) }
    fun onFio2Change(value: String) = _uiState.update { it.copy(fio2 = value) }
    fun onPronosticoChange(value: String) = _uiState.update { it.copy(pronostico = value, error = null) }
    fun onResultadoEvolucionChange(value: String) = _uiState.update { it.copy(resultadoEvolucion = value, error = null) }
    fun onExamenesObsChange(value: String) = _uiState.update { it.copy(examenesObs = value) }

    fun addDxRow() = _uiState.update { it.copy(diagnosticos = it.diagnosticos + DxRow()) }

    fun removeDxRow(index: Int) = _uiState.update {
        val updated = it.diagnosticos.filterIndexed { i, _ -> i != index }
        it.copy(diagnosticos = updated.ifEmpty { listOf(DxRow()) })
    }

    fun onDxCodigoChange(index: Int, value: String) = updateDxRow(index) { it.copy(codigoCie10 = value) }
    fun onDxDescripcionChange(index: Int, value: String) = updateDxRow(index) { it.copy(descripcion = value) }

    private fun updateDxRow(index: Int, transform: (DxRow) -> DxRow) = _uiState.update {
        it.copy(
            diagnosticos = it.diagnosticos.mapIndexed { i, row -> if (i == index) transform(row) else row },
            error = null,
        )
    }

    fun addExamRow() = _uiState.update { it.copy(examenes = it.examenes + ExamRow()) }

    fun removeExamRow(index: Int) = _uiState.update {
        val updated = it.examenes.filterIndexed { i, _ -> i != index }
        it.copy(examenes = updated.ifEmpty { listOf(ExamRow()) })
    }

    fun onExamTipoChange(index: Int, value: String) = updateExamRow(index) { it.copy(tipo = value) }
    fun onExamNombreChange(index: Int, value: String) = updateExamRow(index) { it.copy(nombre = value) }
    fun onExamResultadoChange(index: Int, value: String) = updateExamRow(index) { it.copy(resultado = value) }
    fun onExamUnidadChange(index: Int, value: String) = updateExamRow(index) { it.copy(unidad = value) }
    fun onExamReferenciaChange(index: Int, value: String) = updateExamRow(index) { it.copy(referencia = value) }
    fun onExamFechaChange(index: Int, value: String) = updateExamRow(index) { it.copy(fecha = value) }

    private fun updateExamRow(index: Int, transform: (ExamRow) -> ExamRow) = _uiState.update {
        it.copy(examenes = it.examenes.mapIndexed { i, row -> if (i == index) transform(row) else row })
    }

    fun addRxRow() = _uiState.update { it.copy(prescripciones = it.prescripciones + RxRow()) }

    /** Removing a row shifts the ones after it, so an open search is closed rather than left on the wrong row. */
    fun removeRxRow(index: Int) {
        _uiState.update {
            it.copy(prescripciones = it.prescripciones.filterIndexed { i, _ -> i != index }, drugSearch = null, error = null)
        }
        drugQuery.value = null
    }

    fun onRxQueryChange(index: Int, value: String) {
        updateRxRow(index) { it.copy(query = value) }
        drugQuery.value = index to value
    }

    /** The drug's usual route fills an empty route field; a route already chosen is kept. */
    fun onRxDrugSelected(index: Int, drug: Drug) {
        updateRxRow(index) { it.copy(drug = drug, query = "", route = it.route ?: drug.route) }
        _uiState.update { it.copy(drugSearch = null) }
        drugQuery.value = null
    }

    fun onRxDrugCleared(index: Int) = updateRxRow(index) { it.copy(drug = null) }
    fun onRxDoseChange(index: Int, value: String) = updateRxRow(index) { it.copy(dose = value) }
    fun onRxFrequencyChange(index: Int, value: String) = updateRxRow(index) { it.copy(frequency = value) }
    fun onRxRouteChange(index: Int, value: RouteOfAdministration) = updateRxRow(index) { it.copy(route = value) }
    fun onRxDurationChange(index: Int, value: String) = updateRxRow(index) { it.copy(duration = value) }
    fun onRxInstructionsChange(index: Int, value: String) = updateRxRow(index) { it.copy(instructions = value) }

    private fun updateRxRow(index: Int, transform: (RxRow) -> RxRow) = _uiState.update {
        it.copy(
            prescripciones = it.prescripciones.mapIndexed { i, row -> if (i == index) transform(row) else row },
            error = null,
        )
    }

    fun save() {
        val state = _uiState.value
        val ids = loadedIds ?: return
        state.hospitalizacion ?: return
        if (state.isSaving) {
            return
        }
        if (state.subjective.isBlank() || state.objective.isBlank() ||
            state.diagnosticos.none { it.descripcion.isNotBlank() } ||
            state.pronostico.isBlank() || state.resultadoEvolucion.isBlank()
        ) {
            _uiState.update { it.copy(error = REQUIRED_FIELDS_ERROR) }
            return
        }
        // The backend silently skips a prescription without a drug, dose or frequency when it records
        // treatments, so an incomplete row is refused here rather than lost there.
        val prescriptionRows = state.prescripciones.filterNot { it.isBlank }
        if (prescriptionRows.any { it.drug == null || it.dose.isBlank() || it.frequency.isBlank() }) {
            _uiState.update { it.copy(error = RX_INCOMPLETE_ERROR) }
            return
        }
        val pronostico = Pronostico.entries.find { it.toDisplayLabel() == state.pronostico } ?: return
        val resultado = EvolucionResultado.entries.find { it.toDisplayLabel() == state.resultadoEvolucion } ?: return

        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val (fecha, hora) = nowFechaHora()
                val medico = sessionRepository.session.value?.displayName.orEmpty()
                val created = clinicalNoteRepository.addEvolucion(
                    ids.second,
                    Evolucion(
                        id = "",
                        fecha = fecha,
                        hora = hora,
                        medico = medico,
                        vitals = Vitals(
                            pa = state.pa,
                            fc = state.fc,
                            fr = state.fr,
                            temp = state.temp,
                            satO2 = state.satO2,
                            fio2 = state.fio2,
                        ),
                        diagnosticos = state.diagnosticos
                            .filter { it.descripcion.isNotBlank() }
                            .map { DiagnosticoCie10(it.codigoCie10, it.descripcion) },
                        subjective = state.subjective,
                        objective = state.objective,
                        assessment = state.assessment,
                        plan = state.plan,
                        rx = state.rx.ifBlank { "—" },
                        pronostico = pronostico,
                        resultado = resultado,
                        examenes = state.examenes
                            .filter { it.nombre.isNotBlank() }
                            .map { Examen(it.tipo, it.nombre, it.resultado, it.unidad, it.referencia, it.fecha) },
                        examenesObs = state.examenesObs,
                        prescripciones = prescriptionRows.map { row ->
                            val drug = checkNotNull(row.drug)
                            Prescripcion(
                                drugId = drug.id,
                                drugName = drug.displayName,
                                dose = row.dose.trim(),
                                frequency = row.frequency.trim(),
                                route = row.route,
                                duration = row.duration.trim(),
                                instructions = row.instructions.trim(),
                            )
                        },
                    ),
                )
                signIfAllowed(ids.second, created.id)
                _uiState.update { it.copy(isSaving = false, createdEvolucionId = created.id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = "No se pudo guardar la evolucion") }
            }
        }
    }

    /**
     * "Guardar" finishes the note for whoever can sign it (HCEM-74's decision); anyone else leaves a
     * draft for a signer. A failed signature still keeps the saved draft: the note view shows it as
     * pending and offers to sign it again, so the user isn't stuck on this form.
     */
    private suspend fun signIfAllowed(hospId: String, evoId: String) {
        if (sessionRepository.session.value?.can(Permission.DOCUMENT_SIGN) != true) return
        try {
            clinicalNoteRepository.signEvolucion(hospId, evoId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Left as a draft, as described above.
        }
    }

    private fun nowFechaHora(): Pair<String, String> {
        val now = clock.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return formatDate(now.date) to formatTime(now.time)
    }
}
