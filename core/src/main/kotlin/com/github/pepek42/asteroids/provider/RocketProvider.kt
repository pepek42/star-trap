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
import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.component.RocketComponent
import com.github.pepek42.asteroids.component.SpriteComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.WeaponComponent
import com.github.pepek42.asteroids.component.baseInfoCmp
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.relativisticCmpOptional
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.uFromV
import com.github.pepek42.asteroids.weapon.ROCKET_BURN_SECONDS
import com.github.pepek42.asteroids.weapon.ROCKET_DAMAGE
import com.github.pepek42.asteroids.weapon.ROCKET_DENSITY
import com.github.pepek42.asteroids.weapon.ROCKET_HEALTH
import com.github.pepek42.asteroids.weapon.ROCKET_LENGTH
import com.github.pepek42.asteroids.weapon.ROCKET_SPRITE_LENGTH
import com.github.pepek42.asteroids.weapon.ROCKET_WIDTH
import ktx.ashley.entity
import ktx.ashley.with
import ktx.box2d.body
import ktx.box2d.box
import ktx.math.plus
import ktx.math.vec2

/** Atlas region of the rocket sprite (hull only), also the coast image: see `RenderSystem` for the plume swap. */
const val ROCKET_REGION = "player_rocket"

/** Atlas region of the same sprite with the motor plume drawn behind the hull (same size, so the swap needs no resize). */
const val ROCKET_BURN_REGION = "player_rocket_burn"

/**
 * Creates a pod rocket (ST-181): a small dynamic box like the lock-on shot, carrying the shooter's proper velocity `u`
 * (so it starts at rest relative to the ship), a [RocketComponent] with the fixed launch heading, and one health point so
 * a laser can shoot it down. No `LifetimeComponent`: it lives until it hits something or crosses the arena wall
 * (`BoundaryFieldSystem`).
 */
class RocketProvider(
    private val textureAtlas: TextureAtlas,
    private val engine: Engine,
    private val world: World,
    private val settings: RelativitySettings = RelativitySettings(),
) {
    /**
     * Launches a rocket from [shooter] along `weaponCmp.aimAngleRad` (the computer's aim, gimballed pod) or the hull angle.
     * [weaponCmp] also supplies the muzzle offset.
     */
    fun spawnRocket(shooter: Entity, weaponCmp: WeaponComponent): Entity {
        val shooterBody = shooter.bodyCmp.body
        val heading = weaponCmp.aimAngleRad ?: shooterBody.angle
        val dirX = MathUtils.cos(heading)
        val dirY = MathUtils.sin(heading)
        // Muzzle: the weapon-local point plus the rocket's own length, so it starts clear of the hull.
        val position = shooterBody.position +
            Vector2(weaponCmp.weaponPosition).rotateRad(shooterBody.angle) +
            vec2(dirX, dirY).scl(ROCKET_LENGTH)
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
                    // The heading is fixed for the whole flight.
                    fixedRotation = true
                    box(width = ROCKET_LENGTH, height = ROCKET_WIDTH) {
                        density = ROCKET_DENSITY
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
                val rocketSprite = textureAtlas.createSprite(ROCKET_REGION)
                // The headless balance runner hands out texture-less sprites, which are never drawn.
                val width = rocketSprite.width.takeIf { it > 0f } ?: ROCKET_SPRITE_LENGTH
                val height = rocketSprite.height.takeIf { it > 0f } ?: ROCKET_SPRITE_LENGTH
                val scale = ROCKET_SPRITE_LENGTH / width
                rocketSprite.setSize(width * scale, height * scale)
                rocketSprite.setOriginCenter()
                sprite = rocketSprite
            }
            with<BaseInfoComponent> {
                this.faction = faction
                entityType = EntityType.ROCKET
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
            with<RocketComponent> {
                burnLeft = ROCKET_BURN_SECONDS
                headingX = dirX
                headingY = dirY
                damage = ROCKET_DAMAGE
                shooterId = shooter.baseInfoCmp.id
            }
            with<HealthComponent> {
                health = ROCKET_HEALTH
            }
        }
    }
}
