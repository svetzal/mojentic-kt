package com.mojentic.llm.recovery

import io.ktor.http.fromHttpToGmtDate
import io.ktor.util.date.GMTDate

private const val WEEKDAY_LENGTH = 3
private const val MAX_FUTURE_YEARS = 50

private val rfc850 = Regex(
    "(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday), " +
        "(\\d{2})-([A-Z][a-z]{2})-(?<year>\\d{2}) (?<time>\\d{2}:\\d{2}:\\d{2}) GMT",
)
private val asctime = Regex("(Mon|Tue|Wed|Thu|Fri|Sat|Sun) ([A-Z][a-z]{2}) {1,2}(\\d{1,2}) (?<time>\\d{2}:\\d{2}:\\d{2}) (?<year>\\d{4})")

/** Normalize obsolete HTTP dates before Ktor parsing; RFC 9110 requires all three recipient forms. */
internal fun recoveryHttpDate(value: String, now: Long): Long {
    val normalized = rfc850.matchEntire(value)?.let { match ->
        val (weekday, day, month) = match.destructured
        val shortYear = requireNotNull(match.groups["year"]).value
        val time = requireNotNull(match.groups["time"]).value
        val current = GMTDate(now)
        val century = (current.year + MAX_FUTURE_YEARS) / 100 * 100
        val candidate = century + shortYear.toInt()
        val candidateDate = "${weekday.take(WEEKDAY_LENGTH)}, $day $month $candidate $time GMT"
        val futureBoundary = GMTDate(
            current.seconds,
            current.minutes,
            current.hours,
            current.dayOfMonth,
            current.month,
            current.year + MAX_FUTURE_YEARS,
        ).timestamp
        // Compare the complete date, including dates just over the fifty-year boundary.
        val year = if (candidateDate.fromHttpToGmtDate().timestamp > futureBoundary) candidate - 100 else candidate
        "${weekday.take(WEEKDAY_LENGTH)}, $day $month $year $time GMT"
    } ?: asctime.matchEntire(value)?.let { match ->
        val (weekday, month, day) = match.destructured
        val time = requireNotNull(match.groups["time"]).value
        val year = requireNotNull(match.groups["year"]).value
        "$weekday, ${day.padStart(2, '0')} $month $year $time GMT"
    } ?: value
    return normalized.fromHttpToGmtDate().timestamp
}
