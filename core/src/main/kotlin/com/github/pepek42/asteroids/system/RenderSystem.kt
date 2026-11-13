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
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.viewport.Viewport
import com.github.pepek42.asteroids.Game
import com.github.pepek42.asteroids.component.AsteroidComponent
import com.github.pepek42.asteroids.component.BaseInfoComponent
import com.github.pepek42.asteroids.component.HealthComponent
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.GravitySourceComponent
import com.github.pepek42.asteroids.component.GravitySourceKind
import com.github.pepek42.asteroids.component.gravitySourceCmp
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.SpriteComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.EntityType
import com.github.pepek42.asteroids.component.baseInfoMapper
import com.github.pepek42.asteroids.component.bodyCmpOptional
import com.github.pepek42.asteroids.component.playerMapper
import com.github.pepek42.asteroids.component.relativisticCmpOptional
import com.github.pepek42.asteroids.component.rocketCmp
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
import com.github.pepek42.asteroids.provider.ROCKET_BURN_REGION
import com.github.pepek42.asteroids.provider.ROCKET_REGION
import com.github.pepek42.asteroids.render.AdditiveSpriteQueue
import com.github.pepek42.asteroids.render.BlackHoleRenderer
import com.github.pepek42.asteroids.render.BlackbodyLut
import com.github.pepek42.asteroids.render.DOPPLER_MIN_BETA
import com.github.pepek42.asteroids.render.FieldShimmerRenderer
import com.github.pepek42.asteroids.render.LensingPass
import com.github.pepek42.asteroids.render.LightSources
import com.github.pepek42.asteroids.render.LitSpriteRenderer
import com.github.pepek42.asteroids.render.OccluderPool
import com.github.pepek42.asteroids.render.rebuild
import com.github.pepek42.asteroids.render.PilotViewProjector
import com.github.pepek42.asteroids.render.ReferenceGridRenderer
import com.github.pepek42.asteroids.render.MIN_CONTRACTION_SPEED
import com.github.pepek42.asteroids.render.StarLight
import com.github.pepek42.asteroids.render.SPRITE_VERTEX_FLOATS
import com.github.pepek42.asteroids.render.SPRITE_VERTEX_STRIDE
import com.github.pepek42.asteroids.render.TIDAL_VISUAL_MIN_SCALE
import com.github.pepek42.asteroids.render.contractOffset
import com.github.pepek42.asteroids.render.tidalOffset
import com.github.pepek42.asteroids.render.tidalStretch
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
    private val litSprites: LitSpriteRenderer? = null,
    private val star: StarLight? = null,
    private val lensing: LensingPass? = null,
) : IteratingSystem(allOf(SpriteComponent::class, TransformComponent::class).get()), Disposable {
    private val backgroundProvider: BackgroundProvider = game.get<BackgroundProvider>()

    /** Rocket sprite with and without the motor plume (ST-181); same size, so [applyRocketPlume] only swaps the UVs. */
    private val rocketRegion: TextureRegion? = game.get<TextureAtlas>().findRegion(ROCKET_REGION)
    private val rocketBurnRegion: TextureRegion? = game.get<TextureAtlas>().findRegion(ROCKET_BURN_REGION)
    private var players: ImmutableArray<Entity>? = null
    private var holes: ImmutableArray<Entity>? = null
    private var asteroids: ImmutableArray<Entity>? = null
    private var diskBodies: ImmutableArray<Entity>? = null
    private var pilotViewActive = false

    /** Scratch sprite the ghosts of removed entities are built on (ST-079). */
    private val ghostSprite = Sprite()
    private val lightSources = LightSources()
    private val occluders = OccluderPool()

    /** True for the frame when [occluders] hold this frame's shadow casters (ST-098). */
    private var shadowsActive = false

    /** True for the frame when lit sprites are drawn: lighting on, the shader compiled and at least one light exists. */
    private var lightingActive = false

    /** True while the quad being drawn goes through the lit shader (it is flushed right after, see [endLit]). */
    private var litNow = false

    init {
        logger.info { "Init finished" }
    }

    override fun addedToEngine(engine: Engine) {
        super.addedToEngine(engine)
        players = engine.getEntitiesFor(allOf(PlayerComponent::class, TransformComponent::class).get())
        holes = engine.getEntitiesFor(allOf(GravitySourceComponent::class, TransformComponent::class).get())
        asteroids = engine.getEntitiesFor(allOf(AsteroidComponent::class, BodyComponent::class, TransformComponent::class).get())
        // Bodies that push accretion-disk particles aside (ST-175); BlackHoleRenderer filters by type.
        diskBodies = engine.getEntitiesFor(
            allOf(BaseInfoComponent::class, BodyComponent::class, TransformComponent::class, HealthComponent::class).get()
        )
    }

    override fun update(deltaTime: Float) {
        viewport.apply()
        val camera = viewport.camera as OrthographicCamera
        // Pilot view needs the player as the observer; without one (dead, between levels) fall back to the world view.
        val player = players?.let { if (it.size() > 0) it[0] else null }
        pilotViewActive = settings.pilotView && player != null
        if (pilotViewActive) {
            val p = player!!.transformCmp.interpolatedPosition
            pilotView.begin(p.x, p.y)
        }
        drawBackdrop(camera)
        if (debugSettings.gridEnabled) {
            gridRenderer.render(camera, camera.zoom, mapProvider.mapWidth, mapProvider.mapHeight)
        }
        holes?.let { blackHoleRenderer.render(camera, it, if (pilotViewActive) pilotView else null, observer.worldTime, settings.c, debugSettings.einsteinRingEnabled, diskBodies) }
        lightingActive = false
        if (settings.lightingEnabled && litSprites?.ready == true) {
            holes?.let { lightSources.rebuild(it, if (settings.starLightEnabled) star else null) }
            lightingActive = lightSources.count > 0
        }
        shadowsActive = lightingActive && settings.shadowsEnabled
        if (shadowsActive) {
            val sources = holes
            val rocks = asteroids
            if (sources != null && rocks != null) occluders.rebuild(sources, rocks) else shadowsActive = false
        }
        batch.use(camera) {
            additive.clear()
            super.update(deltaTime)
            if (pilotViewActive) drawGhosts()
            litSprites?.release(batch)
            drawAdditive()
            fieldShimmerRenderer.render(batch, pilotViewActive && pilotView.aberrationEnabled, pilotView.ox, pilotView.oy, pilotView.betaX, pilotView.betaY)
        }
    }

    /**
     * Draws the nebula and starfield. With a black hole in the level and lensing on (ST-088) they go through [LensingPass]: into an
     * off-screen buffer, then to the screen with one lens quad per hole; otherwise straight to the screen. Logical width (not back
     * buffer) keeps star sizes HiDPI independent.
     */
    private fun drawBackdrop(camera: OrthographicCamera) {
        val lens = lensing
        val lensCount = if (lens != null && lens.ready && settings.lensingEnabled) collectLenses() else 0
        val lensed = lens != null && lensCount > 0 && lens.beginCapture()
        val aberrated = if (pilotViewActive && pilotView.aberrationEnabled) pilotView else null
        batch.use(camera) { backgroundProvider.renderBg(camera, camera.zoom, Gdx.graphics.width, observer, aberrated, pilotViewActive) }
        if (lensed && lens != null) {
            lens.endCapture()
            viewport.apply()
            lens.draw(camera, batch, lensCentres, lensRadii, lensCount)
        }
    }

    /**
     * Fills [lensCentres] and [lensRadii] with every black hole's lens: the interpolated position in the world view, the apparent
     * (retarded, boosted) position in pilot view (FAKE: the aberrated backdrop is lensed as if unaberrated, physics-lies row 19).
     * Returns the number of lenses. Grows the arrays only when a level has more holes than before.
     */
    private fun collectLenses(): Int {
        val list = holes ?: return 0
        if (lensRadii.size < list.size()) {
            lensRadii = FloatArray(list.size())
            lensCentres = FloatArray(list.size() * 2)
        }
        var n = 0
        for (i in 0 until list.size()) {
            val e = list[i]
            val source = e.gravitySourceCmp
            if (source.kind != GravitySourceKind.BLACK_HOLE) continue
            if (pilotViewActive) {
                if (!pilotView.retardedCentre(e, tmp)) continue
                pilotView.apparentPoint(tmp.x, tmp.y, tmp)
            } else {
                tmp.set(e.transformCmp.interpolatedPosition)
            }
            lensCentres[2 * n] = tmp.x
            lensCentres[2 * n + 1] = tmp.y
            lensRadii[n] = source.rs
            n++
        }
        return n
    }

    override fun dispose() {
        lensing?.dispose()
        fieldShimmerRenderer.dispose()
        gridRenderer.dispose()
        blackHoleRenderer.dispose()
        litSprites?.dispose()
    }

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val sprite = entity.spriteCmp.sprite
        if (sprite.texture == null) {
            logger.error { "Trying to render sprite with no texture $entity" }
            return
        }
        val isPlayer = playerMapper.has(entity)
        val type = baseInfoMapper.get(entity)?.entityType ?: EntityType.UNKNOWN
        if (type == EntityType.ROCKET) applyRocketPlume(entity, sprite)
        if (pilotViewActive) {
            if (!pilotView.project(entity, scratch)) return
            if (!isPlayer) {
                tintAndQueue(sprite, type, pilotView.sourceX, pilotView.sourceY, pilotView.sourceVx, pilotView.sourceVy)
            }
            beginLit(
                entity, sprite, pilotView.sourceX, pilotView.sourceY, pilotView.sourceAngleRad, pilotView.sourceLag,
            )
            batch.draw(sprite.texture, scratch, 0, VERTEX_FLOATS)
            endLit()
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
        val rel = entity.relativisticCmpOptional
        val stretch = if (rel != null && rel.tidalRatio > 0f) tidalStretch(rel.tidalRatio) else 1f
        val stretched = stretch >= TIDAL_VISUAL_MIN_SCALE
        beginLit(entity, sprite, cx, cy, transformComp.interpolatedRotationDeg * MathUtils.degreesToRadians, 0f)
        if (!contract && !tinted && !stretched) {
            sprite.draw(batch)
            endLit()
            return
        }
        System.arraycopy(sprite.vertices, 0, scratch, 0, VERTEX_FLOATS)
        if (stretched && rel != null) {
            // Tidal stretch along r̂ first (ST-153), then the contraction along v̂.
            for (i in 0 until VERTEX_FLOATS step VERTEX_STRIDE) {
                tidalOffset(scratch[i] - cx, scratch[i + 1] - cy, rel.tidalDirX, rel.tidalDirY, stretch, tmp)
                scratch[i] = cx + tmp.x
                scratch[i + 1] = cy + tmp.y
            }
        }
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
        endLit()
    }

    /**
     * Pilot view only (ST-079): draws the removed entities whose last light has not reached the pilot yet, so a destroyed asteroid
     * vanishes when the light of its destruction arrives, not before. Lit like the live entity was (ST-202: the lighting data is kept in the ghost),
     * with the same Doppler tint, contraction, Terrell corners and boost.
     */
    private fun drawGhosts() {
        litSprites?.release(batch)
        litNow = false
        pilotView.history.forEachGhost { _, history, ghost, removedAt ->
            if (!pilotView.projectGhost(history, ghost, removedAt, ghostSprite, scratch)) return@forEachGhost
            if (ghostSprite.texture == null) return@forEachGhost
            tintAndQueue(ghostSprite, ghost.type, pilotView.sourceX, pilotView.sourceY, pilotView.sourceVx, pilotView.sourceVy)
            val lit = litSprites
            litNow = lightingActive && lit != null && lit.prepare(
                batch, ghostSprite, ghost.entityId, ghost.type, ghost.sphereNormals, lightSources,
                pilotView.sourceX, pilotView.sourceY, pilotView.sourceAngleRad, pilotView.sourceLag, if (shadowsActive) occluders else null,
            )
            if (!litNow) lit?.release(batch)
            batch.draw(ghostSprite.texture, scratch, 0, VERTEX_FLOATS)
            endLit()
        }
    }

    /**
     * Shows the motor plume on a rocket while its motor burns (ST-181) by swapping the sprite to the plume image and back at
     * burnout. The plume follows the true `burnLeft`, so in the pilot view a distant rocket's plume is not light-delayed
     * (physics-lies row 49).
     */
    private fun applyRocketPlume(entity: Entity, sprite: Sprite) {
        val region = (if (entity.rocketCmp.burnLeft > 0f) rocketBurnRegion else rocketRegion) ?: return
        if (sprite.u != region.u || sprite.v != region.v) sprite.setRegion(region)
    }

    /**
     * Switches to the lit shader for the quad about to be drawn when lighting is active and [entity] has normals (ST-097),
     * else to the default shader. ([ax], [ay], [angleRad]) is the sprite's arena-frame state, [lag] its retarded-time lag.
     */
    private fun beginLit(entity: Entity, sprite: Sprite, ax: Float, ay: Float, angleRad: Float, lag: Float) {
        val lit = litSprites
        litNow = lightingActive && lit != null && lit.prepare(batch, entity, sprite, lightSources, ax, ay, angleRad, lag, if (shadowsActive) occluders else null)
        if (!litNow) lit?.release(batch)
    }

    /** Draws the lit quad now: the uniforms of the next sprite differ, so lit sprites are not batched. */
    private fun endLit() {
        if (litNow) batch.flush()
        litNow = false
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
        val glow = tintSpriteVertices(scratch, d, col.r, col.g, col.b, col.a, BlackbodyLut, rgb, pilotViewActive)
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
    private var lensCentres = FloatArray(0)
    private var lensRadii = FloatArray(0)
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
