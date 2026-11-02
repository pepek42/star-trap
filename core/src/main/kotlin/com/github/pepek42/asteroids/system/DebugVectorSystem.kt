package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Disposable
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.playerCmpOptional
import com.github.pepek42.asteroids.component.relativisticCmp
import com.github.pepek42.asteroids.component.transformCmp
import com.github.pepek42.asteroids.debug.DebugSettings
import com.github.pepek42.asteroids.physics.RelativitySettings
import ktx.ashley.allOf
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Floats written by [arrowSegments]: three line segments (shaft and two head lines) of 4 floats each. */
const val ARROW_FLOATS = 12

/**
 * Fills [out] with an arrow from ([ox], [oy]) along ([dx], [dy]) * [scale]: shaft, then the two head lines of length
 * [headLen] at +-[headAngleRad] from the reversed direction. Each segment is x1, y1, x2, y2. A zero-length vector
 * fills zeros (nothing visible, no NaN).
 */
fun arrowSegments(
    ox: Float, oy: Float, dx: Float, dy: Float, scale: Float, headLen: Float, headAngleRad: Float, out: FloatArray,
) {
    val tx = ox + dx * scale
    val ty = oy + dy * scale
    val len = sqrt((tx - ox) * (tx - ox) + (ty - oy) * (ty - oy))
    if (len < 1e-6f || len.isNaN()) {
        out.fill(0f, 0, ARROW_FLOATS)
        return
    }
    // Unit vector pointing back from the tip to the origin.
    val bx = (ox - tx) / len
    val by = (oy - ty) / len
    val ca = cos(headAngleRad)
    val sa = sin(headAngleRad)
    out[0] = ox; out[1] = oy; out[2] = tx; out[3] = ty
    out[4] = tx; out[5] = ty
    out[6] = tx + headLen * (bx * ca - by * sa)
    out[7] = ty + headLen * (bx * sa + by * ca)
    out[8] = tx; out[9] = ty
    out[10] = tx + headLen * (bx * ca + by * sa)
    out[11] = ty + headLen * (-bx * sa + by * ca)
}

/** On-screen radius (world units) of the c ring. Velocity scale = this / c, so `v` never leaves the ring. */
const val VEC_C_RING_RADIUS = 5f

/** On-screen length (world units) of an acceleration arrow at full engine proper acceleration. */
const val VEC_FULL_ACCEL_LENGTH = 3f

const val VEC_HEAD_LENGTH = 0.4f
const val VEC_HEAD_ANGLE_DEG = 25f

/** Seconds: world units per (u/s) of velocity, so that `c` maps to [VEC_C_RING_RADIUS]. */
fun velocityVectorScale(c: Float) = VEC_C_RING_RADIUS / c

/** Seconds squared: world units per (u/s^2), so that the full engine acceleration maps to [VEC_FULL_ACCEL_LENGTH]. */
fun accelVectorScale(engineProperAccel: Float) = VEC_FULL_ACCEL_LENGTH / engineProperAccel

/** Draws v (green), u (cyan) and the last applied acceleration (orange) of relativistic bodies, plus the c ring. */
class DebugVectorSystem(
    private val camera: Camera,
    private val relativitySettings: RelativitySettings,
    private val debugSettings: DebugSettings,
) : IteratingSystem(allOf(RelativisticComponent::class, BodyComponent::class, TransformComponent::class).get()),
    Disposable {
    private val shapeRenderer = ShapeRenderer()
    private val segments = FloatArray(ARROW_FLOATS)
    private val headAngleRad = VEC_HEAD_ANGLE_DEG * MathUtils.degreesToRadians
    private var velScale = 1f
    private var accScale = 1f

    override fun update(deltaTime: Float) {
        if (!debugSettings.showVectors) return
        velScale = velocityVectorScale(relativitySettings.c)
        accScale = accelVectorScale(relativitySettings.engineProperAccel)
        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeType.Line)
        super.update(deltaTime)
        shapeRenderer.end()
    }

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val pos = entity.transformCmp.interpolatedPosition
        val rel = entity.relativisticCmp
        val body = entity.bodyCmp.body
        val v = body.linearVelocity
        arrow(Color.GREEN, pos.x, pos.y, v.x, v.y, velScale)
        arrow(Color.CYAN, pos.x, pos.y, rel.u.x, rel.u.y, velScale)
        arrow(Color.ORANGE, pos.x, pos.y, rel.lastAccel.x, rel.lastAccel.y, accScale)
        if (entity.playerCmpOptional != null) {
            shapeRenderer.color = Color.GRAY
            shapeRenderer.circle(pos.x, pos.y, VEC_C_RING_RADIUS, RING_SEGMENTS)
        }
    }

    private fun arrow(color: Color, ox: Float, oy: Float, dx: Float, dy: Float, scale: Float) {
        arrowSegments(ox, oy, dx, dy, scale, VEC_HEAD_LENGTH, headAngleRad, segments)
        shapeRenderer.color = color
        for (i in 0 until ARROW_FLOATS step 4) {
            shapeRenderer.line(segments[i], segments[i + 1], segments[i + 2], segments[i + 3])
        }
    }

    override fun dispose() = shapeRenderer.dispose()

    private companion object {
        const val RING_SEGMENTS = 64
    }
}
