package com.github.pepek42.asteroids.provider

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.World
import com.github.pepek42.asteroids.component.BaseInfoComponent
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.EntityType
import com.github.pepek42.asteroids.component.HealthComponent
import com.github.pepek42.asteroids.component.LifetimeClock
import com.github.pepek42.asteroids.component.LifetimeComponent
import com.github.pepek42.asteroids.component.LockOnComponent
import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.component.SpriteComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.WeaponComponent
import com.github.pepek42.asteroids.component.baseInfoCmp
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.relativisticCmpOptional
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.uFromV
import com.github.pepek42.asteroids.weapon.LOCK_ON_BURN_SECONDS
import com.github.pepek42.asteroids.weapon.LOCK_ON_DAMAGE
import com.github.pepek42.asteroids.weapon.LOCK_ON_DENSITY
import com.github.pepek42.asteroids.weapon.LOCK_ON_HEALTH
import com.github.pepek42.asteroids.weapon.LOCK_ON_LENGTH
import com.github.pepek42.asteroids.weapon.LOCK_ON_LIFETIME
import com.github.pepek42.asteroids.weapon.LOCK_ON_SPRITE_LENGTH
import com.github.pepek42.asteroids.weapon.LOCK_ON_WIDTH
import ktx.ashley.entity
import ktx.ashley.with
import ktx.box2d.body
import ktx.box2d.box
import ktx.math.plus
import ktx.math.vec2

/**
 * Creates a homing lock-on shot (ST-164): a small dynamic box with its own relativistic engine, a [LockOnComponent] that
 * remembers the target, and a sprite that points along the lock-on shot's heading. The body starts with the shooter's proper
 * velocity `u`, so firing while flying fast does not teleport the lock-on shot to rest.
 */
class LockOnProvider(
    private val textureAtlas: TextureAtlas,
    private val engine: Engine,
    private val world: World,
    private val settings: RelativitySettings = RelativitySettings(),
) {
    /**
     * Launches a lock-on shot from [shooter] at [target] (null = no lock, the lock-on shot keeps its launch heading). [weaponCmp]
     * supplies the muzzle position and, when set, the launch heading `aimAngleRad`; otherwise the hull angle is used.
     */
    fun spawnLockOn(shooter: Entity, target: Entity?, weaponCmp: WeaponComponent): Entity {
        val shooterBody = shooter.bodyCmp.body
        val heading = weaponCmp.aimAngleRad ?: shooterBody.angle
        val dirX = MathUtils.cos(heading)
        val dirY = MathUtils.sin(heading)
        // The muzzle is a weapon-local point plus the lock-on shot's own length, so it starts clear of the hull.
        val position = shooterBody.position +
            Vector2(weaponCmp.weaponPosition).rotateRad(shooterBody.angle) +
            vec2(dirX, dirY).scl(LOCK_ON_LENGTH)
        val shooterV = shooter.relativisticCmpOptional?.vSet ?: shooterBody.linearVelocity
        val faction = shooter.baseInfoCmp.faction

        return engine.entity {
            with<BodyComponent> {
                body = world.body(BodyDef.BodyType.DynamicBody) {
                    this.angle = heading
                    this.position.set(position.x, position.y)
                    linearVelocity.set(shooterV)
                    // RelativisticDynamics rewrites the velocity every step; the body must not fall asleep.
                    allowSleep = false
                    bullet = true
                    box(width = LOCK_ON_LENGTH, height = LOCK_ON_WIDTH) {
                        density = LOCK_ON_DENSITY
                        filter.categoryBits = faction.ownCategoryBit
                        filter.maskBits = faction.collidesWithMask
                    }
                    userData = this@entity.entity
                }
            }
            with<TransformComponent> {
                prevRotationDeg = heading * MathUtils.radiansToDegrees
                prevPosition.set(position.x, position.y)
            }
            with<SpriteComponent> {
                val lockOnSprite = textureAtlas.createSprite("player_lock_on")
                // The headless balance runner hands out texture-less sprites, which are never drawn.
                val width = lockOnSprite.width.takeIf { it > 0f } ?: LOCK_ON_SPRITE_LENGTH
                val height = lockOnSprite.height.takeIf { it > 0f } ?: LOCK_ON_SPRITE_LENGTH
                val scale = LOCK_ON_SPRITE_LENGTH / width
                lockOnSprite.setSize(width * scale, height * scale)
                lockOnSprite.setOriginCenter()
                sprite = lockOnSprite
            }
            with<BaseInfoComponent> {
                this.faction = faction
                entityType = EntityType.LOCK_ON
            }
            with<RelativisticComponent> {
                val shooterRel = shooter.relativisticCmpOptional
                if (shooterRel != null) {
                    u.set(shooterRel.u)
                } else {
                    uFromV(shooterV.x, shooterV.y, settings.c, settings.betaMaxMassive, u)
                }
                captureRestMass(this@entity.entity.bodyCmp.body)
            }
            with<LockOnComponent> {
                this.target = target
                targetId = target?.baseInfoCmp?.id ?: -1
                shooterId = shooter.baseInfoCmp.id
                burnLeft = LOCK_ON_BURN_SECONDS
                damage = LOCK_ON_DAMAGE
            }
            with<LifetimeComponent> {
                remaining = LOCK_ON_LIFETIME
                total = LOCK_ON_LIFETIME
                clock = LifetimeClock.WORLD
            }
            with<HealthComponent> {
                health = LOCK_ON_HEALTH
            }
        }
    }
}
