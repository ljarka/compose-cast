package io.composecast.server.admin

import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// Dates in the panel (owner 2026-11-26: ISO timestamps were "not very human readable"). Pages show
// `humanTime` and put `exactTime` in a tooltip. The server runs in UTC (Docker), so exact times say UTC.

private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH).withZone(ZoneOffset.UTC)
private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH).withZone(ZoneOffset.UTC)
private val FULL_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC)
private val EXACT = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'", Locale.ENGLISH).withZone(ZoneOffset.UTC)

/**
 * "just now", "5 minutes ago", "3 hours ago", "yesterday at 18:57", "4 days ago", "26 Nov",
 * "26 Nov 2025": the closer, the more relative.
 */
internal fun humanTime(at: Instant, now: Instant = Instant.now()): String {
    val ago = Duration.between(at, now)
    val today = now.atZone(ZoneOffset.UTC).toLocalDate()
    val day = at.atZone(ZoneOffset.UTC).toLocalDate()
    return when {
        ago.isNegative || ago.toMinutes() < 1 -> "just now"
        ago.toMinutes() < 60 -> plural(ago.toMinutes(), "minute") + " ago"
        ago.toHours() < 24 && day == today -> plural(ago.toHours(), "hour") + " ago"
        day == today.minusDays(1) -> "yesterday at ${TIME.format(at)}"
        ago.toDays() < 7 -> plural(maxOf(ago.toDays(), 2), "day") + " ago"
        day.year == today.year -> DAY_MONTH.format(at)
        else -> FULL_DATE.format(at)
    }
}

/** "26 Nov 2026, 18:57 UTC": for tooltips next to [humanTime]. */
internal fun exactTime(at: Instant): String = EXACT.format(at)

/** The template fields for one timestamp: `<prefix>` (human), `<prefix>Exact`, `<prefix>Iso`. */
internal fun timeFields(prefix: String, at: Instant?): List<Pair<String, String>> = listOf(
    prefix to (at?.let { humanTime(it) } ?: ""),
    "${prefix}Exact" to (at?.let(::exactTime) ?: ""),
    "${prefix}Iso" to (at?.toString() ?: ""),
)

private fun plural(n: Long, unit: String) = if (n == 1L) "1 $unit" else "$n ${unit}s"
