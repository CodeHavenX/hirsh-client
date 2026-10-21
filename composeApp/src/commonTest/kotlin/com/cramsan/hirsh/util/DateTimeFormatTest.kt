package com.cramsan.hirsh.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

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

    @Test
    fun `formatDateTime shows an instant's date and time in the given time zone`() {
        val instant = Instant.parse("2026-05-20T19:30:00Z")

        assertEquals("20 May 2026 · 14:30", formatDateTime(instant, TimeZone.of("America/Lima")))
        assertEquals("20 May 2026 · 19:30", formatDateTime(instant, TimeZone.UTC))
    }

    @Test
    fun `formatDate of an instant uses the local date, which can differ from UTC's`() {
        val instant = Instant.parse("2026-06-16T00:20:00Z")

        assertEquals("15 Jun 2026", formatDate(instant, TimeZone.of("America/Lima")))
        assertEquals("16 Jun 2026", formatDate(instant, TimeZone.UTC))
    }
}
