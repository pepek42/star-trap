package com.github.pepek42.asteroids.tool.sound

import java.io.ByteArrayOutputStream
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max

/** Sample rate of every generated sound. */
const val SAMPLE_RATE = 22050

/** Output peak after normalisation: -1 dBFS. */
val PEAK_MINUS_1_DBFS = sPow(10.0, -1.0 / 20.0).toFloat()

// StrictMath variants: bit-identical results on every JVM/machine, so the committed WAVs regenerate identically.
internal fun sSin(x: Double) = StrictMath.sin(x)
internal fun sCos(x: Double) = StrictMath.cos(x)
internal fun sExp(x: Double) = StrictMath.exp(x)
internal fun sPow(a: Double, b: Double) = StrictMath.pow(a, b)
internal fun sPowF(a: Float, b: Float) = StrictMath.pow(a.toDouble(), b.toDouble()).toFloat()
internal fun sTanh(x: Double) = StrictMath.tanh(x)

/** Fade in/out applied by [Dsp.normalize] to avoid clicks. */
const val FADE_SECONDS = 0.005f

/** Small DSP kit. Buffers are mono FloatArrays at [SAMPLE_RATE]; time arguments are in seconds. */
object Dsp {
    fun samples(seconds: Float) = (seconds * SAMPLE_RATE).toInt()

    // ---- oscillators ----
    /** Sine with a per-sample frequency (Hz) so pitch sweeps are phase-continuous. */
    fun sine(n: Int, freq: (Float) -> Float): FloatArray = osc(n, freq) { sSin(2 * PI * it).toFloat() }

    fun saw(n: Int, freq: (Float) -> Float): FloatArray = osc(n, freq) { (2 * it - 1).toFloat() }

    fun square(n: Int, freq: (Float) -> Float): FloatArray = osc(n, freq) { if (it < 0.5) 1f else -1f }

