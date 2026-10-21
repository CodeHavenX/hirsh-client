package com.cramsan.hirsh.repository

import app.cash.turbine.test
import com.cramsan.hirsh.model.DiagnosticoCie10
import com.cramsan.hirsh.model.EpisodeStatus
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.EvolucionResultado
import com.cramsan.hirsh.model.Filiacion
import com.cramsan.hirsh.model.HcSectionKey
import com.cramsan.hirsh.model.Pronostico
import com.cramsan.hirsh.model.Vitals
import com.cramsan.hirsh.network.ApiError
import com.cramsan.hirsh.network.ApiException
import com.cramsan.hirsh.util.Clock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

// Seeded patient ids from PatientRepository.kt's InMemoryPatientRepository -- named here (rather
// than repeating the raw UUIDs) so it's still obvious at a glance which patient each test targets.
private const val PATIENT_GONZALEZ_ID = "07c98942-3654-4034-b960-f3265814e214"
private const val PATIENT_SANTIESTEBAN_ID = "a7efc7af-d998-43e8-8abd-d07c1155ef9f"
private const val PATIENT_MENDOZA_ID = "1e0697d0-f3e4-4755-85ad-d888d2b15f9d"
private const val PATIENT_VASQUEZ_ID = "caaedede-5d72-4a52-bb72-7532d7cdda83"

