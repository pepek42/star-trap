package com.github.pepek42.asteroids.tool.sound

import com.github.pepek42.asteroids.tool.sound.Dsp.bandPass
import com.github.pepek42.asteroids.tool.sound.Dsp.expDecay
import com.github.pepek42.asteroids.tool.sound.Dsp.gain
import com.github.pepek42.asteroids.tool.sound.Dsp.lowPass
import com.github.pepek42.asteroids.tool.sound.Dsp.lowPassSweep
import com.github.pepek42.asteroids.tool.sound.Dsp.mix
import com.github.pepek42.asteroids.tool.sound.Dsp.mul
import com.github.pepek42.asteroids.tool.sound.Dsp.normalize
import com.github.pepek42.asteroids.tool.sound.Dsp.place
import com.github.pepek42.asteroids.tool.sound.Dsp.samples
import java.util.Random

/**
 * One recipe per sound, named after the file (without .wav). Each returns normalised samples.
 * "Hull layer" sounds are what the pilot hears conducted through the hull; "synth_" sounds are the computer's.
 * Every recipe uses its own fixed seed, so output is deterministic.
 */
object SoundRecipes {
    private const val LOOP_FADE_S = 0.25f

    /** Low brown-noise rumble + sub-bass hum, looping. */
    fun engine_loop(): FloatArray {
        val len = 2f
        val n = samples(len + LOOP_FADE_S)
        val rng = Random(1001)
        val rumble = lowPass(Dsp.brown(n, rng), 140f)
        // Integer-cycle hums so the loop point is smooth even without the crossfade.
        val hum = Dsp.sine(n, 46f)
        val hum2 = Dsp.sine(n, 92f)
        val mixed = mix(rumble to 1f, hum to 0.35f, hum2 to 0.12f)
        return normalize(Dsp.loopable(mixed, LOOP_FADE_S), fade = false)
    }

    /** Dull thump plus a falling (1800 to 700 Hz) capacitor whine; not a "pew". */
    fun laser_fire(): FloatArray {
        val n = samples(0.45f)
        val rng = Random(1002)
        val thump = mul(Dsp.sweep(n, 90f, 40f), expDecay(n, 0.07f))
        val click = mul(lowPass(Dsp.white(n, rng), 600f), expDecay(n, 0.012f))
        val whine = mul(Dsp.sweep(n, 1800f, 700f), expDecay(n, 0.10f))
        val whineQuiet = lowPass(whine, 2200f)
        return normalize(mix(thump to 1f, click to 0.5f, whineQuiet to 0.12f))
    }

    /** Metallic knock: noise burst excited through inharmonic band-pass resonances. */
    fun hull_hit(): FloatArray {
        val n = samples(0.5f)
        val rng = Random(1003)
        val burst = mul(Dsp.white(n, rng), expDecay(n, 0.006f))
        val modeHz = floatArrayOf(310f, 587f, 1130f, 2240f)
        val modeGain = floatArrayOf(1f, 0.8f, 0.5f, 0.3f)
        val ring = FloatArray(n)
        for (k in modeHz.indices) {
            val tau = 0.18f / (modeHz[k] / 310f).coerceAtMost(3f)
            val m = mul(bandPass(burst, modeHz[k], 25f), expDecay(n, tau))
            for (i in 0 until n) ring[i] += m[i] * modeGain[k] * 6f
        }
        val thud = mul(Dsp.sine(n, 70f), expDecay(n, 0.04f))
        // 1 ms fade-in keeps the attack sharp.
        return normalize(mix(ring to 1f, thud to 0.5f), fadeSeconds = 0.001f)
    }

    /** Longer, friction-like scrape: band-passed noise with a wobbling centre and saturation. */
    fun hull_scrape(): FloatArray {
        val len = 0.8f
        val n = samples(len)
        val rng = Random(1004)
        val noise = Dsp.white(n, rng)
        val scrape = Dsp.biquad(noise, Dsp.FilterType.BAND_PASS, 4f) { t ->
            900f + 350f * sSin(2 * Math.PI * 9 * t).toFloat() + 400f * t
        }
        val grit = mul(scrape, Dsp.adsr(n, 0.05f, 0.1f, 0.7f, 0.25f))
        return normalize(Dsp.saturate(gain(grit, 4f), 2f))
    }

    /** Two-tone computer beep, repeated twice. */
    fun alarm_proximity(): FloatArray {
        val beep = { hz: Float, len: Float ->
            val n = samples(len)
            mul(Dsp.sine(n, hz), Dsp.adsr(n, 0.005f, 0.01f, 0.9f, 0.02f))
        }
        val pair = mix(
            place(beep(880f, 0.09f), 0f, 0.4f) to 1f,
            place(beep(1175f, 0.09f), 0.12f, 0.4f) to 1f,
        )
        val both = mix(place(pair, 0f, 0.8f) to 1f, place(pair, 0.4f, 0.8f) to 1f)
        return normalize(both)
    }

    /** Harsher, lower square-wave warning with a buzzy sweep. */
    fun alarm_hull_damage(): FloatArray {
        val n = samples(0.7f)
        val tone = lowPass(Dsp.square(n) { t -> if (((t * 5).toInt() % 2) == 0) 330f else 247f }, 1600f)
        val env = Dsp.adsr(n, 0.01f, 0.05f, 0.85f, 0.1f)
        return normalize(Dsp.saturate(gain(mul(tone, env), 1.5f), 1.5f))
    }

    private fun asteroidBreak(seed: Long, len: Float, boomHz: Float, cutoffFrom: Float, tau: Float): FloatArray {
        val n = samples(len)
        val rng = Random(seed)
        val crunch = mul(lowPassSweep(Dsp.white(n, rng), cutoffFrom, 200f, 1.2f), expDecay(n, tau))
        val boom = mul(Dsp.sweep(n, boomHz * 1.6f, boomHz), expDecay(n, tau * 1.3f))
        return normalize(mix(crunch to 1f, boom to 0.9f))
    }

    fun synth_asteroid_break_small() = asteroidBreak(1005, 0.35f, 90f, 5000f, 0.07f)

    fun synth_asteroid_break_medium() = asteroidBreak(1006, 0.7f, 60f, 3500f, 0.15f)

    fun synth_asteroid_break_large() = asteroidBreak(1007, 1.3f, 38f, 2400f, 0.3f)

    /** Short rising two-note chirp, computer lock-on confirmation. */
    fun synth_ui_lock(): FloatArray {
        val n = samples(0.18f)
        val a = mul(Dsp.sine(n / 2, 1320f), Dsp.adsr(n / 2, 0.003f, 0.01f, 0.8f, 0.02f))
        val b = mul(Dsp.sine(n / 2, 1980f), Dsp.adsr(n / 2, 0.003f, 0.01f, 0.8f, 0.04f))
        return normalize(mix(place(a, 0f, 0.18f) to 1f, place(b, 0.08f, 0.18f) to 1f))
    }

    /** File name (without extension) to recipe. */
    val all: Map<String, () -> FloatArray> = linkedMapOf(
        "engine_loop" to ::engine_loop,
        "laser_fire" to ::laser_fire,
        "hull_hit" to ::hull_hit,
        "hull_scrape" to ::hull_scrape,
        "alarm_proximity" to ::alarm_proximity,
        "alarm_hull_damage" to ::alarm_hull_damage,
        "synth_asteroid_break_small" to ::synth_asteroid_break_small,
        "synth_asteroid_break_medium" to ::synth_asteroid_break_medium,
        "synth_asteroid_break_large" to ::synth_asteroid_break_large,
        "synth_ui_lock" to ::synth_ui_lock,
    )
}
