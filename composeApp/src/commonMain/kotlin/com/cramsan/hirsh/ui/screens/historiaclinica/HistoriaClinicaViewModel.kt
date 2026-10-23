package com.cramsan.hirsh.ui.screens.historiaclinica

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cramsan.hirsh.model.Diagnostico
import com.cramsan.hirsh.model.EnfermedadActual
import com.cramsan.hirsh.model.EpisodeStatus
import com.cramsan.hirsh.model.ExamenFisico
import com.cramsan.hirsh.model.ExamenRegional
import com.cramsan.hirsh.model.Filiacion
import com.cramsan.hirsh.model.HcSectionKey
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.MotivoIngreso
import com.cramsan.hirsh.model.NoteStatus
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Permission
import com.cramsan.hirsh.model.Plan
import com.cramsan.hirsh.model.can
import com.cramsan.hirsh.model.isReadyToSign
import com.cramsan.hirsh.network.ApiError
import com.cramsan.hirsh.network.ApiException
import com.cramsan.hirsh.repository.ClinicalNoteRepository
import com.cramsan.hirsh.repository.EpisodeRepository
import com.cramsan.hirsh.repository.PatientRepository
import com.cramsan.hirsh.repository.PdfExportRepository
import com.cramsan.hirsh.repository.PdfExportTarget
import com.cramsan.hirsh.repository.SessionRepository
import com.cramsan.hirsh.ui.components.LoadError
import com.cramsan.hirsh.ui.components.PdfDownload
import com.cramsan.hirsh.ui.components.PdfDownloader
import com.cramsan.hirsh.ui.components.toLoadError
import com.cramsan.hirsh.util.FileSaver
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
    /** Signed, voided, or the stay is closed: sections are shown but can't be saved (HCEM-79). */
    val isReadOnly: Boolean = false,
    /** The signed-in user may sign this draft. Whether every section is filled in is [HistoriaClinica.isReadyToSign]. */
    val canSign: Boolean = false,
    val isSigning: Boolean = false,
    val error: String? = null,
    /** A signed Historia Clinica, for users with PDF_EXPORT on a platform that can save files (HCEM-14). */
    val canDownloadPdf: Boolean = false,
    val pdfDownload: PdfDownload = PdfDownload.Idle,
)

internal const val SAVE_CONFLICT_ERROR = "Otro usuario modifico la historia; revisa y guarda de nuevo"
internal const val SIGN_CONFLICT_ERROR = "La historia fue modificada por otro usuario; revisala antes de firmar"
internal const val SIGN_FORBIDDEN_ERROR = "Tu cuenta no puede firmar: necesita el permiso de firma y un numero de colegiatura (CMP)"
internal const val SAVE_ERROR = "No se pudo guardar la seccion"
internal const val SIGN_ERROR = "No se pudo firmar la historia clinica"
internal const val SIGN_INCOMPLETE_ERROR = "Completa todas las secciones antes de firmar"

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
    val isSigning: Boolean = false,
    val showPrintPreview: Boolean = false,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoriaClinicaViewModel(
    private val patientRepository: PatientRepository,
    private val episodeRepository: EpisodeRepository,
    private val clinicalNoteRepository: ClinicalNoteRepository,
    private val sessionRepository: SessionRepository,
    pdfExportRepository: PdfExportRepository,
    fileSaver: FileSaver,
) : ViewModel() {

    private val pdfDownloader = PdfDownloader(pdfExportRepository, fileSaver, viewModelScope)

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
                    sessionRepository.session,
                ) { patient, hospitalizacion, historiaClinica, form, session ->
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
                        isReadOnly = hospitalizacion?.status != EpisodeStatus.ACTIVE ||
                            historiaClinica.status == NoteStatus.SIGNED || historiaClinica.status == NoteStatus.VOIDED,
                        canSign = session?.can(Permission.DOCUMENT_SIGN) == true &&
                            hospitalizacion?.status == EpisodeStatus.ACTIVE && historiaClinica.status == NoteStatus.DRAFT,
                        isSigning = form.isSigning,
                        error = form.error,
                        canDownloadPdf = pdfDownloader.isAvailable && session?.can(Permission.PDF_EXPORT) == true &&
                            historiaClinica.status == NoteStatus.SIGNED,
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
        .combine(pdfDownloader.state) { state, download -> state.copy(pdfDownload = download) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, HistoriaClinicaUiState())

    fun downloadPdf() {
        val ids = requestedIds.value ?: return
        if (!uiState.value.canDownloadPdf) return
        pdfDownloader.download(PdfExportTarget.HistoriaClinica(ids.hospId))
    }

    fun load(patientId: String, hospId: String) {
        if (requestedIds.value == RequestedIds(patientId, hospId)) return
        requestedIds.value = RequestedIds(patientId, hospId)
        formState.value = FormState()
    }

    fun retry() {
        attempt.update { it + 1 }
    }

    fun selectSection(key: HcSectionKey) = formState.update {
        it.copy(activeSection = key, fieldOverrides = emptyMap(), motivoOverrides = null, error = null)
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
        if (formState.value.isSaving || state.hospitalizacion == null || state.isReadOnly) {
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

        formState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                clinicalNoteRepository.saveHistoriaClinicaSection(ids.hospId, state.activeSection, data)
                formState.update { it.copy(isSaving = false, fieldOverrides = emptyMap(), motivoOverrides = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The typed values stay in the form either way, so nothing is lost: on a conflict the
                // repository has already reloaded the other user's version underneath them.
                val message = if ((e as? ApiException)?.error is ApiError.Conflict) SAVE_CONFLICT_ERROR else SAVE_ERROR
                formState.update { it.copy(isSaving = false, error = message) }
            }
        }
    }

    /** Signs the draft once every section with a form is complete (HCEM-74's decision). */
    fun sign() {
        val ids = requestedIds.value ?: return
        val state = uiState.value
        if (!state.canSign || state.isSigning) return
        if (!state.historiaClinica.isReadyToSign()) {
            formState.update { it.copy(error = SIGN_INCOMPLETE_ERROR) }
            return
        }
        formState.update { it.copy(isSigning = true, error = null) }
        viewModelScope.launch {
            try {
                clinicalNoteRepository.signHistoriaClinica(ids.hospId)
                formState.update { it.copy(isSigning = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = when ((e as? ApiException)?.error) {
                    is ApiError.Conflict -> SIGN_CONFLICT_ERROR
                    ApiError.Forbidden -> SIGN_FORBIDDEN_ERROR
                    else -> SIGN_ERROR
                }
                formState.update { it.copy(isSigning = false, error = message) }
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