private val FIXED_NOW: Instant = Instant.parse("2027-01-15T10:30:00Z")

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

        repository.getHospitalizations(PATIENT_GONZALEZ_ID).test {
            assertEquals(3, awaitItem().size)
        }
    }

    @Test
    fun `getHospitalizations is empty for a patient with none`() = runTest {
        val repository = InMemoryHospitalizationRepository(FakeClock())

        repository.getHospitalizations(PATIENT_SANTIESTEBAN_ID).test {
            assertEquals(emptyList(), awaitItem())
        }
    }

    @Test
    fun `getHospitalization resolves by patientId and hospId together`() = runTest {
        val repository = InMemoryHospitalizationRepository(FakeClock())

        repository.getHospitalization(PATIENT_MENDOZA_ID, "h_mendoza_1").test {
            assertEquals("h_mendoza_1", awaitItem()?.id)
        }
    }

    @Test
    fun `getHospitalization does not fall back when hospId belongs to a different patient`() = runTest {
        val repository = InMemoryHospitalizationRepository(FakeClock())

        // h_mendoza_1 is real, but under PATIENT_MENDOZA_ID, not PATIENT_VASQUEZ_ID -- must resolve to
        // not-found, never render Jesus's stay under Maria Vasquez's record.
        repository.getHospitalization(PATIENT_VASQUEZ_ID, "h_mendoza_1").test {
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
    fun `addHospitalization stamps startDate to now, keeps the given references, and starts ACTIVE`() = runTest {
        val repository = InMemoryHospitalizationRepository(FakeClock())

        val created = repository.addHospitalization(
            patientId = PATIENT_SANTIESTEBAN_ID,
            service = PlaceholderCatalog.service("Medicina General"),
            bed = PlaceholderCatalog.bed("05"),
            attendingPhysician = PlaceholderCatalog.physician("Dr. Lin"),
            admissionReason = "Control",
        )

        assertEquals(FIXED_NOW, created.startDate)
        assertNull(created.endDate)
        assertEquals(EpisodeStatus.ACTIVE, created.status)
        assertEquals("Medicina General", created.currentService?.name)
        assertEquals("05", created.currentBed?.number)
        assertEquals("Dr. Lin", created.attendingPhysician?.fullName)
        assertEquals("Control", created.admissionReason)
        assertTrue(created.id.startsWith("h_"))
    }

    @Test
    fun `addHospitalization is visible to an existing getHospitalizations observer`() = runTest {
        val repository = InMemoryHospitalizationRepository(FakeClock())

        repository.getHospitalizations(PATIENT_SANTIESTEBAN_ID).test {
            assertEquals(emptyList(), awaitItem())
            repository.addHospitalization(
                patientId = PATIENT_SANTIESTEBAN_ID,
                service = PlaceholderCatalog.service("Medicina General"),
                bed = PlaceholderCatalog.bed("05"),
                attendingPhysician = PlaceholderCatalog.physician("Dr. Lin"),
                admissionReason = "Control",
            )
            assertEquals(1, awaitItem().size)
        }
    }

    @Test
    fun `addHospitalization refuses a second active stay with Conflict naming the open one`() = runTest {
        val repository = InMemoryHospitalizationRepository(FakeClock())

        val error = assertFailsWith<ApiException> {
            repository.addHospitalization(
                patientId = PATIENT_MENDOZA_ID,
                service = PlaceholderCatalog.service("Medicina General"),
                bed = PlaceholderCatalog.bed("05"),
                attendingPhysician = PlaceholderCatalog.physician("Dr. Lin"),
                admissionReason = null,
            )
        }

        assertEquals(ApiError.Conflict("h_mendoza_1"), error.error)
        assertEquals(1, repository.hospitalizations.value.count { it.patientId == PATIENT_MENDOZA_ID })
    }

    @Test
    fun `discharge sets status to CLOSED and stamps endDate`() = runTest {
        val repository = InMemoryHospitalizationRepository(FakeClock())

        repository.discharge("h_mendoza_1", jpaVersion = 0L)

        repository.getHospitalization(PATIENT_MENDOZA_ID, "h_mendoza_1").test {
            val hospitalization = awaitItem()
            assertEquals(EpisodeStatus.CLOSED, hospitalization?.status)
            assertEquals(FIXED_NOW, hospitalization?.endDate)
        }
    }

    @Test
    fun `discharge is a no-op for an unknown hospId`() = runTest {
        val repository = InMemoryHospitalizationRepository(FakeClock())

        repository.discharge("does-not-exist", jpaVersion = 0L)

        repository.getHospitalizations(PATIENT_MENDOZA_ID).test {
            assertEquals(EpisodeStatus.ACTIVE, awaitItem().single().status)
        }
    }

    @Test
    fun `discharge bumps jpaVersion on success`() = runTest {
        val repository = InMemoryHospitalizationRepository(FakeClock())

        repository.discharge("h_mendoza_1", jpaVersion = 0L)

        repository.getHospitalization(PATIENT_MENDOZA_ID, "h_mendoza_1").test {
            assertEquals(1L, awaitItem()?.jpaVersion)
        }
    }

    @Test
    fun `discharge against a stale jpaVersion throws ApiException wrapping Conflict, without mutating the record`() =
        runTest {
            val repository = InMemoryHospitalizationRepository(FakeClock())

            val exception = assertFailsWith<ApiException> {
                repository.discharge("h_mendoza_1", jpaVersion = 99L)
            }

            assertEquals(ApiError.Conflict("h_mendoza_1"), exception.error)
            repository.getHospitalization(PATIENT_MENDOZA_ID, "h_mendoza_1").test {
                val hospitalization = awaitItem()
                assertEquals(EpisodeStatus.ACTIVE, hospitalization?.status)
                assertEquals(0L, hospitalization?.jpaVersion)
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

        repository.getHospitalization(PATIENT_MENDOZA_ID, "h_mendoza_1").test {
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

        repository.getHospitalization(PATIENT_MENDOZA_ID, "h_mendoza_1").test {
            val evoluciones = awaitItem()?.evoluciones.orEmpty()
            assertEquals(4, evoluciones.size)
            assertEquals("15 Jan 2027", evoluciones.first().fecha)
        }
    }

    @Test
    fun `addEvolucion is visible to an existing getHospitalization observer without re-subscribing`() = runTest {
        val repository = InMemoryHospitalizationRepository(FakeClock())

        repository.getHospitalization(PATIENT_MENDOZA_ID, "h_mendoza_1").test {
            assertEquals(3, awaitItem()?.evoluciones?.size)
            repository.addEvolucion("h_mendoza_1", draftEvolucion())
            assertEquals(4, awaitItem()?.evoluciones?.size)
        }
    }
}
