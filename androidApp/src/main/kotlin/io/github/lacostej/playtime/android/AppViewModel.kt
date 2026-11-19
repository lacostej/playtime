package io.github.lacostej.playtime.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.engine.android.Android
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import io.github.lacostej.playtime.api.ApiError
import io.github.lacostej.playtime.api.ParentalApi
import io.github.lacostej.playtime.api.TokenProvider
import io.github.lacostej.playtime.api.nintendoHttpClient
import io.github.lacostej.playtime.apply.ExecutionResult
import io.github.lacostej.playtime.apply.Executor
import io.github.lacostej.playtime.apply.readExtension
import io.github.lacostej.playtime.auth.AccessToken
import io.github.lacostej.playtime.auth.LoginRequest
import io.github.lacostej.playtime.auth.NintendoAuth
import io.github.lacostej.playtime.planner.DayState
import io.github.lacostej.playtime.planner.Plan
import io.github.lacostej.playtime.planner.Step
import io.github.lacostej.playtime.screen.Action
import io.github.lacostej.playtime.screen.DemoScenarios
import io.github.lacostej.playtime.screen.ScreenModel
import io.github.lacostej.playtime.screen.clock
import io.github.lacostej.playtime.screen.duration
import io.github.lacostej.playtime.today.buildToday

sealed interface UiState {
    data object Disclaimer : UiState
    data class SignIn(val message: String? = null, val waitingForLink: Boolean = false) : UiState
    data object Loading : UiState
    data class Ready(
        val state: DayState,
        val title: String,
        val demo: Boolean,
        val playedAsOf: Instant?,
        val beforeWakeUp: Boolean,
        val supported: Boolean,
        val consoleLastHeard: Instant? = null,
        val loadedAt: Instant? = null,
        /** Set when an automatic refresh failed; the screen keeps the last data and says so. */
        val refreshFailedAt: Instant? = null,
    ) : UiState
    data class Problem(val message: String) : UiState
}

sealed interface Dialog {
    data class ConfirmChange(val model: ScreenModel) : Dialog
    data class ReadOnly(val steps: String) : Dialog
    data class Mismatch(val text: String) : Dialog
    data class Error(val text: String) : Dialog
    data object About : Dialog
    data object Log : Dialog
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("settings", 0)
    private val store = TokenStore(app)
    val log = LogStore(app)
    private val http = nintendoHttpClient(Android.create())
    private val auth = NintendoAuth(http)
    private var access: AccessToken? = null
    private val tokens = TokenProvider { force ->
        val cached = access
        if (!force && cached != null && !cached.expiresWithin(60, Clock.System.now())) return@TokenProvider cached.idToken
        val session = store.load() ?: throw ApiError.LoginExpired("no saved login")
        auth.refresh(session).also { access = it }.idToken
    }
    private val api = ParentalApi(http, tokens, os = "ANDROID", timeZone = java.util.TimeZone.getDefault().id, logger = log)

    private var login: LoginRequest? = null
    private var deviceId: String? = null
    private var lastLoad = 0L
    private var autoBlockedUntil = 0L
    private var sending = false

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing
    private var demoIndex = 0

    private val _ui = MutableStateFlow<UiState>(UiState.Loading)
    val ui: StateFlow<UiState> = _ui

    /** Wheel value (today's total); null = untouched. */
    private val _dial = MutableStateFlow<Int?>(null)
    val dial: StateFlow<Int?> = _dial

    /** The parent asked to move bedtime so the untouched total becomes playable. */
    private val _fitBedtime = MutableStateFlow(false)
    val fitBedtime: StateFlow<Boolean> = _fitBedtime

    /** Text shown inside the button while sending or just after ("✓ Done …"). */
    private val _buttonMessage = MutableStateFlow<String?>(null)
    val buttonMessage: StateFlow<String?> = _buttonMessage

    private val _dialog = MutableStateFlow<Dialog?>(null)
    val dialog: StateFlow<Dialog?> = _dialog

    init {
        _ui.value = when {
            !prefs.getBoolean(ACKNOWLEDGED, false) -> UiState.Disclaimer
            store.load() == null -> UiState.SignIn()
            else -> UiState.Loading.also { refresh() }
        }
    }

    fun acknowledge() {
        prefs.edit().putBoolean(ACKNOWLEDGED, true).apply()
        _ui.value = if (store.load() == null) UiState.SignIn() else UiState.Loading.also { refresh() }
    }

    /** Returns the URL to open in a browser tab. */
    fun startLogin(): String {
        val request = auth.startLogin()
        login = request
        _ui.value = UiState.SignIn(waitingForLink = true)
        return request.url
    }

