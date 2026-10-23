package com.cramsan.hirsh.ui.screens.scaleapply

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.DiagnosticoCie10
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.EpisodeStatus
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.EvolucionResultado
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Pronostico
import com.cramsan.hirsh.model.ScaleDefinition
import com.cramsan.hirsh.model.ScaleOption
import com.cramsan.hirsh.model.ScaleQuestion
import com.cramsan.hirsh.model.ScaleScore
import com.cramsan.hirsh.model.ScaleSeverity
import com.cramsan.hirsh.model.ScaleSummary
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.model.Vitals
import com.cramsan.hirsh.repository.PlaceholderCatalog
import com.cramsan.hirsh.ui.preview.PreviewResponsive
import com.cramsan.hirsh.ui.theme.HirshTheme
import kotlin.time.Instant

private val previewPatient = Patient(
    id = "1e0697d0-f3e4-4755-85ad-d888d2b15f9d",
    medicalRecordNumber = "HC-00135",
    documentType = DocumentType.NID,
    documentNumber = "70567572",
    fullName = "Jesus Alberto Mendoza Aguilar",
    birthDate = "10/08/1990",
    sex = Sex.MALE,
)

private val previewHospitalizacion = Hospitalizacion(
    id = "h_mendoza_1",
    patientId = previewPatient.id,
    currentService = PlaceholderCatalog.service("Psiquiatria (UHSMA)"),
    currentBed = PlaceholderCatalog.bed("04"),
    attendingPhysician = PlaceholderCatalog.physician("Dr. Hirsh"),
    startDate = Instant.parse("2026-06-15T12:50:00Z"),
    endDate = null,
    admissionReason = "Episodio depresivo grave",
    status = EpisodeStatus.ACTIVE,
)

private val previewEvolucion = Evolucion(
    id = "v5c",
    fecha = "18 Jun 2026",
    hora = "10:30",
    medico = "Dr. Hirsh",
    vitals = Vitals(pa = "110/70", fc = "78", fr = "18", temp = "36.6", satO2 = "98", fio2 = "21"),
    diagnosticos = listOf(DiagnosticoCie10("F32.2", "Episodio depresivo grave")),
    subjective = "Refiere mejor descanso.",
    objective = "Afecto menos aplanado.",
    assessment = "Respuesta parcial.",
    plan = "Mantener esquema.",
    rx = "Sertralina 50 mg",
    pronostico = Pronostico.FAVORABLE,
    resultado = EvolucionResultado.FAVORABLE,
    examenes = emptyList(),
    examenesObs = "",
)

private val frequency = listOf(
    ScaleOption(0, "Ningún día"),
    ScaleOption(1, "Varios días"),
    ScaleOption(2, "Más de la mitad de los días"),
    ScaleOption(3, "Casi todos los días"),
)

private val phq9Items = listOf(
    "Poco interés o placer en hacer cosas",
    "Se ha sentido decaído(a), deprimido(a) o sin esperanzas",
    "Ha tenido dificultad para quedarse o permanecer dormido(a), o ha dormido demasiado",
    "Se ha sentido cansado(a) o con poca energía",
    "Sin apetito o ha comido en exceso",
    "Se ha sentido mal con usted mismo(a), o que es un fracaso o que ha quedado mal con usted mismo(a) o con su familia",
    "Ha tenido dificultad para concentrarse en ciertas actividades, tales como leer el periódico o ver la televisión",
    "¿Se ha movido o hablado tan lento que otras personas podrían haberlo notado? O lo contrario: muy inquieto(a) o agitado(a)",
    "Pensamientos de que estaría mejor muerto(a) o de lastimarse de alguna manera",
)

private val previewPhq9 = ScaleDefinition(
    id = "phq9",
    code = "PHQ-9",
    name = "Cuestionario de salud del paciente (PHQ-9)",
    stem = "Durante las últimas 2 semanas, ¿qué tan seguido ha tenido molestias debido a los siguientes problemas?",
    instructions = "Leer cada ítem al paciente. No sugerir respuestas.",
    questions = phq9Items.mapIndexed { index, text -> ScaleQuestion("q${index + 1}", text, frequency) },
)

private val previewScales = listOf(
    ScaleSummary("phq9", "PHQ-9", "Cuestionario de salud del paciente (PHQ-9)", "Tamizaje y seguimiento de la severidad de síntomas depresivos."),
    ScaleSummary("gad7", "GAD-7", "Escala de ansiedad generalizada (GAD-7)", "Tamizaje de ansiedad generalizada."),
)

private fun previewUiState(
    scale: ScaleDefinition? = previewPhq9,
    answers: Map<String, Int> = emptyMap(),
    score: ScaleScore? = null,
) = ApplyScaleUiState(
    isLoading = false,
    patient = previewPatient,
    hospitalizacion = previewHospitalizacion,
    evolucion = previewEvolucion,
    scales = previewScales,
    scale = scale,
    answers = answers,
    score = score,
)

@Composable
private fun Content(uiState: ApplyScaleUiState) {
    HirshTheme {
        ApplyScaleScreenContent(
            uiState = uiState,
            onClose = {},
            onRetry = {},
            onSelectScale = {},
            onChangeScale = {},
            onAnswer = { _, _ -> },
            onObservationsChange = {},
            onSave = {},
            onConfirmSave = {},
            onDismissAlertConfirmation = {},
        )
    }
}

/** More than one scale published: pick one first. */
@PreviewResponsive
@Composable
private fun ApplyScaleScreenPickerPreview() {
    Content(previewUiState(scale = null))
}

/** Part way through, with the server's running score. */
@PreviewResponsive
@Composable
private fun ApplyScaleScreenAnsweringPreview() {
    Content(
        previewUiState(
            answers = mapOf("q1" to 2, "q2" to 1, "q3" to 3, "q4" to 1),
            score = ScaleScore(7.0, ScaleSeverity.MILD, "Depresión leve", raisesSuicideAlert = false),
        ),
    )
}

/** Every item answered, item 9 above "Ningún día": the suicide-risk warning shows before saving. */
@PreviewResponsive
@Composable
private fun ApplyScaleScreenSuicideRiskPreview() {
    Content(
        previewUiState(
            answers = (1..9).associate { "q$it" to if (it == 9) 1 else 2 },
            score = ScaleScore(17.0, ScaleSeverity.MOD_SEVERE, "Depresión moderadamente severa", raisesSuicideAlert = true),
        ),
    )
}
