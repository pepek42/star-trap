package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.viewport.Viewport
import com.github.pepek42.asteroids.component.BaseInfoComponent
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.EntityType
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.RemoveComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.baseInfoCmp
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.playerCmpOptional
import com.github.pepek42.asteroids.component.relativisticCmpOptional
import com.github.pepek42.asteroids.component.tacticalComputerCmpOptional
import com.github.pepek42.asteroids.component.transformCmp
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.event.PlayerInputListener
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.provider.MapProvider
import com.github.pepek42.asteroids.render.TACTICAL_ARROW_HEAD_PX
import com.github.pepek42.asteroids.render.TACTICAL_MIN_ARROW_BETA
import com.github.pepek42.asteroids.render.TACTICAL_MIN_RADIUS_PX
import com.github.pepek42.asteroids.render.TACTICAL_OUTLINE_MAX_VERTS
import com.github.pepek42.asteroids.render.TACTICAL_TIER_NONE
import com.github.pepek42.asteroids.render.outlineVertices
import com.github.pepek42.asteroids.render.tacticalArrowScale
import com.github.pepek42.asteroids.render.tacticalOverlayVisible
import com.github.pepek42.asteroids.render.worldPerPixel
import com.github.pepek42.asteroids.settings.TacticalSettings
import com.github.pepek42.asteroids.ui.ARENA_BOUNDARY_COLOR
import com.github.pepek42.asteroids.ui.PLAYER_MARKER_COLOR
import com.github.pepek42.asteroids.ui.TACTICAL_ALPHA
import com.github.pepek42.asteroids.ui.TACTICAL_BORDER_ALPHA
import ktx.ashley.allOf
import ktx.ashley.exclude
import kotlin.math.hypot

/**
 * Tactical computer overlay (ST-123): thin outlines of every body at its true interpolated arena position, with
 * arena-frame velocity arrows and the arena border, drawn over the (possibly light-delayed) main view.
 */
class TacticalOverlaySystem(
    private val camera: OrthographicCamera,
    private val viewport: Viewport,
    private val mapProvider: MapProvider,
    private val relativity: RelativitySettings,
    private val tactical: TacticalSettings,
    private val gameEventManager: GameEventManager,
    private val shape: ShapeRenderer = ShapeRenderer(),
) : IteratingSystem(
    allOf(BaseInfoComponent::class, BodyComponent::class, TransformComponent::class)
        .exclude(RemoveComponent::class).get()
),
    PlayerInputListener,
    Disposable {

    private var players: ImmutableArray<Entity>? = null
    private val verts = FloatArray(2 * TACTICAL_OUTLINE_MAX_VERTS)
    private val seg = FloatArray(ARROW_FLOATS)
    private val color = Color()
    private val headAngleRad = VEC_HEAD_ANGLE_DEG * MathUtils.degreesToRadians
    private var wpp = 0f

    override fun addedToEngine(engine: Engine) {
        super.addedToEngine(engine)
        players = engine.getEntitiesFor(allOf(PlayerComponent::class, TransformComponent::class).get())
        gameEventManager.addInputListener(this)
    }

    override fun removedFromEngine(engine: Engine) {
        gameEventManager.removeInputListener(this)
        super.removedFromEngine(engine)
    }

    override fun toggleTacticalOverlay() {
        tactical.inverted = !tactical.inverted
    }

    override fun update(deltaTime: Float) {
        val tier = players?.let { if (it.size() > 0) it[0] else null }?.tacticalComputerCmpOptional?.tier ?: TACTICAL_TIER_NONE
        if (!tacticalOverlayVisible(tactical.mode, relativity.pilotView, tier, tactical.inverted)) return

        viewport.apply()
        wpp = worldPerPixel(camera.viewportWidth, camera.zoom, viewport.screenWidth)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        // Separate alpha func keeps the framebuffer alpha opaque (matters for screenshots)
        Gdx.gl.glBlendFuncSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_ZERO, GL20.GL_ONE)
        shape.projectionMatrix = camera.combined
        shape.begin(ShapeRenderer.ShapeType.Line)
        super.update(deltaTime)
        color.set(ARENA_BOUNDARY_COLOR).a = TACTICAL_BORDER_ALPHA
        shape.color = color
        shape.rect(0f, 0f, mapProvider.mapWidth, mapProvider.mapHeight)
        shape.end()
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val baseInfo = entity.baseInfoCmp
        val isPlayer = entity.playerCmpOptional != null
        color.set(if (isPlayer) PLAYER_MARKER_COLOR else baseInfo.faction.color).a = TACTICAL_ALPHA
        shape.color = color
        val transform = entity.transformCmp
        val x = transform.interpolatedPosition.x
        val y = transform.interpolatedPosition.y
        val angle = transform.interpolatedRotationDeg * MathUtils.degreesToRadians
        val body = entity.bodyCmp.body

        val minRadius = TACTICAL_MIN_RADIUS_PX * wpp
        for (fixture in body.fixtureList) {
            val n = outlineVertices(fixture.shape, x, y, angle, minRadius, verts)
            if (n < 2) continue
            for (i in 0 until n) {
                val j = if (i + 1 == n) 0 else i + 1
                shape.line(verts[2 * i], verts[2 * i + 1], verts[2 * j], verts[2 * j + 1])
            }
        }

        if (baseInfo.entityType == EntityType.PROJECTILE) return
        val v = entity.relativisticCmpOptional?.vSet ?: body.linearVelocity
        if (hypot(v.x, v.y) < TACTICAL_MIN_ARROW_BETA * relativity.c) return
        arrowSegments(
            x, y, v.x, v.y, tacticalArrowScale(relativity.c), TACTICAL_ARROW_HEAD_PX * wpp, headAngleRad, seg,
        )
        for (i in 0 until ARROW_FLOATS step 4) shape.line(seg[i], seg[i + 1], seg[i + 2], seg[i + 3])
    }

    override fun dispose() {
        shape.dispose()
    }
}
