@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.mojentic.llm.tools

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Clock

private val DESCRIPTOR = ToolDescriptor(
    name = "resolve_date",
    description =
    "Take text that specifies a relative date (e.g. 'tomorrow', 'in 3 days', 'next monday') " +
        "and output an absolute ISO-8601 date. Falls back to today's date for unparseable input " +
        "rather than failing, so the assistant can keep moving.",
    parameters = buildJsonObject {
        put("type", JsonPrimitive("object"))
        val properties = buildJsonObject {
            put(
                "relative_date_found",
                buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put(
                        "description",
                        JsonPrimitive("The text referencing a relative date, e.g. 'tomorrow' or 'in 2 weeks'."),
                    )
                },
            )
            put(
                "reference_date_in_iso8601",
                buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put(
                        "description",
                        JsonPrimitive(
                            "Optional ISO-8601 reference date (YYYY-MM-DD). If omitted the current date is used.",
                        ),
                    )
                },
            )
        }
        put("properties", properties)
        put("required", JsonArray(listOf(JsonPrimitive("relative_date_found"))))
    },
)

/**
 * Resolves a small vocabulary of relative date expressions to absolute ISO-8601 dates.
 *
 * Supported expressions (case-insensitive, surrounding whitespace ignored):
 * - `today`, `tomorrow`, `yesterday`
 * - `in N days|weeks|months|years`
 * - `N days|weeks|months|years ago`
 * - `next monday` ... `next sunday`, `last monday` ... `last sunday`
 * - Any literal ISO-8601 date (`YYYY-MM-DD`) passes through unchanged.
 *
 * Unrecognised input resolves to the reference date and is reported back via
 * the `summary` field so the LLM can react. This is intentionally a minimal,
 * dependency-free parser — Phase 1 trades natural-language coverage for
 * multiplatform purity. Apps that need richer parsing can swap in their own
 * tool implementation.
 */
public class DateResolverTool(private val clock: Clock = Clock.System, private val timeZone: TimeZone = TimeZone.currentSystemDefault()) :
    LlmTool {
    override val descriptor: ToolDescriptor = DESCRIPTOR

    @Suppress("ReturnCount")
    override suspend fun execute(arguments: JsonObject): String {
        val relative = arguments["relative_date_found"]
            ?.let { (it as? JsonPrimitive)?.contentOrEmpty() }
            ?.trim()
            .orEmpty()
        val reference = arguments["reference_date_in_iso8601"]
            ?.let { (it as? JsonPrimitive)?.contentOrEmpty() }
            ?.trim()
            ?.takeUnless { it.isEmpty() }
        val today = reference?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: clock.todayIn(timeZone)
        val resolved = resolve(relative.lowercase(), today)
        return buildJsonObject {
            put("relative_date", JsonPrimitive(relative))
            put("resolved_date", JsonPrimitive(resolved.toString()))
            put(
                "summary",
                JsonPrimitive("The date on '$relative' resolves to $resolved"),
            )
        }.toString()
    }

    @Suppress("ReturnCount", "ComplexMethod")
    private fun resolve(text: String, today: LocalDate): LocalDate {
        if (text.isEmpty()) return today
        runCatching { LocalDate.parse(text) }.getOrNull()?.let { return it }
        when (text) {
            "today", "now" -> return today
            "tomorrow" -> return today.plus(DatePeriod(days = 1))
            "yesterday" -> return today.minus(DatePeriod(days = 1))
        }
        offsetIn(text, today)?.let { return it }
        offsetAgo(text, today)?.let { return it }
        weekdayOffset(text, today)?.let { return it }
        return today
    }

    private fun offsetIn(text: String, today: LocalDate): LocalDate? {
        val match = inPattern.matchEntire(text) ?: return null
        val (count, unit) = match.destructured
        val n = count.toIntOrNull() ?: return null
        return today.plus(periodFor(unit, n))
    }

    private fun offsetAgo(text: String, today: LocalDate): LocalDate? {
        val match = agoPattern.matchEntire(text) ?: return null
        val (count, unit) = match.destructured
        val n = count.toIntOrNull() ?: return null
        return today.minus(periodFor(unit, n))
    }

    private fun weekdayOffset(text: String, today: LocalDate): LocalDate? {
        val match = weekdayPattern.matchEntire(text) ?: return null
        val (direction, weekday) = match.destructured
        val target = weekdayFor(weekday) ?: return null
        val currentDow = today.dayOfWeek.ordinal
        val targetDow = target.ordinal
        val rawDelta = targetDow - currentDow
        val delta = when (direction) {
            "next" -> if (rawDelta <= 0) rawDelta + DAYS_PER_WEEK else rawDelta
            "last" -> if (rawDelta >= 0) rawDelta - DAYS_PER_WEEK else rawDelta
            else -> rawDelta
        }
        return today.plus(DatePeriod(days = delta))
    }

    private fun periodFor(unit: String, count: Int): DatePeriod = when (unit) {
        "day", "days" -> DatePeriod(days = count)
        "week", "weeks" -> DatePeriod(days = count * DAYS_PER_WEEK)
        "month", "months" -> DatePeriod(months = count)
        "year", "years" -> DatePeriod(years = count)
        else -> DatePeriod(days = 0)
    }

    private fun weekdayFor(name: String): DayOfWeek? = when (name) {
        "monday" -> DayOfWeek.MONDAY
        "tuesday" -> DayOfWeek.TUESDAY
        "wednesday" -> DayOfWeek.WEDNESDAY
        "thursday" -> DayOfWeek.THURSDAY
        "friday" -> DayOfWeek.FRIDAY
        "saturday" -> DayOfWeek.SATURDAY
        "sunday" -> DayOfWeek.SUNDAY
        else -> null
    }

    private fun JsonPrimitive.contentOrEmpty(): String = content

    private companion object {
        private const val DAYS_PER_WEEK = 7
        private val inPattern = Regex("""in\s+(\d+)\s+(day|days|week|weeks|month|months|year|years)""")
        private val agoPattern = Regex("""(\d+)\s+(day|days|week|weeks|month|months|year|years)\s+ago""")
        private val weekdayPattern = Regex(
            """(next|last)\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)""",
        )
    }
}
