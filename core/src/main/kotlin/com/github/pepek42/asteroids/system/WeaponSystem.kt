package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.WeaponComponent
import com.github.pepek42.asteroids.component.weaponCmp
import com.github.pepek42.asteroids.provider.WeaponProjectileProvider
import ktx.ashley.allOf

class WeaponSystem(
    private val weaponProjectileProvider: WeaponProjectileProvider,
) : IteratingSystem(allOf(WeaponComponent::class, BodyComponent::class).get()) {
    override fun processEntity(entity: Entity, deltaTime: Float) {
        val weaponCmp = entity.weaponCmp
        weaponCmp.cooldown -= deltaTime

        if (entity.weaponCmp.doAttack && weaponCmp.cooldown <= 0) {
            fire(weaponCmp, entity)
        }
    }

    private fun fire(weaponCmp: WeaponComponent, entity: Entity) {
        weaponCmp.cooldown = 1 / weaponCmp.shotsPerSecond
        weaponProjectileProvider.spawnProjectile(weaponCmp, entity)
    }

}
