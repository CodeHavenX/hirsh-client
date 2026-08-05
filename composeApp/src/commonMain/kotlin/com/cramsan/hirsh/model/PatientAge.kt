package com.cramsan.hirsh.model

import kotlinx.datetime.LocalDate

/**
 * Age is derived from [dateOfBirth] rather than stored, so it can never drift
 * out of sync with an edited birth date -- mirrors prototype/shared/data.js's
 * getAge(). [today] is an explicit parameter (not [kotlinx.datetime.Clock]
 * read internally) so this stays deterministically testable without a Clock
 * dependency -- HISS-110's injectable Clock abstraction doesn't exist yet and
 * isn't this ticket's dependency.
 */
fun calculateAge(dateOfBirth: String, today: LocalDate): Int {
    val (day, month, year) = dateOfBirth.split("/").map(String::toInt)
    var age = today.year - year
    if (today.monthNumber < month || (today.monthNumber == month && today.dayOfMonth < day)) {
        age--
    }
    return age
}
