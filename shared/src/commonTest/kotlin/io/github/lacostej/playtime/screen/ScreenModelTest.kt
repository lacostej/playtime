package io.github.lacostej.playtime.screen

import io.github.lacostej.playtime.planner.DayState
import io.github.lacostej.playtime.planner.Step
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Same cases as the reviewed prototype (scenario numbers as in the design). */
class ScreenModelTest {
    private val s = DemoScenarios.all.map { it.second }

    @Test
    fun untouchedScenarios() {
        val m = s.map { ScreenModel.of(it) }
        assertEquals(listOf(0, 60, 60, 30), m.map { it.dial }) // after bedtime: what was played
        assertEquals(listOf(Action.NONE, Action.STOP, Action.STOP, Action.RESET), m.map { it.action })
        assertEquals(listOf(0, 35, 20, 0), m.map { it.toPlay })
        assertEquals(listOf(0, 0, 30, 0), m.map { it.blocked })
        assertEquals(30, m[2].bedtimeOnWheel) // 10 played + 20 until 19:00
        assertEquals(30, m[3].bedtimeOnWheel) // bedtime passed: where the played part ends
        assertEquals(170, m[0].bedtimeOnWheel) // on a later lap: the screen only draws it on the lap shown
        assertEquals(true, m[3].bedtimePassed)
    }

    @Test
    fun addingReducingAndStopping() {
        val more = ScreenModel.of(s[1], 70)
        assertEquals(Action.ADD, more.action); assertEquals(10, more.delta); assertEquals(70, more.allocatedAfter)
        val less = ScreenModel.of(s[1], 45)
        assertEquals(Action.REDUCE, less.action); assertEquals(45, less.allocatedAfter); assertEquals(20, less.toPlay)
        val stop = ScreenModel.of(s[1], 25)
        assertEquals(Action.STOP, stop.action); assertEquals(0, stop.toPlay)
    }

    @Test
    fun wheelCannotGoBelowPlayed() {
        assertEquals(25, ScreenModel.of(s[1], 5).dial)
    }

    @Test
    fun movingBedtimeRemovesTheBlockedPart() {
        val m = ScreenModel.of(s[2], 70)
        assertEquals(0, m.blocked)
        assertEquals(true, m.bedtimeChanged)
        assertEquals(null, m.bedtimeOnWheel) // no moon: play ends at the handle
        assertEquals("19:40", clock(m.bedtimeAfter))
    }

    @Test
    fun unusedTimeAfterBedtimeIsClearedByTheReset() {
        val m = ScreenModel.of(s[3])
        assertEquals(Action.RESET, m.action)
        assertEquals(listOf(io.github.lacostej.playtime.planner.Step.Cancel), m.actionPlan.steps)
    }

    @Test
    fun untouchedStopSendsTheStopPlan() {
        val m = ScreenModel.of(s[1])
        assertEquals(Action.STOP, m.action)
        assertEquals(listOf(io.github.lacostej.playtime.planner.Step.Cancel), m.actionPlan.steps)
    }

    @Test
    fun formats() {
        assertEquals("1 h 10 min", duration(70)); assertEquals("45 min", duration(45)); assertEquals("2 h", duration(120))
        assertEquals("07:05", clock(425))
    }

    @Test
    fun bedtimeCanBeMovedToFitTheUntouchedTotal() {
        // 18:40, 10 played of 1 h, bedtime 19:00 cuts 30 min: move bedtime to 19:30 so all 50 are playable.
        assertEquals(30, ScreenModel.of(s[2]).bedtimeCuts)
        val fit = ScreenModel.of(s[2], fitBedtime = true)
        assertEquals(true, fit.fittingBedtime)
        assertEquals(Action.ADD, fit.action)
        assertEquals(19 * 60 + 30, fit.bedtimeAfter)
        assertEquals(50, fit.toPlay)
        assertEquals(60, fit.dial); assertEquals(0, fit.delta)
        assertEquals(0, fit.blocked)
        assertEquals(fit.fitBedtime, fit.actionPlan) // what Confirm sends to the console
        assertEquals(fit.actionPlan.steps, fit.replan(s[2].extension, s[2].extendedBedtime).steps) // a retry asks for the same
    }

    @Test
    fun afterBedtimeTurningTheWheelGivesTimeFromNow() {
        // 19:20, bedtime 19:00 passed, 30 of 1 h unused: no offer; +30 on the wheel gives 30 from now.
        assertEquals(0, ScreenModel.of(s[3]).bedtimeCuts)
        val more = ScreenModel.of(s[3], 60)
        assertEquals(Action.ADD, more.action)
        assertEquals(30, more.toPlay)
        assertTrue(more.bedtimeAfter >= 19 * 60 + 50, "bedtime ${more.bedtimeAfter}")
    }

    // After bedtime Nintendo grants time only by moving bedtime from the weekday one, so the extension
    // equals the move (95 min to play 20 from 20:15 with bedtime 19:00) and isn't time they can play.
    private val movedAfterBedtime = DayState(now = 20 * 60 + 15, limit = 0, extension = 95, played = 0, configuredBedtime = 19 * 60, extendedBedtime = 20 * 60 + 35)
    private val passedMovedBedtime = movedAfterBedtime.copy(now = 20 * 60 + 39, played = 10)

    @Test
    fun aMovedBedtimeShowsWhatTheyCanPlayNotNintendosExtension() {
        val m = ScreenModel.of(movedAfterBedtime)
        assertEquals(20, m.dial)
        assertEquals(0, m.bedtimeCuts)
        assertEquals(null, m.fitBedtime)
        assertEquals(Action.STOP, m.action)
    }

    @Test
    fun afterBedtimeTheWheelCountsFromNow() {
        val m = ScreenModel.of(passedMovedBedtime)
        assertEquals(10, m.dial) // what was played
        assertEquals(null, m.fitBedtime)
        val more = ScreenModel.of(passedMovedBedtime, 15)
        assertEquals(Action.ADD, more.action); assertEquals(5, more.delta)
        assertEquals(20 * 60 + 45, more.bedtimeAfter) // 5 min from 20:39, rounded up to the 5-minute grid
    }

    @Test
    fun afterBedtimeAnExtensionCanBeReset() {
        val m = ScreenModel.of(passedMovedBedtime)
        assertEquals(Action.RESET, m.action)
        assertEquals(listOf(Step.Cancel), m.actionPlan.steps)
        assertEquals(19 * 60, m.actionPlan.bedtimeAfter)
        assertEquals(true, m.bedtimeChanged); assertEquals(19 * 60, m.bedtimeAfter) // the card shows 20:35 -> 19:00
        assertEquals(m.actionPlan.steps, m.replan(passedMovedBedtime.extension, passedMovedBedtime.extendedBedtime).steps)
        // Once cleared, nothing more to offer, whether Nintendo then reports no extension or 0 minutes.
        for (cleared in listOf(null, 0)) {
            assertEquals(Action.NONE, ScreenModel.of(passedMovedBedtime.copy(extension = cleared, extendedBedtime = null)).action)
        }
    }

    @Test
    fun noBedtimeOfferWhenBedtimeDoesNotCutOrTheWheelMoved() {
        assertEquals(listOf(0, 0), listOf(s[0], s[1]).map { ScreenModel.of(it).bedtimeCuts })
        assertEquals(null, ScreenModel.of(s[1]).fitBedtime)
        val turned = ScreenModel.of(s[2], 70, fitBedtime = true)
        assertEquals(0, turned.bedtimeCuts)
        assertEquals(false, turned.fittingBedtime) // turning the wheel replaces the offer
    }
}
