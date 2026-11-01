package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.physics.box2d.Body
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.MoveComponent
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.moveCmp
import ktx.ashley.allOf
import ktx.math.times
import ktx.math.vec2

class MoveSystem : IteratingSystem(allOf(MoveComponent::class, BodyComponent::class).get()) {
    override fun processEntity(entity: Entity, deltaTime: Float) {
        val moveComponent = entity.moveCmp
        val bodyCmp = entity.bodyCmp

        if (!bodyCmp.moveForcesApplied) {
            applyMainThrusters(moveComponent, bodyCmp.body)
            bodyCmp.moveForcesApplied = true
        }
    }

    private fun applyMainThrusters(
        moveComponent: MoveComponent,
        body: Body
    ) {
        if (moveComponent.thrusters > 0) {
            val angle = body.angle
            val mainThrusters = vec2(MAIN_ENGINE_THRUST, 0f).rotateRad(angle) * moveComponent.thrusters
            body.applyForceToCenter(mainThrusters, true)
        }
    }

    companion object {
        // TODO separate components
        private const val MAIN_ENGINE_THRUST = 300f
    }
}
