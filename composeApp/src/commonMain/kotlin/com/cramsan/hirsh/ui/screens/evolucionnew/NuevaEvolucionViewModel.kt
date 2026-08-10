package com.cramsan.hirsh.ui.screens.evolucionnew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cramsan.hirsh.model.DiagnosticoCie10
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.EvolucionResultado
import com.cramsan.hirsh.model.Examen
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Pronostico
import com.cramsan.hirsh.model.Vitals
import com.cramsan.hirsh.model.toDisplayLabel
import com.cramsan.hirsh.repository.HospitalizationRepository
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
    val openedFecha: String = "",
    val openedHora: String = "",
    val error: String? = null,
    val isSaving: Boolean = false,
    val createdEvolucionId: String? = null,
)

class NuevaEvolucionViewModel(
    private val patientRepository: PatientRepository,
    private val hospitalizationRepository: HospitalizationRepository,
    private val sessionRepository: SessionRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NuevaEvolucionUiState())
    val uiState: StateFlow<NuevaEvolucionUiState> = _uiState.asStateFlow()
    private var loadedIds: Pair<String, String>? = null

    fun load(patientId: String, hospId: String) {
        if (loadedIds == patientId to hospId) return
        loadedIds = patientId to hospId
        val (openedFecha, openedHora) = nowFechaHora()
        _uiState.value = NuevaEvolucionUiState(isLoading = true, openedFecha = openedFecha, openedHora = openedHora)
        viewModelScope.launch {
            val patient = patientRepository.getPatient(patientId).first()
            val hospitalizacion = hospitalizationRepository.getHospitalization(patientId, hospId).first()
            _uiState.update { it.copy(isLoading = false, patient = patient, hospitalizacion = hospitalizacion) }
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
            _uiState.update { it.copy(error = "Completa los campos requeridos") }
            return
        }
        val pronostico = Pronostico.entries.find { it.toDisplayLabel() == state.pronostico } ?: return
        val resultado = EvolucionResultado.entries.find { it.toDisplayLabel() == state.resultadoEvolucion } ?: return

        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val (fecha, hora) = nowFechaHora()
                val medico = sessionRepository.session.value?.displayName.orEmpty()
                val created = hospitalizationRepository.addEvolucion(
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
                    ),
                )
                _uiState.update { it.copy(isSaving = false, createdEvolucionId = created.id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = "No se pudo guardar la evolucion") }
            }
        }
    }

    private fun nowFechaHora(): Pair<String, String> {
        val now = clock.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return formatDate(now.date) to formatTime(now.time)
    }
}
