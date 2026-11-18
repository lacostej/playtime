package org.example.playtime.apply

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import org.example.playtime.api.ParentalApi
import org.example.playtime.api.TokenProvider
import org.example.playtime.planner.DayState
import org.example.playtime.planner.plan

private fun at(h: Int, m: Int = 0) = h * 60 + m

class ExecutorTest {
    private val pauses = mutableListOf<Long>()

    private fun run(console: FakeConsole, played: Int, target: Int, force: Boolean = false): Pair<ExecutionResult, org.example.playtime.planner.Plan> {
        val state = DayState(console.now, limit = 0, extension = console.extension, played = played,
            configuredBedtime = console.configuredBedtime, extendedBedtime = console.extendedBedtime)
        val p = plan(state, target, force)
        val api = ParentalApi(console.http.client, TokenProvider { "T" }, os = "ANDROID")
        val executor = Executor(api, "dev", { api.readExtension("dev", 0) }, console.configuredBedtime, pause = { pauses += it })
        return kotlinx.coroutines.runBlocking { executor.execute(p) } to p
    }

    @Test
    fun topUpBeforeBedtimeConfirmsEachAdd() = runTest {
        val console = FakeConsole(now = at(17, 30), configuredBedtime = at(19), extension = 60)
        val (result, p) = run(console, played = 25, target = 60) // 35 left -> 60: add 25 as 15 + 5 + 5
        assertEquals(3, console.updates)
        assertEquals(3, console.confirms)
        assertEquals(ExecutionResult.Done(85, at(19)), result)
        assertEquals(85, p.extensionAfter)
    }

    @Test
    fun afterBedtimeOnlyConfirmsWhenAskedSoNothingIsAddedTwice() = runTest {
        // 21:24, bedtime 19:15 passed: cancel, then 60 + 60 + 15 + 5 + 5. The first three apply on update;
        // once bedtime is ahead again (21:30) the last two get a preview and need a confirm.
        val console = FakeConsole(now = at(21, 24), configuredBedtime = at(19, 15))
        val (result, _) = run(console, played = 30, target = 15)
        assertEquals(6, console.updates)
        assertEquals(2, console.confirms)
        assertEquals(ExecutionResult.Done(145, at(21, 40)), result) // 145, not more: no add counted twice
    }

    @Test
    fun conflictAfterCancelIsRetriedAfterAPause() = runTest {
        val console = FakeConsole(now = at(16, 10), configuredBedtime = at(19), conflictsAfterCancel = 1)
        val (result, _) = run(console, played = 0, target = 20)
        assertEquals(ExecutionResult.Done(20, at(19)), result)
        assertEquals(5_000L, pauses.first { it != 3_000L }) // the conflict back-off, besides the 3 s polls
    }

    @Test
    fun waitsUntilTheAppliedTotalCatchesUp() = runTest {
        val console = FakeConsole(now = at(16, 10), configuredBedtime = at(19), lagReads = 3)
        val (result, _) = run(console, played = 0, target = 30)
        assertEquals(ExecutionResult.Done(30, at(19)), result)
    }

    @Test
    fun staleBedtimeComesBackAsAMismatch() = runTest {
        // Bedtime should move 19:00 -> 19:10, but Nintendo moves it from a stale 23:50.
        val console = FakeConsole(now = at(18, 40), configuredBedtime = at(19), extension = 60, staleBase = at(23, 50))
        val (result, p) = run(console, played = 10, target = 30)
        assertEquals(at(19, 10), p.bedtimeAfter)
        val mismatch = assertIs<ExecutionResult.Mismatch>(result)
        assertEquals(at(23, 59), mismatch.bedtime)
        assertEquals(p.extensionAfter, mismatch.extension)
    }

    @Test
    fun aChangeThatNeverLandsTimesOutAtThatStep() = runTest {
        val console = FakeConsole(now = at(21), configuredBedtime = at(19), ignoreAdds = true)
        val (result, p) = run(console, played = 0, target = 30)
        val failed = assertIs<ExecutionResult.Failed>(result)
        assertEquals(1, failed.stepIndex) // the cancel worked, the first add didn't
        assertIs<ExtensionTimeout>(failed.error)
        assertEquals(org.example.playtime.planner.Step.Cancel, p.steps[0])
    }
}
