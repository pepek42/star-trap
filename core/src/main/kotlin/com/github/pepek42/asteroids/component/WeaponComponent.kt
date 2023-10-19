package com.github.pepek42.asteroids.component

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Pool
import com.github.pepek42.asteroids.weapon.WeaponType
import ktx.ashley.mapperFor
import ktx.ashley.optionalPropertyFor
import ktx.ashley.propertyFor
import ktx.math.vec2

class WeaponComponent(
    var doAttack: Boolean = false,
    /**
     * Position in entity local coordinate system (Box2D body)
     */
    var weaponPosition: Vector2 = vec2(0f, 0f),
    var weaponType: WeaponType = WeaponType.LASER,
    var ready: Boolean = true,
    var rof: Float = 0f,
    var cooldown: Float = 0f,
    var bulletSpeed: Float = 0f,
    var bulletDamage: Float = 0f,
    var bulletRadius: Float = 0f,
) : Component, Pool.Poolable {
    override fun reset() {
        doAttack = false
        weaponPosition.x = 0f
        weaponPosition.y = 0f
        weaponType = WeaponType.LASER
        ready = true
        rof = 0f
        cooldown = 0f
        bulletSpeed = 0f
        bulletDamage = 0f
        bulletRadius = 0f
    }
}

val weaponMapper = mapperFor<WeaponComponent>()
val Entity.weaponCmp by propertyFor(weaponMapper)
val Entity.weaponCmpOptional by optionalPropertyFor(weaponMapper)
