package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.gdx.physics.box2d.Contact
import com.badlogic.gdx.physics.box2d.ContactImpulse
import com.badlogic.gdx.physics.box2d.ContactListener
import com.badlogic.gdx.physics.box2d.Manifold
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.physics.box2d.World
import com.github.pepek42.asteroids.component.EntityType
import com.github.pepek42.asteroids.component.LaserProjectile
import com.github.pepek42.asteroids.component.RemoveComponent
import com.github.pepek42.asteroids.component.removeMapper
import com.github.pepek42.asteroids.component.baseInfoCmp
import com.github.pepek42.asteroids.component.healthCmp
import com.github.pepek42.asteroids.component.healthMapper
import com.github.pepek42.asteroids.component.bodyCmpOptional
import com.github.pepek42.asteroids.component.photonCmpOptional
import com.github.pepek42.asteroids.component.playerCmpOptional
import com.github.pepek42.asteroids.component.projectileCmp
import com.github.pepek42.asteroids.component.relativisticCmpOptional
import com.github.pepek42.asteroids.physics.DAMAGE_PER_REST_ENERGY
import com.github.pepek42.asteroids.physics.PLAYER_RAM_DAMAGE_FACTOR
import com.github.pepek42.asteroids.physics.PHOTON_DAMAGE_D_MAX
import com.github.pepek42.asteroids.physics.PHOTON_DAMAGE_D_MIN
import com.github.pepek42.asteroids.physics.RAM_GAMMA_THRESHOLD
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.gammaRel
import com.github.pepek42.asteroids.physics.photonEnergyFactor
import com.github.pepek42.asteroids.physics.PhysicsStepListener
import com.github.pepek42.asteroids.component.AsteroidComponent
import com.github.pepek42.asteroids.component.asteroidMapper
import ktx.ashley.allOf
import ktx.ashley.has
import ktx.log.logger

/**
 * Applies a projectile's damage to [target] (if it has health), then marks the projectile as spent and tags it
 * with [RemoveComponent]. Does nothing if the projectile was already spent, so a projectile hits at most once.
 */
fun applyProjectileHit(
    engine: Engine,
    projectile: Entity,
    target: Entity,
    settings: RelativitySettings = RelativitySettings(),
) {
    val projectileCmp = projectile.projectileCmp
    if (projectileCmp.spent) return
    if (target.has(healthMapper)) {
        val targetHealth = target.healthCmp
        val damage = when (val laser = projectileCmp.projectile) {
            is LaserProjectile -> laser.damage * photonDamageFactor(projectile, target, settings)
        }
        targetHealth.health -= damage
    }
    projectileCmp.spent = true
    if (!projectile.has(removeMapper)) {
        projectile.add(engine.createComponent(RemoveComponent::class.java))
    }
}

/**
 * Doppler multiplier on a photon's damage (ST-081, research E12): [photonEnergyFactor] from the shooter's emission β
 * ([ProjectileComponent.shooterBetaX]) and the target's pre-step velocity over `settings.c`, clamped to
 * [PHOTON_DAMAGE_D_MIN]..[PHOTON_DAMAGE_D_MAX]. 1 for non-photon projectiles or when disabled.
 */
private fun photonDamageFactor(projectile: Entity, target: Entity, settings: RelativitySettings): Float {
    if (!settings.photonDopplerDamage || !settings.relativityEnabled) return 1f
    val photon = projectile.photonCmpOptional ?: return 1f
    val projectileCmp = projectile.projectileCmp
    val targetV = target.relativisticCmpOptional?.vSet ?: target.bodyCmpOptional?.body?.linearVelocity ?: return 1f
    val factor = photonEnergyFactor(
        projectileCmp.shooterBetaX, projectileCmp.shooterBetaY,
        targetV.x / settings.c, targetV.y / settings.c,
        photon.dir.x, photon.dir.y,
    )
    return factor.coerceIn(PHOTON_DAMAGE_D_MIN, PHOTON_DAMAGE_D_MAX)
}

/**
 * Impact damage of a collision between two bodies with rest masses [restMassA]/[restMassB] and arena velocities
 * [va]/[vb] (ST-048, research E7). Kinetic energy in the centre-of-momentum frame is `K = (γrel − 1)·μ·c²`, with
 * `γrel` the exact relative Lorentz factor and `μ = mA·mB/(mA + mB)` the reduced rest mass (a static or massless
 * partner leaves `μ` = the other mass; both massless gives 0). Damage is `K / c² · DAMAGE_PER_REST_ENERGY`, so `c`
 * cancels and the damage does not change when c does. Below [RAM_GAMMA_THRESHOLD] (γrel − 1) it is 0.
 */
