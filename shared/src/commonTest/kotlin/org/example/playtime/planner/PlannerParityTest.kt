package org.example.playtime.planner

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Expected values exported from the UI prototype's planner, so the port behaves exactly like the design
 * that was reviewed. States are synthetic. Steps: C = cancel, B<n> = add n with bedtime, A<n> = add n without.
 */
class PlannerParityTest {
    private val states = mapOf(
        "notStarted" to DayState(now = 970, limit = 0, extension = null, played = 0, configuredBedtime = 1140, extendedBedtime = null),
        "playing" to DayState(now = 1050, limit = 0, extension = 60, played = 25, configuredBedtime = 1140, extendedBedtime = null),
        "bedtimeCuts" to DayState(now = 1120, limit = 0, extension = 60, played = 10, configuredBedtime = 1140, extendedBedtime = null),
        "bedtimeBlocks" to DayState(now = 1160, limit = 0, extension = 60, played = 30, configuredBedtime = 1140, extendedBedtime = null),
        "afterBedtimeSmallExtension" to DayState(now = 1147, limit = 0, extension = 5, played = 0, configuredBedtime = 1140, extendedBedtime = null),
        "lateEvening" to DayState(now = 1284, limit = 0, extension = null, played = 30, configuredBedtime = 1155, extendedBedtime = null),
        "staleExtendedBedtime" to DayState(now = 1090, limit = 0, extension = 22, played = 0, configuredBedtime = 960, extendedBedtime = 1342),
        "withAllowance" to DayState(now = 900, limit = 60, extension = null, played = 20, configuredBedtime = 1200, extendedBedtime = null),
        "playedPastLimit" to DayState(now = 1020, limit = 0, extension = 30, played = 45, configuredBedtime = 1140, extendedBedtime = null),
        "nearMidnight" to DayState(now = 1410, limit = 0, extension = null, played = 0, configuredBedtime = 1260, extendedBedtime = null),
    )

    private class Case(
        val state: String, val target: Int, val force: Boolean, val steps: String,
        val changed: Boolean, val extensionAfter: Int, val bedtimeAfter: Int, val stopAt: Int,
        val byBedtime: Boolean, val note: String,
    )

