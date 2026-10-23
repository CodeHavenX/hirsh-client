package com.cramsan.hirsh.ui.screens.hospitalization

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cramsan.hirsh.model.ActiveTreatment
import com.cramsan.hirsh.model.AppliedScale
import com.cramsan.hirsh.model.Diagnostico
import com.cramsan.hirsh.model.DiagnosticoCie10
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.EpisodeStatus
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.EvolucionResultado
import com.cramsan.hirsh.model.HcSection
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.NoteStatus
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Plan
import com.cramsan.hirsh.model.Pronostico
import com.cramsan.hirsh.model.RouteOfAdministration
import com.cramsan.hirsh.model.ScaleScore
import com.cramsan.hirsh.model.ScaleSeverity
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.model.Vitals
import com.cramsan.hirsh.repository.PlaceholderCatalog
import com.cramsan.hirsh.ui.components.ActiveTreatmentsPanel
import com.cramsan.hirsh.ui.components.ActiveTreatmentsSection
import com.cramsan.hirsh.ui.preview.PreviewResponsive
import com.cramsan.hirsh.ui.theme.HirshTheme
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

private val previewPatient = Patient(
    id = "1e0697d0-f3e4-4755-85ad-d888d2b15f9d",
    medicalRecordNumber = "HC-00135",
    documentType = DocumentType.NID,
    documentNumber = "70567572",
    fullName = "Jesus Alberto Mendoza Aguilar",
    birthDate = "10/08/1990",
    phone = "955-123-456",
    bloodType = "B+",
    allergies = emptyList(),
    sex = Sex.MALE,
)

private fun previewEvolucion(id: String, resultado: EvolucionResultado, status: NoteStatus = NoteStatus.SIGNED) = Evolucion(
    id = id,
    fecha = "18 Jun 2026",
    hora = "22:30",
    medico = "Dr. Hirsh",
    vitals = Vitals(pa = "115/72", fc = "80", fr = "20", temp = "37.0", satO2 = "95", fio2 = "24"),
    diagnosticos = listOf(DiagnosticoCie10("B59", "Neumonia por Pneumocystis")),
    subjective = "Paciente refiere notable mejoria de disnea. Tolera destete progresivo de oxigeno.",
    objective = "PA 115/72, FC 80, FR 20, SatO2 95% con FiO2 24%.",
    assessment = "Buena respuesta a tratamiento antimicrobiano.",
    plan = "Continuar TMP-SMX y corticoides en descenso.",
    rx = "TMP-SMX 15mg/kg/dia EV c/8h",
    pronostico = Pronostico.FAVORABLE,
    resultado = resultado,
    examenes = emptyList(),
    examenesObs = "",
    status = status,
)

private fun previewHospitalizacion(
    id: String,
    status: EpisodeStatus,
) = Hospitalizacion(
    id = id,
    patientId = previewPatient.id,
    currentService = PlaceholderCatalog.service("Emergencia - Topico de Medicina"),
    currentBed = PlaceholderCatalog.bed("0"),
    attendingPhysician = PlaceholderCatalog.physician("Dr. Hirsh"),
    startDate = Instant.parse("2026-06-15T12:50:00Z"),
    endDate = if (status == EpisodeStatus.ACTIVE) null else Instant.parse("2026-06-20T10:00:00Z"),
    admissionReason = "Sintomas respiratorios",
    status = status,
)

private val previewHistoriaClinica = HistoriaClinica(
    diagnostico = HcSection(
        complete = true,
        data = Diagnostico(ejeI = "—", ejeII = "—", ejeIII = "Neumonia (B59)", ejeIV = "—", ejeV = "—"),
    ),
    plan = HcSection(
        complete = true,
        data = Plan(
            lugarHospitalizacion = "Emergencia",
            examenesSolicitados = "—",
            psicofarmacos = "—",
            evaluacionesSolicitadas = "—",
        ),
    ),
)

private fun previewUiState(
    hospitalizacion: Hospitalizacion,
    evoluciones: List<Evolucion>,
    hcStatus: NoteStatus = NoteStatus.SIGNED,
) = HospitalizationUiState(
    isLoading = false,
    patient = previewPatient,
    hospitalizacion = hospitalizacion,
    historiaClinica = previewHistoriaClinica.copy(status = hcStatus),
    evoluciones = evoluciones,
    treatments = ActiveTreatmentsSection.Loaded(emptyList()),
    canDownloadPdf = true,
)

private fun previewPhq9(id: String, documentId: String, appliedAt: String, score: ScaleScore) = AppliedScale(
    id = id,
    documentId = documentId,
    scaleCode = "PHQ-9",
    scaleName = "Cuestionario de salud del paciente (PHQ-9)",
    appliedAt = Instant.parse(appliedAt),
    appliedBy = "Dr. Hirsh",
    score = score,
    observations = null,
)

