package io.github.lacostej.playtime.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import io.github.lacostej.playtime.screen.Action
import io.github.lacostej.playtime.screen.ScreenModel
import io.github.lacostej.playtime.screen.clock
import io.github.lacostej.playtime.screen.duration

object Palette {
    val background = Color(0xFFF5F6F2)
    val ink = Color(0xFF16201C)
    val muted = Color(0xFF5B6660)
    val line = Color(0xFFE2E5DF)
    val track = Color(0xFFE7EAE4)
    val tick = Color(0xFFAEB6AE)
    val accent = Color(0xFF1E6F62)
    val played = Color(0xFF8C7A5B)
    val reduce = Color(0xFFC25400)
    val stop = Color(0xFFB3261E)
    val bedtimeMove = Color(0xFF8A4510)
    val blockedBorder = Color(0xFFC2553D)
    val done = Color(0xFFDCEDE8)
    val doneInk = Color(0xFF15554B)
}

private const val TURN = 60 // minutes per full turn, like a parking app

@Composable
fun WheelScreen(
    title: String,
    freshness: String?,
    freshnessWarning: Boolean,
    refreshing: Boolean,
    model: ScreenModel,
    buttonMessage: String?,
    warning: String?,
    onTitle: (() -> Unit)?,
    onAbout: () -> Unit,
    demoLink: String,
    onDemoLink: () -> Unit,
    onDial: (Int) -> Unit,
    onReset: () -> Unit,
    onFitBedtime: () -> Unit,
    onButton: () -> Unit,
) {
    // Scrollable (but exactly one screen tall) so pulling down anywhere can trigger a refresh.
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val screenHeight = maxHeight
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .height(screenHeight)
            .background(Palette.background)
            .pointerInput(model.plan.changed) { detectTapGestures { if (model.plan.changed) onReset() } } // tap away from the wheel resets it
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.ink,
                    modifier = if (onTitle != null) Modifier.clickable(onClick = onTitle) else Modifier,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Space always reserved, so the text doesn't move when the spinner appears.
                    Box(Modifier.padding(end = 6.dp).size(12.dp)) {
                        if (refreshing) CircularProgressIndicator(Modifier.fillMaxSize(), color = Palette.accent, strokeWidth = 1.5.dp)
                    }
                    if (freshness != null) Text(freshness, fontSize = 12.sp, color = if (freshnessWarning) Palette.reduce else Palette.muted)
                }
            }
            Text(demoLink, fontSize = 13.sp, color = Palette.accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onDemoLink).padding(8.dp))
            Text("About", fontSize = 13.sp, color = Palette.muted, modifier = Modifier.clickable(onClick = onAbout).padding(8.dp))
        }
        if (warning != null) {
            Text(warning, fontSize = 13.sp, color = Palette.bedtimeMove, modifier = Modifier.fillMaxWidth().background(Color(0xFFFBEBDD), RoundedCornerShape(10.dp)).padding(10.dp))
        }
        Text("Today’s limits", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Palette.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Wheel(model, onDial, onFitBedtime)
        BedtimeLine(model, onFitBedtime)
        Spacer(Modifier.weight(1f))
        ChangeCard(model)
        Summary(model)
        ActionButton(model, buttonMessage, onButton)
    }
    }
}

@Composable
private fun Wheel(model: ScreenModel, onDial: (Int) -> Unit, onFitBedtime: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    // The gesture handler lives across recompositions: read the latest model and callback, not the first ones.
    val current by rememberUpdatedState(model)
    val dial by rememberUpdatedState(onDial)
    val fit by rememberUpdatedState(onFitBedtime)
    var dragValue by remember { mutableStateOf<Float?>(null) }
    var lastAngle by remember { mutableStateOf(0.0) }
    val value = dragValue ?: model.dial.toFloat()
    val s = model.state

    Box(
        Modifier.fillMaxWidth().aspectRatio(1f)
            .semantics { contentDescription = "Today's play time: ${duration(model.dial)}" }
            // Taps on the wheel never reset it; a tap on the moon or the faded part offers to move bedtime.
            .pointerInput(Unit) { detectTapGestures { p -> if (hitsBedtimeCut(current, p, size.width.toFloat())) fit() } }
            .pointerInput(Unit) {
                fun angle(p: Offset): Double {
                    val a = atan2((p.x - size.width / 2).toDouble(), (size.height / 2 - p.y).toDouble())
                    return if (a < 0) a + 2 * PI else a
                }
                detectDragGestures(
                    onDragStart = { p ->
                        val d = hypot(p.x - size.width / 2.0, p.y - size.height / 2.0) / (size.width / 2.0)
                        if (d in 0.45..1.1) { dragValue = current.dial.toFloat(); lastAngle = angle(p) }
                    },
                    onDragEnd = { dragValue = null },
                    onDragCancel = { dragValue = null },
                    onDrag = { change, _ ->
                        val value0 = dragValue ?: return@detectDragGestures
                        val a = angle(change.position)
                        var d = a - lastAngle
                        if (d > PI) d -= 2 * PI
                        if (d < -PI) d += 2 * PI
                        lastAngle = a
                        val played = current.state.played
                        val next = (value0 + (d / (2 * PI) * TURN).toFloat()).coerceIn(played.toFloat(), ScreenModel.MAX_DIAL.toFloat())
                        dragValue = next
                        val snapped = maxOf(played, (next / 5).roundToInt() * 5)
                        if (snapped != current.dial) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); dial(snapped) }
                        change.consume()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) { drawWheel(model, value) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                val h = model.dial / 60
                val m = model.dial % 60
                if (h > 0) { Big("$h"); UnitLabel("h") }
                if (m > 0 || h == 0) { Big("$m"); UnitLabel("min") }
            }
            val delta = model.delta
            Text(
                if (delta == 0) " " else (if (delta > 0) "+" else "−") + duration(kotlin.math.abs(delta)),
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = actionColor(model) ?: Palette.muted,
            )
        }
    }
}

