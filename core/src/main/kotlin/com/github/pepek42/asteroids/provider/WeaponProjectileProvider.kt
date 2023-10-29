package com.github.pepek42.asteroids.provider

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.World
import com.github.pepek42.asteroids.component.BaseInfoComponent
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.EntityType
import com.github.pepek42.asteroids.component.LaserProjectile
import com.github.pepek42.asteroids.component.ProjectileComponent
import com.github.pepek42.asteroids.component.SpriteComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.WeaponComponent
import com.github.pepek42.asteroids.component.WrapComponent
import com.github.pepek42.asteroids.component.baseInfoCmp
import com.github.pepek42.asteroids.component.bodyCmp
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
) {
    fun spawnProjectile(weaponCmp: WeaponComponent, entity: Entity) {
        val bodyCmp = entity.bodyCmp
        val relativeWeaponPosition = vec2(
            weaponCmp.weaponPosition.x + 2f * weaponCmp.projectileRadius,
            weaponCmp.weaponPosition.y
        ).rotateRad(bodyCmp.body.angle)
        val position = bodyCmp.body.position + relativeWeaponPosition
        val angle = bodyCmp.body.angle
        val faction = entity.baseInfoCmp.faction

        engine.entity {
            with<BodyComponent> {
                body = world.body(BodyDef.BodyType.KinematicBody) {
                    this.angle = angle
                    this.position.set(position.x, position.y)
                    val velocity = when (weaponCmp.weaponType) {
                        WeaponType.LASER -> vec2(
                            weaponCmp.projectileSpeed,
                            0f
                        ).rotateRad(bodyCmp.body.angle)
                    }
                    linearVelocity.set(velocity)
                    bullet = true
                    circle(
                        radius = weaponCmp.projectileRadius,
                    ) {
                        density = 0f
                        isSensor = true
                        filter.categoryBits = faction.entityMaskBit
                        filter.categoryBits = faction.categoryBits
                    }
                    userData = this@entity.entity
                }
            }
            with<TransformComponent> {
                prevRotationDeg = angle
                prevPosition.set(position.x, position.y)
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
            if (weaponCmp.weaponType.trapped) {
                with<WrapComponent>()
            }
            with<ProjectileComponent> {
                projectile = LaserProjectile().apply { damage = 50f }
            }
        }
    }
}
