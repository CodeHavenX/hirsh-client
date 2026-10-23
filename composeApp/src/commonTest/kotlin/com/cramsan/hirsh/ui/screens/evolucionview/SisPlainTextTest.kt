package com.cramsan.hirsh.ui.screens.evolucionview

import com.cramsan.hirsh.model.DiagnosticoCie10
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.EvolucionResultado
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Pronostico
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.model.Vitals
import kotlin.test.Test
import kotlin.test.assertEquals

private val patient = Patient(
    id = "p1",
    medicalRecordNumber = "HC-00142",
    documentType = DocumentType.NID,
    documentNumber = "45678901",
    fullName = "Maria Gonzalez Huerta",
    birthDate = "14/03/1989",
    sex = Sex.FEMALE,
)

private val fullEvolucion = Evolucion(
    id = "evo1",
    fecha = "18 Jun 2026",
    hora = "22:30",
    medico = "Dr. Hirsh",
    vitals = Vitals(pa = "110/70", fc = "78", fr = "18", temp = "36.6", satO2 = "98", fio2 = "21"),
    diagnosticos = listOf(
        DiagnosticoCie10("F32.2", "Episodio depresivo grave"),
        DiagnosticoCie10("", "Insomnio"),
    ),
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

class SisPlainTextTest {

    @Test
    fun `a complete note lists every section in order`() {
        assertEquals(
            """
            === EVOLUCION CLINICA - PSIQUIATRIA ===
            FECHA/HORA: 18 Jun 2026 22:30
            PACIENTE: Maria Gonzalez Huerta - Historia: HC-00142
            MEDICO RESPONSABLE: Dr. Hirsh
            [SUBJETIVO] Refiere mejor descanso.
            [OBJETIVO] Afecto menos aplanado.
            [ANALISIS] Respuesta parcial.
            [PLAN] Mantener esquema.
            [DIAGNOSTICOS] Episodio depresivo grave (F32.2); Insomnio
            [TRATAMIENTO] Sertralina 50 mg
            [SIGNOS VITALES] PA: 110/70 | FC: 78 | FR: 18 | T: 36.6 | SatO2: 98 | FiO2: 21
            ========================================
            """.trimIndent(),
            evolucionSisText(patient, fullEvolucion),
        )
    }

    @Test
    fun `empty sections and the form's dash placeholder are left out`() {
        val sparse = fullEvolucion.copy(
            medico = "",
            assessment = "  ",
            plan = "",
            rx = "—",
            diagnosticos = listOf(DiagnosticoCie10("—", "Episodio depresivo grave"), DiagnosticoCie10("F51", "")),
            vitals = Vitals(pa = "110/70", fc = "", fr = "", temp = "", satO2 = "—", fio2 = ""),
        )

        assertEquals(
            """
            === EVOLUCION CLINICA - PSIQUIATRIA ===
            FECHA/HORA: 18 Jun 2026 22:30
            PACIENTE: Maria Gonzalez Huerta - Historia: HC-00142
            [SUBJETIVO] Refiere mejor descanso.
            [OBJETIVO] Afecto menos aplanado.
            [DIAGNOSTICOS] Episodio depresivo grave
            [SIGNOS VITALES] PA: 110/70
            ========================================
            """.trimIndent(),
            evolucionSisText(patient, sparse),
        )
    }

    @Test
    fun `a multi-line prescription keeps its lines but loses tabs, carriage returns and control characters`() {
        val evolucion = fullEvolucion.copy(rx = "Sertralina 50 mg\tc/24h\r\nClonazepam 0.5 mg \u0007c/noche  \r\n")

        val rxLines = evolucionSisText(patient, evolucion).lines()
            .dropWhile { !it.startsWith("[TRATAMIENTO]") }
            .take(2)

        assertEquals(listOf("[TRATAMIENTO] Sertralina 50 mg c/24h", "Clonazepam 0.5 mg c/noche"), rxLines)
    }
}
