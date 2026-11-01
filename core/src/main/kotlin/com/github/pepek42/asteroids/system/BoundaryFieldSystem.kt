package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.utils.Array
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.baseInfoMapper
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.computeBoundingRadius
import com.github.pepek42.asteroids.debug.LoggingUtils.Companion.defaultLoggingUtils
import com.github.pepek42.asteroids.physics.FIELD_BACKSTOP_RESTITUTION
import com.github.pepek42.asteroids.physics.FIELD_EXPONENT
import com.github.pepek42.asteroids.physics.PhysicsStepListener
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.boundaryAcceleration
import com.github.pepek42.asteroids.physics.boundaryDepthQ
import com.github.pepek42.asteroids.provider.MapProvider
import com.github.pepek42.asteroids.render.ShimmerImpacts
import kotlin.math.pow
import ktx.ashley.allOf
import ktx.log.logger

/**
 * Applies the repulsive arena boundary field to every non-static body once per physics step
 * ([beforeStep]) and enforces a hard backstop after the step ([afterStep]).
 */
class BoundaryFieldSystem(
    private val mapProvider: MapProvider,
    private val impacts: ShimmerImpacts = ShimmerImpacts(),
    private val settings: RelativitySettings = RelativitySettings(),
) : EntitySystem(), PhysicsStepListener {
    private var entities: ImmutableArray<Entity> = ImmutableArray(Array())
    private val accel = Vector2()
    private val velocity = Vector2()

    override fun addedToEngine(engine: Engine) {
        entities = engine.getEntitiesFor(allOf(BodyComponent::class, TransformComponent::class).get())
    }

    override fun update(deltaTime: Float) = Unit

    override fun beforeStep(dt: Float) {
        // Field off: the arena is a hard-walled box, only the backstop in afterStep acts.
        if (!settings.fieldEnabled) return
        val params = settings.fieldParams
        for (i in 0 until entities.size()) {
            val bodyCmp = entities[i].bodyCmp
            val body = bodyCmp.body
            if (body.type == BodyDef.BodyType.StaticBody) continue
            if (bodyCmp.boundingRadius < 0f) bodyCmp.boundingRadius = computeBoundingRadius(body)
            val pos = body.position
            val vel = body.linearVelocity
            boundaryAcceleration(
                pos.x, pos.y, vel.x, vel.y, bodyCmp.boundingRadius,
                mapProvider.mapWidth, mapProvider.mapHeight, params, accel,
            )
            if (accel.x == 0f && accel.y == 0f) continue
            if (body.type == BodyDef.BodyType.DynamicBody) {
                val mass = body.mass
                body.applyForceToCenter(accel.x * mass, accel.y * mass, true)
            } else {
                velocity.set(vel).mulAdd(accel, dt)
                body.setLinearVelocity(velocity)
            }
        }
    }

    override fun afterStep(dt: Float) {
        impacts.time += dt
        for (i in 0 until entities.size()) {
            val bodyCmp = entities[i].bodyCmp
            val body = bodyCmp.body
            if (body.type == BodyDef.BodyType.StaticBody) continue
            if (bodyCmp.boundingRadius < 0f) bodyCmp.boundingRadius = computeBoundingRadius(body)
            val id = entityId(entities[i])
            emitFieldImpact(id, body, bodyCmp.boundingRadius)
            enforceBackstop(id, body, bodyCmp.boundingRadius)
        }
        impacts.prune()
    }

    /** BaseInfoComponent id, or the entity identity hash for entities without one (e.g. in tests). */
    private fun entityId(entity: Entity): Int = baseInfoMapper.get(entity)?.id ?: System.identityHashCode(entity)

    private fun emitFieldImpact(id: Int, body: Body, rBody: Float) {
        val w = mapProvider.mapWidth
        val h = mapProvider.mapHeight
        val x = body.position.x
        val y = body.position.y
        val q = boundaryDepthQ(x, y, rBody, w, h, settings.fieldParams)
        if (q <= SHIMMER_WEAK_MIN_Q) return
        if (!shouldEmitFieldImpact(q, inwardNormalSpeed(body, w, h))) return
        emitAtNearestWall(id, x, y, w, h, q.pow(FIELD_EXPONENT), false)
    }

    /** Velocity component pointing into the nearest wall (positive = moving towards it). */
    private fun inwardNormalSpeed(body: Body, w: Float, h: Float): Float {
        val x = body.position.x
        val y = body.position.y
        val vel = body.linearVelocity
        val dLeft = x
        val dRight = w - x
        val dBottom = y
        val dTop = h - y
        return when (minOf(minOf(dLeft, dRight), minOf(dBottom, dTop))) {
            dLeft -> -vel.x
            dRight -> vel.x
            dBottom -> -vel.y
            else -> vel.y
        }
    }

    private fun emitAtNearestWall(id: Int, x: Float, y: Float, w: Float, h: Float, strength: Float, force: Boolean) {
        val dLeft = x
        val dRight = w - x
        val dBottom = y
        val dTop = h - y
        val dMin = minOf(minOf(dLeft, dRight), minOf(dBottom, dTop))
        when (dMin) {
            dLeft -> impacts.emit(id, 0f, y, strength, force)
            dRight -> impacts.emit(id, w, y, strength, force)
            dBottom -> impacts.emit(id, x, 0f, strength, force)
            else -> impacts.emit(id, x, h, strength, force)
        }
    }

    private fun enforceBackstop(id: Int, body: Body, rBody: Float) {
        val r = if (settings.fieldParams.useBodyRadius) rBody else 0f
        val w = mapProvider.mapWidth
        val h = mapProvider.mapHeight
        var x = body.position.x
        var y = body.position.y
        var vx = body.linearVelocity.x
        var vy = body.linearVelocity.y
        var hit = false
        if (x - r <= 0f) {
            x = r + BACKSTOP_GAP; hit = true
            if (vx < 0f) vx = -FIELD_BACKSTOP_RESTITUTION * vx
        } else if (w - x - r <= 0f) {
            x = w - r - BACKSTOP_GAP; hit = true
            if (vx > 0f) vx = -FIELD_BACKSTOP_RESTITUTION * vx
        }
        if (y - r <= 0f) {
            y = r + BACKSTOP_GAP; hit = true
            if (vy < 0f) vy = -FIELD_BACKSTOP_RESTITUTION * vy
        } else if (h - y - r <= 0f) {
            y = h - r - BACKSTOP_GAP; hit = true
            if (vy > 0f) vy = -FIELD_BACKSTOP_RESTITUTION * vy
        }
        if (!hit) return
        body.setTransform(x, y, body.angle)
        body.setLinearVelocity(vx, vy)
        emitAtNearestWall(id, x, y, w, h, BACKSTOP_STRENGTH, true)
        defaultLoggingUtils.tryLogging { logger.debug { "Boundary backstop hit at ($x, $y)" } }
    }

    companion object {
        private val logger = logger<BoundaryFieldSystem>()
        private const val BACKSTOP_GAP = 0.01f
        private const val SHIMMER_MIN_Q = 0.1f
        const val SHIMMER_WEAK_MIN_Q = 0.05f

        /** Deep bodies always emit; shallow ones (q in 0.05..0.1) only when moving into the wall. */
        fun shouldEmitFieldImpact(q: Float, inwardSpeed: Float): Boolean =
            q > SHIMMER_MIN_Q || (q > SHIMMER_WEAK_MIN_Q && inwardSpeed > 0f)
        private const val BACKSTOP_STRENGTH = 1.5f
    }
}
