@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.mojentic.llm.tools

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

private class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

private val MAY_18_2026_TORONTO = LocalDate(2026, 5, 18)
private val FIXED = FixedClock(Instant.parse("2026-05-18T12:00:00Z"))

class DateResolverToolTest {
    private val tool = DateResolverTool(clock = FIXED, timeZone = TimeZone.UTC)

    @Test
    fun todayResolvesToReferenceDate() = runTest {
        val args = buildJsonObject { put("relative_date_found", JsonPrimitive("today")) }
        val result = Json.parseToJsonElement(tool.execute(args)) as JsonObject

        assertEquals(MAY_18_2026_TORONTO.toString(), (result["resolved_date"] as JsonPrimitive).content)
    }

    @Test
    fun tomorrowAddsOneDay() = runTest {
        val result = Json.parseToJsonElement(
            tool.execute(buildJsonObject { put("relative_date_found", JsonPrimitive("tomorrow")) }),
        ) as JsonObject

        assertEquals("2026-05-19", (result["resolved_date"] as JsonPrimitive).content)
    }

    @Test
    fun yesterdaySubtractsOneDay() = runTest {
        val result = Json.parseToJsonElement(
            tool.execute(buildJsonObject { put("relative_date_found", JsonPrimitive("yesterday")) }),
        ) as JsonObject

        assertEquals("2026-05-17", (result["resolved_date"] as JsonPrimitive).content)
    }

    @Test
    fun inNDaysAddsThatMany() = runTest {
        val result = Json.parseToJsonElement(
            tool.execute(buildJsonObject { put("relative_date_found", JsonPrimitive("in 5 days")) }),
        ) as JsonObject

        assertEquals("2026-05-23", (result["resolved_date"] as JsonPrimitive).content)
    }

    @Test
    fun nMonthsAgoSubtractsCalendarMonths() = runTest {
        val result = Json.parseToJsonElement(
            tool.execute(buildJsonObject { put("relative_date_found", JsonPrimitive("2 months ago")) }),
        ) as JsonObject

        assertEquals("2026-03-18", (result["resolved_date"] as JsonPrimitive).content)
    }

    @Test
    fun nextMondayJumpsForwardToNextWeek() = runTest {
        // May 18 2026 IS a Monday (ISO). 'next monday' should jump 7 days.
        val result = Json.parseToJsonElement(
            tool.execute(buildJsonObject { put("relative_date_found", JsonPrimitive("next monday")) }),
        ) as JsonObject

        assertEquals("2026-05-25", (result["resolved_date"] as JsonPrimitive).content)
    }

    @Test
    fun isoLiteralPassesThrough() = runTest {
        val result = Json.parseToJsonElement(
            tool.execute(buildJsonObject { put("relative_date_found", JsonPrimitive("2030-01-15")) }),
        ) as JsonObject

        assertEquals("2030-01-15", (result["resolved_date"] as JsonPrimitive).content)
    }

    @Test
    fun referenceDateOverridesClock() = runTest {
        val args = buildJsonObject {
            put("relative_date_found", JsonPrimitive("tomorrow"))
            put("reference_date_in_iso8601", JsonPrimitive("2026-12-31"))
        }
        val result = Json.parseToJsonElement(tool.execute(args)) as JsonObject

        assertEquals("2027-01-01", (result["resolved_date"] as JsonPrimitive).content)
    }
}
