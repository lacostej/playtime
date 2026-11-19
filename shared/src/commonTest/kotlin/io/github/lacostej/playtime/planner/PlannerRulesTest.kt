package io.github.lacostej.playtime.planner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun at(h: Int, m: Int = 0) = h * 60 + m

class PlannerRulesTest {
    private val evening = DayState(now = at(17, 30), limit = 0, extension = null, played = 0, configuredBedtime = at(19), extendedBedtime = null)

    @Test
    fun sendsOnlyOfficialAmounts() {
        assertEquals(listOf(60, 60, 15, 5, 5), officialParts(145))
        assertEquals(listOf(15, 5), officialParts(20))
        assertEquals(25, up5(22))
        assertEquals(20, up5(20))
    }

    @Test
    fun firstGrantOfTheDayCancelsFirstToClearTheCarryOver() {
        val p = plan(evening, 20)
        assertEquals(listOf(Step.Cancel, Step.Add(15, false), Step.Add(5, false)), p.steps)
    }

    @Test
    fun topUpKeepsTheExtensionAndDoesNotMoveBedtime() {
        val s = evening.copy(extension = 60, played = 25)
        val p = plan(s, 60) // 35 left, want 60
        assertEquals(listOf(Step.Add(15, false), Step.Add(5, false), Step.Add(5, false)), p.steps)
        assertEquals(at(19), p.bedtimeAfter)
    }

    @Test
    fun loweringCancelsAndGrantsBackWhatWasAlreadyPlayed() {
        val s = evening.copy(extension = 60, played = 25)
        val p = plan(s, 20)
        assertEquals(listOf(Step.Cancel, Step.Add(30, false), Step.Add(15, false)), p.steps) // 25 played + 20
        assertEquals(at(17, 50), p.stopAt)
    }

    @Test
    fun beforeBedtimeOnlyTheOvershootMovesBedtime() {
        val s = evening.copy(now = at(18, 40), extension = 60, played = 10)
        val p = plan(s, 30) // bedtime 19:00 would stop them after 20
        assertEquals(at(19, 10), p.bedtimeAfter)
        assertEquals(listOf(Step.Cancel, Step.Add(5, true), Step.Add(5, true), Step.Add(30, false)), p.steps)
        assertEquals(at(19, 10), p.stopAt)
    }

    @Test
    fun afterBedtimeTheGrantCoversTheGapFromBedtime() {
        // 21:24, bedtime 19:15 passed, 30 played: 15 more minutes needs bedtime at 21:40, so 2 h 25 min.
        val s = DayState(now = at(21, 24), limit = 0, extension = null, played = 30, configuredBedtime = at(19, 15), extendedBedtime = null)
        val p = plan(s, 15)
        assertEquals(listOf(Step.Cancel) + listOf(60, 60, 15, 5, 5).map { Step.Add(it, true) }, p.steps)
        assertEquals(at(21, 40), p.bedtimeAfter)
        assertEquals(at(21, 40), p.stopAt)
        assertTrue(p.stoppedByBedtime)
    }

    @Test
    fun noPlayWantedNeverMovesBedtime() {
        val s = evening.copy(now = at(19, 20), extension = 60, played = 30)
        val p = plan(s, 0, force = true) // trim the 30 unused minutes after bedtime
        assertEquals(listOf(Step.Cancel), p.steps)
        assertEquals(at(19), p.bedtimeAfter)
    }

    @Test
    fun untouchedIsNoChange() {
        val s = evening.copy(now = at(18, 40), extension = 60, played = 10)
        val p = plan(s, 20) // bedtime leaves exactly 20
        assertFalse(p.changed)
        assertEquals(emptyList(), p.steps)
        assertEquals(20, p.playableNow)
    }
}