    private val cases = listOf(
        Case("notStarted", 0, false, "", changed = false, extensionAfter = 0, bedtimeAfter = 1140, stopAt = 970, byBedtime = false, note = "null"),
        Case("notStarted", 0, true, "", changed = true, extensionAfter = 0, bedtimeAfter = 1140, stopAt = 970, byBedtime = false, note = "null"),
        Case("notStarted", 5, false, "C A5", changed = true, extensionAfter = 5, bedtimeAfter = 1140, stopAt = 975, byBedtime = false, note = "null"),
        Case("notStarted", 8, false, "C A5 A5", changed = true, extensionAfter = 10, bedtimeAfter = 1140, stopAt = 980, byBedtime = false, note = "null"),
        Case("notStarted", 20, false, "C A15 A5", changed = true, extensionAfter = 20, bedtimeAfter = 1140, stopAt = 990, byBedtime = false, note = "null"),
        Case("notStarted", 30, false, "C A30", changed = true, extensionAfter = 30, bedtimeAfter = 1140, stopAt = 1000, byBedtime = false, note = "null"),
        Case("notStarted", 45, false, "C A30 A15", changed = true, extensionAfter = 45, bedtimeAfter = 1140, stopAt = 1015, byBedtime = false, note = "null"),
        Case("notStarted", 60, false, "C A60", changed = true, extensionAfter = 60, bedtimeAfter = 1140, stopAt = 1030, byBedtime = false, note = "null"),
        Case("notStarted", 90, false, "C A60 A30", changed = true, extensionAfter = 90, bedtimeAfter = 1140, stopAt = 1060, byBedtime = false, note = "null"),
        Case("notStarted", 150, false, "C A60 A60 A30", changed = true, extensionAfter = 150, bedtimeAfter = 1140, stopAt = 1120, byBedtime = false, note = "null"),
        Case("playing", 0, false, "C", changed = true, extensionAfter = 0, bedtimeAfter = 1140, stopAt = 1050, byBedtime = false, note = "null"),
        Case("playing", 5, false, "C A30", changed = true, extensionAfter = 30, bedtimeAfter = 1140, stopAt = 1055, byBedtime = false, note = "null"),
        Case("playing", 8, false, "C A30 A5", changed = true, extensionAfter = 35, bedtimeAfter = 1140, stopAt = 1060, byBedtime = false, note = "null"),
        Case("playing", 20, false, "C A30 A15", changed = true, extensionAfter = 45, bedtimeAfter = 1140, stopAt = 1070, byBedtime = false, note = "null"),
        Case("playing", 30, false, "C A30 A15 A5 A5", changed = true, extensionAfter = 55, bedtimeAfter = 1140, stopAt = 1080, byBedtime = false, note = "null"),
        Case("playing", 45, false, "A5 A5", changed = true, extensionAfter = 70, bedtimeAfter = 1140, stopAt = 1095, byBedtime = false, note = "null"),
        Case("playing", 60, false, "A15 A5 A5", changed = true, extensionAfter = 85, bedtimeAfter = 1140, stopAt = 1110, byBedtime = false, note = "null"),
        Case("playing", 90, false, "A30 A15 A5 A5", changed = true, extensionAfter = 115, bedtimeAfter = 1140, stopAt = 1140, byBedtime = false, note = "null"),
        Case("playing", 150, false, "B60 A30 A15 A5 A5", changed = true, extensionAfter = 175, bedtimeAfter = 1200, stopAt = 1200, byBedtime = false, note = "null"),
        Case("bedtimeCuts", 0, false, "C", changed = true, extensionAfter = 0, bedtimeAfter = 1140, stopAt = 1120, byBedtime = false, note = "null"),
        Case("bedtimeCuts", 5, false, "C A15", changed = true, extensionAfter = 15, bedtimeAfter = 1140, stopAt = 1125, byBedtime = false, note = "null"),
        Case("bedtimeCuts", 8, false, "C A15 A5", changed = true, extensionAfter = 20, bedtimeAfter = 1140, stopAt = 1130, byBedtime = false, note = "null"),
        Case("bedtimeCuts", 20, false, "", changed = false, extensionAfter = 60, bedtimeAfter = 1140, stopAt = 1140, byBedtime = true, note = "null"),
        Case("bedtimeCuts", 20, true, "C A30", changed = true, extensionAfter = 30, bedtimeAfter = 1140, stopAt = 1140, byBedtime = false, note = "null"),
        Case("bedtimeCuts", 30, false, "C B5 B5 A30", changed = true, extensionAfter = 40, bedtimeAfter = 1150, stopAt = 1150, byBedtime = false, note = "null"),
        Case("bedtimeCuts", 45, false, "C B15 B5 B5 A30", changed = true, extensionAfter = 55, bedtimeAfter = 1165, stopAt = 1165, byBedtime = false, note = "null"),
        Case("bedtimeCuts", 60, false, "C B30 B5 B5 A30", changed = true, extensionAfter = 70, bedtimeAfter = 1180, stopAt = 1180, byBedtime = false, note = "null"),
        Case("bedtimeCuts", 90, false, "C B60 B5 B5 A30", changed = true, extensionAfter = 100, bedtimeAfter = 1210, stopAt = 1210, byBedtime = false, note = "null"),
        Case("bedtimeCuts", 150, false, "C B60 B60 B5 B5 A30", changed = true, extensionAfter = 160, bedtimeAfter = 1270, stopAt = 1270, byBedtime = false, note = "null"),
        Case("bedtimeBlocks", 0, false, "", changed = false, extensionAfter = 60, bedtimeAfter = 1140, stopAt = 1160, byBedtime = true, note = "null"),
        Case("bedtimeBlocks", 0, true, "C", changed = true, extensionAfter = 0, bedtimeAfter = 1140, stopAt = 1140, byBedtime = true, note = "null"),
        Case("bedtimeBlocks", 5, false, "C B30 B5", changed = true, extensionAfter = 35, bedtimeAfter = 1175, stopAt = 1165, byBedtime = false, note = "null"),
        Case("bedtimeBlocks", 8, false, "C B30 B5 B5", changed = true, extensionAfter = 40, bedtimeAfter = 1180, stopAt = 1170, byBedtime = false, note = "extra"),
        Case("bedtimeBlocks", 20, false, "C B30 B15 B5", changed = true, extensionAfter = 50, bedtimeAfter = 1190, stopAt = 1180, byBedtime = false, note = "null"),
        Case("bedtimeBlocks", 30, false, "C B60", changed = true, extensionAfter = 60, bedtimeAfter = 1200, stopAt = 1190, byBedtime = false, note = "null"),
        Case("bedtimeBlocks", 45, false, "C B60 B15", changed = true, extensionAfter = 75, bedtimeAfter = 1215, stopAt = 1205, byBedtime = false, note = "null"),
        Case("bedtimeBlocks", 60, false, "C B60 B30", changed = true, extensionAfter = 90, bedtimeAfter = 1230, stopAt = 1220, byBedtime = false, note = "null"),
        Case("bedtimeBlocks", 90, false, "C B60 B60", changed = true, extensionAfter = 120, bedtimeAfter = 1260, stopAt = 1250, byBedtime = false, note = "null"),
        Case("bedtimeBlocks", 150, false, "C B60 B60 B60", changed = true, extensionAfter = 180, bedtimeAfter = 1320, stopAt = 1310, byBedtime = false, note = "null"),
        Case("afterBedtimeSmallExtension", 0, false, "", changed = false, extensionAfter = 5, bedtimeAfter = 1140, stopAt = 1147, byBedtime = true, note = "null"),
        Case("afterBedtimeSmallExtension", 0, true, "C", changed = true, extensionAfter = 0, bedtimeAfter = 1140, stopAt = 1140, byBedtime = true, note = "null"),
        Case("afterBedtimeSmallExtension", 5, false, "C B15", changed = true, extensionAfter = 15, bedtimeAfter = 1155, stopAt = 1155, byBedtime = true, note = "extra"),
        Case("afterBedtimeSmallExtension", 8, false, "C B15", changed = true, extensionAfter = 15, bedtimeAfter = 1155, stopAt = 1155, byBedtime = true, note = "extra"),
        Case("afterBedtimeSmallExtension", 20, false, "C B30", changed = true, extensionAfter = 30, bedtimeAfter = 1170, stopAt = 1170, byBedtime = true, note = "extra"),
        Case("afterBedtimeSmallExtension", 30, false, "C B30 B5 B5", changed = true, extensionAfter = 40, bedtimeAfter = 1180, stopAt = 1180, byBedtime = true, note = "extra"),
        Case("afterBedtimeSmallExtension", 45, false, "C B30 B15 B5 B5", changed = true, extensionAfter = 55, bedtimeAfter = 1195, stopAt = 1195, byBedtime = true, note = "extra"),
        Case("afterBedtimeSmallExtension", 60, false, "C B60 B5 B5", changed = true, extensionAfter = 70, bedtimeAfter = 1210, stopAt = 1210, byBedtime = true, note = "extra"),
        Case("afterBedtimeSmallExtension", 90, false, "C B60 B30 B5 B5", changed = true, extensionAfter = 100, bedtimeAfter = 1240, stopAt = 1240, byBedtime = true, note = "extra"),
        Case("afterBedtimeSmallExtension", 150, false, "C B60 B60 B30 B5 B5", changed = true, extensionAfter = 160, bedtimeAfter = 1300, stopAt = 1300, byBedtime = true, note = "extra"),
        Case("lateEvening", 0, false, "", changed = false, extensionAfter = 0, bedtimeAfter = 1155, stopAt = 1284, byBedtime = false, note = "null"),
        Case("lateEvening", 0, true, "", changed = true, extensionAfter = 0, bedtimeAfter = 1155, stopAt = 1155, byBedtime = true, note = "null"),
        Case("lateEvening", 5, false, "C B60 B60 B15", changed = true, extensionAfter = 135, bedtimeAfter = 1290, stopAt = 1290, byBedtime = true, note = "extra"),
        Case("lateEvening", 8, false, "C B60 B60 B15 B5", changed = true, extensionAfter = 140, bedtimeAfter = 1295, stopAt = 1295, byBedtime = true, note = "extra"),
        Case("lateEvening", 20, false, "C B60 B60 B30", changed = true, extensionAfter = 150, bedtimeAfter = 1305, stopAt = 1305, byBedtime = true, note = "extra"),
        Case("lateEvening", 30, false, "C B60 B60 B30 B5 B5", changed = true, extensionAfter = 160, bedtimeAfter = 1315, stopAt = 1315, byBedtime = true, note = "extra"),
        Case("lateEvening", 45, false, "C B60 B60 B30 B15 B5 B5", changed = true, extensionAfter = 175, bedtimeAfter = 1330, stopAt = 1330, byBedtime = true, note = "extra"),
        Case("lateEvening", 60, false, "C B60 B60 B60 B5 B5", changed = true, extensionAfter = 190, bedtimeAfter = 1345, stopAt = 1345, byBedtime = true, note = "extra"),
        Case("lateEvening", 90, false, "C B60 B60 B60 B30 B5 B5", changed = true, extensionAfter = 220, bedtimeAfter = 1375, stopAt = 1375, byBedtime = true, note = "extra"),
        Case("lateEvening", 150, false, "C B60 B60 B60 B60 B30 B5 B5", changed = true, extensionAfter = 280, bedtimeAfter = 1435, stopAt = 1435, byBedtime = true, note = "extra"),
        Case("staleExtendedBedtime", 0, false, "C", changed = true, extensionAfter = 0, bedtimeAfter = 960, stopAt = 960, byBedtime = true, note = "null"),
        Case("staleExtendedBedtime", 5, false, "C B60 B60 B15", changed = true, extensionAfter = 135, bedtimeAfter = 1095, stopAt = 1095, byBedtime = true, note = "extra"),
        Case("staleExtendedBedtime", 8, false, "C B60 B60 B15 B5", changed = true, extensionAfter = 140, bedtimeAfter = 1100, stopAt = 1100, byBedtime = true, note = "extra"),
        Case("staleExtendedBedtime", 20, false, "C B60 B60 B30", changed = true, extensionAfter = 150, bedtimeAfter = 1110, stopAt = 1110, byBedtime = true, note = "extra"),
        Case("staleExtendedBedtime", 30, false, "A5 A5", changed = true, extensionAfter = 32, bedtimeAfter = 1342, stopAt = 1122, byBedtime = false, note = "null"),
        Case("staleExtendedBedtime", 45, false, "A15 A5 A5", changed = true, extensionAfter = 47, bedtimeAfter = 1342, stopAt = 1137, byBedtime = false, note = "null"),
        Case("staleExtendedBedtime", 60, false, "A30 A5 A5", changed = true, extensionAfter = 62, bedtimeAfter = 1342, stopAt = 1152, byBedtime = false, note = "null"),
        Case("staleExtendedBedtime", 90, false, "A60 A5 A5", changed = true, extensionAfter = 92, bedtimeAfter = 1342, stopAt = 1182, byBedtime = false, note = "null"),
        Case("staleExtendedBedtime", 150, false, "A60 A60 A5 A5", changed = true, extensionAfter = 152, bedtimeAfter = 1342, stopAt = 1242, byBedtime = false, note = "null"),
        Case("withAllowance", 0, false, "", changed = true, extensionAfter = 0, bedtimeAfter = 1200, stopAt = 940, byBedtime = false, note = "allowance"),
        Case("withAllowance", 5, false, "C", changed = true, extensionAfter = 0, bedtimeAfter = 1200, stopAt = 940, byBedtime = false, note = "allowance"),
        Case("withAllowance", 8, false, "C", changed = true, extensionAfter = 0, bedtimeAfter = 1200, stopAt = 940, byBedtime = false, note = "allowance"),
        Case("withAllowance", 20, false, "C", changed = true, extensionAfter = 0, bedtimeAfter = 1200, stopAt = 940, byBedtime = false, note = "allowance"),
        Case("withAllowance", 30, false, "C", changed = true, extensionAfter = 0, bedtimeAfter = 1200, stopAt = 940, byBedtime = false, note = "allowance"),
        Case("withAllowance", 45, false, "C A5", changed = true, extensionAfter = 5, bedtimeAfter = 1200, stopAt = 945, byBedtime = false, note = "null"),
        Case("withAllowance", 60, false, "C A15 A5", changed = true, extensionAfter = 20, bedtimeAfter = 1200, stopAt = 960, byBedtime = false, note = "null"),
        Case("withAllowance", 90, false, "C A30 A15 A5", changed = true, extensionAfter = 50, bedtimeAfter = 1200, stopAt = 990, byBedtime = false, note = "null"),
        Case("withAllowance", 150, false, "C A60 A30 A15 A5", changed = true, extensionAfter = 110, bedtimeAfter = 1200, stopAt = 1050, byBedtime = false, note = "null"),
        Case("playedPastLimit", 0, false, "", changed = false, extensionAfter = 30, bedtimeAfter = 1140, stopAt = 1020, byBedtime = false, note = "null"),
        Case("playedPastLimit", 0, true, "A15", changed = true, extensionAfter = 45, bedtimeAfter = 1140, stopAt = 1020, byBedtime = false, note = "null"),
        Case("playedPastLimit", 5, false, "A15 A5", changed = true, extensionAfter = 50, bedtimeAfter = 1140, stopAt = 1025, byBedtime = false, note = "null"),
        Case("playedPastLimit", 8, false, "A15 A5 A5", changed = true, extensionAfter = 55, bedtimeAfter = 1140, stopAt = 1030, byBedtime = false, note = "null"),
        Case("playedPastLimit", 20, false, "A30 A5", changed = true, extensionAfter = 65, bedtimeAfter = 1140, stopAt = 1040, byBedtime = false, note = "null"),
        Case("playedPastLimit", 30, false, "A30 A15", changed = true, extensionAfter = 75, bedtimeAfter = 1140, stopAt = 1050, byBedtime = false, note = "null"),
        Case("playedPastLimit", 45, false, "A60", changed = true, extensionAfter = 90, bedtimeAfter = 1140, stopAt = 1065, byBedtime = false, note = "null"),
        Case("playedPastLimit", 60, false, "A60 A15", changed = true, extensionAfter = 105, bedtimeAfter = 1140, stopAt = 1080, byBedtime = false, note = "null"),
        Case("playedPastLimit", 90, false, "A60 A30 A15", changed = true, extensionAfter = 135, bedtimeAfter = 1140, stopAt = 1110, byBedtime = false, note = "null"),
        Case("playedPastLimit", 150, false, "B30 A60 A60 A15", changed = true, extensionAfter = 195, bedtimeAfter = 1170, stopAt = 1170, byBedtime = false, note = "null"),
        Case("nearMidnight", 0, false, "", changed = false, extensionAfter = 0, bedtimeAfter = 1260, stopAt = 1410, byBedtime = false, note = "null"),
        Case("nearMidnight", 0, true, "", changed = true, extensionAfter = 0, bedtimeAfter = 1260, stopAt = 1260, byBedtime = true, note = "null"),
        Case("nearMidnight", 5, false, "C B60 B60 B30 B5", changed = true, extensionAfter = 155, bedtimeAfter = 1415, stopAt = 1415, byBedtime = true, note = "extra"),
        Case("nearMidnight", 8, false, "C B60 B60 B30 B5 B5", changed = true, extensionAfter = 160, bedtimeAfter = 1420, stopAt = 1420, byBedtime = true, note = "extra"),
        Case("nearMidnight", 20, false, "C B60 B60 B30 B15 B5", changed = true, extensionAfter = 170, bedtimeAfter = 1430, stopAt = 1430, byBedtime = true, note = "extra"),
        Case("nearMidnight", 30, false, "C B60 B60 B60", changed = true, extensionAfter = 180, bedtimeAfter = 1439, stopAt = 1439, byBedtime = true, note = "extra"),
        Case("nearMidnight", 45, false, "C B60 B60 B60 B15", changed = true, extensionAfter = 195, bedtimeAfter = 1439, stopAt = 1439, byBedtime = true, note = "extra"),
        Case("nearMidnight", 60, false, "C B60 B60 B60 B30", changed = true, extensionAfter = 210, bedtimeAfter = 1439, stopAt = 1439, byBedtime = true, note = "extra"),
        Case("nearMidnight", 90, false, "C B60 B60 B60 B60", changed = true, extensionAfter = 240, bedtimeAfter = 1439, stopAt = 1439, byBedtime = true, note = "extra"),
        Case("nearMidnight", 150, false, "C B60 B60 B60 B60 B60", changed = true, extensionAfter = 300, bedtimeAfter = 1439, stopAt = 1439, byBedtime = true, note = "extra"),
    )

    private fun encode(steps: List<Step>) = steps.joinToString(" ") {
        when (it) {
            Step.Cancel -> "C"
            is Step.Add -> (if (it.withBedtime) "B" else "A") + it.minutes
        }
    }

    private fun encode(note: Note?) = when (note) {
        null -> "null"
        is Note.ExtraMinutesForBedtime -> "extra"
        Note.BedtimeCapped -> "capped"
        is Note.AllowanceAboveTarget -> "allowance"
    }

    @Test
    fun matchesPrototype() {
        val failures = cases.mapNotNull { c ->
            val p = plan(states.getValue(c.state), c.target, c.force)
            val got = listOf(encode(p.steps), p.changed, p.extensionAfter, p.bedtimeAfter, p.stopAt, p.stoppedByBedtime, encode(p.note))
            val want = listOf(c.steps, c.changed, c.extensionAfter, c.bedtimeAfter, c.stopAt, c.byBedtime, c.note)
            if (got == want) null else "${c.state} target=${c.target} force=${c.force}\n  want $want\n  got  $got"
        }
        assertEquals(emptyList(), failures, "${failures.size} of ${cases.size} cases differ")
    }
}
