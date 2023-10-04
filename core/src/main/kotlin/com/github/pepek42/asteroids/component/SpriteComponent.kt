package com.github.pepek42.asteroids.component

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.utils.Pool
import ktx.ashley.mapperFor

class SpriteComponent : Component, Pool.Poolable {
    lateinit var sprite: Sprite
    override fun reset() {
        sprite.texture.dispose()
    }
}

val spriteMapper = mapperFor<SpriteComponent>()
