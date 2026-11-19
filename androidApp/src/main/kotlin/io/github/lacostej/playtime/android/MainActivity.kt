package io.github.lacostej.playtime.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.lacostej.playtime.screen.Action
import io.github.lacostej.playtime.screen.ScreenModel
import io.github.lacostej.playtime.screen.clock
import io.github.lacostej.playtime.screen.duration

const val DISCLAIMER = "This is an independent, unofficial project. It is not affiliated with, endorsed by, or sponsored by Nintendo Co., Ltd. " +
    "\"Nintendo\" and \"Nintendo Switch\" are trademarks of Nintendo. This app communicates only with Nintendo's servers using your own account; use it at your own risk."

/** The app's palette for Material components (buttons, checkbox, dialogs, text fields). */
private val AppColors = lightColorScheme(
    primary = Palette.accent,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = Palette.accent,
    background = Palette.background,
    onBackground = Palette.ink,
    surface = androidx.compose.ui.graphics.Color.White,
    onSurface = Palette.ink,
    onSurfaceVariant = Palette.muted,
    surfaceContainerHigh = androidx.compose.ui.graphics.Color.White,
    outline = Palette.tick,
)

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Light background: dark status-bar and navigation-bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        handleLink(intent)
        setContent {
            MaterialTheme(colorScheme = AppColors) {
                Column(Modifier.fillMaxSize().background(Palette.background).safeDrawingPadding()) {
                    App(vm) { url -> CustomTabsIntent.Builder().build().launchUrl(this@MainActivity, Uri.parse(url)) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    private fun handleLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme?.startsWith("npf") == true) vm.completeLogin(data.toString())
    }
}

@Composable
fun App(vm: AppViewModel, openBrowser: (String) -> Unit) {
    val ui by vm.ui.collectAsState()
    val dial by vm.dial.collectAsState()
    val fitBedtime by vm.fitBedtime.collectAsState()
    val message by vm.buttonMessage.collectAsState()
    val dialog by vm.dialog.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onForeground() }

    // While the app is visible: refresh every 2 min, and re-render "N min ago" every 30 s.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var now by remember { mutableStateOf(kotlin.time.Clock.System.now()) }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(30_000)
                now = kotlin.time.Clock.System.now()
                vm.autoRefresh()
            }
        }
    }

    when (val s = ui) {
        UiState.Disclaimer -> Disclaimer(vm::acknowledge)
        is UiState.SignIn -> SignIn(s, onStart = { openBrowser(vm.startLogin()) }, onPaste = vm::completeLogin, onDemo = vm::startDemo)
        UiState.Loading -> Centered { CircularProgressIndicator() }
        is UiState.Problem -> Centered {
            Text(s.message, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Button(onClick = vm::refresh) { Text("Try again") }
        }
        is UiState.Ready -> if (!s.supported) {
            Centered { Text("This first version needs a daily play-time limit on the console for today. Set one in the official app (it can be 0).") }
        } else {
            val model = ScreenModel.of(s.state, dial, fitBedtime)
            val (freshness, warn) = freshness(s, now, canPlay = ScreenModel.of(s.state).plan.playableNow > 0)
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            WheelScreen(
                title = s.title,
                freshness = freshness,
                freshnessWarning = warn,
                refreshing = refreshing,
                model = model,
                buttonMessage = message,
                warning = if (s.beforeWakeUp) "It's still before the console's wake-up time. Extra time added now may not behave as expected." else null,
                onTitle = if (s.demo) vm::nextDemoScenario else null,
                onAbout = vm::showAbout,
                demoLink = if (s.demo) "Exit demo" else "Demo",
                onDemoLink = if (s.demo) vm::exitDemo else vm::startDemo,
                onDial = vm::setDial,
                onReset = vm::resetDial,
                onFitBedtime = vm::fitBedtime,
                onButton = { vm.onButton(model) },
            )
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        is Dialog.ConfirmChange -> AlertDialog(
            onDismissRequest = vm::dismissDialog,
            title = { Text(if (d.model.action == Action.STOP) "Stop play now?" else "Reduce today's time?") },
            text = {
                Text(
                    if (d.model.action == Action.STOP) "They won't be able to play any more today. If a game is running, the console will close it."
                    else "Today's play time goes from ${duration(d.model.allocated)} to ${duration(d.model.dial)}. " +
                        "They'll have ${duration(d.model.toPlay)} left. Bedtime: ${clock(d.model.bedtimeAfter)}.",
                )
            },
            confirmButton = {
                val stop = d.model.action == Action.STOP
                TextButton(onClick = { vm.confirmed(d.model) }) {
                    Text(if (stop) "Stop play" else "Reduce", color = if (stop) Palette.stop else Palette.reduce, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = vm::dismissDialog) { Text("Cancel") } },
        )
        is Dialog.ReadOnly -> AlertDialog(
            onDismissRequest = vm::dismissDialog,
            title = { Text("Read-only test build") },
            text = { Text("Nothing was sent. This build would send:\n\n${d.steps}") },
            confirmButton = { TextButton(onClick = vm::dismissDialog) { Text("OK") } },
        )
        is Dialog.Mismatch -> AlertDialog(
            onDismissRequest = vm::dismissDialog,
            title = { Text("Different result") },
            text = { Text(d.text) },
            confirmButton = { TextButton(onClick = vm::dismissDialog) { Text("OK") } },
        )
        is Dialog.Error -> AlertDialog(
            onDismissRequest = vm::dismissDialog,
            title = { Text("Something went wrong") },
            text = { Text(d.text) },
            confirmButton = { TextButton(onClick = vm::dismissDialog) { Text("OK") } },
        )
        Dialog.About -> AlertDialog(
            onDismissRequest = vm::dismissDialog,
            title = { Text("About") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(DISCLAIMER + "\n\nOpen source, MIT licence. Builds on the pynintendoparental project.")
                    Text(if (BuildConfig.WRITES_ENABLED) "This build can change the console." else "Read-only build: nothing is sent to the console.", fontSize = 13.sp, color = Palette.muted)
                    TextButton(onClick = vm::showLog) { Text("Activity log") }
                }
            },
            confirmButton = { TextButton(onClick = vm::dismissDialog) { Text("Close") } },
            dismissButton = { TextButton(onClick = vm::signOut) { Text("Sign out") } },
        )
        Dialog.Log -> {
            val lines by vm.log.lines.collectAsState()
            val clipboard = androidx.compose.ui.platform.LocalClipboard.current
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            AlertDialog(
                onDismissRequest = vm::dismissDialog,
                title = { Text("Activity log") },
                text = {
                    Text(
                        if (lines.isEmpty()) "Nothing logged yet." else lines.takeLast(200).joinToString("\n"),
                        fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, lineHeight = 14.sp,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                },
                confirmButton = { TextButton(onClick = vm::dismissDialog) { Text("Close") } },
                dismissButton = {
                    TextButton(onClick = {
                        val clip = android.content.ClipData.newPlainText("Activity log", lines.joinToString("\n"))
                        scope.launch { clipboard.setClipEntry(androidx.compose.ui.platform.ClipEntry(clip)) }
                    }) { Text("Copy") }
                },
            )
        }
    }
}

private fun hhmm(t: kotlin.time.Instant): String =
    java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(t.toEpochMilliseconds()))

/** One line on how fresh the data is; the flag turns it orange. */
private fun freshness(s: UiState.Ready, now: kotlin.time.Instant, canPlay: Boolean): Pair<String?, Boolean> {
    if (s.demo) return null to false
    val loaded = s.loadedAt ?: return null to false
    val parts = mutableListOf<String>()
    val failed = s.refreshFailedAt
    val minutes = (now - loaded).inWholeMinutes
    parts += when {
        failed != null && failed > loaded -> "Couldn't refresh · data from ${hhmm(loaded)}"
        minutes < 1 -> "Updated just now"
        else -> "Updated $minutes min ago"
    }
    // Played time lags the console. That only matters while they can play: then say how old its last report is.
    s.consoleLastHeard?.let { if (canPlay && (now - it).inWholeMinutes >= 15) parts += "last report ${hhmm(it)}" }
    return parts.joinToString(" · ") to (failed != null && failed > loaded)
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun Disclaimer(onAccept: () -> Unit) {
    var checked by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Before you start", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text("• This is an unofficial app, made by a parent. It works with Nintendo Switch Parental Controls.")
        Text("• Using it may conflict with Nintendo's terms of service, and Nintendo could restrict your account.")
        Text("• Your sign-in goes only to Nintendo. Nothing is sent anywhere else.")
        Text(DISCLAIMER, fontSize = 13.sp, color = Palette.muted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = { checked = it })
            Text("I understand")
        }
        Button(onClick = onAccept, enabled = checked, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
    }
}

@Composable
private fun SignIn(state: UiState.SignIn, onStart: () -> Unit, onPaste: (String) -> Unit, onDemo: () -> Unit) {
    var link by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Sign in", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text("Sign in with the Nintendo Account that manages your console's parental controls.")
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Sign in with Nintendo Account") }
        if (state.waitingForLink) {
            Text(
                "If the app doesn't open by itself after you sign in: on the page that shows your account, " +
                    "press and hold \"Select this person\", copy the link, and paste it here.",
                fontSize = 14.sp, color = Palette.muted,
            )
            OutlinedTextField(link, { link = it }, label = { Text("Link") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = { onPaste(link) }, enabled = link.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Continue with this link") }
        }
        state.message?.let { Text(it, color = Palette.stop) }
        TextButton(onClick = onDemo) { Text("Try the demo (no account needed)") }
    }
}
