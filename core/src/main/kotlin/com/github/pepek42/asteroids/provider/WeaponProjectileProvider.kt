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
import com.github.pepek42.asteroids.component.LaserProjectile
import com.github.pepek42.asteroids.component.LifetimeClock
import com.github.pepek42.asteroids.component.LifetimeComponent
import com.github.pepek42.asteroids.component.PhotonComponent
import com.github.pepek42.asteroids.component.ProjectileComponent
import com.github.pepek42.asteroids.component.SpriteComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.WeaponComponent
import com.github.pepek42.asteroids.component.baseInfoCmp
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.relativisticCmpOptional
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.aberratePhoton
import com.github.pepek42.asteroids.weapon.PHOTON_WORLD_LIFETIME
import com.github.pepek42.asteroids.weapon.WeaponType
import ktx.ashley.entity
import ktx.ashley.with
import ktx.box2d.body
import ktx.box2d.circle
import ktx.math.plus
import ktx.math.vec2

class WeaponProjectileProvider(
    private val textureAtlas: TextureAtlas,
    private val engine: Engine,
    private val world: World,
    private val settings: RelativitySettings = RelativitySettings(),
) {
    fun spawnProjectile(weaponCmp: WeaponComponent, entity: Entity) {
        val bodyCmp = entity.bodyCmp
        val aim = weaponCmp.aimAngleRad ?: bodyCmp.body.angle
        val muzzle = vec2(2f * weaponCmp.projectileRadius, 0f).rotateRad(aim)
        val position = bodyCmp.body.position +
            Vector2(weaponCmp.weaponPosition).rotateRad(bodyCmp.body.angle) + muzzle
        val photonDir = photonDirection(aim, entity)
        val angle = photonDir.angleRad()
        val faction = entity.baseInfoCmp.faction

        engine.entity {
            with<BodyComponent> {
                body = world.body(BodyDef.BodyType.KinematicBody) {
                    this.angle = angle
                    this.position.set(position.x, position.y)
                    val velocity = when (weaponCmp.weaponType) {
                        WeaponType.LASER -> Vector2(photonDir).scl(settings.c)
                    }
                    linearVelocity.set(velocity)
                    bullet = true
                    circle(
                        radius = weaponCmp.projectileRadius,
                    ) {
                        density = 0f
                        isSensor = true
                        filter.categoryBits = faction.ownCategoryBit
                        filter.maskBits = faction.collidesWithMask
                    }
                    userData = this@entity.entity
                }
            }
            with<TransformComponent> {
                prevRotationDeg = angle * MathUtils.radiansToDegrees
                prevPosition.set(position.x, position.y)
            }
            with<PhotonComponent> {
                this.dir.set(photonDir)
            }
            with<SpriteComponent> {
                val sprite = textureAtlas.createSprite("player_laser", 1)
                val scale = weaponCmp.projectileRadius / sprite.texture.height * 3
                sprite.setSize(sprite.texture.width * scale, sprite.texture.height * scale)
                sprite.setOriginCenter()
                this.sprite = sprite
            }
            with<BaseInfoComponent> {
                this.faction = faction
                entityType = EntityType.PROJECTILE
            }
            if (weaponCmp.weaponType == WeaponType.LASER) {
                with<LifetimeComponent> {
                    remaining = PHOTON_WORLD_LIFETIME
                    total = PHOTON_WORLD_LIFETIME
                    clock = LifetimeClock.WORLD
                }
            }
            with<ProjectileComponent> {
                projectile = LaserProjectile().apply { damage = 50f }
            }
        }
    }

    /** Unit direction in W of a photon fired at shooter-frame angle [aim], aberrated by the shooter's velocity. */
    private fun photonDirection(aim: Float, shooter: Entity): Vector2 {
        val nx = MathUtils.cos(aim)
        val ny = MathUtils.sin(aim)
        if (!settings.relativityEnabled) return vec2(nx, ny)
        val vShip = shooter.relativisticCmpOptional?.vSet ?: shooter.bodyCmp.body.linearVelocity
        return aberratePhoton(vShip.x / settings.c, vShip.y / settings.c, nx, ny, Vector2())
    }
}
