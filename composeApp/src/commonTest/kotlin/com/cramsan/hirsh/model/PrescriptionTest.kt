package com.cramsan.hirsh.model

import kotlin.test.Test
import kotlin.test.assertEquals

private fun drug(name: String) = Drug("d1", name, "500 mg", "Capsula", RouteOfAdministration.PO, null, isPsychotropic = false)

private fun patientAllergicTo(vararg allergies: Pair<AllergyType, String>) = Patient(
    id = "p1",
    medicalRecordNumber = "HC-1",
    documentType = DocumentType.NID,
    documentNumber = "12345678",
    fullName = "Paciente Prueba",
    birthDate = "01/01/1990",
    sex = Sex.FEMALE,
    allergies = allergies.mapIndexed { i, (type, description) -> Allergy("a$i", type, description, null) },
)

private fun evolucion(rx: String, prescripciones: List<Prescripcion>) = Evolucion(
    id = "e1",
    fecha = "",
    hora = "",
    medico = "",
    vitals = Vitals("", "", "", "", "", ""),
    diagnosticos = emptyList(),
    subjective = "",
    objective = "",
    assessment = "",
    plan = "",
    rx = rx,
    pronostico = Pronostico.RESERVADO,
    resultado = EvolucionResultado.ESTACIONARIA,
    examenes = emptyList(),
    examenesObs = "",
    prescripciones = prescripciones,
)

class PrescriptionTest {

    @Test
    fun `a drug matches a medication allergy by name either way round, ignoring case and accents`() {
        val patient = patientAllergicTo(AllergyType.MEDICATION to "ACIDO VALPROICO", AllergyType.MEDICATION to "Haloperidol decanoato")

        assertEquals(listOf("a0"), patient.allergiesMatching(drug("Ácido valproico")).map { it.id })
        assertEquals(listOf("a1"), patient.allergiesMatching(drug("haloperidol")).map { it.id })
        assertEquals(emptyList(), patient.allergiesMatching(drug("Sertralina")))
    }

    @Test
    fun `only medication allergies flag a drug`() {
        val patient = patientAllergicTo(AllergyType.FOOD to "Litio", AllergyType.MEDICATION to " ")

        assertEquals(emptyList(), patient.allergiesMatching(drug("Carbonato de litio")))
    }

    @Test
    fun `the treatment text lists prescriptions, then the free text unless it is the blank placeholder`() {
        val sertralina = Prescripcion("d1", "Sertralina 50 mg · Tableta", "50 mg", "Cada 24 h", RouteOfAdministration.PO, "30 dias", "")

        assertEquals(
            "Sertralina 50 mg · Tableta — 50 mg — Cada 24 h — VO — 30 dias\nDieta blanda",
            evolucion("Dieta blanda", listOf(sertralina)).treatmentText(),
        )
        assertEquals("Sertralina 50 mg · Tableta — 50 mg — Cada 24 h — VO — 30 dias", evolucion("—", listOf(sertralina)).treatmentText())
        assertEquals("", evolucion("—", emptyList()).treatmentText())
    }
}
