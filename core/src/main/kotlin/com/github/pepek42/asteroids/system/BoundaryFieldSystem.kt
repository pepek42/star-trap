package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.physics.box2d.BodyDef
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.computeBoundingRadius
import com.github.pepek42.asteroids.debug.LoggingUtils.Companion.defaultLoggingUtils
import com.github.pepek42.asteroids.physics.BoundaryFieldParams
import com.github.pepek42.asteroids.physics.FIELD_BACKSTOP_RESTITUTION
import com.github.pepek42.asteroids.physics.PhysicsStepListener
import com.github.pepek42.asteroids.physics.boundaryAcceleration
import com.github.pepek42.asteroids.provider.MapProvider
import ktx.ashley.allOf
import ktx.log.logger

/**
 * Applies the repulsive arena boundary field to every non-static body once per physics step
 * ([beforeStep]) and enforces a hard backstop after the step ([afterStep]).
 */
class BoundaryFieldSystem(
    private val mapProvider: MapProvider,
    private val params: BoundaryFieldParams = BoundaryFieldParams(),
) : EntitySystem(), PhysicsStepListener {
    private var entities: ImmutableArray<Entity> = ImmutableArray(com.badlogic.gdx.utils.Array())
    private val accel = Vector2()
    private val velocity = Vector2()

    override fun addedToEngine(engine: Engine) {
        entities = engine.getEntitiesFor(allOf(BodyComponent::class, TransformComponent::class).get())
    }

    override fun update(deltaTime: Float) = Unit

    override fun beforeStep(dt: Float) {
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
        for (i in 0 until entities.size()) {
            val bodyCmp = entities[i].bodyCmp
            val body = bodyCmp.body
            if (body.type == BodyDef.BodyType.StaticBody) continue
            if (bodyCmp.boundingRadius < 0f) bodyCmp.boundingRadius = computeBoundingRadius(body)
            enforceBackstop(body, bodyCmp.boundingRadius)
        }
    }

    private fun enforceBackstop(body: Body, rBody: Float) {
        val r = if (params.useBodyRadius) rBody else 0f
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
        defaultLoggingUtils.tryLogging { logger.debug { "Boundary backstop hit at ($x, $y)" } }
    }

    companion object {
        private val logger = logger<BoundaryFieldSystem>()
        private const val BACKSTOP_GAP = 0.01f
    }
}
