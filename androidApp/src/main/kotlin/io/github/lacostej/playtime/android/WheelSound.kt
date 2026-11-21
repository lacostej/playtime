package io.github.lacostej.playtime.android

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/** Experimental wheel sounds, synthesised at start-up (no audio assets). Silent when the phone is on silent. */
object WheelSound {
    enum class Kind(val label: String) { OFF("Off"), TICK("Tick"), CLICK("Click"), WOOD("Wood"), BLIP("Blip"), RISING("Rising") }

    private const val KEY = "wheel_sound"
    private const val RATE = 44100
    private var pool: SoundPool? = null
    private val ids = mutableMapOf<Kind, Int>()
    private var prefs: SharedPreferences? = null
    var kind by mutableStateOf(Kind.BLIP)
        private set

    fun init(context: Context) {
        if (pool != null) return
        val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs = p
        kind = Kind.entries.firstOrNull { it.name == p.getString(KEY, null) } ?: Kind.BLIP
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val sp = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attributes).build()
        for (k in Kind.entries) {
            val samples = synth(k) ?: continue
            val f = File(context.cacheDir, "wheel-${k.name.lowercase()}.wav")
            f.writeBytes(wav(samples))
            ids[k] = sp.load(f.path, 1)
        }
        pool = sp
    }

    fun choose(k: Kind) {
        kind = k
        prefs?.edit()?.putString(KEY, k.name)?.apply()
        step(55)
    }

    /** One 5-minute step of the wheel, now at [value] minutes; full hours sound a little stronger. */
    fun step(value: Int) {
        val id = ids[kind] ?: return
        val hour = value > 0 && value % 60 == 0
        var rate = if (kind == Kind.RISING) 0.7f + 1.1f * value.coerceIn(0, 240) / 240f else 1f
        if (hour && kind != Kind.RISING) rate *= 1.25f
        val volume = if (hour) 1f else 0.6f
        pool?.play(id, volume, volume, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    private fun synth(k: Kind): DoubleArray? = when (k) {
        Kind.OFF -> null
        Kind.TICK -> {
            // High-passed noise burst: a dry mechanical tick.
            val r = java.util.Random(7)
            var prev = 0.0
            gen(0.006) { t -> val n = r.nextDouble() * 2 - 1; val hp = n - prev; prev = n; hp * exp(-t / 0.0008) }
        }
        Kind.CLICK -> gen(0.015) { t -> (0.6 * sin(2 * PI * 1800 * t) + 0.4 * sin(2 * PI * 4200 * t)) * exp(-t / 0.003) }
        Kind.WOOD -> gen(0.04) { t -> (0.7 * sin(2 * PI * 900 * t) + 0.3 * sin(2 * PI * 2300 * t)) * exp(-t / 0.008) }
        Kind.BLIP, Kind.RISING -> gen(0.03) { t -> sin(2 * PI * 1320 * t) * min(1.0, t / 0.002) * exp(-t / 0.006) }
    }

    private fun gen(seconds: Double, f: (Double) -> Double): DoubleArray {
        val s = DoubleArray((seconds * RATE).toInt()) { i -> f(i.toDouble() / RATE) }
        val peak = s.maxOf { abs(it) }.takeIf { it > 0 } ?: 1.0
        return DoubleArray(s.size) { s[it] / peak * 0.8 }
    }

    /** 16-bit mono PCM WAV. */
    private fun wav(samples: DoubleArray): ByteArray {
        val data = samples.size * 2
        val b = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(RATE).putInt(RATE * 2).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(data)
        for (v in samples) b.putShort((v.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort())
        return b.array()
    }
}
