package com.github.pepek42.asteroids.render

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.github.pepek42.asteroids.component.baseInfoMapper
import com.github.pepek42.asteroids.component.bodyCmpOptional
import com.github.pepek42.asteroids.component.playerMapper
import com.github.pepek42.asteroids.component.relativisticCmpOptional
import com.github.pepek42.asteroids.component.spriteCmp
import com.github.pepek42.asteroids.component.transformCmp
import com.github.pepek42.asteroids.physics.GameClock
import com.github.pepek42.asteroids.physics.ObserverState
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.RetardedState
import com.github.pepek42.asteroids.physics.apparentOffset
import com.github.pepek42.asteroids.physics.gammaFromV
import com.github.pepek42.asteroids.physics.retarded
import com.github.pepek42.asteroids.physics.worldHullToPilot
import com.github.pepek42.asteroids.system.LightHistorySystem
import com.github.pepek42.asteroids.system.PhysicsSystem.Companion.PHYSICS_UPDATE_INTERVAL
import kotlin.math.sqrt

/** Floats per sprite quad: 4 corners of (x, y, color, u, v). */
const val SPRITE_VERTEX_FLOATS = 20

/** Floats per sprite corner. */
const val SPRITE_VERTEX_STRIDE = 5

/** A sprite moving slower than this (u/s) is not contracted. */
internal const val MIN_CONTRACTION_SPEED = 1e-3f

/**
 * Maps the arena point ([px], [py]) to where the pilot at ([ox], [oy]) with velocity fraction ([betaX], [betaY]) sees it:
 * `o + apparentOffset(p - o, β)`, or p itself when [aberration] is false. Pure and allocation-free.
 */
fun pilotCorner(
    px: Float, py: Float, ox: Float, oy: Float, betaX: Float, betaY: Float, aberration: Boolean, out: Vector2,
): Vector2 {
    if (!aberration) return out.set(px, py)
    apparentOffset(px - ox, py - oy, betaX, betaY, out)
    return out.set(ox + out.x, oy + out.y)
}

/**
 * Per-corner retarded position (ST-075, Terrell-Penrose, research E8/E9): refines one sprite corner of an object whose centre
 * was solved at emission time `t_c` (arena position ([xc], [yc]), velocity ([vcx], [vcy]) then). The corner sits at the offset
 * ([qx], [qy]) from the centre (rotated and contracted at `t_c`); [lookback] = `now - t_c`, the observer is at ([ox], [oy]).
 * Looking back `s` from `t_c`: `p(s) = x_c + q - v_c s`, `h(s) = |p(s) - o| - c (lookback + s)`. One Newton step at `s = 0`
 * gives `s = h(0) / (c + r̂·v_c)` with `r̂ = normalize(x_c + q - o)` (`h' = -r̂·v_c - c`). Writes `p(s)` to [out]. Exact for a
 * static object (`v_c = 0`). Rotation and the change of `v_c` during `s` are ignored (FAKE, tiny: `s` is about `|q|/c`).
 * Pure and allocation-free.
 */
fun cornerRetarded(
    xc: Float, yc: Float, qx: Float, qy: Float, vcx: Float, vcy: Float, ox: Float, oy: Float,
    lookback: Float, c: Float, out: Vector2,
): Vector2 {
    val px = xc + qx
    val py = yc + qy
    if (vcx == 0f && vcy == 0f) return out.set(px, py)
    val rx = px - ox
    val ry = py - oy
    val r = sqrt(rx * rx + ry * ry)
    // Corner on top of the observer: no direction, keep the centre's solution.
    if (r < MIN_CORNER_DISTANCE) return out.set(px, py)
    val h0 = r - c * lookback
    val denom = c + (rx * vcx + ry * vcy) / r
    // |v| < c keeps denom > 0; a degenerate value (photon heading at the observer) skips the refinement.
    if (denom < c * MIN_CORNER_DENOM_FRACTION) return out.set(px, py)
    val s = h0 / denom
    return out.set(px - vcx * s, py - vcy * s)
}

/** Corners closer than this to the observer (u) are not retarded individually (no direction). */
internal const val MIN_CORNER_DISTANCE = 1e-3f

/** [cornerRetarded] skips the Newton step when `c + r̂·v` falls below this fraction of c. */
internal const val MIN_CORNER_DENOM_FRACTION = 1e-3f

