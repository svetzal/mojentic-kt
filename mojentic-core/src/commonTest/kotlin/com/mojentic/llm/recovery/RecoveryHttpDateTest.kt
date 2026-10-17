package com.mojentic.llm.recovery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class RecoveryHttpDateTest {
    private val now = Instant.parse("2026-10-17T00:00:00Z").toEpochMilliseconds()

    @Test
    fun obsoleteFormsMatchImfDateIncludingSingleDigitDay() {
        val expected = Instant.parse("1994-11-06T08:49:37Z").toEpochMilliseconds()
        for (value in listOf(
            "Sun, 06 Nov 1994 08:49:37 GMT",
            "Sunday, 06-Nov-94 08:49:37 GMT",
            "Sun Nov  6 08:49:37 1994",
        )) {
            assertEquals(expected, recoveryHttpDate(value, now), value)
        }
    }

    @Test
    fun rfc850YearMoreThanFiftyYearsAheadUsesPastCentury() {
        assertEquals(
            Instant.parse("2075-01-01T00:00:00Z").toEpochMilliseconds(),
            recoveryHttpDate("Tuesday, 01-Jan-75 00:00:00 GMT", now),
        )
        assertEquals(
            Instant.parse("1976-10-18T00:00:00Z").toEpochMilliseconds(),
            recoveryHttpDate("Monday, 18-Oct-76 00:00:00 GMT", now),
        )
        assertEquals(
            Instant.parse("2076-10-17T00:00:00Z").toEpochMilliseconds(),
            recoveryHttpDate("Saturday, 17-Oct-76 00:00:00 GMT", now),
        )
        assertEquals(
            Instant.parse("1977-01-01T00:00:00Z").toEpochMilliseconds(),
            recoveryHttpDate("Saturday, 01-Jan-77 00:00:00 GMT", now),
        )
    }

    @Test
    fun twoDigitYearCanReferToNextCenturyWithinFiftyYears() {
        assertEquals(
            Instant.parse("2000-01-01T00:00:00Z").toEpochMilliseconds(),
            recoveryHttpDate("Saturday, 01-Jan-00 00:00:00 GMT", 0),
        )
    }
}
