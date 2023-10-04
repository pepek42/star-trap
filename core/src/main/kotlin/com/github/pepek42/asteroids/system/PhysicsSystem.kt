package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.gdx.physics.box2d.World
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.bodyMapper
import com.github.pepek42.asteroids.component.transformMapper
import ktx.ashley.allOf
import ktx.ashley.get
import kotlin.math.min


class PhysicsSystem(
    private val world: World,
    engine: Engine,
) : EntitySystem() {
    private val entities = engine.getEntitiesFor(allOf(BodyComponent::class, TransformComponent::class).get())
    private var accumulator = 0f

    // TODO handle if network multiplayer
    private val maxTimeToProcess = 5 * PHYSICS_UPDATE_INTERVAL

    override fun update(deltaTime: Float) {
        accumulator += min(maxTimeToProcess, deltaTime)
        while (accumulator >= PHYSICS_UPDATE_INTERVAL) {

            entities.forEach { entity ->
                entity[bodyMapper]!!.run {
                    val transform: TransformComponent = entity[transformMapper]!!
                    transform.prevPosition.set(
                        // TODO Can I take size form sprite?
                        body.position.x,// - transform.size.x * 0.5f,
                        body.position.y,// - transform.size.y * 0.5f
                    )
                }
            }

            world.step(PHYSICS_UPDATE_INTERVAL, 6, 2)
            accumulator -= PHYSICS_UPDATE_INTERVAL
        }
        val alpha = accumulator / PHYSICS_UPDATE_INTERVAL

        for (entity in entities) {
            val body = entity[bodyMapper]!!.body
            val transform = entity[transformMapper]!!
            transform.position.set(
                body.position.x,// - transform.width / 2,
                body.position.y,// - transform.height / 2
            )

            transform.interpolatedPosition.set(transform.prevPosition.lerp(transform.position, alpha))
            // TODO rotation
        }

    }

    companion object {
        private const val PHYSICS_UPDATE_INTERVAL = 1f / 45
    }

}

