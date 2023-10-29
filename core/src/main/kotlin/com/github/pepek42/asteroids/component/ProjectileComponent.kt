package com.github.pepek42.asteroids.component

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import ktx.ashley.mapperFor
import ktx.ashley.propertyFor

class ProjectileComponent : Component {
    var projectile: Projectile = LaserProjectile()
}

sealed class Projectile

val projectileMapper = mapperFor<ProjectileComponent>()
val Entity.projectileCmp by propertyFor(projectileMapper)

class LaserProjectile : Projectile() {
    var damage: Float = 0f
}
