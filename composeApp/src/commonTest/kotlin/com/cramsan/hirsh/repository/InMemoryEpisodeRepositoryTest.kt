package com.cramsan.hirsh.repository

import app.cash.turbine.test
import com.cramsan.hirsh.model.DischargeCondition
import com.cramsan.hirsh.model.DischargeDetails
import com.cramsan.hirsh.model.DischargeType
import com.cramsan.hirsh.model.EpisodeStatus
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
private val DISCHARGE = DischargeDetails(DischargeType.MEDICAL, DischargeCondition.IMPROVED, "F32.2")

private const val PATIENT_MENDOZA_ID = "1e0697d0-f3e4-4755-85ad-d888d2b15f9d"
private const val PATIENT_VASQUEZ_ID = "caaedede-5d72-4a52-bb72-7532d7cdda83"

private val FIXED_NOW: Instant = Instant.parse("2027-01-15T10:30:00Z")

private class FakeClock(private val instant: Instant = FIXED_NOW) : Clock {
    override fun now(): Instant = instant
}

class InMemoryEpisodeRepositoryTest {

    @Test
    fun `getHospitalizations returns only the requested patient's stays`() = runTest {
        val repository = InMemoryEpisodeRepository(FakeClock())

        repository.getHospitalizations(PATIENT_GONZALEZ_ID).test {
            assertEquals(3, awaitItem().size)
        }
    }

    @Test
    fun `getHospitalizations is empty for a patient with none`() = runTest {
        val repository = InMemoryEpisodeRepository(FakeClock())

        repository.getHospitalizations(PATIENT_SANTIESTEBAN_ID).test {
            assertEquals(emptyList(), awaitItem())
        }
    }

    @Test
    fun `getHospitalization resolves by patientId and hospId together`() = runTest {
        val repository = InMemoryEpisodeRepository(FakeClock())

        repository.getHospitalization(PATIENT_MENDOZA_ID, "h_mendoza_1").test {
            assertEquals("h_mendoza_1", awaitItem()?.id)
        }
    }

    @Test
    fun `getHospitalization does not fall back when hospId belongs to a different patient`() = runTest {
        val repository = InMemoryEpisodeRepository(FakeClock())

        // h_mendoza_1 is real, but under PATIENT_MENDOZA_ID, not PATIENT_VASQUEZ_ID -- must resolve to
        // not-found, never render Jesus's stay under Maria Vasquez's record.
        repository.getHospitalization(PATIENT_VASQUEZ_ID, "h_mendoza_1").test {
            assertNull(awaitItem())
        }
    }

    @Test
    fun `addHospitalization stamps startDate to now, keeps the given references, and starts ACTIVE`() = runTest {
        val repository = InMemoryEpisodeRepository(FakeClock())

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
        val repository = InMemoryEpisodeRepository(FakeClock())

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
        val repository = InMemoryEpisodeRepository(FakeClock())

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
    fun `discharge sets status to CLOSED, stamps endDate and records the details`() = runTest {
        val repository = InMemoryEpisodeRepository(FakeClock())

        repository.discharge("h_mendoza_1", DISCHARGE, jpaVersion = 0L)

        repository.getHospitalization(PATIENT_MENDOZA_ID, "h_mendoza_1").test {
            val hospitalization = awaitItem()
            assertEquals(EpisodeStatus.CLOSED, hospitalization?.status)
            assertEquals(FIXED_NOW, hospitalization?.endDate)
            assertEquals(DISCHARGE, hospitalization?.discharge)
        }
    }

    @Test
    fun `a transfer discharge sets status to TRANSFERRED, as the backend does`() = runTest {
        val repository = InMemoryEpisodeRepository(FakeClock())

        repository.discharge("h_mendoza_1", DISCHARGE.copy(type = DischargeType.TRANSFER), jpaVersion = 0L)

        repository.getHospitalization(PATIENT_MENDOZA_ID, "h_mendoza_1").test {
            assertEquals(EpisodeStatus.TRANSFERRED, awaitItem()?.status)
        }
    }

    @Test
    fun `discharge of an unknown hospId throws NotFound, as the backend does`() = runTest {
        val repository = InMemoryEpisodeRepository(FakeClock())

        val error = assertFailsWith<ApiException> { repository.discharge("does-not-exist", DISCHARGE, jpaVersion = 0L) }

        assertEquals(ApiError.NotFound("does-not-exist"), error.error)
    }

    @Test
    fun `discharge bumps jpaVersion on success`() = runTest {
        val repository = InMemoryEpisodeRepository(FakeClock())

        repository.discharge("h_mendoza_1", DISCHARGE, jpaVersion = 0L)

        repository.getHospitalization(PATIENT_MENDOZA_ID, "h_mendoza_1").test {
            assertEquals(1L, awaitItem()?.jpaVersion)
        }
    }

    @Test
    fun `discharge against a stale jpaVersion throws ApiException wrapping Conflict, without mutating the record`() =
        runTest {
            val repository = InMemoryEpisodeRepository(FakeClock())

            val exception = assertFailsWith<ApiException> {
                repository.discharge("h_mendoza_1", DISCHARGE, jpaVersion = 99L)
            }

            assertEquals(ApiError.Conflict("h_mendoza_1"), exception.error)
            repository.getHospitalization(PATIENT_MENDOZA_ID, "h_mendoza_1").test {
                val hospitalization = awaitItem()
                assertEquals(EpisodeStatus.ACTIVE, hospitalization?.status)
                assertEquals(0L, hospitalization?.jpaVersion)
            }
        }
}
