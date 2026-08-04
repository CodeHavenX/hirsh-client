package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Sex

interface PatientRepository {
    suspend fun getPatients(): List<Patient>
    suspend fun getPatient(id: String): Patient?
}

/**
 * In-memory stand-in, seeded from prototype/shared/data.js, so the patient list and
 * navigation flow are demonstrable before the backend service (separate repo) exists.
 * Replace with an implementation backed by [io.ktor.client.HttpClient] once that
 * service exposes a patients endpoint.
 */
class InMemoryPatientRepository : PatientRepository {

    private val patients = listOf(
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
    )

    override suspend fun getPatients(): List<Patient> = patients

    override suspend fun getPatient(id: String): Patient? = patients.find { it.id == id }
}
