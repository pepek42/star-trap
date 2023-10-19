package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.World
import com.github.pepek42.asteroids.Game
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.SpriteComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.WeaponComponent
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.weaponCmp
import com.github.pepek42.asteroids.physics.combineSpeeds
import com.github.pepek42.asteroids.weapon.WeaponType
import ktx.ashley.allOf
import ktx.ashley.entity
import ktx.ashley.with
import ktx.box2d.body
import ktx.box2d.circle
import ktx.math.plus
import ktx.math.vec2

class WeaponSystem(
    private val world: World,
    private val game: Game,
) : IteratingSystem(allOf(WeaponComponent::class, BodyComponent::class).get()) {
    override fun processEntity(entity: Entity, deltaTime: Float) {
        val weaponCmp = entity.weaponCmp
        weaponCmp.cooldown -= deltaTime

        if (entity.weaponCmp.doAttack && weaponCmp.cooldown <= 0) {
            fire(weaponCmp, entity)
        }
    }

    private fun fire(weaponCmp: WeaponComponent, entity: Entity) {
        weaponCmp.cooldown = 1 / weaponCmp.rof
        val bodyCmp = entity.bodyCmp
        val relativeWeaponPosition = vec2(
            weaponCmp.weaponPosition.x + 2f * weaponCmp.bulletRadius,
            weaponCmp.weaponPosition.y
        ).rotateRad(bodyCmp.body.angle)
        val position = bodyCmp.body.position + relativeWeaponPosition
        val angle = bodyCmp.body.angle

        engine.entity {
            with<BodyComponent> {
                body = world.body(BodyDef.BodyType.DynamicBody) {
                    this.angle = angle
                    this.position.set(position.x, position.y)
                    val velocity = when (weaponCmp.weaponType) {
                        WeaponType.LASER -> vec2(weaponCmp.bulletSpeed, 0f).rotateRad(bodyCmp.body.angle)
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
                val sprite = game.get<TextureAtlas>().createSprite("player_laser", 1)
                val scale = weaponCmp.bulletRadius / sprite.texture.height * 3
                sprite.setSize(sprite.texture.width * scale, sprite.texture.height * scale)
                sprite.setOriginCenter()
                this.sprite = sprite
            }
        }
    }

}
