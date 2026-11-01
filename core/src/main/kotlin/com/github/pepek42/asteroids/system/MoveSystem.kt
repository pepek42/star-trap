package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.physics.box2d.Body
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.MoveComponent
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.moveCmp
import com.github.pepek42.asteroids.physics.PhysicsStepListener
import ktx.ashley.allOf
import ktx.math.times
import ktx.math.vec2

class MoveSystem : EntitySystem(), PhysicsStepListener {
    private var entities: ImmutableArray<Entity>? = null

    override fun addedToEngine(engine: Engine) {
        super.addedToEngine(engine)
        entities = engine.getEntitiesFor(allOf(MoveComponent::class, BodyComponent::class).get())
    }

    override fun update(deltaTime: Float) {
        // Thrust is applied per physics step in beforeStep.
    }

    override fun beforeStep(dt: Float) {
        val list = entities ?: return
        for (entity in list) {
            applyMainThrusters(entity.moveCmp, entity.bodyCmp.body)
        }
    }

    override fun afterStep(dt: Float) {
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
        const val MAIN_ENGINE_THRUST = 300f
    }
}