/** Whether a tap at [p] lands on the ring between the moon and the handle, while moving bedtime is on offer. */
private fun hitsBedtimeCut(model: ScreenModel, p: Offset, width: Float): Boolean {
    if (model.fitBedtime == null || model.fittingBedtime) return false
    val half = width / 2
    val d = hypot(p.x - half, p.y - half) / half
    if (d !in 0.6f..0.95f) return false // the ring sits at 0.77 of the half-width
    var a = atan2((p.x - half).toDouble(), (half - p.y).toDouble())
    if (a < 0) a += 2 * PI
    var laps = model.dial / TURN
    if (model.dial % TURN == 0 && laps > 0) laps -= 1
    val minute = laps * TURN + a / (2 * PI) * TURN
    val cutStart = model.state.played + model.toPlay
    return minute in (cutStart - 4.0)..(model.dial + 2.0)
}

@Composable
private fun BedtimeLine(model: ScreenModel, onFit: () -> Unit) {
    // Height always reserved, so nothing below moves when the line appears.
    val offer = model.fitBedtime != null && !model.fittingBedtime
    Row(
        Modifier.fillMaxWidth().height(36.dp).then(if (offer) Modifier.clickable(onClick = onFit) else Modifier),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        val fit = model.fitBedtime ?: return@Row
        MoonIcon()
        if (model.fittingBedtime) {
            Text("Bedtime moves to ${clock(fit.bedtimeAfter)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Palette.bedtimeMove)
        } else {
            Text("Bedtime cuts ${duration(model.bedtimeCuts)} · ", fontSize = 14.sp, color = Palette.muted)
            Text(
                "Move bedtime to ${clock(fit.bedtimeAfter)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Palette.bedtimeMove,
                textDecoration = TextDecoration.Underline,
            )
        }
    }
}

@Composable
private fun MoonIcon() {
    Canvas(Modifier.padding(end = 6.dp).size(18.dp)) {
        val k = size.width / 24f
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(Palette.bedtimeMove, 10 * k, c)
        drawCircle(Palette.background, 8.5f * k, Offset(c.x + 4.4f * k, c.y - 3.7f * k))
    }
}

@Composable private fun Big(t: String) = Text(t, fontSize = 56.sp, fontWeight = FontWeight.SemiBold, color = Palette.ink)
@Composable private fun UnitLabel(t: String) = Text(t, fontSize = 18.sp, color = Palette.muted, modifier = Modifier.padding(start = 2.dp, end = 6.dp, bottom = 10.dp))

private fun DrawScope.drawWheel(model: ScreenModel, value: Float) {
    val k = size.width / 300f // geometry from the prototype's 300-unit wheel
    val c = Offset(size.width / 2, size.height / 2)
    val r = 116 * k
    val w = 22 * k
    val box = Size(2 * r, 2 * r)
    val topLeft = Offset(c.x - r, c.y - r)
    fun at(frac: Float, radius: Float) = Offset(c.x + radius * sin(frac * 2 * PI).toFloat(), c.y - radius * cos(frac * 2 * PI).toFloat())
    fun arc(fromMin: Float, toMin: Float, color: Color, width: Float, cap: StrokeCap, alpha: Float = 1f) {
        if (toMin - fromMin < 0.01f) return
        drawArc(color, -90f + fromMin / TURN * 360f, (toMin - fromMin) / TURN * 360f, false, topLeft, box, alpha, Stroke(width, cap = cap))
    }

    drawCircle(Palette.track, r, c, style = Stroke(w))
    for (i in 0 until 12) {
        val major = i % 3 == 0
        drawLine(Palette.tick, at(i / 12f, r - 22 * k), at(i / 12f, r - (if (major) 34 else 28) * k), (if (major) 2 else 1) * k, StrokeCap.Round)
    }

    var laps = floor(value / TURN).toInt()
    var rem = value - laps * TURN
    if (rem < 0.01f && laps > 0) { laps -= 1; rem = TURN.toFloat() }
    val lapStart = laps * TURN
    if (laps >= 1) drawCircle(Palette.accent, r, c, alpha = 0.35f, style = Stroke(w))
    arc(0f, minOf(rem, TURN - 0.01f), Palette.accent, w, StrokeCap.Round)

    val s = model.state
    if (laps == 0 && s.played > 0) arc(0f, minOf(s.played.toFloat(), value), Palette.played, w, StrokeCap.Round)

    // After-bedtime part: faded green with a reddish border, painted opaque so it reads the same on any lap.
    if (model.blocked > 0) {
        val from = maxOf((s.played + model.toPlay).toFloat(), lapStart.toFloat()) - lapStart
        val to = value - lapStart
        arc(from, to, Palette.blockedBorder, w, StrokeCap.Butt)
        arc(from, to, Palette.background, w - 4 * k, StrokeCap.Butt)
        arc(from, to, Palette.accent, w - 4 * k, StrokeCap.Butt, alpha = 0.5f)
    }

    // Moon where bedtime stops play, only on the lap shown.
    model.bedtimeOnWheel?.let { pos ->
        var lap = pos / TURN
        var f = (pos - lap * TURN) / TURN.toFloat()
        if (f == 0f && lap > 0) { lap -= 1; f = 1f }
        if (lap == laps) {
            val m = at(f, r)
            drawCircle(Color.White, 12 * k, m)
            drawCircle(Palette.bedtimeMove, 12 * k, m, style = Stroke(2 * k))
            drawCircle(Palette.bedtimeMove, 6 * k, m)
            drawCircle(Color.White, 5 * k, Offset(m.x + 2.6f * k, m.y - 2.2f * k))
        }
    }

    val handle = at(minOf(rem / TURN, 0.9999f), r)
    drawCircle(Color.White, 17 * k, handle)
    drawCircle(Palette.accent, 17 * k, handle, style = Stroke(4 * k))
}

private fun actionColor(model: ScreenModel): Color? = when {
    model.delta == 0 -> null
    model.action == Action.STOP -> Palette.stop
    model.delta < 0 -> Palette.reduce
    else -> Palette.accent
}

@Composable
private fun ChangeCard(model: ScreenModel) {
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).border(1.dp, Palette.line, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Show the total the parent chose (the wheel), not Nintendo's timer, which after bedtime has to be much larger.
        val changedTotal = model.plan.changed && model.dial != model.allocated
        Row4("Play time", duration(model.allocated), Palette.ink, if (changedTotal) duration(model.dial) else "", actionColor(model) ?: Palette.accent)
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.line))
        val passedBefore = model.bedtimeBefore <= model.state.now
        Row4(
            "Bedtime", clock(model.bedtimeBefore), if (passedBefore) Palette.stop else Palette.ink,
            if (model.bedtimeChanged) clock(model.bedtimeAfter) else "", if (model.bedtimePassed) Palette.stop else Palette.bedtimeMove,
        )
    }
}