/**
 * Builds the sprite quads of the pilot view (ST-074, research E8 + E9) without touching the GL batch, so it can be tested
 * headless. Call [begin] once per frame, then [project] per sprite entity. Reuses its scratch objects: no per-frame allocation.
 */
class PilotViewProjector(
    private val history: LightHistorySystem,
    private val observer: ObserverState,
    private val clock: GameClock,
    private val settings: RelativitySettings,
) {
    private val state = RetardedState()
    private val tmp = Vector2()
    private val contracted = Vector2()
    /** Observer (player) position latched by [begin]. */
    var ox = 0f
        private set
    var oy = 0f
        private set

    /** Observer velocity over c latched by [begin] (`ObserverState.betaVisual`). */
    var betaX = 0f
        private set
    var betaY = 0f
        private set

    /** Interpolated arena time latched by [begin]. */
    var now = 0f
        private set

    /** Whether the light delay half of the pilot view is on ([RelativitySettings.pilotDelay]). */
    val delayEnabled: Boolean get() = settings.pilotDelay

    /** Whether the aberration boost is on ([RelativitySettings.pilotAberration]). */
    val aberrationEnabled: Boolean get() = settings.pilotAberration

    /** Light age `now - t_emit` (s) of the last successful [retardedCentre] call; 0 without the history. */
    var centreLag = 0f
        private set

    /** Arena velocity (u/s) of the source at emission in the last successful [retardedCentre] call; 0 without the history. */
    var centreVx = 0f
        private set
    var centreVy = 0f
        private set

    /** Arena position and velocity (u, u/s) of the source drawn by the last successful [project] call: retarded when the history is used. */
    var sourceX = 0f
        private set
    var sourceY = 0f
        private set
    var sourceVx = 0f
        private set
    var sourceVy = 0f
        private set

    /** Arena-frame angle (rad) of the source drawn by the last [project] call (retarded when the history is used; the player's is its true hull angle). */
    var sourceAngleRad = 0f
        private set

    /** Seconds between the interpolated arena time and the emission time of the last [project] call (0 without the history). Used for lighting (ST-097). */
    var sourceLag = 0f
        private set

    /** Latches the observer for this frame: [playerX]/[playerY] is the player's interpolated position. */
    fun begin(playerX: Float, playerY: Float) {
        ox = playerX
        oy = playerY
        betaX = observer.betaVisual.x
        betaY = observer.betaVisual.y
        // Interpolated arena time: the newest step minus the part of the step the render lerp has not reached yet.
        now = clock.worldTime - (1f - clock.interpolationAlpha) * PHYSICS_UPDATE_INTERVAL
    }

    /**
     * Writes where the pilot sees the centre of [entity] into [out]: retarded (when [RelativitySettings.pilotDelay]) and boosted
     * (when [RelativitySettings.pilotAberration]), the same point [project] centres the quad on. Returns false when its light
     * has not arrived yet. Call [begin] first. Allocation-free.
     */
    fun apparentCentre(entity: Entity, out: Vector2): Boolean {
        if (!retardedCentre(entity, out)) return false
        if (playerMapper.has(entity)) return true
        return apparentPoint(out.x, out.y, out).let { true }
    }

    /** Boosts the arena point ([px], [py]) into the pilot frame like a sprite corner (identity without aberration). Call [begin] first. */
    fun apparentPoint(px: Float, py: Float, out: Vector2): Vector2 =
        pilotCorner(px, py, ox, oy, betaX, betaY, settings.pilotAberration, out)

    /**
     * Writes the arena position the pilot sees [entity] at (retarded when [RelativitySettings.pilotDelay], else interpolated)
     * into [out], before the aberration boost. False when its light has not arrived. Call [begin] first. Allocation-free.
     */
    fun retardedCentre(entity: Entity, out: Vector2): Boolean {
        centreLag = 0f
        centreVx = 0f
        centreVy = 0f
        if (playerMapper.has(entity)) return out.set(ox, oy).let { true }
        val h = if (settings.pilotDelay) baseInfoMapper.get(entity)?.let { history.historyOf(it.id) } else null
        val x: Float
        val y: Float
        if (h != null) {
            if (!h.retarded(ox, oy, now, settings.c, state)) return false
            x = state.x
            y = state.y
            centreLag = now - state.t
            centreVx = state.vx
            centreVy = state.vy
        } else {
            val pos = entity.transformCmp.interpolatedPosition
            x = pos.x
            y = pos.y
        }
        out.set(x, y)
        return true
    }

    /**
     * Writes the four corners of [entity]'s sprite as seen by the pilot into [out] (20 floats, the Sprite vertex layout).
     * Returns false when the entity's light has not reached the pilot yet (skip drawing it).
     */
    fun project(entity: Entity, out: FloatArray): Boolean {
        val sprite = entity.spriteCmp.sprite
        val transform = entity.transformCmp
        val x: Float
        val y: Float
        val angleDeg: Float
        var vx = 0f
        var vy = 0f
        var lag = 0f
        var perCorner = false
        var arenaAngleDeg = transform.interpolatedRotationDeg
        if (playerMapper.has(entity)) {
            x = ox
            y = oy
            angleDeg = if (settings.pilotHullTransform) {
                worldHullToPilot(transform.interpolatedRotationDeg * MathUtils.degreesToRadians, betaX, betaY) *
                    MathUtils.radiansToDegrees
            } else {
                transform.interpolatedRotationDeg
            }
        } else {
            val h = if (settings.pilotDelay) baseInfoMapper.get(entity)?.let { history.historyOf(it.id) } else null
            if (h != null) {
                if (!h.retarded(ox, oy, now, settings.c, state)) return false
                x = state.x
                y = state.y
                angleDeg = state.angle * MathUtils.radiansToDegrees
                vx = state.vx
                vy = state.vy
                lag = now - state.t
                perCorner = true
                arenaAngleDeg = angleDeg
            } else {
                x = transform.interpolatedPosition.x
                y = transform.interpolatedPosition.y
                angleDeg = transform.interpolatedRotationDeg
                entity.bodyCmpOptional?.body?.linearVelocity?.let {
                    vx = it.x
                    vy = it.y
                }
            }
        }
        sourceX = x
        sourceY = y
        sourceVx = vx
        sourceVy = vy
        sourceAngleRad = arenaAngleDeg * MathUtils.degreesToRadians
        sourceLag = lag
        sprite.rotation = angleDeg
        sprite.setPosition(x - sprite.width / 2, y - sprite.height / 2)
        System.arraycopy(sprite.vertices, 0, out, 0, SPRITE_VERTEX_FLOATS)

        val speed = sqrt(vx * vx + vy * vy)
        val contract = speed > MIN_CONTRACTION_SPEED
        val k = if (contract) contractionK(gammaFromV(vx, vy, settings.c)) else 0f
        val isPlayer = playerMapper.has(entity)
        // Tidal stretch (ST-153) uses the true (not retarded) tidal state, before the contraction.
        val rel = entity.relativisticCmpOptional
        val stretch = if (rel != null && rel.tidalRatio > 0f) tidalStretch(rel.tidalRatio) else 1f
        val stretched = stretch >= TIDAL_VISUAL_MIN_SCALE
        for (i in 0 until SPRITE_VERTEX_FLOATS step SPRITE_VERTEX_STRIDE) {
            var px = out[i]
            var py = out[i + 1]
            if (stretched && rel != null) {
                tidalOffset(px - x, py - y, rel.tidalDirX, rel.tidalDirY, stretch, contracted)
                px = x + contracted.x
                py = y + contracted.y
            }
            if (contract) {
                contractOffset(px - x, py - y, vx / speed, vy / speed, k, contracted)
                px = x + contracted.x
                py = y + contracted.y
            }
            if (perCorner && contract) {
                // Terrell-Penrose (ST-075): the corner's own retarded position, from its offset at the centre's emission time.
                cornerRetarded(x, y, px - x, py - y, vx, vy, ox, oy, lag, settings.c, contracted)
                px = contracted.x
                py = contracted.y
            }
            if (isPlayer) {
                // The player is at rest in its own frame: no boost.
                out[i] = px
                out[i + 1] = py
            } else {
                pilotCorner(px, py, ox, oy, betaX, betaY, settings.pilotAberration, tmp)
                out[i] = tmp.x
                out[i + 1] = tmp.y
            }
        }
        return true
    }
}
