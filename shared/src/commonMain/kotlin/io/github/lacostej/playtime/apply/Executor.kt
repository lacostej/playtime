package io.github.lacostej.playtime.apply

import kotlinx.coroutines.delay
import io.github.lacostej.playtime.api.ApiError
import io.github.lacostej.playtime.api.ExtraTimeStatus
import io.github.lacostej.playtime.api.ParentalApi
import io.github.lacostej.playtime.planner.Plan
import io.github.lacostej.playtime.planner.Step
import io.github.lacostej.playtime.today.NONE_TODAY

sealed interface ExecutionResult {
    /** Read back from Nintendo and matching the plan. */
    data class Done(val extension: Int, val bedtime: Int) : ExecutionResult

    /** Applied, but Nintendo's result differs from the plan (seen: a stale bedtime coming back). Show what really happened. */
    data class Mismatch(val extension: Int?, val bedtime: Int, val expectedExtension: Int, val expectedBedtime: Int) : ExecutionResult

    /** Stopped at step [stepIndex]; earlier steps were applied. */
    data class Failed(val stepIndex: Int, val error: Throwable) : ExecutionResult
}

/** What Nintendo currently reports for today's extension. */
data class ExtensionReading(
    /** Minutes in today's extension (device.extraPlayingTime), or null if none. */
    val duration: Int?,
    /** Extended bedtime in minutes since midnight, or null if the extension doesn't move bedtime. */
    val bedtime: Int?,
    /** fetchExtraPlayingTimeState.extendedPlayingTime: lags behind [duration] until the change is fully applied. */
    val applied: Int?,
)

class ExtensionTimeout(step: Step) : Exception("Nintendo didn't apply $step in time")

/** Today's extension as Nintendo reports it (two reads: the setting and the applied state). */
suspend fun ParentalApi.readExtension(deviceId: String, nowEpochSeconds: Long): ExtensionReading {
    val extra = parentalControlSetting(deviceId).ownedDevice?.device?.extraPlayingTime
        ?.takeIf { it.expiresAt == null || it.expiresAt > nowEpochSeconds }
    return ExtensionReading(
        duration = extra?.inOneDay?.let { if (it.isInfinity) NONE_TODAY else it.duration },
        bedtime = extra?.bedtime?.endTime?.minutes,
        applied = extraPlayingTimeState(deviceId).extendedPlayingTime,
    )
}

/**
 * Sends a [Plan] to Nintendo, one step at a time, and checks the result.
 *
 * Protocol (observed on the API, 2026-11):
 * - update TO_ADDED returns a nextStepDetail when bedtime is still ahead; then nothing changes until confirm.
 *   Without a nextStepDetail (after bedtime) the update already applied it, and confirming would add it twice.
 * - Cancel is an update TO_CANCELED, never confirmed.
 * - Changes take about 6 s to show; an update right after a cancel can get 409.
 */
class Executor(
    private val api: ParentalApi,
    private val deviceId: String,
    private val read: suspend () -> ExtensionReading,
    private val configuredBedtime: Int,
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val pollIntervalMs: Long = 3_000,
    private val settleTimeoutMs: Long = 30_000,
    private val logger: io.github.lacostej.playtime.api.ActivityLogger = io.github.lacostej.playtime.api.ActivityLogger.None,
) {
    suspend fun execute(plan: Plan): ExecutionResult {
        logger.log("plan: ${plan.steps.joinToString(", ") { describe(it) }}; expect ${plan.extensionAfter} min extra, bedtime ${plan.bedtimeAfter / 60}:${(plan.bedtimeAfter % 60).toString().padStart(2, '0')}")
        val result = run(plan)
        logger.log("result: $result")
        return result
    }

    private fun describe(step: Step) = when (step) {
        Step.Cancel -> "cancel"
        is Step.Add -> "add ${step.minutes}" + if (step.withBedtime) " +bedtime" else ""
    }

    private suspend fun run(plan: Plan): ExecutionResult {
        var last = try { read() } catch (e: ApiError) { return ExecutionResult.Failed(0, e) }
        logger.log("before: $last")
        for ((i, step) in plan.steps.withIndex()) {
            logger.log("step ${i + 1}/${plan.steps.size}: ${describe(step)}")
            try {
                last = when (step) {
                    Step.Cancel -> {
                        retryOnConflict { api.updateExtraPlayingTime(deviceId, ExtraTimeStatus.TO_CANCELED) }
                        settle(step) { it.duration == null }
                    }
                    is Step.Add -> {
                        val before = last.duration
                        val update = retryOnConflict { api.updateExtraPlayingTime(deviceId, ExtraTimeStatus.TO_ADDED, step.minutes) }
                        if (update.nextStepDetail != null) {
                            retryOnConflict { api.confirmExtraPlayingTime(deviceId, step.minutes, step.withBedtime) }
                        }
                        // Landed once the duration changed and the applied total agrees with it (it lags behind).
                        settle(step) { it.duration != before && it.applied == it.duration }
                    }
                }
                logger.log("  landed: $last")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                logger.log("  failed: ${e::class.simpleName}: ${e.message}")
                return ExecutionResult.Failed(i, e)
            }
        }
        val bedtime = last.bedtime ?: configuredBedtime
        val extension = last.duration ?: 0
        return if (extension == plan.extensionAfter && bedtime == plan.bedtimeAfter) {
            ExecutionResult.Done(extension, bedtime)
        } else {
            ExecutionResult.Mismatch(last.duration, bedtime, plan.extensionAfter, plan.bedtimeAfter)
        }
    }

    /** Re-read until [done], every [pollIntervalMs], for at most [settleTimeoutMs]; returns the settled reading. */
    private suspend fun settle(step: Step, done: (ExtensionReading) -> Boolean): ExtensionReading {
        var waited = 0L
        while (true) {
            pause(pollIntervalMs)
            waited += pollIntervalMs
            val r = read()
            if (done(r)) return r
            if (waited >= settleTimeoutMs) throw ExtensionTimeout(step)
        }
    }

    /** 409 means the previous change is still being applied: wait 5, 10, then 20 s; give up after that. */
    private suspend fun <T> retryOnConflict(call: suspend () -> T): T {
        var wait = 5_000L
        repeat(3) {
            try {
                return call()
            } catch (e: ApiError.Conflict) {
                pause(wait)
                wait *= 2
            }
        }
        return call()
    }
}
