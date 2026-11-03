package com.github.pepek42.asteroids.render

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.github.pepek42.asteroids.component.baseInfoMapper
import com.github.pepek42.asteroids.component.bodyCmpOptional
import com.github.pepek42.asteroids.component.playerMapper
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
    private var ox = 0f
    private var oy = 0f
    private var betaX = 0f
    private var betaY = 0f
    private var now = 0f

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
        if (playerMapper.has(entity)) return out.set(ox, oy).let { true }
        val h = if (settings.pilotDelay) baseInfoMapper.get(entity)?.let { history.historyOf(it.id) } else null
        val x: Float
        val y: Float
        if (h != null) {
            if (!h.retarded(ox, oy, now, settings.c, state)) return false
            x = state.x
            y = state.y
        } else {
            val pos = entity.transformCmp.interpolatedPosition
            x = pos.x
            y = pos.y
        }
        pilotCorner(x, y, ox, oy, betaX, betaY, settings.pilotAberration, out)
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
        if (playerMapper.has(entity)) {
            x = ox
            y = oy
            angleDeg = worldHullToPilot(transform.interpolatedRotationDeg * MathUtils.degreesToRadians, betaX, betaY) *
                MathUtils.radiansToDegrees
        } else {
            val h = if (settings.pilotDelay) baseInfoMapper.get(entity)?.let { history.historyOf(it.id) } else null
            if (h != null) {
                if (!h.retarded(ox, oy, now, settings.c, state)) return false
                x = state.x
                y = state.y
                angleDeg = state.angle * MathUtils.radiansToDegrees
                vx = state.vx
                vy = state.vy
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
        sprite.rotation = angleDeg
        sprite.setPosition(x - sprite.width / 2, y - sprite.height / 2)
        System.arraycopy(sprite.vertices, 0, out, 0, SPRITE_VERTEX_FLOATS)

        val speed = sqrt(vx * vx + vy * vy)
        val contract = speed > MIN_CONTRACTION_SPEED
        val k = if (contract) contractionK(gammaFromV(vx, vy, settings.c)) else 0f
        val isPlayer = playerMapper.has(entity)
        for (i in 0 until SPRITE_VERTEX_FLOATS step SPRITE_VERTEX_STRIDE) {
            var px = out[i]
            var py = out[i + 1]
            if (contract) {
                contractOffset(px - x, py - y, vx / speed, vy / speed, k, contracted)
                px = x + contracted.x
                py = y + contracted.y
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
