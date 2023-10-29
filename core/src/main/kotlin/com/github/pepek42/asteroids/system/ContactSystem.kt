package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.gdx.physics.box2d.Contact
import com.badlogic.gdx.physics.box2d.ContactImpulse
import com.badlogic.gdx.physics.box2d.ContactListener
import com.badlogic.gdx.physics.box2d.Manifold
import com.badlogic.gdx.physics.box2d.World
import com.github.pepek42.asteroids.component.EntityType
import com.github.pepek42.asteroids.component.LaserProjectile
import com.github.pepek42.asteroids.component.baseInfoCmp
import com.github.pepek42.asteroids.component.healthCmp
import com.github.pepek42.asteroids.component.healthMapper
import com.github.pepek42.asteroids.component.projectileCmp
import ktx.ashley.has
import ktx.log.logger

class ContactSystem(
    world: World,
) : EntitySystem(), ContactListener {
    init {
        world.setContactListener(this)
    }

    override fun beginContact(contact: Contact) {
        logger.debug { "beginContact ${contact.fixtureA.body.userData}, ${contact.fixtureB.body.userData}" }
        val entityOne = contact.fixtureA.body.userData as Entity
        val entityTwo = contact.fixtureB.body.userData as Entity
        if (entityOne.baseInfoCmp.entityType == EntityType.PROJECTILE) {
            applyProjectileDamage(entityOne, entityTwo)
        } else if (entityTwo.baseInfoCmp.entityType == EntityType.PROJECTILE) {
            applyProjectileDamage(entityTwo, entityOne)
        }
    }

    private fun applyProjectileDamage(projectileEntity: Entity, targetEntity: Entity) {
        if (targetEntity.has(healthMapper)) {
            val targetHealth = targetEntity.healthCmp
            val damage = when (val projectile = projectileEntity.projectileCmp.projectile) {
                is LaserProjectile -> projectile.damage
            }
            logger.debug { "applyDamage $damage to ${targetEntity.baseInfoCmp} having $targetHealth" }
            targetHealth.health -= damage
        }
    }

    override fun endContact(contact: Contact) = Unit

    override fun preSolve(contact: Contact, oldManifold: Manifold) = Unit

    override fun postSolve(contact: Contact, impulse: ContactImpulse) = Unit

    companion object {
        private val logger = logger<ContactSystem>()
    }
}
