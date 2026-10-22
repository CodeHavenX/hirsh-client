package com.cramsan.hirsh.repository

import app.cash.turbine.test
import com.cramsan.hirsh.model.DiagnosticoCie10
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.EvolucionResultado
import com.cramsan.hirsh.model.Filiacion
import com.cramsan.hirsh.model.HcSectionKey
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.Pronostico
import com.cramsan.hirsh.model.Vitals
import com.cramsan.hirsh.util.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private val FIXED_NOW: Instant = Instant.parse("2027-01-15T10:30:00Z")

private class FixedClock(private val instant: Instant = FIXED_NOW) : Clock {
    override fun now(): Instant = instant
}

/** A real backend episode's id -- nothing seeded under it. */
private const val UNSEEDED_HOSP_ID = "8d1c3a52-0a4e-4f7e-9d61-2b7f1f3c9e10"

private fun draftEvolucion(fecha: String = "15 Jan 2027", hora: String = "10:30") = Evolucion(
    id = "ignored",
    fecha = fecha,
    hora = hora,
    medico = "Dr. Hirsh",
    vitals = Vitals(pa = "120/80", fc = "70", fr = "16", temp = "36.5", satO2 = "98", fio2 = "21"),
    diagnosticos = listOf(DiagnosticoCie10("J20.9", "Bronquitis aguda")),
    subjective = "s",
    objective = "o",
    assessment = "a",
    plan = "p",
    rx = "—",
    pronostico = Pronostico.FAVORABLE,
    resultado = EvolucionResultado.FAVORABLE,
    examenes = emptyList(),
    examenesObs = "",
)

private val newFiliacion = Filiacion(
    edad = "36 anos",
    fechaNacimiento = "10/08/1990",
    estadoCivil = "Soltero(a)",
    sexo = "Masculino",
    dni = "70567572",
    gradoInstruccion = "Superior",
    ocupacion = "Su casa",
    lugarNacimiento = "Lurin",
    lugarProcedencia = "San Bartolo, Lima",
    familiarResponsable = "—",
    direccion = "Asent.H. San Jose, San Bartolo",
    servicioIngreso = "Emergencia - Topico de Medicina",
)

class InMemoryClinicalNoteRepositoryTest {

    @Test
    fun `a seeded stay has its prototype notes`() = runTest {
        val repository = InMemoryClinicalNoteRepository(FixedClock())

        assertEquals(3, repository.getEvoluciones("h_mendoza_1").first().size)
        assertEquals(true, repository.getHistoriaClinica("h_ricaldi_1").first().filiacion.complete)
    }

    @Test
    fun `a stay with no notes yet starts empty`() = runTest {
        val repository = InMemoryClinicalNoteRepository(FixedClock())

        assertEquals(HistoriaClinica(), repository.getHistoriaClinica(UNSEEDED_HOSP_ID).first())
        assertEquals(emptyList(), repository.getEvoluciones(UNSEEDED_HOSP_ID).first())
    }

    @Test
    fun `getEvolucion resolves an evolucion by hospId and evoId`() = runTest {
        val repository = InMemoryClinicalNoteRepository(FixedClock())

        assertEquals("v5", repository.getEvolucion("h_mendoza_1", "v5").first()?.id)
    }

    @Test
    fun `getEvolucion is null for an unknown evoId, or an evoId under another stay`() = runTest {
        val repository = InMemoryClinicalNoteRepository(FixedClock())

        assertNull(repository.getEvolucion("h_mendoza_1", "does-not-exist").first())
        assertNull(repository.getEvolucion("h_remon_1", "v5").first())
    }

    @Test
    fun `saveHistoriaClinicaSection marks the section complete and leaves others untouched`() = runTest {
        val repository = InMemoryClinicalNoteRepository(FixedClock())

        repository.saveHistoriaClinicaSection("h_mendoza_1", HcSectionKey.FILIACION, newFiliacion)

        val hc = repository.getHistoriaClinica("h_mendoza_1").first()
        assertEquals(true, hc.filiacion.complete)
        assertEquals(newFiliacion, hc.filiacion.data)
        assertEquals(false, hc.motivoIngreso.complete)
    }

    @Test
    fun `saveHistoriaClinicaSection works for a stay with no notes yet`() = runTest {
        val repository = InMemoryClinicalNoteRepository(FixedClock())

        repository.saveHistoriaClinicaSection(UNSEEDED_HOSP_ID, HcSectionKey.FILIACION, newFiliacion)

        assertEquals(newFiliacion, repository.getHistoriaClinica(UNSEEDED_HOSP_ID).first().filiacion.data)
    }

    @Test
    fun `saveHistoriaClinicaSection rejects a section with no digital form yet`() = runTest {
        val repository = InMemoryClinicalNoteRepository(FixedClock())

        val result = runCatching {
            repository.saveHistoriaClinicaSection("h_mendoza_1", HcSectionKey.ANTECEDENTES, "anything")
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun `addEvolucion assigns its own id, ignoring any id on the passed evolucion`() = runTest {
        val repository = InMemoryClinicalNoteRepository(FixedClock())

        val created = repository.addEvolucion("h_mendoza_1", draftEvolucion())

        assertTrue(created.id.startsWith("evo_"))
        assertTrue(created.id != "ignored")
    }

    @Test
    fun `addEvolucion prepends so the newest evolucion is first, matching seed ordering`() = runTest {
        val repository = InMemoryClinicalNoteRepository(FixedClock())

        repository.addEvolucion("h_mendoza_1", draftEvolucion())

        val evoluciones = repository.getEvoluciones("h_mendoza_1").first()
        assertEquals(4, evoluciones.size)
        assertEquals("15 Jan 2027", evoluciones.first().fecha)
    }

    @Test
    fun `addEvolucion is visible to an existing observer without re-subscribing`() = runTest {
        val repository = InMemoryClinicalNoteRepository(FixedClock())

        repository.getEvoluciones("h_mendoza_1").test {
            assertEquals(3, awaitItem().size)
            repository.addEvolucion("h_mendoza_1", draftEvolucion())
            assertEquals(4, awaitItem().size)
        }
    }
}
