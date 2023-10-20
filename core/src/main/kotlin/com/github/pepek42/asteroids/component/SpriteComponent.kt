package com.github.pepek42.asteroids.component

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.utils.Pool
import ktx.ashley.mapperFor
import ktx.ashley.propertyFor

class SpriteComponent : Component, Pool.Poolable {
    lateinit var sprite: Sprite
    override fun reset() {
        sprite.texture = null
    }
}

val spriteMapper = mapperFor<SpriteComponent>()
val Entity.spriteCmp by propertyFor(spriteMapper)
