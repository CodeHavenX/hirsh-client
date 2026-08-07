package com.cramsan.hirsh.repository

import app.cash.turbine.test
import com.cramsan.hirsh.model.DiagnosticoCie10
import com.cramsan.hirsh.model.EstadoHospitalizacion
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.EvolucionResultado
import com.cramsan.hirsh.model.Filiacion
import com.cramsan.hirsh.model.HcSectionKey
import com.cramsan.hirsh.model.Pronostico
import com.cramsan.hirsh.model.Vitals
import com.cramsan.hirsh.util.Clock
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

// Built from a local wall-clock value (not a literal UTC Instant) so
// formatDate/formatTime's output is independent of the test host's time zone.
private val FIXED_NOW: Instant = LocalDateTime(2027, 1, 15, 10, 30).toInstant(TimeZone.currentSystemDefault())

private class FakeClock(private val instant: Instant = FIXED_NOW) : Clock {
    override fun now(): Instant = instant
}

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

class HospitalizationRepositoryTest {

    @Test
    fun `getHospitalizations returns only the requested patient's stays`() = runTest {
        val repository = InMemoryHospitalizationRepository(FakeClock())

        repository.getHospitalizations("#00142").test {
            assertEquals(3, awaitItem().size)
        }
    }

    @Test
    fun `getHospitalizations is empty for a patient with none`() = runTest {
        val repository = InMemoryHospitalizationRepository(FakeClock())

        repository.getHospitalizations("#00124").test {
            assertEquals(emptyList(), awaitItem())
        }
    }

    @Test
    fun `getHospitalization resolves by patientId and hospId together`() = runTest {
        val repository = InMemoryHospitalizationRepository(FakeClock())

        repository.getHospitalization("#00135", "h_mendoza_1").test {
            assertEquals("h_mendoza_1", awaitItem()?.id)
        }
    }

    @Test
    fun `getHospitalization does not fall back when hospId belongs to a different patient`() = runTest {
        val repository = InMemoryHospitalizationRepository(FakeClock())

        // h_mendoza_1 is real, but under #00135, not #00131 -- must resolve to
        // not-found, never render Jesus's stay under Maria Vasquez's record.
        repository.getHospitalization("#00131", "h_mendoza_1").test {
            assertNull(awaitItem())
        }
    }

    @Test
    fun `getEvolucion resolves a nested evolucion by hospId and evoId`() = runTest {
        val repository = InMemoryHospitalizationRepository(FakeClock())

        repository.getEvolucion("h_mendoza_1", "v5").test {
            assertEquals("v5", awaitItem()?.id)
        }
    }

    @Test
    fun `getEvolucion is null for an unknown evoId`() = runTest {
        val repository = InMemoryHospitalizationRepository(FakeClock())

        repository.getEvolucion("h_mendoza_1", "does-not-exist").test {
            assertNull(awaitItem())
        }
    }

    @Test
    fun `addHospitalization stamps fecha and hora to now and starts Activa`() = runTest {
        val repository = InMemoryHospitalizationRepository(FakeClock())

        val created = repository.addHospitalization(
            patientId = "#00124",
            servicio = "Medicina General",
            cama = "05",
            medicoResponsable = "Dr. Lin",
            motivoIngreso = "Control",
        )

        assertEquals("15 Jan 2027", created.fechaIngreso)
        assertEquals("10:30", created.horaIngreso)
        assertNull(created.fechaAlta)
        assertNull(created.horaAlta)
        assertEquals(EstadoHospitalizacion.ACTIVA, created.estado)
        assertTrue(created.id.startsWith("h_"))
    }

    @Test
    fun `addHospitalization is visible to an existing getHospitalizations observer`() = runTest {
        val repository = InMemoryHospitalizationRepository(FakeClock())

        repository.getHospitalizations("#00124").test {
            assertEquals(emptyList(), awaitItem())
            repository.addHospitalization(
                patientId = "#00124",
                servicio = "Medicina General",
                cama = "05",
                medicoResponsable = "Dr. Lin",
                motivoIngreso = "Control",
            )
            assertEquals(1, awaitItem().size)
        }
    }

    @Test
    fun `discharge sets estado to Alta and stamps fechaAlta and horaAlta`() = runTest {
        val repository = InMemoryHospitalizationRepository(FakeClock())

        repository.discharge("h_mendoza_1")

        repository.getHospitalization("#00135", "h_mendoza_1").test {
            val hospitalization = awaitItem()
            assertEquals(EstadoHospitalizacion.ALTA, hospitalization?.estado)
            assertEquals("15 Jan 2027", hospitalization?.fechaAlta)
            assertEquals("10:30", hospitalization?.horaAlta)
        }
    }

    @Test
    fun `discharge is a no-op for an unknown hospId`() = runTest {
        val repository = InMemoryHospitalizationRepository(FakeClock())

        repository.discharge("does-not-exist")

        repository.getHospitalizations("#00135").test {
            assertEquals(EstadoHospitalizacion.ACTIVA, awaitItem().single().estado)
        }
    }

    @Test
    fun `saveHistoriaClinicaSection marks the section complete and leaves others untouched`() = runTest {
        val repository = InMemoryHospitalizationRepository(FakeClock())
        val newFiliacion = Filiacion(
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

        repository.saveHistoriaClinicaSection("h_mendoza_1", HcSectionKey.FILIACION, newFiliacion)

        repository.getHospitalization("#00135", "h_mendoza_1").test {
            val hc = awaitItem()?.historiaClinica
            assertEquals(true, hc?.filiacion?.complete)
            assertEquals(newFiliacion, hc?.filiacion?.data)
            assertEquals(false, hc?.motivoIngreso?.complete)
        }
    }

    @Test
    fun `saveHistoriaClinicaSection rejects a section with no digital form yet`() = runTest {
        val repository = InMemoryHospitalizationRepository(FakeClock())

        val result = runCatching {
            repository.saveHistoriaClinicaSection("h_mendoza_1", HcSectionKey.ANTECEDENTES, "anything")
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun `addEvolucion assigns its own id, ignoring any id on the passed evolucion`() = runTest {
        val repository = InMemoryHospitalizationRepository(FakeClock())

        val created = repository.addEvolucion("h_mendoza_1", draftEvolucion())

        assertTrue(created.id.startsWith("evo_"))
        assertTrue(created.id != "ignored")
    }

    @Test
    fun `addEvolucion prepends so the newest evolucion is first, matching seed ordering`() = runTest {
        val repository = InMemoryHospitalizationRepository(FakeClock())

        repository.addEvolucion("h_mendoza_1", draftEvolucion())

        repository.getHospitalization("#00135", "h_mendoza_1").test {
            val evoluciones = awaitItem()?.evoluciones.orEmpty()
            assertEquals(4, evoluciones.size)
            assertEquals("15 Jan 2027", evoluciones.first().fecha)
        }
    }

    @Test
    fun `addEvolucion is visible to an existing getHospitalization observer without re-subscribing`() = runTest {
        val repository = InMemoryHospitalizationRepository(FakeClock())

        repository.getHospitalization("#00135", "h_mendoza_1").test {
            assertEquals(3, awaitItem()?.evoluciones?.size)
            repository.addEvolucion("h_mendoza_1", draftEvolucion())
            assertEquals(4, awaitItem()?.evoluciones?.size)
        }
    }
}
