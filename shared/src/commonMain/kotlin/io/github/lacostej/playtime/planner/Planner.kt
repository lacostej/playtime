package io.github.lacostej.playtime.planner

/** Times are minutes since local midnight (0..1439); durations are minutes. */
data class DayState(
    val now: Int,
    /** Today's allowance from the weekday setting. */
    val limit: Int,
    /** Today's extra minutes, or null when there is no extension today. */
    val extension: Int?,
    val played: Int,
    /** Today's bedtime from the weekday setting. */
    val configuredBedtime: Int,
    /** Today's extended bedtime, or null when the extension does not move bedtime. */
    val extendedBedtime: Int?,
)

sealed interface Step {
    /** Cancel today's extension (minutes and bedtime). */
    data object Cancel : Step

    /** Add one official amount (5, 15, 30 or 60); [withBedtime] asks for bedtime to move by the same amount. */
    data class Add(val minutes: Int, val withBedtime: Boolean) : Step
}

sealed interface Note {
    /** Moving bedtime far enough grants more minutes than asked; bedtime still stops play. */
    data class ExtraMinutesForBedtime(val minutesGranted: Int) : Note

    /** Bedtime can't go past 23:59. */
    data object BedtimeCapped : Note

    /** The weekday allowance alone already exceeds the target; going lower needs a settings change. */
    data class AllowanceAboveTarget(val allowanceLeft: Int) : Note
}

data class Plan(
    val steps: List<Step>,
    val changed: Boolean,
    /** What they can play right now, before the plan. */
    val playableNow: Int,
    val extensionAfter: Int,
    val bedtimeBefore: Int,
    val bedtimeAfter: Int,
    /** When play stops if they start now. */
    val stopAt: Int,
    val stoppedByBedtime: Boolean,
    val note: Note?,
)

const val LAST_MINUTE = 23 * 60 + 59

private val OFFICIAL_AMOUNTS = listOf(60, 30, 15, 5)

/** Round up to the 5-minute grid the official app uses. */
fun up5(minutes: Int): Int = (minutes + 4).floorDiv(5) * 5

/** Split into official amounts, largest first (145 -> 60, 60, 15, 5, 5). */
fun officialParts(minutes: Int): List<Int> {
    var rest = minutes
    val out = mutableListOf<Int>()
    for (v in OFFICIAL_AMOUNTS) while (rest >= v) { out += v; rest -= v }
    return out
}

private fun adds(minutes: Int, withBedtime: Boolean) = officialParts(minutes).map { Step.Add(it, withBedtime) }

private class Candidate(
    val steps: List<Step>,
    val extensionAfter: Int,
    val leftAfter: Int,
    val bedtimeAfter: Int,
    val stopAt: Int,
    val stoppedByBedtime: Boolean,
    val note: Note?,
)

private fun build(s: DayState, target: Int, cancel: Boolean): Candidate {
    val raw = s.limit + (s.extension ?: 0) - s.played // negative when they played past the limit
    val left = maxOf(0, raw)
    val steps = mutableListOf<Step>()
    val add: Int
    val base: Int
    val extensionBefore: Int
    if (!cancel) {
        add = up5(maxOf(0, target - raw))
        base = s.extendedBedtime ?: s.configuredBedtime
        extensionBefore = s.extension ?: 0
    } else {
        if (s.extension != null || target > 0) steps += Step.Cancel
        // After a cancel, time already played beyond the allowance has to be granted back too.
        add = if (target == 0) 0 else up5(maxOf(0, target - s.limit + s.played))
        base = s.configuredBedtime
        extensionBefore = 0
    }

    if (s.now >= base && target > 0) {
        // Past bedtime every add moves bedtime by its own minutes (there is no "keep bedtime" choice),
        // so the grant must cover the whole gap from bedtime to now + target.
        val granted = maxOf(add, up5(s.now + target - base))
        val bedtime = minOf(base + granted, LAST_MINUTE)
        steps += adds(granted, withBedtime = true)
        val extensionAfter = extensionBefore + granted
        val leftAfter = maxOf(0, s.limit + extensionAfter - s.played)
        val stopAt = minOf(s.now + leftAfter, bedtime)
        return Candidate(
            steps, extensionAfter, leftAfter, bedtime, stopAt, bedtime < s.now + leftAfter,
            if (leftAfter > target) Note.ExtraMinutesForBedtime(leftAfter) else null,
        )
    }

    val shift = if (target > 0) up5(maxOf(0, s.now + target - base)) else 0 // no play wanted: never move bedtime
    val bedtime = minOf(base + shift, LAST_MINUTE)
    steps += adds(shift, withBedtime = true)
    if (add - shift > 0) steps += adds(add - shift, withBedtime = false)
    val extensionAfter = extensionBefore + maxOf(add, shift)
    val leftAfter = maxOf(0, s.limit + extensionAfter - s.played)
    val stopAt = minOf(s.now + leftAfter, bedtime)
    val byBedtime = bedtime < s.now + leftAfter
    var note: Note? = null
    if (leftAfter > target && target > 0 && byBedtime) note = Note.ExtraMinutesForBedtime(leftAfter)
    if (base + shift > LAST_MINUTE) note = Note.BedtimeCapped
    if (target < left && add == 0 && s.limit - s.played > target) note = Note.AllowanceAboveTarget(s.limit - s.played)
    return Candidate(steps, extensionAfter, leftAfter, bedtime, stopAt, byBedtime, note)
}

/**
 * Plan the API calls that let them play [target] minutes from now.
 * [force]: the parent changed today's total even though what they can play now stays the same
 * (e.g. trimming unused minutes after bedtime).
 */
fun plan(s: DayState, target: Int, force: Boolean = false): Plan {
    val left = maxOf(0, s.limit + (s.extension ?: 0) - s.played)
    val bedtimeBefore = if (s.extension != null && s.extendedBedtime != null) s.extendedBedtime else s.configuredBedtime
    val playableNow = maxOf(0, minOf(left, bedtimeBefore - s.now))
    if (target == playableNow && !force) {
        return Plan(
            steps = emptyList(), changed = false, playableNow = playableNow, extensionAfter = s.extension ?: 0,
            bedtimeBefore = bedtimeBefore, bedtimeAfter = bedtimeBefore, stopAt = s.now + playableNow,
            stoppedByBedtime = playableNow < left, note = null,
        )
    }
    var best = build(s, target, cancel = true)
    if (s.extension != null && target >= left) {
        // Keeping the extension saves a cancel, unless moving bedtime would then grant more than asked.
        val keep = build(s, target, cancel = false)
        if (keep.leftAfter <= best.leftAfter) best = keep
    }
    return Plan(
        steps = best.steps, changed = true, playableNow = playableNow, extensionAfter = best.extensionAfter,
        bedtimeBefore = bedtimeBefore, bedtimeAfter = best.bedtimeAfter, stopAt = best.stopAt,
        stoppedByBedtime = best.stoppedByBedtime, note = best.note,
    )
}
