package com.github.pepek42.asteroids.system

import com.badlogic.ashley.systems.IntervalSystem
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.physics.box2d.World
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.SpriteComponent
import ktx.ashley.allOf
import ktx.ashley.get


class PhysicsSystem(
    private val world: World,
) : IntervalSystem(PHYSICS_UPDATE_INTERVAL) {

    override fun updateInterval() {
        world.step(PHYSICS_UPDATE_INTERVAL, 6, 2)
        updateRenderData()
    }

    private fun updateRenderData() {
        val entities = engine.getEntitiesFor(allOf(SpriteComponent::class, BodyComponent::class).get())
        for (entity in entities) {
            val body = entity[BodyComponent.mapper]!!.body
            val sprite = entity[SpriteComponent.mapper]!!.sprite
            sprite.setPosition(body.position.x, body.position.y)
            sprite.rotation = body.angle * MathUtils.radiansToDegrees
        }
    }

    companion object {
        private const val PHYSICS_UPDATE_INTERVAL = 1f / 45
    }

}
