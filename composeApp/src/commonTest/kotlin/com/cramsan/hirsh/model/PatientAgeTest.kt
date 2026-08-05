package com.cramsan.hirsh.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class PatientAgeTest {

    @Test
    fun `age counts a birthday already passed this year`() {
        val today = LocalDate(2026, 8, 5)

        assertEquals(37, calculateAge("14/03/1989", today))
    }

    @Test
    fun `age does not count a birthday not yet reached this year`() {
        val today = LocalDate(2026, 8, 5)

        assertEquals(36, calculateAge("17/12/1989", today))
    }

    @Test
    fun `age counts a birthday that falls exactly today`() {
        val today = LocalDate(2026, 8, 5)

        assertEquals(37, calculateAge("05/08/1989", today))
    }

    @Test
    fun `age does not count a birthday later this same month`() {
        val today = LocalDate(2026, 8, 5)

        assertEquals(36, calculateAge("20/08/1989", today))
    }
}
