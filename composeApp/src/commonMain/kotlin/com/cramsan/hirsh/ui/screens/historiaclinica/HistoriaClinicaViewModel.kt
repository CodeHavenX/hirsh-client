package com.cramsan.hirsh.ui.screens.historiaclinica

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cramsan.hirsh.model.Diagnostico
import com.cramsan.hirsh.model.EnfermedadActual
import com.cramsan.hirsh.model.ExamenFisico
import com.cramsan.hirsh.model.ExamenRegional
import com.cramsan.hirsh.model.Filiacion
import com.cramsan.hirsh.model.HcSectionKey
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.MotivoIngreso
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Plan
import com.cramsan.hirsh.repository.ClinicalNoteRepository
import com.cramsan.hirsh.repository.EpisodeRepository
import com.cramsan.hirsh.repository.PatientRepository
import com.cramsan.hirsh.ui.components.LoadError
import com.cramsan.hirsh.ui.components.toLoadError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoriaClinicaUiState(
    val isLoading: Boolean = true,
    val patient: Patient? = null,
    val hospitalizacion: Hospitalizacion? = null,
    val historiaClinica: HistoriaClinica = HistoriaClinica(),
    val activeSection: HcSectionKey = HcSectionKey.FILIACION,
    val fieldValues: Map<String, String> = emptyMap(),
    val motivoIngresoDraft: MotivoIngreso = MotivoIngreso(),
    val isSaving: Boolean = false,
    val showPrintPreview: Boolean = false,
    val loadError: LoadError? = null,
)

private data class RequestedIds(val patientId: String, val hospId: String)

/**
 * [fieldOverrides]/[motivoOverrides] hold only what the user has typed since
 * selecting [activeSection] -- null/absent means "not yet touched, derive from
 * the persisted section data." This lets the effective field values be computed
 * fresh on every [uiState] emission (persisted-value-or-override), so there's no
 * separate seeding step that could race the async hospitalizacion fetch on
 * initial load.
 */
