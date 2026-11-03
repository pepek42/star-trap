package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.viewport.Viewport
import com.github.pepek42.asteroids.Game
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.SpriteComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.bodyCmpOptional
import com.github.pepek42.asteroids.component.relativisticCmpOptional
import com.github.pepek42.asteroids.component.spriteCmp
import com.github.pepek42.asteroids.component.transformCmp
import com.github.pepek42.asteroids.debug.DebugSettings
import com.github.pepek42.asteroids.debug.LoggingUtils.Companion.defaultLoggingUtils
import com.github.pepek42.asteroids.physics.ObserverState
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.gammaFromV
import com.github.pepek42.asteroids.provider.BackgroundProvider
import com.github.pepek42.asteroids.provider.MapProvider
import com.github.pepek42.asteroids.render.FieldShimmerRenderer
import com.github.pepek42.asteroids.render.PilotViewProjector
import com.github.pepek42.asteroids.render.ReferenceGridRenderer
import com.github.pepek42.asteroids.render.MIN_CONTRACTION_SPEED
import com.github.pepek42.asteroids.render.SPRITE_VERTEX_FLOATS
import com.github.pepek42.asteroids.render.SPRITE_VERTEX_STRIDE
import com.github.pepek42.asteroids.render.contractOffset
import com.github.pepek42.asteroids.render.contractionK
import kotlin.math.sqrt
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
    private val settings: RelativitySettings,
    private val pilotView: PilotViewProjector,
    private val observer: ObserverState,
) : IteratingSystem(allOf(SpriteComponent::class, TransformComponent::class).get()), Disposable {
    private val backgroundProvider: BackgroundProvider = game.get<BackgroundProvider>()
    private var players: ImmutableArray<Entity>? = null
    private var pilotViewActive = false

    init {
        logger.info { "Init finished" }
    }

    override fun addedToEngine(engine: Engine) {
        super.addedToEngine(engine)
        players = engine.getEntitiesFor(allOf(PlayerComponent::class, TransformComponent::class).get())
    }

    override fun update(deltaTime: Float) {
        viewport.apply()
        val camera = viewport.camera as OrthographicCamera
        batch.use(camera) {
            // Logical width (not back buffer) keeps star sizes HiDPI independent.
            backgroundProvider.renderBg(camera, camera.zoom, Gdx.graphics.width, observer)
        }
        if (debugSettings.gridEnabled) {
            gridRenderer.render(camera, camera.zoom, mapProvider.mapWidth, mapProvider.mapHeight)
        }
        // Pilot view needs the player as the observer; without one (dead, between levels) fall back to the world view.
        val player = players?.let { if (it.size() > 0) it[0] else null }
        pilotViewActive = settings.pilotView && player != null
        if (pilotViewActive) {
            val p = player!!.transformCmp.interpolatedPosition
            pilotView.begin(p.x, p.y)
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
        if (pilotViewActive) {
            if (pilotView.project(entity, scratch)) batch.draw(sprite.texture, scratch, 0, VERTEX_FLOATS)
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
        val v = entity.bodyCmpOptional?.body?.linearVelocity
        val speed = if (v == null) 0f else sqrt(v.x * v.x + v.y * v.y)
        if (v == null || speed <= MIN_CONTRACTION_SPEED) {
            sprite.draw(batch)
            return
        }
        val gamma = entity.relativisticCmpOptional?.gUsed ?: gammaFromV(v.x, v.y, settings.c)
        val k = contractionK(gamma)
        val vhx = v.x / speed
        val vhy = v.y / speed
        val cx = transformComp.interpolatedPosition.x
        val cy = transformComp.interpolatedPosition.y
        System.arraycopy(sprite.vertices, 0, scratch, 0, VERTEX_FLOATS)
        for (i in 0 until VERTEX_FLOATS step VERTEX_STRIDE) {
            contractOffset(scratch[i] - cx, scratch[i + 1] - cy, vhx, vhy, k, tmp)
            scratch[i] = cx + tmp.x
            scratch[i + 1] = cy + tmp.y
        }
        batch.draw(sprite.texture, scratch, 0, VERTEX_FLOATS)
    }

    private val scratch = FloatArray(VERTEX_FLOATS)
    private val tmp = Vector2()

    companion object {
        private const val VERTEX_FLOATS = SPRITE_VERTEX_FLOATS
        private const val VERTEX_STRIDE = SPRITE_VERTEX_STRIDE
        private val logger = logger<RenderSystem>()
    }
}
