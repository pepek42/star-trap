package com.github.pepek42.asteroids.physics

import kotlin.math.min

/**
 * Warp factor for the pilot clock: the arena advances `warp · dt_real` per frame, where warp = γ clamped to [1, max].
 * Returns 1 when disabled or when γ is not finite.
 */
fun timeWarpFactor(gamma: Float, enabled: Boolean, max: Float): Float =
    if (!enabled || !gamma.isFinite()) 1f else gamma.coerceIn(1f, max)

/** True when γ exceeds the warp cap, i.e. the world cannot keep up with the pilot's γ (declared FAKE). */
fun isWarpCapped(gamma: Float, enabled: Boolean, max: Float): Boolean =
    enabled && gamma.isFinite() && gamma > max + 1e-4f

/** Arena time to simulate for one frame: `realDt · warp`, limited to [maxWorldDt]. */
fun worldDelta(realDt: Float, warp: Float, maxWorldDt: Float): Float = min(realDt * warp, maxWorldDt)

/** Per-frame state of the pilot clock. */
class GameClock {
    /** The pilot's frame time (after the debug time scale). */
    var realDt = 0f

    /** Current time warp factor applied to [realDt]. */
    var warp = 1f

    /** Arena time to simulate this frame. */
    var worldDt = 0f

    /** True when the warp was clamped by its maximum. */
    var warpCapped = false

    /** True when the physics step cap cut [worldDt]. */
    var stepsCapped = false

    /** Sum of the simulated physics steps. */
    var worldTime = 0f

    /** `PhysicsSystem`'s render lerp factor. */
    var interpolationAlpha = 0f

    fun reset() {
        realDt = 0f
        warp = 1f
        worldDt = 0f
        warpCapped = false
        stepsCapped = false
        worldTime = 0f
        interpolationAlpha = 0f
    }
}
