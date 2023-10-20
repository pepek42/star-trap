package com.github.pepek42.asteroids.provider

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.physics.box2d.World
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.SpriteComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.WeaponComponent
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.physics.combineSpeeds
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
            weaponCmp.weaponPosition.x + 2f * weaponCmp.bulletRadius,
            weaponCmp.weaponPosition.y
        ).rotateRad(bodyCmp.body.angle)
        val position = bodyCmp.body.position + relativeWeaponPosition
        val angle = bodyCmp.body.angle

        engine.entity {
            with<BodyComponent> {
                body = world.body(com.badlogic.gdx.physics.box2d.BodyDef.BodyType.DynamicBody) {
                    this.angle = angle
                    this.position.set(position.x, position.y)
                    val velocity = when (weaponCmp.weaponType) {
                        WeaponType.LASER -> vec2(
                            weaponCmp.bulletSpeed,
                            0f
                        ).rotateRad(bodyCmp.body.angle)

                        WeaponType.PROJECTILE -> combineSpeeds(
                            bodyCmp.body.linearVelocity,
                            vec2(weaponCmp.bulletSpeed, 0f).rotateRad(bodyCmp.body.angle)
                        )
                    }
                    linearVelocity.set(velocity)
                    bullet = true
                    circle(
                        radius = weaponCmp.bulletRadius,
                    ) {
                        density = 0f
                    }
                }
            }
            with<TransformComponent> {
                prevRotationDeg = angle
                prevPosition.set(position.x, position.y)
            }
            with<SpriteComponent> {
                val sprite = textureAtlas.createSprite("player_laser", 1)
                val scale = weaponCmp.bulletRadius / sprite.texture.height * 3
                sprite.setSize(sprite.texture.width * scale, sprite.texture.height * scale)
                sprite.setOriginCenter()
                this.sprite = sprite
            }
        }
    }
}
