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
import com.github.pepek42.asteroids.component.bodyCmpOptional
import com.github.pepek42.asteroids.render.TARGET_RING_PULSE_PERIOD_S
import com.github.pepek42.asteroids.component.playerCmpOptional
import com.github.pepek42.asteroids.component.relativisticCmpOptional
import com.github.pepek42.asteroids.component.tacticalComputerCmpOptional
import com.github.pepek42.asteroids.component.transformCmp
import com.github.pepek42.asteroids.component.weaponCmpOptional
import com.github.pepek42.asteroids.component.HealthComponent
import com.github.pepek42.asteroids.component.removeMapper
import com.github.pepek42.asteroids.faction.FactionEnum
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.event.PlayerInputListener
import com.github.pepek42.asteroids.input.ControlScheme
import com.github.pepek42.asteroids.input.ControlSettings
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.pilotHullToWorld
import com.github.pepek42.asteroids.physics.trueLeadDirection
import com.github.pepek42.asteroids.provider.MapProvider
import com.github.pepek42.asteroids.render.LASER_DAMAGE_LABEL_MIN_FACTOR
import com.github.pepek42.asteroids.render.displayedPhotonFactor
import com.github.pepek42.asteroids.render.formatDamageFactor
import com.github.pepek42.asteroids.render.LOCK_RETICLE_RADIUS_PX
import com.github.pepek42.asteroids.render.LOCK_RETICLE_TICK_GAP_PX
import com.github.pepek42.asteroids.render.LOCK_RETICLE_TICK_LEN_PX
import com.github.pepek42.asteroids.render.PilotViewProjector
import com.github.pepek42.asteroids.render.TACTICAL_ARROW_HEAD_PX
import com.github.pepek42.asteroids.render.TARGET_RING_GLOW_STEP_PX
import com.github.pepek42.asteroids.render.TARGET_RING_LAYER_ALPHA
import com.github.pepek42.asteroids.render.TARGET_RING_SEGMENTS
import com.github.pepek42.asteroids.render.targetRingLayerAlpha
import com.github.pepek42.asteroids.render.targetRingRadius
import com.github.pepek42.asteroids.component.computeBoundingRadius
import com.github.pepek42.asteroids.ecs.levelTargetFamily
import com.github.pepek42.asteroids.ui.TARGET_RING_COLOR
import com.github.pepek42.asteroids.render.TACTICAL_LEAD_MAX
import com.github.pepek42.asteroids.render.TACTICAL_LEAD_RING_PX
import com.github.pepek42.asteroids.render.TACTICAL_LEAD_RING_SEGMENTS
import com.github.pepek42.asteroids.render.TACTICAL_MIN_ARROW_BETA
import com.github.pepek42.asteroids.render.TACTICAL_MIN_RADIUS_PX
import com.github.pepek42.asteroids.render.TACTICAL_OUTLINE_MAX_VERTS
import com.github.pepek42.asteroids.render.TACTICAL_TIER_INTERCEPT
import com.github.pepek42.asteroids.render.TACTICAL_TIER_NONE
import com.github.pepek42.asteroids.render.insertNearest
import com.github.pepek42.asteroids.render.tacticalLeadRange
import com.github.pepek42.asteroids.render.outlineVertices
import com.github.pepek42.asteroids.render.tacticalArrowScale
import com.github.pepek42.asteroids.render.tacticalOverlayVisible
import com.github.pepek42.asteroids.render.worldPerPixel
import com.github.pepek42.asteroids.settings.TacticalSettings
import com.github.pepek42.asteroids.ui.ARENA_BOUNDARY_COLOR
import com.github.pepek42.asteroids.ui.LOCK_RETICLE_ALPHA
import com.github.pepek42.asteroids.ui.LOCK_RETICLE_COLOR
import com.github.pepek42.asteroids.ui.PLAYER_MARKER_COLOR
import com.github.pepek42.asteroids.ui.TACTICAL_ALPHA
import com.github.pepek42.asteroids.ui.TACTICAL_BORDER_ALPHA
import com.github.pepek42.asteroids.ui.DAMAGE_UP_COLOR
import com.github.pepek42.asteroids.ui.DAMAGE_DOWN_COLOR
import ktx.ashley.allOf
import ktx.ashley.exclude
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Tactical computer overlay (ST-123): thin outlines of every body at its true interpolated arena position, with
 * arena-frame velocity arrows and the arena border, drawn over the (possibly light-delayed) main view.
 * Tier 2 (ST-125) adds intercept markers: for the nearest targets, a ring where to point so a photon laser hits, computed
 * from the true arena state (the supercomputer cheat, physics-lies row 34).
 */
