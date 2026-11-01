package com.github.pepek42.asteroids.render

import com.badlogic.gdx.utils.IntFloatMap
import kotlin.math.exp

const val SHIMMER_MAX_IMPACTS = 16
const val SHIMMER_DECAY = 0.6f
const val SHIMMER_IMPACT_LIFETIME = 1.5f
const val SHIMMER_REFRESH_SECONDS = 0.25f

private const val FLOATS_PER_IMPACT = 4
private const val OFFSET_X = 0
private const val OFFSET_Y = 1
private const val OFFSET_START = 2
private const val OFFSET_STRENGTH = 3

/**
 * Fixed-size buffer of recent boundary impacts, uploaded by the shimmer shader as `u_impacts[]`.
 * [data] holds (x, y, startTime, strength) per impact; the first [count] impacts are valid.
 * Neither [emit] nor [prune] allocates (apart from rare internal map growth for new entity ids).
 */
class ShimmerImpacts(val capacity: Int = SHIMMER_MAX_IMPACTS) {
    var time = 0f
    val data = FloatArray(capacity * FLOATS_PER_IMPACT)
    var count = 0
        private set
    private val lastEmit = IntFloatMap()

    fun emit(entityId: Int, x: Float, y: Float, strength: Float, force: Boolean = false) {
        if (!force) {
            val last = lastEmit.get(entityId, -Float.MAX_VALUE)
            if (time - last < SHIMMER_REFRESH_SECONDS) return
        }
        lastEmit.put(entityId, time)
        val slot = if (count < capacity) count++ else weakestSlot()
        val base = slot * FLOATS_PER_IMPACT
        data[base + OFFSET_X] = x
        data[base + OFFSET_Y] = y
        data[base + OFFSET_START] = time
        data[base + OFFSET_STRENGTH] = strength
    }

    /** Drops impacts older than [SHIMMER_IMPACT_LIFETIME] and compacts [data]. */
    fun prune() {
        var write = 0
        for (read in 0 until count) {
            val src = read * FLOATS_PER_IMPACT
            if (time - data[src + OFFSET_START] > SHIMMER_IMPACT_LIFETIME) continue
            if (write != read) {
                System.arraycopy(data, src, data, write * FLOATS_PER_IMPACT, FLOATS_PER_IMPACT)
            }
            write++
        }
        count = write
    }

    private fun weakestSlot(): Int {
        var best = 0
        var bestValue = Float.MAX_VALUE
        for (i in 0 until count) {
            val base = i * FLOATS_PER_IMPACT
            val age = time - data[base + OFFSET_START]
            val value = data[base + OFFSET_STRENGTH] * exp(-age / SHIMMER_DECAY)
            if (value < bestValue) {
                bestValue = value
                best = i
            }
        }
        return best
    }
}