    /** The npf…://auth link, from the redirect or pasted by the user. */
    fun completeLogin(link: String) {
        val request = login ?: run { _ui.value = UiState.SignIn("Start the sign-in again, then use the new link."); return }
        viewModelScope.launch {
            try {
                val code = auth.sessionTokenCode(link, request.state)
                store.save(auth.exchangeSessionToken(code, request.verifier))
                log.log("signed in")
                login = null
                access = null
                _ui.value = UiState.Loading
                load()
            } catch (e: IllegalArgumentException) {
                log.log("sign-in link rejected: ${e.message}")
                _ui.value = UiState.SignIn(e.message, waitingForLink = true)
            } catch (e: ApiError) {
                log.log("sign-in failed: ${e::class.simpleName}")
                _ui.value = UiState.SignIn(message(e), waitingForLink = true)
            }
        }
    }

    fun startDemo() {
        demoIndex = 0
        showDemo()
    }

    /** Back from the demo to the signed-in console (the login stays stored while in the demo). */
    fun exitDemo() {
        clearChange()
        if (store.load() == null) _ui.value = UiState.SignIn() else { _ui.value = UiState.Loading; refresh() }
    }

    fun nextDemoScenario() {
        demoIndex = (demoIndex + 1) % DemoScenarios.all.size
        showDemo()
    }

    private fun showDemo() {
        val (label, state) = DemoScenarios.all[demoIndex]
        clearChange()
        _ui.value = UiState.Ready(state, "Demo ${demoIndex + 1}/4 · $label", demo = true, playedAsOf = null, beforeWakeUp = false, supported = true)
    }

    fun signOut() {
        log.log("signed out")
        store.clear()
        access = null
        deviceId = null
        _dialog.value = null
        _ui.value = UiState.SignIn()
    }

    fun setDial(value: Int) { _dial.value = value; _fitBedtime.value = false; _buttonMessage.value = null }
    fun resetDial() { clearChange() }
    fun fitBedtime() { _dial.value = null; _fitBedtime.value = true; _buttonMessage.value = null }
    private fun clearChange() { _dial.value = null; _fitBedtime.value = false }
    fun showAbout() { _dialog.value = Dialog.About }
    fun showLog() { _dialog.value = Dialog.Log }
    fun dismissDialog() { _dialog.value = null }

    /** Back in the foreground: refresh, at most once a minute (be gentle with the API). */
    fun onForeground() = autoRefresh(minGapMs = 60_000)

    /**
     * Periodic refresh while the screen is open (every 2 min, like but slower than Home Assistant's 60 s;
     * the console only reports play time every ~10 min anyway). Never while sending; after a failure, wait 15 min.
     */
    fun autoRefresh(minGapMs: Long = 110_000) {
        val ready = _ui.value as? UiState.Ready ?: return
        val now = System.currentTimeMillis()
        if (ready.demo || sending || _refreshing.value || now < autoBlockedUntil || now - lastLoad < minGapMs) return
        viewModelScope.launch { load(quiet = true) }
    }

    /** Pull to refresh, or "Try again". */
    fun refresh() {
        val ready = _ui.value as? UiState.Ready
        if (ready?.demo == true) {
            viewModelScope.launch { _refreshing.value = true; delay(400); _refreshing.value = false }
            return
        }
        if (sending) return
        viewModelScope.launch { load() }
    }

    /** [quiet]: an automatic refresh; failures show in the freshness line instead of a dialog. */
    private suspend fun load(quiet: Boolean = false, resetDial: Boolean = false) {
        _refreshing.value = true
        log.log(if (quiet) "refresh (automatic)" else "refresh")
        try {
            val device = api.ownedDevices().ownedDevices.firstOrNull()
                ?: run { _ui.value = UiState.Problem("No console is linked to this Nintendo Account."); return }
            deviceId = device.deviceId
            val today = buildToday(api.parentalControlSetting(device.deviceId), api.dailySummaries(device.deviceId), device.device?.timeZone, Clock.System.now())
            lastLoad = System.currentTimeMillis()
            autoBlockedUntil = 0
            log.log("today: ${today.state}")
            if (resetDial) clearChange() // an automatic refresh never throws away a change in progress
            _ui.value = UiState.Ready(
                today.state, device.label ?: "Console", demo = false, playedAsOf = today.playedAsOf,
                beforeWakeUp = today.beforeWakeUp, supported = today.allowanceEnabled,
                consoleLastHeard = today.consoleLastHeard, loadedAt = Clock.System.now(),
            )
        } catch (e: ApiError) {
            log.log("refresh failed: ${e::class.simpleName}")
            val ready = _ui.value as? UiState.Ready
            if (quiet && ready != null && e !is ApiError.LoginExpired) {
                _ui.value = ready.copy(refreshFailedAt = Clock.System.now())
                autoBlockedUntil = System.currentTimeMillis() + 15 * 60_000 // as Home Assistant: wait 15 min after an error
            } else {
                handle(e)
            }
        } finally {
            _refreshing.value = false
        }
    }

