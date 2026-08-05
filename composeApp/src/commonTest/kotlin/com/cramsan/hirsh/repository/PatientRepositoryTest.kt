package com.cramsan.hirsh.repository

import app.cash.turbine.test
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Sex
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val PATIENT_ID = "#00142"

class PatientRepositoryTest {

    @Test
    fun `updatePatient with one changed field logs exactly that field`() = runTest {
        val repository = InMemoryPatientRepository()
        val current = repository.patients.value.first { it.id == PATIENT_ID }

        repository.updatePatient(
            id = PATIENT_ID,
            newValues = current.copy(phone = "999-999-999"),
            changedBy = "apatel",
            fecha = "01 Ene 2027",
            hora = "10:00",
        )

        repository.getChangeLog(PATIENT_ID).test {
            val entries = awaitItem()
            val newest = entries.first()
            assertEquals("apatel", newest.changedBy)
            assertEquals("01 Ene 2027", newest.fecha)
            assertEquals("10:00", newest.hora)
            assertEquals(1, newest.fields.size)
            assertEquals("phone", newest.fields.single().field)
            assertEquals("Telefono de contacto", newest.fields.single().label)
            assertEquals(current.phone, newest.fields.single().oldValue)
            assertEquals("999-999-999", newest.fields.single().newValue)
        }
    }

    @Test
    fun `updatePatient with several changed fields groups them into one entry`() = runTest {
        val repository = InMemoryPatientRepository()
        val current = repository.patients.value.first { it.id == PATIENT_ID }

        repository.updatePatient(
            id = PATIENT_ID,
            newValues = current.copy(phone = "999-999-999", bloodType = "AB-"),
            changedBy = "apatel",
            fecha = "01 Ene 2027",
            hora = "10:00",
        )

        repository.getChangeLog(PATIENT_ID).test {
            val newest = awaitItem().first()
            assertEquals(2, newest.fields.size)
            assertEquals(setOf("phone", "bloodType"), newest.fields.map { it.field }.toSet())
        }
    }

    @Test
    fun `updatePatient with no actual changes does not append a log entry`() = runTest {
        val repository = InMemoryPatientRepository()
        val current = repository.patients.value.first { it.id == PATIENT_ID }

        repository.updatePatient(
            id = PATIENT_ID,
            newValues = current.copy(),
            changedBy = "apatel",
            fecha = "01 Ene 2027",
            hora = "10:00",
        )

        // Seed fixture: #00142 starts with exactly 2 historical entries (see the
        // "seeded patients have their prototype change history" test below).
        repository.getChangeLog(PATIENT_ID).test {
            assertEquals(2, awaitItem().size)
        }
    }

    @Test
    fun `sequential updates prepend newest first`() = runTest {
        val repository = InMemoryPatientRepository()
        val current = repository.patients.value.first { it.id == PATIENT_ID }

        repository.updatePatient(
            id = PATIENT_ID,
            newValues = current.copy(phone = "111-111-111"),
            changedBy = "apatel",
            fecha = "01 Ene 2027",
            hora = "09:00",
        )
        repository.updatePatient(
            id = PATIENT_ID,
            newValues = current.copy(phone = "222-222-222"),
            changedBy = "mreyes",
            fecha = "02 Ene 2027",
            hora = "10:00",
        )

        repository.getChangeLog(PATIENT_ID).test {
            val entries = awaitItem()
            assertEquals("mreyes", entries[0].changedBy)
            assertEquals("apatel", entries[1].changedBy)
        }
    }

    @Test
    fun `getChangeLog for a patient with no history emits an empty list`() = runTest {
        val repository = InMemoryPatientRepository()

        repository.getChangeLog("#00135").test {
            assertEquals(emptyList(), awaitItem())
        }
    }

    @Test
    fun `seeded patients have their prototype change history`() = runTest {
        val repository = InMemoryPatientRepository()

        repository.getChangeLog("#00142").test {
            val entries = awaitItem()
            assertEquals(2, entries.size)
            assertTrue(entries.any { it.changedBy == "apatel" && it.fields.single().field == "allergies" })
            assertTrue(entries.any { it.changedBy == "mreyes" && it.fields.single().field == "phone" })
        }
        repository.getChangeLog("#00131").test {
            val entries = awaitItem()
            assertEquals(2, entries.size)
            assertTrue(entries.any { it.changedBy == "admin" && it.fields.single().field == "assignedDoctor" })
            assertTrue(entries.any { it.changedBy == "mreyes" && it.fields.single().field == "bloodType" })
        }
        repository.getChangeLog("#00138").test {
            val entries = awaitItem()
            assertEquals(1, entries.size)
            assertEquals("nationalId", entries.single().fields.single().field)
        }
    }

    @Test
    fun `updatePatient is a no-op for an unknown patient id`() = runTest {
        val repository = InMemoryPatientRepository()
        val bogus = Patient(
            id = "#does-not-exist",
            name = "Nobody",
            dateOfBirth = "01/01/2000",
            phone = "000-000-000",
            assignedDoctor = "Dr. Patel",
            lastVisit = "—",
            bloodType = "O+",
            allergies = "Ninguna",
            nationalId = "00000000",
            sex = Sex.MALE,
        )

        repository.updatePatient(
            id = "#does-not-exist",
            newValues = bogus,
            changedBy = "apatel",
            fecha = "01 Ene 2027",
            hora = "10:00",
        )

        assertTrue(repository.patients.value.none { it.id == "#does-not-exist" })
    }
}