class TacticalOverlaySystem(
    private val camera: OrthographicCamera,
    private val viewport: Viewport,
    private val mapProvider: MapProvider,
    private val relativity: RelativitySettings,
    private val tactical: TacticalSettings,
    private val gameEventManager: GameEventManager,
    private val controls: ControlSettings,
    private val shape: ShapeRenderer = ShapeRenderer(),
    private val batch: SpriteBatch? = null,
    private val font: BitmapFont? = null,
    private val pilotView: PilotViewProjector? = null,
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
    private val leadDist2 = FloatArray(TACTICAL_LEAD_MAX)
    private val leadIndex = IntArray(TACTICAL_LEAD_MAX)
    private val leadDir = Vector2()
    private val muzzle = Vector2()
    private val reticlePos = Vector2()
    private val ringPos = Vector2()
    private var levelTargets: ImmutableArray<Entity>? = null
    private var ringTime = 0f

    override fun addedToEngine(engine: Engine) {
        super.addedToEngine(engine)
        players = engine.getEntitiesFor(allOf(PlayerComponent::class, TransformComponent::class).get())
        candidates = engine.getEntitiesFor(allOf(HealthComponent::class, BodyComponent::class, BaseInfoComponent::class).get())
        playerInput = engine.getSystem(PlayerInputSystem::class.java)
        levelTargets = engine.getEntitiesFor(levelTargetFamily())
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
        ringTime = (ringTime + deltaTime) % TARGET_RING_PULSE_WRAP_S
        drawTargetRings()
        drawLockReticle()
        if (!tacticalOverlayVisible(tactical.mode, relativity.pilotView, tier, tactical.inverted)) return

        viewport.apply()
        wpp = worldPerPixel(camera.viewportWidth, camera.zoom, viewport.screenWidth)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        // Separate alpha func keeps the framebuffer alpha opaque (matters for screenshots)
        Gdx.gl.glBlendFuncSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_ZERO, GL20.GL_ONE)
        shape.projectionMatrix = camera.combined
        shape.begin(ShapeRenderer.ShapeType.Line)
        super.update(deltaTime)
        if (tier >= TACTICAL_TIER_INTERCEPT && tactical.showInterceptMarkers) drawInterceptMarkers()
        color.set(ARENA_BOUNDARY_COLOR).a = TACTICAL_BORDER_ALPHA
        shape.color = color
        shape.rect(0f, 0f, mapProvider.mapWidth, mapProvider.mapHeight)
        shape.end()
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        drawLaserDamageLabel()
    }

    /**
     * Target rings (owner 2026-11-07): a glowing red ring around every level target (asteroids and enemy ships, the set
     * the level-clear check waits for), at the position its sprite is drawn (apparent centre in pilot view). Independent of
     * the overlay toggle, like the lock reticle. The glow is a few concentric 1 px rings with falling alpha under additive
     * blending (WebGL has no wide lines), whose outer layers pulse gently.
     */
    private fun drawTargetRings() {
        if (!tactical.showTargetRings) return
        val targets = levelTargets ?: return
        if (targets.size() == 0) return
        val view = pilotView
        val apparent = relativity.pilotView && view != null
        if (apparent) {
            val player = players?.let { if (it.size() > 0) it[0] else null } ?: return
            val p = player.transformCmp.interpolatedPosition
            view!!.begin(p.x, p.y)
        }
        viewport.apply()
        wpp = worldPerPixel(camera.viewportWidth, camera.zoom, viewport.screenWidth)
        val step = TARGET_RING_GLOW_STEP_PX * wpp
        Gdx.gl.glEnable(GL20.GL_BLEND)
        // Additive colour, framebuffer alpha kept opaque.
        Gdx.gl.glBlendFuncSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE, GL20.GL_ZERO, GL20.GL_ONE)
        shape.projectionMatrix = camera.combined
        shape.begin(ShapeRenderer.ShapeType.Line)
        for (i in 0 until targets.size()) {
            val e = targets[i]
            if (apparent) {
                if (!view!!.apparentCentre(e, ringPos)) continue
            } else {
                ringPos.set(e.transformCmp.interpolatedPosition)
            }
            val bodyCmp = e.bodyCmpOptional ?: continue
            if (bodyCmp.boundingRadius < 0f) bodyCmp.boundingRadius = computeBoundingRadius(bodyCmp.body)
            val r = targetRingRadius(bodyCmp.boundingRadius, wpp)
            for (layer in TARGET_RING_LAYER_ALPHA.indices) {
                color.set(TARGET_RING_COLOR).a = targetRingLayerAlpha(layer, ringTime)
                shape.color = color
                if (layer == 0) {
                    shape.circle(ringPos.x, ringPos.y, r, TARGET_RING_SEGMENTS)
                } else {
                    shape.circle(ringPos.x, ringPos.y, r + layer * step, TARGET_RING_SEGMENTS)
                    shape.circle(ringPos.x, ringPos.y, maxOf(r - layer * step, step), TARGET_RING_SEGMENTS)
                }
            }
        }
        shape.end()
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    /**
     * Auto-aim lock-on reticle (ST-142): circle plus four ticks on the locked target, constant screen size, at the position
     * its sprite is drawn (apparent, light-delayed centre in pilot view, interpolated position in world view). Part of
     * auto-aim, so independent of the overlay toggle. Runs after the render system, so it is drawn over the sprites.
     */
    private fun drawLockReticle() {
        val target = playerInput?.autoAimTarget ?: return
        if (removeMapper.has(target)) return
        val player = players?.let { if (it.size() > 0) it[0] else null } ?: return
        val view = pilotView
        if (relativity.pilotView && view != null) {
            val p = player.transformCmp.interpolatedPosition
            view.begin(p.x, p.y)
            if (!view.apparentCentre(target, reticlePos)) return
        } else {
            reticlePos.set(target.transformCmp.interpolatedPosition)
        }
        viewport.apply()
        wpp = worldPerPixel(camera.viewportWidth, camera.zoom, viewport.screenWidth)
        val radius = LOCK_RETICLE_RADIUS_PX * wpp
        val inner = radius + LOCK_RETICLE_TICK_GAP_PX * wpp
        val outer = inner + LOCK_RETICLE_TICK_LEN_PX * wpp
        val x = reticlePos.x
        val y = reticlePos.y
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFuncSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_ZERO, GL20.GL_ONE)
        shape.projectionMatrix = camera.combined
        shape.begin(ShapeRenderer.ShapeType.Line)
        color.set(LOCK_RETICLE_COLOR).a = LOCK_RETICLE_ALPHA
        shape.color = color
        shape.circle(x, y, radius, TARGET_RING_SEGMENTS)
        shape.line(x - outer, y, x - inner, y)
        shape.line(x + inner, y, x + outer, y)
        shape.line(x, y - outer, x, y - inner)
        shape.line(x, y + inner, x, y + outer)
        shape.end()
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    /**
     * Intercept markers (ST-125): ring at the point to aim at so a photon hits each of the nearest [TACTICAL_LEAD_MAX]
     * targets, with a line from the target. Uses the true state of shooter and targets. Must run inside the shape `Line` batch.
     */
    private fun drawInterceptMarkers() {
        val player = players?.let { if (it.size() > 0) it[0] else null } ?: return
        val list = candidates ?: return
        val c = relativity.c
        val range2 = tacticalLeadRange(c).let { it * it }
        // The shooter point is the muzzle (ST-137), so close targets have no parallax error against the hull centre.
        val sPos = muzzleWorld(player, muzzle)
        val sx = sPos.x
        val sy = sPos.y
        var n = 0
        for (i in 0 until list.size()) {
            val e = list[i]
            if (e === player || removeMapper.has(e)) continue
            val tPos = e.transformCmp.interpolatedPosition
            val dx = tPos.x - sx
            val dy = tPos.y - sy
            val d2 = dx * dx + dy * dy
            if (d2 > range2) continue
            n = insertNearest(leadDist2, leadIndex, n, d2, i)
        }
        if (n == 0) return

        val relOn = relativity.relativityEnabled
        val pv = player.relativisticCmpOptional?.vSet ?: player.bodyCmp.body.linearVelocity
        val bx = if (relOn) pv.x / c else 0f
        val by = if (relOn) pv.y / c else 0f
        val aimsTurret = relativity.pilotHullTransform || controls.scheme == ControlScheme.AUTO_AIM ||
            controls.scheme == ControlScheme.TWIN_STICK
        val ringRadius = TACTICAL_LEAD_RING_PX * wpp
        for (k in 0 until n) {
            val target = list[leadIndex[k]]
            val tPos = target.transformCmp.interpolatedPosition
            val tv = target.relativisticCmpOptional?.vSet ?: target.bodyCmp.body.linearVelocity
            val t = trueLeadDirection(sx, sy, bx, by, tPos.x, tPos.y, tv.x, tv.y, c, leadDir)
            if (t.isNaN()) continue
            val hitX = tPos.x + tv.x * t
            val hitY = tPos.y + tv.y * t
            val range = hypot(hitX - sx, hitY - sy)
            var dirX = leadDir.x
            var dirY = leadDir.y
            if (!aimsTurret) {
                // Outside view + mouse-hull fires along worldHullToPilot(hull), so the cursor must sit on the W hull angle.
                val hull = pilotHullToWorld(atan2(dirY, dirX), bx, by)
                dirX = cos(hull)
                dirY = sin(hull)
            }
            val mx = sx + dirX * range
            val my = sy + dirY * range
            color.set(target.baseInfoCmp.faction.color).a = TACTICAL_ALPHA
            shape.color = color
            shape.circle(mx, my, ringRadius, TACTICAL_LEAD_RING_SEGMENTS)
            shape.line(tPos.x, tPos.y, mx, my)
        }
    }

    /**
     * Muzzle world position (ST-137) into [out], as `WeaponProjectileProvider` spawns it: the interpolated hull centre plus
     * `weaponPosition` rotated by the interpolated hull angle. Allocation-free.
     */
    private fun muzzleWorld(player: Entity, out: Vector2): Vector2 {
        val tr = player.transformCmp
        val pos = tr.interpolatedPosition
        out.set(pos.x, pos.y)
        val w = player.weaponCmpOptional?.weaponPosition ?: return out
        val angle = tr.interpolatedRotationDeg * MathUtils.degreesToRadians
        val cosA = cos(angle)
        val sinA = sin(angle)
        out.x = pos.x + w.x * cosA - w.y * sinA
        out.y = pos.y + w.x * sinA + w.y * cosA
        return out
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

        /** [ringTime] wraps at a whole number of pulses so float precision never degrades. */
        const val TARGET_RING_PULSE_WRAP_S = 100f * TARGET_RING_PULSE_PERIOD_S
    }
}
