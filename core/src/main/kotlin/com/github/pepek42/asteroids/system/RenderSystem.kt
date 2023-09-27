package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.utils.viewport.Viewport
import com.github.pepek42.asteroids.component.SpriteComponent
import ktx.ashley.allOf
import ktx.ashley.get
import ktx.graphics.use
import ktx.log.logger

class RenderSystem(
    private val batch: SpriteBatch,
    private val viewport: Viewport,
) : IteratingSystem(allOf(SpriteComponent::class).get()) {
    private val logger = logger<RenderSystem>()

    override fun processEntity(entity: Entity?, deltaTime: Float) {
        batch.use(viewport.camera) {
            entity!![SpriteComponent.mapper]!!.sprite.run {
                if (texture == null) {
                    logger.error { "Trying to render sprite with no texture $entity" }
                    return
                }
                draw(batch)
            }
        }
    }
}