@PreviewResponsive
@Composable
private fun HospitalizationScreenActivaPreview() {
    val hospitalizacion = previewHospitalizacion(
        id = "h_mendoza_1",
        status = EpisodeStatus.ACTIVE,
    )
    val evoluciones = listOf(
        previewEvolucion("v5c", EvolucionResultado.FAVORABLE, NoteStatus.DRAFT),
        previewEvolucion("v5", EvolucionResultado.ESTACIONARIA),
    )
    HirshTheme {
        HospitalizationScreenContent(
            uiState = previewUiState(hospitalizacion, evoluciones, hcStatus = NoteStatus.DRAFT),
            onNewEvolucion = {},
            onOpenHistoriaClinica = {},
            onEvolucionSelected = {},
            onDischarge = {},
            onRetry = {},
        )
    }
}

/** status = CLOSED (Alta) -- exercises the "Dar de alta" button being hidden and the "Fecha alta" KV row. */
@PreviewResponsive
@Composable
private fun HospitalizationScreenAltaPreview() {
    val hospitalizacion = previewHospitalizacion(
        id = "h_mendoza_2",
        status = EpisodeStatus.CLOSED,
    )
    val evoluciones = listOf(previewEvolucion("v5c", EvolucionResultado.FAVORABLE))
    HirshTheme {
        HospitalizationScreenContent(
            uiState = previewUiState(hospitalizacion, evoluciones),
            onNewEvolucion = {},
            onOpenHistoriaClinica = {},
            onEvolucionSelected = {},
            onDischarge = {},
            onRetry = {},
        )
    }
}

/** No evoluciones yet -- exercises the empty-state copy. */
@PreviewResponsive
@Composable
private fun HospitalizationScreenNoEvolucionesPreview() {
    val hospitalizacion = previewHospitalizacion(
        id = "h_mendoza_3",
        status = EpisodeStatus.ACTIVE,
    )
    val evoluciones = emptyList<Evolucion>()
    HirshTheme {
        HospitalizationScreenContent(
            uiState = previewUiState(hospitalizacion, evoluciones),
            onNewEvolucion = {},
            onOpenHistoriaClinica = {},
            onEvolucionSelected = {},
            onDischarge = {},
            onRetry = {},
        )
    }
}

/** status = TRANSFERRED -- the third state: closed like Alta, with its own badge. */
@PreviewResponsive
@Composable
private fun HospitalizationScreenTransferredPreview() {
    val hospitalizacion = previewHospitalizacion(
        id = "h_mendoza_4",
        status = EpisodeStatus.TRANSFERRED,
    )
    val evoluciones = listOf(previewEvolucion("v5c", EvolucionResultado.FAVORABLE))
    HirshTheme {
        HospitalizationScreenContent(
            uiState = previewUiState(hospitalizacion, evoluciones),
            onNewEvolucion = {},
            onOpenHistoriaClinica = {},
            onEvolucionSelected = {},
            onDischarge = {},
            onRetry = {},
        )
    }
}

/**
 * The "Escalas aplicadas" section alone, since it sits below the fold of the full screen: PHQ-9 applied twice during
 * the stay, the series falling, and the first result having raised the suicide-risk alert.
 */
@PreviewResponsive
@Composable
private fun HospitalizationAppliedScalesPreview() {
    val scales = listOf(
        previewPhq9("s2", "v5c", "2026-06-18T15:30:00Z", ScaleScore(9.0, ScaleSeverity.MILD, "Depresión leve", raisesSuicideAlert = false)),
        previewPhq9("s1", "v5", "2026-06-16T14:00:00Z", ScaleScore(18.0, ScaleSeverity.MOD_SEVERE, "Depresión moderadamente severa", raisesSuicideAlert = true)),
    )
    HirshTheme {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            AppliedScalesList(
                section = AppliedScalesSection.Loaded(scales),
                onScaleSelected = {},
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}

/** The "Tratamiento activo" panel alone, below the fold of the full screen, for a user who can suspend treatments. */
@PreviewResponsive
@Composable
private fun HospitalizationActiveTreatmentsPreview() {
    val treatments = listOf(
        ActiveTreatment("t1", "Sertralina 50 mg · Tableta", "50 mg", "cada 24 h", RouteOfAdministration.PO, "h1", LocalDate(2026, 6, 15)),
        ActiveTreatment("t2", "Haloperidol 5 mg/mL · Ampolla", "5 mg", "condicional a agitacion", RouteOfAdministration.IM, "h1", LocalDate(2026, 6, 15)),
    )
    HirshTheme {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            ActiveTreatmentsPanel(
                section = ActiveTreatmentsSection.Loaded(treatments),
                onSuspend = {},
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}
