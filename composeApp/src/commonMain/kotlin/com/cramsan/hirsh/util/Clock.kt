package com.cramsan.hirsh.util

import kotlin.time.Instant

/**
 * Seam around [kotlin.time.Clock] so view models never call
 * `Clock.System.now()` directly -- that would make anything derived from
 * "now" (admision/evolucion timestamps, discharge's fechaAlta/horaAlta)
 * untestable without a real wall-clock dependency. Kept to a single
 * [Instant]-returning method; local-date/time conversion belongs to
 * HISS-111's formatter, which already needs a [kotlinx.datetime.TimeZone]
 * of its own.
 *
 * `kotlinx.datetime.Clock` was removed in kotlinx-datetime 0.7.x in favor of
 * the stdlib's [kotlin.time.Clock] (stabilized in Kotlin 2.3) -- this wraps
 * that, not the older kotlinx-datetime type the ticket originally named.
 */
interface Clock {
    fun now(): Instant
}

class DefaultClock : Clock {
    override fun now(): Instant = kotlin.time.Clock.System.now()
}
