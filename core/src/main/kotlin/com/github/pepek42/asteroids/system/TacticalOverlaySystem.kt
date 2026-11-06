package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
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
import com.github.pepek42.asteroids.component.HealthComponent
import com.github.pepek42.asteroids.component.removeMapper
import com.github.pepek42.asteroids.faction.FactionEnum
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.event.PlayerInputListener
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.provider.MapProvider
import com.github.pepek42.asteroids.render.LASER_DAMAGE_LABEL_MIN_FACTOR
import com.github.pepek42.asteroids.render.displayedPhotonFactor
import com.github.pepek42.asteroids.render.formatDamageFactor
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
import com.github.pepek42.asteroids.ui.DAMAGE_UP_COLOR
import com.github.pepek42.asteroids.ui.DAMAGE_DOWN_COLOR
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
    private val batch: SpriteBatch? = null,
    private val font: BitmapFont? = null,
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
    private var playerInput: PlayerInputSystem? = null
    private var candidates: ImmutableArray<Entity>? = null
    private val label = StringBuilder()
    private val screenProjection = Matrix4()
    private val labelPos = Vector3()
    private val aimDir = Vector2()

    override fun addedToEngine(engine: Engine) {
        super.addedToEngine(engine)
        players = engine.getEntitiesFor(allOf(PlayerComponent::class, TransformComponent::class).get())
        candidates = engine.getEntitiesFor(allOf(HealthComponent::class, BodyComponent::class, BaseInfoComponent::class).get())
        playerInput = engine.getSystem(PlayerInputSystem::class.java)
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
        drawLaserDamageLabel()
    }

    /**
     * Laser damage multiplier next to the aimed target (ST-133): the auto-aim target, else the target nearest the cursor.
     * Uses the true state (assist), never the delayed sprite.
     */
    private fun drawLaserDamageLabel() {
        val batch = batch ?: return
        val font = font ?: return
        if (!tactical.showLaserDamage || !relativity.relativityEnabled || !relativity.photonDopplerDamage) return
        val player = players?.let { if (it.size() > 0) it[0] else null } ?: return
        val target = playerInput?.autoAimTarget ?: nearestToCursor() ?: return
        val playerPos = player.transformCmp.interpolatedPosition
        val targetPos = target.transformCmp.interpolatedPosition
        val auto = playerInput?.autoAimTarget === target
        if (auto) {
            aimDir.set(playerInput!!.autoAimDir)
        } else {
            aimDir.set(targetPos).sub(playerPos)
            if (aimDir.isZero) return
        }
        aimDir.nor()
        val c = relativity.c
        val pv = player.relativisticCmpOptional?.vSet ?: player.bodyCmp.body.linearVelocity
        val tv = target.relativisticCmpOptional?.vSet ?: target.bodyCmp.body.linearVelocity
        val factor = displayedPhotonFactor(
            tactical.showLaserDamage, relativity.relativityEnabled,
            pv.x / c, pv.y / c, tv.x / c, tv.y / c, aimDir.x, aimDir.y,
        ) ?: return
        formatDamageFactor(factor, label)
        labelPos.set(targetPos.x, targetPos.y, 0f)
        camera.project(labelPos, viewport.screenX.toFloat(), viewport.screenY.toFloat(), viewport.screenWidth.toFloat(), viewport.screenHeight.toFloat())
        screenProjection.setToOrtho2D(0f, 0f, Gdx.graphics.backBufferWidth.toFloat(), Gdx.graphics.backBufferHeight.toFloat())
        batch.projectionMatrix = screenProjection
        font.color = if (factor >= LASER_DAMAGE_LABEL_MIN_FACTOR) DAMAGE_UP_COLOR else DAMAGE_DOWN_COLOR
        batch.begin()
        font.draw(batch, label, labelPos.x + LASER_LABEL_OFFSET_PX, labelPos.y + LASER_LABEL_OFFSET_PX)
        batch.end()
        font.setColor(1f, 1f, 1f, 1f)
    }

    private fun nearestToCursor(): Entity? {
        val input = playerInput ?: return null
        val list = candidates ?: return null
        val cursor = input.aimWorld
        var best: Entity? = null
        var bestDist2 = Float.MAX_VALUE
        for (i in 0 until list.size()) {
            val e = list[i]
            if (e.baseInfoCmp.faction == FactionEnum.PLAYER || removeMapper.has(e)) continue
            val pos = e.transformCmp.interpolatedPosition
            val d2 = pos.dst2(cursor)
            if (d2 < bestDist2) {
                bestDist2 = d2
                best = e
            }
        }
        return best
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

    private companion object {
        /** Screen-pixel offset of the laser damage label from the target centre. */
        const val LASER_LABEL_OFFSET_PX = 14f
    }
}
