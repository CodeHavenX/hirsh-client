package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Sex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

interface PatientRepository {
    val patients: StateFlow<List<Patient>>
    fun getPatient(id: String): Flow<Patient?>
}

/**
 * In-memory stand-in, seeded from prototype/shared/data.js, so the patient list and
 * navigation flow are demonstrable before the backend service (separate repo) exists.
 * Replace with an implementation backed by [io.ktor.client.HttpClient] once that
 * service exposes a patients endpoint.
 */
class InMemoryPatientRepository : PatientRepository {

    private val _patients = MutableStateFlow(
        listOf(
            Patient(
                id = "#00142",
                name = "Maria Gonzalez Huerta",
                dateOfBirth = "14/03/1989",
                phone = "987-654-321",
                assignedDoctor = "Dr. Patel",
                lastVisit = "12 Abr 2026",
                bloodType = "O+",
                allergies = "Penicilina",
                nationalId = "45678901",
                sex = Sex.FEMALE,
            ),
            Patient(
                id = "#00138",
                name = "Eduardo Remon Huertas",
                dateOfBirth = "17/07/1962",
                phone = "912-345-678",
                assignedDoctor = "Dr. Reyes",
                lastVisit = "17 Jun 2026",
                bloodType = "A+",
                allergies = "Ninguna",
                nationalId = "09147875",
                sex = Sex.MALE,
            ),
            Patient(
                id = "#00135",
                name = "Jesus Alberto Mendoza Aguilar",
                dateOfBirth = "10/08/1990",
                phone = "955-123-456",
                assignedDoctor = "Dr. Patel",
                lastVisit = "18 Jun 2026",
                bloodType = "B+",
                allergies = "Ninguna",
                nationalId = "70567572",
                sex = Sex.MALE,
            ),
            Patient(
                id = "#00131",
                name = "Maria Santos Vasquez Davila",
                dateOfBirth = "29/01/1943",
                phone = "998-765-432",
                assignedDoctor = "Dr. Lin",
                lastVisit = "19 Jun 2026",
                bloodType = "AB+",
                allergies = "Sulfas",
                nationalId = "07024120",
                sex = Sex.FEMALE,
            ),
            Patient(
                id = "#00129",
                name = "Karla Sofia Ricaldi Sedano",
                dateOfBirth = "15/10/2012",
                phone = "998-984-134",
                assignedDoctor = "Dr. Reyes",
                lastVisit = "20 May 2026",
                bloodType = "—",
                allergies = "Ninguna",
                nationalId = "70083906",
                sex = Sex.FEMALE,
            ),
            Patient(
                id = "#00124",
                name = "Olga Karen Santiesteban Bracamonte",
                dateOfBirth = "12/06/1980",
                phone = "944-556-677",
                assignedDoctor = "Dr. Patel",
                lastVisit = "12 Jun 2026",
                bloodType = "O-",
                allergies = "Ninguna",
                nationalId = "40734432",
                sex = Sex.FEMALE,
            ),
        ),
    )
    override val patients: StateFlow<List<Patient>> = _patients.asStateFlow()

    override fun getPatient(id: String): Flow<Patient?> = patients.map { list -> list.find { it.id == id } }
}