private data class FormState(
    val activeSection: HcSectionKey = HcSectionKey.FILIACION,
    val fieldOverrides: Map<String, String> = emptyMap(),
    val motivoOverrides: MotivoIngreso? = null,
    val isSaving: Boolean = false,
    val showPrintPreview: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoriaClinicaViewModel(
    private val patientRepository: PatientRepository,
    private val episodeRepository: EpisodeRepository,
    private val clinicalNoteRepository: ClinicalNoteRepository,
) : ViewModel() {

    private val requestedIds = MutableStateFlow<RequestedIds?>(null)
    private val formState = MutableStateFlow(FormState())

    /** Bumped by [retry] to re-run the reads for the same stay. */
    private val attempt = MutableStateFlow(0)

    val uiState: StateFlow<HistoriaClinicaUiState> = combine(requestedIds, attempt) { ids, _ -> ids }
        .flatMapLatest { ids ->
            if (ids == null) {
                flowOf(HistoriaClinicaUiState(isLoading = true))
            } else {
                combine(
                    patientRepository.getPatient(ids.patientId),
                    episodeRepository.getHospitalization(ids.patientId, ids.hospId),
                    clinicalNoteRepository.getHistoriaClinica(ids.hospId),
                    formState,
                ) { patient, hospitalizacion, historiaClinica, form ->
                    val persistedFieldMap = fieldMapFor(form.activeSection, historiaClinica).orEmpty()
                    val effectiveFieldValues = HC_SECTION_FIELDS[form.activeSection].orEmpty().associate { spec ->
                        spec.key to (form.fieldOverrides[spec.key] ?: persistedFieldMap[spec.key].orEmpty())
                    }
                    val effectiveMotivo = form.motivoOverrides
                        ?: historiaClinica.motivoIngreso.data
                        ?: MotivoIngreso()
                    HistoriaClinicaUiState(
                        isLoading = false,
                        patient = patient,
                        hospitalizacion = hospitalizacion,
                        historiaClinica = historiaClinica,
                        activeSection = form.activeSection,
                        fieldValues = effectiveFieldValues,
                        motivoIngresoDraft = effectiveMotivo,
                        isSaving = form.isSaving,
                        showPrintPreview = form.showPrintPreview,
                    )
                }
                    .onStart { emit(HistoriaClinicaUiState(isLoading = true)) }
                    .catch { emit(HistoriaClinicaUiState(isLoading = false, loadError = it.toLoadError())) }
            }
        }
        // Eagerly, not WhileSubscribed like this screen family's other ViewModels -- save() below
        // reads uiState.value synchronously to build the section payload, which needs this flow
        // actively collecting regardless of whether the Screen currently has a collectAsState()
        // subscriber, not just whenever one happens to be attached.
        .stateIn(viewModelScope, SharingStarted.Eagerly, HistoriaClinicaUiState())

    fun load(patientId: String, hospId: String) {
        if (requestedIds.value == RequestedIds(patientId, hospId)) return
        requestedIds.value = RequestedIds(patientId, hospId)
        formState.value = FormState()
    }

    fun retry() {
        attempt.update { it + 1 }
    }

    fun selectSection(key: HcSectionKey) = formState.update {
        it.copy(activeSection = key, fieldOverrides = emptyMap(), motivoOverrides = null)
    }

    fun onFieldChange(key: String, value: String) = formState.update {
        it.copy(fieldOverrides = it.fieldOverrides + (key to value))
    }

    fun onMotivoOptionChange(current: MotivoIngreso, optionKey: String, checked: Boolean) =
        formState.update { it.copy(motivoOverrides = current.withOption(optionKey, checked)) }

    fun onMotivoOtrosDetalleChange(current: MotivoIngreso, value: String) =
        formState.update { it.copy(motivoOverrides = current.copy(otrosDetalle = value)) }

    fun openPrintPreview() = formState.update { it.copy(showPrintPreview = true) }

    fun closePrintPreview() = formState.update { it.copy(showPrintPreview = false) }

    fun save() {
        val ids = requestedIds.value ?: return
        val state = uiState.value
        if (formState.value.isSaving || state.hospitalizacion == null) {
            return
        }
        val data: Any = when (state.activeSection) {
            HcSectionKey.FILIACION -> Filiacion(
                edad = state.fieldValues["edad"].orEmpty(),
                fechaNacimiento = state.fieldValues["fechaNacimiento"].orEmpty(),
                estadoCivil = state.fieldValues["estadoCivil"].orEmpty(),
                sexo = state.fieldValues["sexo"].orEmpty(),
                dni = state.fieldValues["dni"].orEmpty(),
                gradoInstruccion = state.fieldValues["gradoInstruccion"].orEmpty(),
                ocupacion = state.fieldValues["ocupacion"].orEmpty(),
                lugarNacimiento = state.fieldValues["lugarNacimiento"].orEmpty(),
                lugarProcedencia = state.fieldValues["lugarProcedencia"].orEmpty(),
                familiarResponsable = state.fieldValues["familiarResponsable"].orEmpty(),
                direccion = state.fieldValues["direccion"].orEmpty(),
                servicioIngreso = state.fieldValues["servicioIngreso"].orEmpty(),
            )
            HcSectionKey.MOTIVO_INGRESO -> state.motivoIngresoDraft
            HcSectionKey.ENFERMEDAD_ACTUAL -> EnfermedadActual(
                tiempoEnfermedad = state.fieldValues["tiempoEnfermedad"].orEmpty(),
                formaInicio = state.fieldValues["formaInicio"].orEmpty(),
                curso = state.fieldValues["curso"].orEmpty(),
                duracionEpisodio = state.fieldValues["duracionEpisodio"].orEmpty(),
                relato = state.fieldValues["relato"].orEmpty(),
            )
            HcSectionKey.EXAMEN_FISICO -> ExamenFisico(
                pa = state.fieldValues["pa"].orEmpty(),
                fc = state.fieldValues["fc"].orEmpty(),
                fr = state.fieldValues["fr"].orEmpty(),
                temp = state.fieldValues["temp"].orEmpty(),
                peso = state.fieldValues["peso"].orEmpty(),
                talla = state.fieldValues["talla"].orEmpty(),
                imc = state.fieldValues["imc"].orEmpty(),
                estadoGeneral = state.fieldValues["estadoGeneral"].orEmpty(),
                examenRegional = ExamenRegional(
                    cabezaCuello = state.fieldValues["examenRegional.cabezaCuello"].orEmpty(),
                    toraxPulmones = state.fieldValues["examenRegional.toraxPulmones"].orEmpty(),
                    corazon = state.fieldValues["examenRegional.corazon"].orEmpty(),
                    abdomen = state.fieldValues["examenRegional.abdomen"].orEmpty(),
                    neurologico = state.fieldValues["examenRegional.neurologico"].orEmpty(),
                ),
            )
            HcSectionKey.DIAGNOSTICO -> Diagnostico(
                ejeI = state.fieldValues["ejeI"].orEmpty(),
                ejeII = state.fieldValues["ejeII"].orEmpty(),
                ejeIII = state.fieldValues["ejeIII"].orEmpty(),
                ejeIV = state.fieldValues["ejeIV"].orEmpty(),
                ejeV = state.fieldValues["ejeV"].orEmpty(),
            )
            HcSectionKey.PLAN -> Plan(
                lugarHospitalizacion = state.fieldValues["lugarHospitalizacion"].orEmpty(),
                examenesSolicitados = state.fieldValues["examenesSolicitados"].orEmpty(),
                psicofarmacos = state.fieldValues["psicofarmacos"].orEmpty(),
                evaluacionesSolicitadas = state.fieldValues["evaluacionesSolicitadas"].orEmpty(),
            )
            else -> return
        }

        formState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                clinicalNoteRepository.saveHistoriaClinicaSection(ids.hospId, state.activeSection, data)
                formState.update { it.copy(isSaving = false, fieldOverrides = emptyMap(), motivoOverrides = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                formState.update { it.copy(isSaving = false) }
            }
        }
    }
}

private fun MotivoIngreso.withOption(key: String, checked: Boolean): MotivoIngreso = when (key) {
    "riesgoSuicida" -> copy(riesgoSuicida = checked)
    "riesgoHomicida" -> copy(riesgoHomicida = checked)
    "heteroagresividad" -> copy(heteroagresividad = checked)
    "agitacionPsicomotriz" -> copy(agitacionPsicomotriz = checked)
    "psicosis" -> copy(psicosis = checked)
    "adicciones" -> copy(adicciones = checked)
    "trastornoAfecto" -> copy(trastornoAfecto = checked)
    "tca" -> copy(tca = checked)
    "precisionDiagnostica" -> copy(precisionDiagnostica = checked)
    "precisionTerapeutica" -> copy(precisionTerapeutica = checked)
    "otros" -> copy(otros = checked)
    else -> this
}
