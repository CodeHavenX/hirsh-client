package com.cramsan.hirsh.model

import kotlin.test.Test
import kotlin.test.assertEquals

class HistoriaClinicaTest {

    @Test
    fun `completionCount is 0 of 10 for a blank Historia Clinica`() {
        assertEquals(0 to 10, HistoriaClinica().completionCount())
    }

    @Test
    fun `completionCount counts only the completed modeled sections`() {
        val historiaClinica = HistoriaClinica(
            filiacion = HcSection(complete = true, data = sampleFiliacion()),
            diagnostico = HcSection(complete = true, data = sampleDiagnostico()),
        )

        assertEquals(2 to 10, historiaClinica.completionCount())
    }

    @Test
    fun `completionCount counts all 6 modeled sections when every one is complete`() {
        val historiaClinica = allSectionsComplete()

        assertEquals(6 to 10, historiaClinica.completionCount())
    }

    @Test
    fun `statusLabel is Borrador when nothing is complete`() {
        assertEquals("Borrador", HistoriaClinica().statusLabel())
    }

    @Test
    fun `statusLabel is En progreso when some sections are complete`() {
        val historiaClinica = HistoriaClinica(filiacion = HcSection(complete = true, data = sampleFiliacion()))

        assertEquals("En progreso", historiaClinica.statusLabel())
    }

    @Test
    fun `statusLabel stays En progreso even with all 6 modeled sections complete -- Completa is unreachable until the remaining 4 sections get a digital form`() {
        assertEquals("En progreso", allSectionsComplete().statusLabel())
    }
}

private fun allSectionsComplete() = HistoriaClinica(
    filiacion = HcSection(complete = true, data = sampleFiliacion()),
    motivoIngreso = HcSection(complete = true, data = MotivoIngreso()),
    enfermedadActual = HcSection(complete = true, data = sampleEnfermedadActual()),
    examenFisico = HcSection(complete = true, data = sampleExamenFisico()),
    diagnostico = HcSection(complete = true, data = sampleDiagnostico()),
    plan = HcSection(complete = true, data = samplePlan()),
)

private fun sampleFiliacion() = Filiacion(
    edad = "37 anos",
    fechaNacimiento = "14/03/1989",
    estadoCivil = "Casada",
    sexo = "Femenino",
    dni = "45678901",
    gradoInstruccion = "Superior",
    ocupacion = "Independiente",
    lugarNacimiento = "Lima",
    lugarProcedencia = "Lima",
    familiarResponsable = "—",
    direccion = "—",
    servicioIngreso = "Medicina General",
)

private fun sampleEnfermedadActual() = EnfermedadActual(
    tiempoEnfermedad = "5 dias",
    formaInicio = "Insidioso",
    curso = "Progresivo",
    duracionEpisodio = "5 dias",
    relato = "—",
)

private fun sampleExamenFisico() = ExamenFisico(
    pa = "118/76",
    fc = "78",
    fr = "18",
    temp = "37.8",
    peso = "—",
    talla = "—",
    imc = "—",
    estadoGeneral = "—",
    examenRegional = ExamenRegional(
        cabezaCuello = "—",
        toraxPulmones = "—",
        corazon = "—",
        abdomen = "—",
        neurologico = "—",
    ),
)

private fun sampleDiagnostico() = Diagnostico(ejeI = "—", ejeII = "—", ejeIII = "Bronquitis aguda (J20.9)", ejeIV = "—", ejeV = "—")

private fun samplePlan() = Plan(
    lugarHospitalizacion = "Medicina General",
    examenesSolicitados = "—",
    psicofarmacos = "—",
    evaluacionesSolicitadas = "—",
)
