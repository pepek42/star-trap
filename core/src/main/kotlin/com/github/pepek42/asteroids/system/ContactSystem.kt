package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.gdx.physics.box2d.Contact
import com.badlogic.gdx.physics.box2d.ContactImpulse
import com.badlogic.gdx.physics.box2d.ContactListener
import com.badlogic.gdx.physics.box2d.Manifold
import com.badlogic.gdx.physics.box2d.World
import com.github.pepek42.asteroids.component.EntityType
import com.github.pepek42.asteroids.component.LaserProjectile
import com.github.pepek42.asteroids.component.RemoveComponent
import com.github.pepek42.asteroids.component.removeMapper
import com.github.pepek42.asteroids.component.baseInfoCmp
import com.github.pepek42.asteroids.component.healthCmp
import com.github.pepek42.asteroids.component.healthMapper
import com.github.pepek42.asteroids.component.projectileCmp
import ktx.ashley.has
import ktx.log.logger

/**
 * Applies a projectile's damage to [target] (if it has health), then marks the projectile as spent and tags it
 * with [RemoveComponent]. Does nothing if the projectile was already spent, so a projectile hits at most once.
 */
fun applyProjectileHit(engine: Engine, projectile: Entity, target: Entity) {
    val projectileCmp = projectile.projectileCmp
    if (projectileCmp.spent) return
    if (target.has(healthMapper)) {
        val targetHealth = target.healthCmp
        val damage = when (val laser = projectileCmp.projectile) {
            is LaserProjectile -> laser.damage
        }
        targetHealth.health -= damage
    }
    projectileCmp.spent = true
    if (!projectile.has(removeMapper)) {
        projectile.add(engine.createComponent(RemoveComponent::class.java))
    }
}

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
            applyProjectileHit(engine, entityOne, entityTwo)
        } else if (entityTwo.baseInfoCmp.entityType == EntityType.PROJECTILE) {
            applyProjectileHit(engine, entityTwo, entityOne)
        }
    }

    override fun endContact(contact: Contact) = Unit

    override fun preSolve(contact: Contact, oldManifold: Manifold) = Unit

    override fun postSolve(contact: Contact, impulse: ContactImpulse) = Unit

    companion object {
        private val logger = logger<ContactSystem>()
    }
}
