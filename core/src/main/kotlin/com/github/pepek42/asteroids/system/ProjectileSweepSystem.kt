package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.physics.box2d.Fixture
import com.badlogic.gdx.physics.box2d.RayCastCallback
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.Array
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.ProjectileComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.baseInfoCmp
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.projectileCmp
import com.github.pepek42.asteroids.component.transformCmp
import com.github.pepek42.asteroids.faction.FactionEnum
import com.github.pepek42.asteroids.faction.collidesWith
import com.github.pepek42.asteroids.physics.PhysicsStepListener
import ktx.ashley.allOf

/**
 * Sensor projectiles don't use continuous collision detection, so fast ones can skip small targets. After every
 * physics step this casts a ray from the projectile's start-of-tick position to its current position and applies
 * the hit to the nearest valid target. Valid while `PhysicsSystem.PHYSICS_SUBSTEPS == 1`, because the start position
 * ([TransformComponent.prevPosition]) is stored once per tick. Known limitation: a projectile reflected by the
 * boundary backstop gets a chord ray from its old position to the post-reflection position (low risk).
 */
class ProjectileSweepSystem(private val world: World) : EntitySystem(), PhysicsStepListener {
    private var entities: ImmutableArray<Entity> = ImmutableArray(Array())
    private val callback = SweepCallback()
    private val start = Vector2()
    private val end = Vector2()

    override fun addedToEngine(engine: Engine) {
        entities = engine.getEntitiesFor(
            allOf(ProjectileComponent::class, BodyComponent::class, TransformComponent::class).get()
        )
    }

    override fun update(deltaTime: Float) = Unit

    override fun beforeStep(dt: Float) = Unit

    override fun afterStep(dt: Float) {
        val engine = engine ?: return
        for (i in 0 until entities.size()) {
            val projectile = entities[i]
            if (projectile.projectileCmp.spent) continue
            val body = projectile.bodyCmp.body
            start.set(projectile.transformCmp.prevPosition)
            end.set(body.position)
            if (start.dst2(end) <= MIN_RAY_LENGTH_SQ) continue
            callback.reset(body, projectile.baseInfoCmp.faction)
            world.rayCast(callback, start, end)
            val target = callback.target ?: continue
            applyProjectileHit(engine, projectile, target)
        }
        callback.reset(null, null)
    }

    private class SweepCallback : RayCastCallback {
        private var ownBody: Body? = null
        private var faction: FactionEnum? = null
        private var bestFraction = Float.MAX_VALUE
        var target: Entity? = null
            private set

        fun reset(ownBody: Body?, faction: FactionEnum?) {
            this.ownBody = ownBody
            this.faction = faction
            bestFraction = Float.MAX_VALUE
            target = null
        }

        override fun reportRayFixture(fixture: Fixture, point: Vector2, normal: Vector2, fraction: Float): Float {
            if (fixture.isSensor || fixture.body === ownBody) return IGNORE
            val entity = fixture.body.userData as? Entity ?: return IGNORE
            val targetFaction = entity.baseInfoCmp.faction
            if (faction?.collidesWith(targetFaction) != true) return IGNORE
            if (fraction < bestFraction) {
                bestFraction = fraction
                target = entity
            }
            return fraction
        }
    }

    companion object {
        private const val IGNORE = -1f
        private const val MIN_RAY_LENGTH_SQ = 1e-10f
    }
}