    /** [freq] receives time in seconds. [shape] receives phase in 0..1. */
    private fun osc(n: Int, freq: (Float) -> Float, shape: (Double) -> Float): FloatArray {
        val out = FloatArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            out[i] = shape(phase)
            phase += freq(i.toFloat() / SAMPLE_RATE) / SAMPLE_RATE
            phase -= kotlin.math.floor(phase)
        }
        return out
    }

    fun sine(n: Int, hz: Float) = sine(n) { hz }

    /** Exponential pitch sweep from [from] to [to] Hz over the buffer. */
    fun sweep(n: Int, from: Float, to: Float): FloatArray {
        val dur = n.toFloat() / SAMPLE_RATE
        return sine(n) { t -> from * sPowF(to / from, (t / dur).coerceIn(0f, 1f)) }
    }

    // ---- noise ----
    fun white(n: Int, rng: Random) = FloatArray(n) { rng.nextFloat() * 2 - 1 }

    /** Leaky-integrated white noise, roughly unit-ish amplitude. */
    fun brown(n: Int, rng: Random): FloatArray {
        val out = FloatArray(n)
        var acc = 0f
        for (i in 0 until n) {
            acc = (acc + 0.04f * (rng.nextFloat() * 2 - 1)) / 1.02f
            out[i] = acc * 8f
        }
        return out
    }

    // ---- envelopes (return gain buffers) ----
    fun adsr(n: Int, a: Float, d: Float, s: Float, r: Float): FloatArray {
        val out = FloatArray(n)
        val ai = samples(a)
        val di = samples(d)
        val ri = samples(r)
        val relStart = n - ri
        for (i in 0 until n) {
            val base = when {
                i < ai -> i.toFloat() / max(1, ai)
                i < ai + di -> 1f + (s - 1f) * (i - ai).toFloat() / max(1, di)
                else -> s
            }
            out[i] = if (i >= relStart) base * (n - i).toFloat() / max(1, ri) else base
        }
        return out
    }

    /** Instant attack then exp(-t/tau) decay. */
    fun expDecay(n: Int, tau: Float) = FloatArray(n) { sExp((-(it.toFloat() / SAMPLE_RATE) / tau).toDouble()).toFloat() }

    // ---- filters ----
    enum class FilterType { LOW_PASS, BAND_PASS }

    /** RBJ biquad; [cutoff] gets time in seconds so the cutoff can sweep. */
    fun biquad(x: FloatArray, type: FilterType, q: Float = 0.707f, cutoff: (Float) -> Float): FloatArray {
        val out = FloatArray(x.size)
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        for (i in x.indices) {
            val fc = cutoff(i.toFloat() / SAMPLE_RATE).toDouble().coerceIn(20.0, SAMPLE_RATE * 0.45)
            val w = 2 * PI * fc / SAMPLE_RATE
            val alpha = sSin(w) / (2 * q)
            val cw = sCos(w)
            val b0: Double
            val b1: Double
            val b2: Double
            when (type) {
                FilterType.LOW_PASS -> { b0 = (1 - cw) / 2; b1 = 1 - cw; b2 = (1 - cw) / 2 }
                FilterType.BAND_PASS -> { b0 = alpha; b1 = 0.0; b2 = -alpha }
            }
            val a0 = 1 + alpha
            val a1 = -2 * cw
            val a2 = 1 - alpha
            val xi = x[i].toDouble()
            val y = (b0 * xi + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2) / a0
            x2 = x1; x1 = xi; y2 = y1; y1 = y
            out[i] = y.toFloat()
        }
        return out
    }

    fun lowPass(x: FloatArray, hz: Float, q: Float = 0.707f) = biquad(x, FilterType.LOW_PASS, q) { hz }

    fun lowPassSweep(x: FloatArray, from: Float, to: Float, q: Float = 0.707f): FloatArray {
        val dur = x.size.toFloat() / SAMPLE_RATE
        return biquad(x, FilterType.LOW_PASS, q) { t -> from * sPowF(to / from, (t / dur).coerceIn(0f, 1f)) }
    }

    fun bandPass(x: FloatArray, hz: Float, q: Float) = biquad(x, FilterType.BAND_PASS, q) { hz }

    /** One-pole low-pass. */
    fun onePole(x: FloatArray, hz: Float): FloatArray {
        val a = 1f - sExp((-2f * PI.toFloat() * hz / SAMPLE_RATE).toDouble()).toFloat()
        var y = 0f
        return FloatArray(x.size) { y += a * (x[it] - y); y }
    }

    // ---- shaping / mixing ----
    fun saturate(x: FloatArray, drive: Float) = FloatArray(x.size) { sTanh((x[it] * drive).toDouble()).toFloat() }

    fun mul(a: FloatArray, b: FloatArray) = FloatArray(a.size) { a[it] * b[it] }

    fun gain(a: FloatArray, g: Float) = FloatArray(a.size) { a[it] * g }

    /** Sum of buffers (each with a gain); result has the length of the longest. */
    fun mix(vararg parts: Pair<FloatArray, Float>): FloatArray {
        val out = FloatArray(parts.maxOf { it.first.size })
        for (p in parts) {
            val buf = p.first
            for (i in buf.indices) out[i] += buf[i] * p.second
        }
        return out
    }

    /** Places [x] at [offsetSeconds] inside a silent buffer of [totalSeconds]. */
    fun place(x: FloatArray, offsetSeconds: Float, totalSeconds: Float): FloatArray {
        val out = FloatArray(samples(totalSeconds))
        val o = samples(offsetSeconds)
        for (i in x.indices) if (o + i < out.size) out[o + i] = x[i]
        return out
    }

    /** Seamless loop: the last [fadeSeconds] equal-power cross-fade into the material that follows the loop end. */
    fun loopable(x: FloatArray, fadeSeconds: Float): FloatArray {
        val f = samples(fadeSeconds)
        val n = x.size - f
        val out = x.copyOf(n)
        for (i in 0 until f) {
            val t = i.toFloat() / f
            out[i] = x[i] * StrictMath.sqrt(t.toDouble()).toFloat() + x[n + i] * StrictMath.sqrt((1 - t).toDouble()).toFloat()
        }
        return out
    }

    /** Scale to -1 dBFS; [fade] adds a short fade-in and fade-out (skip for loops). */
    fun normalize(x: FloatArray, fade: Boolean = true, fadeSeconds: Float = FADE_SECONDS): FloatArray {
        // Fade first, then scale, so the faded file still peaks at exactly -1 dBFS.
        val out = x.copyOf()
        if (fade) {
            val f = minOf(samples(fadeSeconds), out.size / 2)
            for (i in 0 until f) {
                val k = i.toFloat() / f
                out[i] *= k
                out[out.size - 1 - i] *= k
            }
        }
        val peak = out.maxOfOrNull { abs(it) } ?: 0f
        val g = if (peak > 0f) PEAK_MINUS_1_DBFS / peak else 1f
        for (i in out.indices) out[i] *= g
        return out
    }

    // ---- WAV ----
    /** 16-bit mono PCM WAV, little endian. */
    fun toWav(x: FloatArray): ByteArray {
        val dataLen = x.size * 2
        val b = ByteArrayOutputStream(44 + dataLen)
        fun i32(v: Int) { for (s in 0..24 step 8) b.write((v shr s) and 0xFF) }
        fun i16(v: Int) { b.write(v and 0xFF); b.write((v shr 8) and 0xFF) }
        b.write("RIFF".toByteArray()); i32(36 + dataLen); b.write("WAVE".toByteArray())
        b.write("fmt ".toByteArray()); i32(16); i16(1); i16(1); i32(SAMPLE_RATE); i32(SAMPLE_RATE * 2); i16(2); i16(16)
        b.write("data".toByteArray()); i32(dataLen)
        for (v in x) i16(Math.round(v.coerceIn(-1f, 1f) * 32767f))
        return b.toByteArray()
    }
}
