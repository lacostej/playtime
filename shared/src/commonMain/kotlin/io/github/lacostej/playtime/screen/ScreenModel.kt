package io.github.lacostej.playtime.screen

import io.github.lacostej.playtime.planner.DayState
import io.github.lacostej.playtime.planner.Plan
import io.github.lacostej.playtime.planner.plan

enum class Action { NONE, ADD, REDUCE, STOP, RESET }

/**
 * What the wheel screen shows for today's [state] and the wheel value [dial] (today's total, in minutes),
 * as designed in the prototype. Pure: shared by Android and iOS.
 */
data class ScreenModel(
    val state: DayState,
    /**
     * The wheel's untouched value: allowance + extension, never below what was played; or, once bedtime
     * has passed or been moved, played + what they can play, since the extension then mostly mirrors the move.
     */
    val allocated: Int,
    /** The wheel value, never below [DayState.played]. */
    val dial: Int,
    val plan: Plan,
    /** The plan the button sends: [plan], or the stop plan when the untouched button means "Stop play". */
    val actionPlan: Plan,
    val action: Action,
    val delta: Int,
    val allocatedAfter: Int,
    /** Minutes they can play from now after the change (or now, if untouched). */
    val toPlay: Int,
    /** Part of the wheel value that bedtime makes unplayable (shown faded). */
    val blocked: Int,
    /** Where the moon sits on the wheel (minutes of today's total), or null when hidden. */
    val bedtimeOnWheel: Int?,
    val bedtimeBefore: Int,
    val bedtimeAfter: Int,
    val bedtimeChanged: Boolean,
    val bedtimePassed: Boolean,
    /** Minutes of today's total that bedtime makes unplayable, while the wheel is untouched; 0 otherwise. */
    val bedtimeCuts: Int = 0,
    /** The plan that moves bedtime so all of today's total is playable; offered while [bedtimeCuts] > 0. */
    val fitBedtime: Plan? = null,
    /** True when [plan] is [fitBedtime], chosen by the parent. */
    val fittingBedtime: Boolean = false,
) {
    /** The same request planned again from what Nintendo now shows, e.g. after a step landed wrong. */
    fun replan(extension: Int?, extendedBedtime: Int?): Plan {
        val s = state.copy(extension = extension, extendedBedtime = extendedBedtime)
        return when (action) {
            Action.STOP -> plan(s, 0)
            Action.RESET -> plan(s, 0, force = true)
            else -> plan(s, maxOf(0, dial - s.played), force = true)
        }
    }

    companion object {
        const val MAX_DIAL = 240

        fun allocated(s: DayState) = maxOf(s.limit + (s.extension ?: 0), s.played)

        /** [fitBedtime]: the parent asked to move bedtime so the untouched total becomes playable. */
        fun of(s: DayState, dial: Int? = null, fitBedtime: Boolean = false): ScreenModel {
            val untouched = plan(s, -1)
            val bedtimePassedOrMoved = untouched.bedtimeBefore <= s.now || untouched.bedtimeBefore != s.configuredBedtime
            val allocated = if (bedtimePassedOrMoved) s.played + untouched.playableNow else allocated(s)
            val v = (dial ?: allocated).coerceIn(s.played, maxOf(MAX_DIAL, allocated))
            val cuts = if (v == allocated && !bedtimePassedOrMoved) allocated - s.played - untouched.playableNow else 0
            val fit = if (cuts > 0) plan(s, allocated - s.played, force = true).takeIf { it.changed } else null
            val fitting = fitBedtime && fit != null
            val target = if (fitting) allocated - s.played else if (v == allocated) untouched.playableNow else maxOf(0, v - s.played)
            val p = if (fitting) fit!! else plan(s, target, force = v != allocated)
            val toPlay = maxOf(0, p.stopAt - s.now)
            val allocatedAfter = if (!p.changed) allocated else if (bedtimePassedOrMoved) s.played + toPlay else maxOf(s.limit + p.extensionAfter, s.played)
            val playEnd = s.played + toPlay
            val stopPlan = if (!p.changed && p.playableNow > 0) plan(s, 0) else null
            val canStop = stopPlan != null && stopPlan.stopAt <= s.now
            // Nothing left to play after bedtime, but an extension is still there: offer to clear it.
            val resetPlan = if (!p.changed && untouched.bedtimeBefore <= s.now && (s.extension ?: 0) > 0) plan(s, 0, force = true) else null
            val action = when {
                resetPlan != null -> Action.RESET
                p.changed && target == 0 && p.playableNow > 0 -> Action.STOP
                !p.changed && canStop -> Action.STOP
                !p.changed -> Action.NONE
                v < allocated -> Action.REDUCE
                else -> Action.ADD
            }
            val untilBedtime = p.bedtimeAfter - s.now
            return ScreenModel(
                state = s, allocated = allocated, dial = v, plan = p,
                actionPlan = resetPlan ?: stopPlan?.takeIf { !p.changed && canStop } ?: p,
                action = action, delta = v - allocated, allocatedAfter = allocatedAfter, toPlay = toPlay,
                blocked = if (v - playEnd >= 5) v - playEnd else 0,
                // Only where bedtime limits play; when the plan moves bedtime, play ends at the handle (± rounding).
                bedtimeOnWheel = if (p.bedtimeAfter == p.bedtimeBefore && (untilBedtime > 0 || s.played > 0)) s.played + maxOf(0, untilBedtime) else null,
                bedtimeBefore = p.bedtimeBefore, bedtimeAfter = p.bedtimeAfter,
                bedtimeChanged = p.bedtimeAfter != p.bedtimeBefore,
                bedtimePassed = p.bedtimeAfter <= s.now,
                bedtimeCuts = cuts, fitBedtime = fit, fittingBedtime = fitting,
            )
        }
    }
}

/** "1 h 10 min", "45 min", "2 h". */
fun duration(minutes: Int): String {
    if (minutes < 60) return "$minutes min"
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0) "$h h" else "$h h $m min"
}

/** "19:05". */
fun clock(minuteOfDay: Int): String {
    val m = minuteOfDay.coerceIn(0, 23 * 60 + 59)
    return (m / 60).toString().padStart(2, '0') + ":" + (m % 60).toString().padStart(2, '0')
}

/** The four prototype scenarios, for demo mode (synthetic). */
object DemoScenarios {
    val all: List<Pair<String, DayState>> = listOf(
        "Not started, nothing granted · 16:10" to DayState(16 * 60 + 10, 0, null, 0, 19 * 60, null),
        "Playing, time left · 17:30" to DayState(17 * 60 + 30, 0, 60, 25, 19 * 60, null),
        "Bedtime cuts their time · 18:40" to DayState(18 * 60 + 40, 0, 60, 10, 19 * 60, null),
        "Bedtime passed, time unused · 19:20" to DayState(19 * 60 + 20, 0, 60, 30, 19 * 60, null),
    )
}
