package org.example.playtime.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import org.example.playtime.screen.Action
import org.example.playtime.screen.ScreenModel
import org.example.playtime.screen.clock
import org.example.playtime.screen.duration

const val DISCLAIMER = "This is an independent, unofficial project. It is not affiliated with, endorsed by, or sponsored by Nintendo Co., Ltd. " +
    "\"Nintendo\" and \"Nintendo Switch\" are trademarks of Nintendo. This app communicates only with Nintendo's servers using your own account; use it at your own risk."

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleLink(intent)
        setContent {
            Column(Modifier.fillMaxSize().background(Palette.background).safeDrawingPadding()) {
                App(vm) { url -> CustomTabsIntent.Builder().build().launchUrl(this@MainActivity, Uri.parse(url)) }
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
    val message by vm.buttonMessage.collectAsState()
    val dialog by vm.dialog.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onForeground() }

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
            val model = ScreenModel.of(s.state, dial)
            WheelScreen(
                title = s.title,
                updatedAt = s.updatedAt?.let { "Play time as of ${java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(it.toEpochMilliseconds()))}" },
                model = model,
                buttonMessage = message,
                warning = if (s.beforeWakeUp) "It's still before the console's wake-up time. Extra time added now may not behave as expected." else null,
                onTitle = if (s.demo) vm::nextDemoScenario else null,
                onAbout = vm::showAbout,
                onDial = vm::setDial,
                onReset = vm::resetDial,
                onButton = { vm.onButton(model) },
            )
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
            confirmButton = { TextButton(onClick = { vm.confirmed(d.model) }) { Text(if (d.model.action == Action.STOP) "Stop time" else "Reduce") } },
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
            text = { Text(DISCLAIMER + "\n\nOpen source, MIT licence. Builds on the pynintendoparental project.") },
            confirmButton = { TextButton(onClick = vm::dismissDialog) { Text("Close") } },
            dismissButton = { TextButton(onClick = vm::signOut) { Text("Sign out") } },
        )
    }
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