    /** The button: changes that remove time ask first (Stop play, less time). */
    fun onButton(model: ScreenModel) {
        when (model.action) {
            Action.NONE -> Unit
            Action.ADD, Action.RESET -> send(model) // a reset after bedtime takes no playable time
            Action.REDUCE, Action.STOP -> _dialog.value = Dialog.ConfirmChange(model)
        }
    }

    private fun doneText(model: ScreenModel) = when (model.action) {
        Action.STOP -> "✓ Stopped"
        Action.RESET -> "✓ Reset"
        else -> "✓ Done"
    }

    fun confirmed(model: ScreenModel) {
        _dialog.value = null
        send(model)
    }

    private fun send(model: ScreenModel) {
        val ready = _ui.value as? UiState.Ready ?: return
        val plan = model.actionPlan
        if (ready.demo) {
            _ui.value = ready.copy(state = simulate(ready.state, plan))
            done(doneText(model) + " (demo)")
            return
        }
        if (!BuildConfig.WRITES_ENABLED) {
            _dialog.value = Dialog.ReadOnly(describe(plan))
            return
        }
        val id = deviceId ?: return
        viewModelScope.launch {
            sending = true
            _buttonMessage.value = "Sending…"
            log.log("confirm: ${model.action} ${duration(model.allocated)} -> ${duration(model.dial)}" + (if (model.fittingBedtime) " (move bedtime to ${clock(model.bedtimeAfter)})" else "") + " from ${ready.state}")
            val executor = Executor(api, id, { api.readExtension(id, Clock.System.now().epochSeconds) }, ready.state.configuredBedtime, logger = log)
            when (val result = executor.execute(plan) { r -> model.replan(r.duration, r.bedtime) }) {
                is ExecutionResult.Done -> { load(resetDial = true); done(doneText(model) + " · checked with Nintendo") }
                is ExecutionResult.Mismatch -> {
                    load(resetDial = true); _buttonMessage.value = null
                    _dialog.value = Dialog.Mismatch(
                        "Nintendo applied something different from the plan: ${duration(result.extension ?: 0)} extra, bedtime ${clock(result.bedtime)} " +
                            "(planned ${duration(result.expectedExtension)}, bedtime ${clock(result.expectedBedtime)}). The screen now shows what Nintendo reports.",
                    )
                }
                is ExecutionResult.Failed -> {
                    _buttonMessage.value = null
                    val e = result.error
                    if (e is ApiError) handle(e) else _dialog.value = Dialog.Error("Nintendo didn't confirm the change in time. The screen shows the latest state.")
                    load(resetDial = true)
                }
            }
            sending = false
        }
    }

    private fun done(text: String) {
        clearChange()
        _buttonMessage.value = text
        viewModelScope.launch { delay(2_500); if (_buttonMessage.value == text) _buttonMessage.value = null }
    }

    private fun handle(e: ApiError) {
        when (e) {
            is ApiError.LoginExpired -> { store.clear(); access = null; _ui.value = UiState.SignIn("Your sign-in expired. Please sign in again.") }
            else -> if (_ui.value is UiState.Ready) _dialog.value = Dialog.Error(message(e)) else _ui.value = UiState.Problem(message(e))
        }
    }

    private fun message(e: ApiError) = when (e) {
        is ApiError.LoginExpired -> "Your sign-in expired. Please sign in again."
        is ApiError.ServiceChanged -> "Nintendo's service changed. Update the app, or use the official app for now."
        is ApiError.RateLimited -> "Nintendo asked us to slow down. Try again in a few minutes."
        is ApiError.Conflict -> "Another change is still being applied. Try again in a moment."
        is ApiError.Network -> "Can't reach Nintendo. Check your connection."
        is ApiError.Http -> "Nintendo returned an error (${e.status})."
        is ApiError.Refused -> when (e.status) {
            "NO_EFFECT" -> "Nintendo says play time can't be extended any more today."
            "OVERTIME_ERROR" -> "Nintendo won't extend play time past bedtime."
            "DURING_LATE_NIGHT_ERROR" -> "Nintendo doesn't allow extending play time late at night."
            else -> "Nintendo couldn't apply the change."
        } + " The screen shows the latest state."
    }

    private companion object {
        const val ACKNOWLEDGED = "acknowledged_unofficial"
    }
}

/** What the plan would send, for the read-only build. */
private fun describe(plan: Plan): String = plan.steps.joinToString("\n") {
    when (it) {
        Step.Cancel -> "• Cancel today's extra time"
        is Step.Add -> "• Add ${it.minutes} min" + if (it.withBedtime) ", moving bedtime" else ""
    }
}

/** Demo mode: apply the plan to the synthetic state. */
private fun simulate(s: DayState, plan: Plan): DayState =
    s.copy(extension = plan.extensionAfter, extendedBedtime = if (plan.bedtimeAfter != s.configuredBedtime) plan.bedtimeAfter else null)
