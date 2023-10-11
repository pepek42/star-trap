package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.utils.viewport.Viewport
import com.github.pepek42.asteroids.AsteroidsCoop
import com.github.pepek42.asteroids.component.SpriteComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.spriteCmp
import com.github.pepek42.asteroids.component.transformMapper
import com.github.pepek42.asteroids.debug.LoggingUtils.Companion.defaultLoggingUtils
import com.github.pepek42.asteroids.provider.BackgroundProvider
import ktx.ashley.allOf
import ktx.ashley.get
import ktx.graphics.use
import ktx.log.logger

class RenderSystem(
    game: AsteroidsCoop,
    private val batch: SpriteBatch,
    private val viewport: Viewport,
) : IteratingSystem(allOf(SpriteComponent::class, TransformComponent::class).get()) {
    private val backgroundProvider: BackgroundProvider = game.get<BackgroundProvider>()


    init {


        logger.info { "Init finished" }
    }

    override fun update(deltaTime: Float) {
        batch.use(viewport.camera) {
            backgroundProvider.renderBg(deltaTime)
            super.update(deltaTime)
        }
    }

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val sprite = entity.spriteCmp.sprite
        if (sprite.texture == null) {
            logger.error { "Trying to render sprite with no texture $entity" }
            return
        }
        val transformComp = entity[transformMapper]!!
        sprite.rotation = transformComp.interpolatedRotationDeg
        sprite.setPosition(
            transformComp.interpolatedPosition.x - sprite.width / 2,
            transformComp.interpolatedPosition.y - sprite.height / 2,
        )
        defaultLoggingUtils.tryLogging {
            logger.debug {
                """

                Region X  -> ${sprite.regionX}
                Region Y  -> ${sprite.regionY}
                Sprite x  -> ${sprite.x}
                Sprite y  -> ${sprite.y}
                Origin x  -> ${sprite.originX}
                Origin y  -> ${sprite.originY}
                Width     -> ${sprite.width}
                Height    -> ${sprite.height}
                Rotation  -> ${sprite.rotation}
            """.trimIndent()
            }
        }
        sprite.draw(batch)
    }

    companion object {
        private val logger = logger<RenderSystem>()
    }
}
