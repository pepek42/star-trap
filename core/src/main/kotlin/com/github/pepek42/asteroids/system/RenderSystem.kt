package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.utils.viewport.Viewport
import com.github.pepek42.asteroids.AsteroidsCoop
import com.github.pepek42.asteroids.component.SpriteComponent
import ktx.ashley.allOf
import ktx.ashley.get
import ktx.graphics.use
import ktx.log.logger

class RenderSystem(
    game: AsteroidsCoop,
    private val batch: SpriteBatch,
    private val viewport: Viewport,
) : IteratingSystem(allOf(SpriteComponent::class).get()) {
    private val logger = logger<RenderSystem>()
    private val background: Sprite

    init {
        val backgroundTexture = game.ctx.inject<AssetManager>().get("textures/background_1.png", Texture::class.java)
        backgroundTexture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat)

        background = Sprite(backgroundTexture)
    }

    override fun update(deltaTime: Float) {
        batch.use(viewport.camera) {
            renderBackground(deltaTime)
            super.update(deltaTime)
        }
    }

    private fun renderBackground(deltaTime: Float) {

        background.scroll(deltaTime * 0.1f, deltaTime * 0.1f)
        val halfScreenW = viewport.screenWidth / 2
        val halfScreenH = viewport.screenHeight / 2

        logger.debug { "Camera position: ${viewport.camera.position}\nscreen: ${viewport.screenX},${viewport.screenY} (${viewport.screenWidth}x${viewport.screenHeight})" }
        batch.draw(
            background,
            viewport.camera.position.x - halfScreenW,
            viewport.camera.position.y - halfScreenH,
            viewport.camera.position.x + halfScreenW,
            viewport.camera.position.y + halfScreenH,
        )
    }

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val sprite = entity[SpriteComponent.mapper]!!.sprite
        if (sprite.texture == null) {
            logger.error { "Trying to render sprite with no texture $entity" }
            return
        }
        sprite.draw(batch)
    }
}
