package com.github.pepek42.asteroids.component

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.utils.Pool
import ktx.ashley.mapperFor
import ktx.ashley.optionalPropertyFor
import ktx.ashley.propertyFor

class ProjectileComponent : Component, Pool.Poolable {
    var projectile: Projectile = LaserProjectile()

    /** True once the projectile has dealt its damage; it must not hit anything else. */
    var spent = false

    /** True once the shimmer ripple for leaving the arena has been emitted. */
    var exitRippleEmitted = false

    override fun reset() {
        projectile = LaserProjectile()
        spent = false
        exitRippleEmitted = false
    }
}

sealed class Projectile

val projectileMapper = mapperFor<ProjectileComponent>()
val Entity.projectileCmp by propertyFor(projectileMapper)
val Entity.projectileCmpOptional by optionalPropertyFor(projectileMapper)

class LaserProjectile : Projectile() {
    var damage: Float = 0f
}
