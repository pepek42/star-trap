package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Gdx
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.viewport.Viewport
import com.github.pepek42.asteroids.Game
import com.github.pepek42.asteroids.component.SpriteComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.spriteCmp
import com.github.pepek42.asteroids.component.transformCmp
import com.github.pepek42.asteroids.debug.LoggingUtils.Companion.defaultLoggingUtils
import com.github.pepek42.asteroids.provider.BackgroundProvider
import com.github.pepek42.asteroids.debug.DebugSettings
import com.github.pepek42.asteroids.provider.MapProvider
import com.github.pepek42.asteroids.render.FieldShimmerRenderer
import com.github.pepek42.asteroids.render.ReferenceGridRenderer
import ktx.ashley.allOf
import ktx.graphics.use
import ktx.log.logger

class RenderSystem(
    game: Game,
    private val batch: SpriteBatch,
    private val viewport: Viewport,
    private val fieldShimmerRenderer: FieldShimmerRenderer,
    private val gridRenderer: ReferenceGridRenderer,
    private val mapProvider: MapProvider,
    private val debugSettings: DebugSettings,
) : IteratingSystem(allOf(SpriteComponent::class, TransformComponent::class).get()), Disposable {
    private val backgroundProvider: BackgroundProvider = game.get<BackgroundProvider>()

    init {
        logger.info { "Init finished" }
    }

    override fun update(deltaTime: Float) {
        viewport.apply()
        val camera = viewport.camera as OrthographicCamera
        batch.use(camera) {
            // Logical width (not back buffer) keeps star sizes HiDPI independent.
            backgroundProvider.renderBg(camera, camera.zoom, Gdx.graphics.width)
        }
        if (debugSettings.gridEnabled) {
            gridRenderer.render(camera, camera.zoom, mapProvider.mapWidth, mapProvider.mapHeight)
        }
        batch.use(camera) {
            super.update(deltaTime)
            fieldShimmerRenderer.render(batch)
        }
    }

    override fun dispose() {
        fieldShimmerRenderer.dispose()
        gridRenderer.dispose()
    }

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val sprite = entity.spriteCmp.sprite
        if (sprite.texture == null) {
            logger.error { "Trying to render sprite with no texture $entity" }
            return
        }
        val transformComp = entity.transformCmp
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
