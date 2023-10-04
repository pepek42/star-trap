package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.viewport.Viewport
import com.github.pepek42.asteroids.AsteroidsCoop
import com.github.pepek42.asteroids.UNIT_SCALE
import com.github.pepek42.asteroids.component.SpriteComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.spriteMapper
import com.github.pepek42.asteroids.component.transformMapper
import ktx.ashley.allOf
import ktx.ashley.get
import ktx.graphics.use
import ktx.log.logger

class RenderSystem(
    game: AsteroidsCoop,
    private val batch: SpriteBatch,
    private val viewport: Viewport,
) : IteratingSystem(allOf(SpriteComponent::class, TransformComponent::class).get()) {
    private val background: Sprite

    init {
        val backgroundTexture = game.get<AssetManager>().get("textures/background_1.png", Texture::class.java)
        backgroundTexture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat)

        background = Sprite(backgroundTexture)

        logger.info { "Init finished" }
    }

    override fun update(deltaTime: Float) {
        batch.use(viewport.camera) {
//            renderBackground(deltaTime)
            super.update(deltaTime)
        }
    }

    private fun renderBackground(deltaTime: Float) {
        background.scroll(deltaTime * 0.1f, deltaTime * 0.1f)
        logger.debug { "Camera position: ${viewport.camera.position}\nscreen: ${viewport.screenX},${viewport.screenY} (${viewport.screenWidth}x${viewport.screenHeight})" }
        val corner = viewport.camera.unproject(Vector3(0f, viewport.screenHeight.toFloat(), 0f))
        batch.draw(
            background,
            corner.x,
            corner.y,
            viewport.screenWidth.toFloat() / UNIT_SCALE,
            viewport.screenHeight.toFloat() / UNIT_SCALE,
        )
    }

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val sprite = entity[spriteMapper]!!.sprite
        if (sprite.texture == null) {
            logger.error { "Trying to render sprite with no texture $entity" }
            return
        }
        val transformComp = entity[transformMapper]!!
        sprite.setPosition(
            transformComp.interpolatedPosition.x,
            transformComp.interpolatedPosition.y,
        )
        sprite.draw(batch)
    }

    companion object {
        private val logger = logger<RenderSystem>()
    }
}
