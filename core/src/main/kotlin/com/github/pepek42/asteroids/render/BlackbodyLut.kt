package com.github.pepek42.asteroids.render

import kotlin.math.exp
import kotlin.math.ln

/** Number of entries in [BlackbodyLut]. */
const val BLACKBODY_LUT_SIZE = 256

/** Lowest temperature in [BlackbodyLut] (K). Doppler-shifted red stars are clamped here. */
const val BLACKBODY_LUT_MIN_K = 1000f

/** Highest temperature in [BlackbodyLut] (K). Doppler-shifted blue stars are clamped here. */
const val BLACKBODY_LUT_MAX_K = 60000f

/**
 * Blackbody colour lookup table: [BLACKBODY_LUT_SIZE] entries, log-spaced between [BLACKBODY_LUT_MIN_K] and
 * [BLACKBODY_LUT_MAX_K], built once from [blackbodyRgb]. Lets the per-frame Doppler pass colour 1500 stars
 * without evaluating pow/ln per star and without allocating.
 */
object BlackbodyLut {
    private val logMin = ln(BLACKBODY_LUT_MIN_K)
    private val logSpan = ln(BLACKBODY_LUT_MAX_K) - logMin
    private val rgb = FloatArray(BLACKBODY_LUT_SIZE * 3)

    init {
        for (i in 0 until BLACKBODY_LUT_SIZE) {
            val k = exp(logMin + logSpan * i / (BLACKBODY_LUT_SIZE - 1))
            val c = blackbodyRgb(k)
            rgb[i * 3] = c[0]
            rgb[i * 3 + 1] = c[1]
            rgb[i * 3 + 2] = c[2]
        }
    }

    /** Writes the colour (r, g, b in 0..1) for [kelvin] into `out[0..2]`, linearly interpolated. Does not allocate. */
    fun rgbInto(kelvin: Float, out: FloatArray) {
        val pos = ((ln(kelvin.coerceIn(BLACKBODY_LUT_MIN_K, BLACKBODY_LUT_MAX_K)) - logMin) / logSpan) *
            (BLACKBODY_LUT_SIZE - 1)
        val i0 = pos.toInt().coerceIn(0, BLACKBODY_LUT_SIZE - 2)
        val f = pos - i0
        val a = i0 * 3
        val b = a + 3
        out[0] = rgb[a] + (rgb[b] - rgb[a]) * f
        out[1] = rgb[a + 1] + (rgb[b + 1] - rgb[a + 1]) * f
        out[2] = rgb[a + 2] + (rgb[b + 2] - rgb[a + 2]) * f
    }
}
