package com.github.pepek42.asteroids.render

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.utils.ImmutableArray
import com.github.pepek42.asteroids.component.GravitySourceKind
import com.github.pepek42.asteroids.component.bodyCmpOptional
import com.github.pepek42.asteroids.component.gravitySourceCmp
import com.github.pepek42.asteroids.component.transformCmp
import com.github.pepek42.asteroids.physics.BH_RS

/** Capacity of [LightSources]: the black holes of one level plus room for the star (ST-099). */
const val MAX_LIGHT_SOURCES = 8

/**
 * Position of an emitter at the retarded time [tRet] of the object it lights (pilot view, FAKE 1 of [LitSpriteRenderer]):
 * the emitter's position now ([eNow], one axis) taken back along its velocity [v] by `now - tRet`. Pure.
 */
fun emitterAtRetardedTime(eNow: Float, v: Float, now: Float, tRet: Float): Float = eNow - v * (now - tRet)

/**
 * The lights of one level (ST-097): one disk light per black hole, rebuilt every frame by [rebuild] into reused arrays.
 * Black holes emit nothing themselves; the light is the accretion disk (ST-087), so a hole with a smaller or larger `rs`
 * after a merge scales [LightSource.size] and [LightSource.range] with `rs / BH_RS`. ST-099 adds the star.
 *
 * [rebuild] stores the current (interpolated) emitter positions and velocities; [applyLag] moves every emitter back to
 * the time an object's light was emitted so each sprite can be lit as in its own retarded moment.
 */
class LightSources {
    val lights = Array(MAX_LIGHT_SOURCES) { LightSource() }
    var count = 0
        private set

    /** Ambient for this frame's lit sprites: [LIGHT_AMBIENT] with a directional light (the star), else [LIGHT_AMBIENT_NO_STAR]. */
    var ambient = LIGHT_AMBIENT_NO_STAR
        private set

    private val baseX = FloatArray(MAX_LIGHT_SOURCES)
    private val baseY = FloatArray(MAX_LIGHT_SOURCES)
    private val velX = FloatArray(MAX_LIGHT_SOURCES)
    private val velY = FloatArray(MAX_LIGHT_SOURCES)
    private val rgb = FloatArray(3)
    private var appliedLag = 0f

    /** Re-reads [holes] (entities with a gravity source and a transform): the black holes become disk lights. */
    fun rebuild(holes: ImmutableArray<Entity>) {
        count = 0
        for (i in 0 until holes.size()) {
            if (count == MAX_LIGHT_SOURCES) break
            val e = holes[i]
            val source = e.gravitySourceCmp
            if (source.kind != GravitySourceKind.BLACK_HOLE) continue
            val light = lights[count]
            configureDiskLight(light, rgb)
            val scale = source.rs / BH_RS
            light.size *= scale
            light.range *= scale
            val pos = e.transformCmp.interpolatedPosition
            baseX[count] = pos.x
            baseY[count] = pos.y
            val v = e.bodyCmpOptional?.body?.linearVelocity
            velX[count] = v?.x ?: 0f
            velY[count] = v?.y ?: 0f
            light.x = pos.x
            light.y = pos.y
            count++
        }
        appliedLag = 0f
        var directional = false
        for (i in 0 until count) if (lights[i].directional) directional = true
        ambient = if (directional) LIGHT_AMBIENT else LIGHT_AMBIENT_NO_STAR
    }

    /** Places every emitter where it was [lag] seconds ago (`now - tRet`); 0 is the current position. Allocation-free. */
    fun applyLag(lag: Float) {
        if (lag == appliedLag) return
        appliedLag = lag
        for (i in 0 until count) {
            lights[i].x = emitterAtRetardedTime(baseX[i], velX[i], lag, 0f)
            lights[i].y = emitterAtRetardedTime(baseY[i], velY[i], lag, 0f)
        }
    }
}
