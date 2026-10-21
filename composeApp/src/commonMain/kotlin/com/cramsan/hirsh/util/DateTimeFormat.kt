package com.cramsan.hirsh.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Mirrors the prototype's `toLocaleDateString('es-PE', { day: '2-digit',
 * month: 'short', year: 'numeric' })` -- despite the es-PE locale name, the
 * seed data (`prototype/shared/data.js`, e.g. `'20 May 2026'`) shows it
 * actually renders English abbreviated month names, which
 * [MonthNames.ENGLISH_ABBREVIATED] already matches exactly.
 */
private val dateFormat = LocalDate.Format {
    dayOfMonth(padding = Padding.NONE)
    char(' ')
    monthName(MonthNames.ENGLISH_ABBREVIATED)
    char(' ')
    year()
}

/** Mirrors the prototype's `toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' })`. */
private val timeFormat = LocalTime.Format {
    hour()
    char(':')
    minute()
}

fun formatDate(date: LocalDate): String = dateFormat.format(date)

fun formatTime(time: LocalTime): String = timeFormat.format(time)

/** "20 May 2026 · 14:30" for [instant] in [timeZone] -- the display form of a backend timestamp. */
@OptIn(ExperimentalTime::class)
fun formatDateTime(instant: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
    val local = instant.toLocalDateTime(timeZone)
    return "${formatDate(local.date)} · ${formatTime(local.time)}"
}

/** Just the date part of [instant] in [timeZone]. */
@OptIn(ExperimentalTime::class)
fun formatDate(instant: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): String =
    formatDate(instant.toLocalDateTime(timeZone).date)