@Composable
private fun Row4(label: String, before: String, beforeColor: Color, after: String, afterColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = Palette.muted, modifier = Modifier.weight(1f))
        Text(before, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = beforeColor, textAlign = TextAlign.End, modifier = Modifier.width(76.dp))
        Text(if (after.isEmpty()) "" else "→", fontSize = 15.sp, color = Color(0xFF8B948E), textAlign = TextAlign.Center, modifier = Modifier.width(26.dp))
        Text(after, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = afterColor, modifier = Modifier.width(100.dp))
    }
}

@Composable
private fun Summary(model: ScreenModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (model.state.played > 0) {
            Dot(Palette.played); Text("${duration(model.state.played)} played", fontSize = 14.sp, color = Palette.muted)
            Spacer(Modifier.width(14.dp))
        }
        Dot(Palette.accent)
        val none = model.toPlay == 0
        Text(
            "${duration(model.toPlay)} to play" + if (!none && model.plan.stoppedByBedtime) " before bedtime" else "",
            fontSize = 14.sp, color = if (none) Palette.stop else Palette.muted, fontWeight = if (none) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable private fun Dot(color: Color) = Box(Modifier.padding(end = 5.dp).size(8.dp).background(color, CircleShape))

@Composable
private fun ActionButton(model: ScreenModel, message: String?, onClick: () -> Unit) {
    val (bg, fg) = when {
        message != null -> Palette.done to Palette.doneInk
        model.action == Action.STOP -> Palette.stop to Color.White
        model.action == Action.REDUCE -> Palette.reduce to Color.White
        model.action == Action.ADD -> Palette.accent to Color.White
        else -> Palette.track to Palette.muted
    }
    Button(
        onClick = onClick,
        enabled = message == null && model.action != Action.NONE,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg, disabledContainerColor = bg, disabledContentColor = fg),
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
    ) {
        Text(message ?: if (model.action == Action.STOP) "Stop time" else "Confirm", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
