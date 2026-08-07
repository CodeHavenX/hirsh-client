package com.cramsan.hirsh.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class DateTimeFormatTest {

    @Test
    fun `formatDate matches the prototype seed data's two-digit-day style`() {
        assertEquals("20 May 2026", formatDate(LocalDate(2026, 5, 20)))
    }

    @Test
    fun `formatDate does not zero-pad a single-digit day`() {
        assertEquals("5 Jun 2026", formatDate(LocalDate(2026, 6, 5)))
    }

    @Test
    fun `formatTime matches the prototype seed data's style`() {
        assertEquals("14:30", formatTime(LocalTime(14, 30)))
    }

    @Test
    fun `formatTime zero-pads a single-digit hour and minute`() {
        assertEquals("09:05", formatTime(LocalTime(9, 5)))
    }
}