fun ramDamage(va: Vector2, vb: Vector2, restMassA: Float, restMassB: Float, c: Float): Float {
    val excess = gammaRel(va, vb, c) - 1f
    if (excess < RAM_GAMMA_THRESHOLD) return 0f
    val mu = when {
        restMassA <= 0f -> maxOf(restMassB, 0f)
        restMassB <= 0f -> restMassA
        else -> restMassA * restMassB / (restMassA + restMassB)
    }
    return excess * mu * DAMAGE_PER_REST_ENERGY
}

class ContactSystem(
    world: World,
    private val settings: RelativitySettings = RelativitySettings(),
) : EntitySystem(), ContactListener, PhysicsStepListener {
    private val asteroids = allOf(AsteroidComponent::class).get()

    // Scratch vectors: beginContact runs inside world.step and must not allocate (web perf).
    private val velocityA = Vector2()
    private val velocityB = Vector2()

    init {
        world.setContactListener(this)
    }

    override fun beginContact(contact: Contact) {
        logger.debug { "beginContact ${contact.fixtureA.body.userData}, ${contact.fixtureB.body.userData}" }
        val entityOne = contact.fixtureA.body.userData as Entity
        val entityTwo = contact.fixtureB.body.userData as Entity
        if (entityOne.baseInfoCmp.entityType == EntityType.PROJECTILE) {
            applyProjectileHit(engine, entityOne, entityTwo, settings)
        } else if (entityTwo.baseInfoCmp.entityType == EntityType.PROJECTILE) {
            applyProjectileHit(engine, entityTwo, entityOne, settings)
        } else if (!contact.fixtureA.isSensor && !contact.fixtureB.isSensor) {
            applyRamDamage(entityOne, contact.fixtureA.body, entityTwo, contact.fixtureB.body)
        }
    }

    /**
     * Damages each of the two colliding bodies that has health by [ramDamage] (the player's share is scaled by
     * [PLAYER_RAM_DAMAGE_FACTOR]). Velocities are the ones set before the step ([vSet]), not the post-solve ones.
     */
    private fun applyRamDamage(entityA: Entity, bodyA: Body, entityB: Entity, bodyB: Body) {
        val healthA = healthMapper.get(entityA)
        val healthB = healthMapper.get(entityB)
        if (healthA == null && healthB == null) return
        if (isRamImmune(entityA) || isRamImmune(entityB)) return
        val relA = entityA.relativisticCmpOptional
        val relB = entityB.relativisticCmpOptional
        velocityA.set(relA?.vSet ?: bodyA.linearVelocity)
        velocityB.set(relB?.vSet ?: bodyB.linearVelocity)
        val massA = relA?.restMass?.takeIf { it > 0f } ?: bodyA.mass
        val massB = relB?.restMass?.takeIf { it > 0f } ?: bodyB.mass
        val damage = ramDamage(velocityA, velocityB, massA, massB, settings.c)
        if (damage <= 0f) return
        if (healthA != null) healthA.health -= damage * ramFactor(entityA)
        if (healthB != null) healthB.health -= damage * ramFactor(entityB)
    }

    private fun isRamImmune(entity: Entity) = (asteroidMapper.get(entity)?.ramImmunity ?: 0f) > 0f

    override fun beforeStep(dt: Float) = Unit

    /** Counts down fragment ram immunity ([FRAGMENT_RAM_IMMUNITY]) in physics time. */
    override fun afterStep(dt: Float) {
        val entities = engine?.getEntitiesFor(asteroids) ?: return
        for (i in 0 until entities.size()) {
            val asteroid = asteroidMapper.get(entities[i])
            if (asteroid.ramImmunity > 0f) asteroid.ramImmunity = maxOf(0f, asteroid.ramImmunity - dt)
        }
    }

    private fun ramFactor(entity: Entity) = if (entity.playerCmpOptional != null) PLAYER_RAM_DAMAGE_FACTOR else 1f

    override fun endContact(contact: Contact) = Unit

    override fun preSolve(contact: Contact, oldManifold: Manifold) = Unit

    override fun postSolve(contact: Contact, impulse: ContactImpulse) = Unit

    companion object {
        private val logger = logger<ContactSystem>()
    }
}
