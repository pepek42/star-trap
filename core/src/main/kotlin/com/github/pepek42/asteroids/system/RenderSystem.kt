package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.viewport.Viewport
import com.github.pepek42.asteroids.Game
import com.github.pepek42.asteroids.component.GravitySourceComponent
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.SpriteComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.EntityType
import com.github.pepek42.asteroids.component.baseInfoMapper
import com.github.pepek42.asteroids.component.bodyCmpOptional
import com.github.pepek42.asteroids.component.playerMapper
import com.github.pepek42.asteroids.component.relativisticCmpOptional
import com.github.pepek42.asteroids.component.spriteCmp
import com.github.pepek42.asteroids.component.transformCmp
import com.github.pepek42.asteroids.debug.DebugSettings
import com.github.pepek42.asteroids.debug.LoggingUtils.Companion.defaultLoggingUtils
import com.github.pepek42.asteroids.physics.ObserverState
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.dopplerFactor
import com.github.pepek42.asteroids.physics.gammaFromV
import com.github.pepek42.asteroids.provider.BackgroundProvider
import com.github.pepek42.asteroids.provider.MapProvider
import com.github.pepek42.asteroids.render.AdditiveSpriteQueue
import com.github.pepek42.asteroids.render.BlackHoleRenderer
import com.github.pepek42.asteroids.render.BlackbodyLut
import com.github.pepek42.asteroids.render.DOPPLER_MIN_BETA
import com.github.pepek42.asteroids.render.FieldShimmerRenderer
import com.github.pepek42.asteroids.render.PilotViewProjector
import com.github.pepek42.asteroids.render.ReferenceGridRenderer
import com.github.pepek42.asteroids.render.MIN_CONTRACTION_SPEED
import com.github.pepek42.asteroids.render.SPRITE_VERTEX_FLOATS
import com.github.pepek42.asteroids.render.SPRITE_VERTEX_STRIDE
import com.github.pepek42.asteroids.render.contractOffset
import com.github.pepek42.asteroids.render.contractionK
import com.github.pepek42.asteroids.render.glowsAdditively
import com.github.pepek42.asteroids.render.effectiveDoppler
import com.github.pepek42.asteroids.render.tintSpriteVertices
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
    private val blackHoleRenderer: BlackHoleRenderer = BlackHoleRenderer(),
) : IteratingSystem(allOf(SpriteComponent::class, TransformComponent::class).get()), Disposable {
    private val backgroundProvider: BackgroundProvider = game.get<BackgroundProvider>()
    private var players: ImmutableArray<Entity>? = null
    private var holes: ImmutableArray<Entity>? = null
    private var pilotViewActive = false

    init {
        logger.info { "Init finished" }
    }

    override fun addedToEngine(engine: Engine) {
        super.addedToEngine(engine)
        players = engine.getEntitiesFor(allOf(PlayerComponent::class, TransformComponent::class).get())
        holes = engine.getEntitiesFor(allOf(GravitySourceComponent::class, TransformComponent::class).get())
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
        holes?.let { blackHoleRenderer.render(camera, it, if (pilotViewActive) pilotView else null, observer.worldTime, settings.c) }
        batch.use(camera) {
            additive.clear()
            super.update(deltaTime)
            drawAdditive()
            fieldShimmerRenderer.render(batch)
        }
    }

    override fun dispose() {
        fieldShimmerRenderer.dispose()
        gridRenderer.dispose()
        blackHoleRenderer.dispose()
    }

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val sprite = entity.spriteCmp.sprite
        if (sprite.texture == null) {
            logger.error { "Trying to render sprite with no texture $entity" }
            return
        }
        val isPlayer = playerMapper.has(entity)
        val type = baseInfoMapper.get(entity)?.entityType ?: EntityType.UNKNOWN
        if (pilotViewActive) {
            if (!pilotView.project(entity, scratch)) return
            if (!isPlayer) {
                tintAndQueue(sprite, type, pilotView.sourceX, pilotView.sourceY, pilotView.sourceVx, pilotView.sourceVy)
            }
            batch.draw(sprite.texture, scratch, 0, VERTEX_FLOATS)
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
        val vx = v?.x ?: 0f
        val vy = v?.y ?: 0f
        val speed = sqrt(vx * vx + vy * vy)
        val contract = v != null && speed > MIN_CONTRACTION_SPEED
        val cx = transformComp.interpolatedPosition.x
        val cy = transformComp.interpolatedPosition.y
        val tinted = !isPlayer && observerBlueShiftActive(vx, vy)
        if (!contract && !tinted) {
            sprite.draw(batch)
            return
        }
        System.arraycopy(sprite.vertices, 0, scratch, 0, VERTEX_FLOATS)
        if (contract) {
            val gamma = entity.relativisticCmpOptional?.gUsed ?: gammaFromV(vx, vy, settings.c)
            val k = contractionK(gamma)
            val vhx = vx / speed
            val vhy = vy / speed
            for (i in 0 until VERTEX_FLOATS step VERTEX_STRIDE) {
                contractOffset(scratch[i] - cx, scratch[i + 1] - cy, vhx, vhy, k, tmp)
                scratch[i] = cx + tmp.x
                scratch[i + 1] = cy + tmp.y
            }
        }
        if (tinted) tintAndQueue(sprite, type, cx, cy, vx, vy)
        batch.draw(sprite.texture, scratch, 0, VERTEX_FLOATS)
    }

    /**
     * True when the Doppler tint can differ from white: the observer or the source moves. At rest on both sides D = 1 and the
     * plain sprite path is used.
     */
    private fun observerBlueShiftActive(vx: Float, vy: Float): Boolean =
        observer.betaVisual.len2() >= DOPPLER_MIN_BETA * DOPPLER_MIN_BETA ||
            vx * vx + vy * vy >= (DOPPLER_MIN_BETA * settings.c).let { it * it }

    /**
     * Tints the quad in [scratch] by the Doppler factor of a source at ([sx], [sy]) moving at ([vx], [vy]) as seen by the
     * observer (ST-045): `n̂` runs from the source to the observer, β_s = v / c. Blueshifted sprites also queue an additive glow
     * copy, unless [glowsAdditively] is false for [type] (projectiles, ST-121).
     */
    private fun tintAndQueue(sprite: Sprite, type: EntityType, sx: Float, sy: Float, vx: Float, vy: Float) {
        val nx = observer.position.x - sx
        val ny = observer.position.y - sy
        val dist = sqrt(nx * nx + ny * ny)
        val c = settings.c
        val d = if (dist < MIN_TINT_DISTANCE || c <= 0f) {
            1f
        } else {
            betaSrc.set(vx / c, vy / c)
            effectiveDoppler(dopplerFactor(observer.betaVisual, betaSrc, nx / dist, ny / dist))
        }
        val col = sprite.color
        val glow = tintSpriteVertices(scratch, d, col.r, col.g, col.b, col.a, BlackbodyLut, rgb)
        if (glow > 0f && glowsAdditively(type)) additive.add(sprite.texture, scratch, glow * col.a)
    }

    /** Draws the queued glow copies once with additive blending, then restores the blend function. */
    private fun drawAdditive() {
        if (additive.size == 0) return
        val src = batch.blendSrcFunc
        val dst = batch.blendDstFunc
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        additive.draw(batch)
        batch.setBlendFunction(src, dst)
    }

    private val scratch = FloatArray(VERTEX_FLOATS)
    private val tmp = Vector2()
    private val betaSrc = Vector2()
    private val rgb = FloatArray(3)
    private val additive = AdditiveSpriteQueue()

    companion object {
        private const val VERTEX_FLOATS = SPRITE_VERTEX_FLOATS
        private const val VERTEX_STRIDE = SPRITE_VERTEX_STRIDE

        /** Closer than this (u) the source-to-observer direction is undefined and the tint stays white. */
        private const val MIN_TINT_DISTANCE = 1e-3f
        private val logger = logger<RenderSystem>()
    }
}
