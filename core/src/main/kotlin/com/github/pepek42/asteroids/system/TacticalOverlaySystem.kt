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
import com.github.pepek42.asteroids.render.PILOT_BAND_SEGMENTS
import com.github.pepek42.asteroids.render.PilotViewProjector
import com.github.pepek42.asteroids.render.buildOutlineVertices
import com.github.pepek42.asteroids.render.outlineVertexFloats
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
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.trueLeadDirection
import com.github.pepek42.asteroids.provider.MapProvider
import com.github.pepek42.asteroids.render.LASER_DAMAGE_LABEL_MIN_FACTOR
import com.github.pepek42.asteroids.render.displayedPhotonFactor
import com.github.pepek42.asteroids.render.formatDamageFactor
import com.github.pepek42.asteroids.render.LOCK_RETICLE_RADIUS_PX
import com.github.pepek42.asteroids.render.LOCK_RETICLE_TICK_GAP_PX
import com.github.pepek42.asteroids.render.LOCK_RETICLE_TICK_LEN_PX
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
import com.github.pepek42.asteroids.render.THREAT_CROSS_PX
import com.github.pepek42.asteroids.render.THREAT_HORIZON_LIGHT_SECONDS
import com.github.pepek42.asteroids.render.THREAT_MISS_RADIUS_FACTOR
import com.github.pepek42.asteroids.render.closestApproach
import com.github.pepek42.asteroids.render.edgePointer
import com.github.pepek42.asteroids.render.threatAlpha
import com.github.pepek42.asteroids.component.ProjectileComponent
import com.github.pepek42.asteroids.render.insertNearest
import com.github.pepek42.asteroids.component.LockOnComponent
import com.github.pepek42.asteroids.component.RocketComponent
import com.github.pepek42.asteroids.component.lockOnCmp
import com.github.pepek42.asteroids.component.baseInfoMapper
import com.github.pepek42.asteroids.render.LOCK_ON_MARKER_LINE_ALPHA
import com.github.pepek42.asteroids.render.LOCK_ON_MARKER_PX
import com.github.pepek42.asteroids.render.lockOnChevron
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
import com.github.pepek42.asteroids.component.weaponLoadoutCmpOptional
import com.github.pepek42.asteroids.ui.TARGET_LOCKED_COLOR
import com.github.pepek42.asteroids.ui.TARGET_LOCKING_COLOR
import com.github.pepek42.asteroids.weapon.TargetLockState
import com.github.pepek42.asteroids.weapon.WeaponType
import com.github.pepek42.asteroids.weapon.lockFraction
import com.github.pepek42.asteroids.weapon.lockReticleRadius
import com.github.pepek42.asteroids.weapon.rocketInterceptDir
import com.github.pepek42.asteroids.weapon.rocketLaunchAim
import com.github.pepek42.asteroids.input.AUTO_AIM_RANGE
import ktx.ashley.allOf
import ktx.ashley.exclude
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Floats in a lock-on chevron: tip, left wing, right wing (x, y each). */
private const val CHEVRON_FLOATS = 6

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
    private val borderVerts = FloatArray(outlineVertexFloats(PILOT_BAND_SEGMENTS))
    private val borderScratch = Vector2()
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
    private var projectiles: ImmutableArray<Entity>? = null
    private var lockOnShots: ImmutableArray<Entity>? = null
    private var rockets: ImmutableArray<Entity>? = null
    private val chevron = FloatArray(CHEVRON_FLOATS)
    private val miss = Vector2()
    private val edgePos = Vector2()

    override fun addedToEngine(engine: Engine) {
        super.addedToEngine(engine)
        players = engine.getEntitiesFor(allOf(PlayerComponent::class, TransformComponent::class).get())
        candidates = engine.getEntitiesFor(allOf(HealthComponent::class, BodyComponent::class, BaseInfoComponent::class).get())
        playerInput = engine.getSystem(PlayerInputSystem::class.java)
        levelTargets = engine.getEntitiesFor(levelTargetFamily())
        projectiles = engine.getEntitiesFor(
            allOf(ProjectileComponent::class, BaseInfoComponent::class, BodyComponent::class, TransformComponent::class).get(),
        )
        lockOnShots = engine.getEntitiesFor(
            allOf(LockOnComponent::class, TransformComponent::class, BaseInfoComponent::class).exclude(RemoveComponent::class).get(),
        )
        rockets = engine.getEntitiesFor(
            allOf(RocketComponent::class, TransformComponent::class, BaseInfoComponent::class).exclude(RemoveComponent::class).get(),
        )
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
        val player = players?.let { if (it.size() > 0) it[0] else null }
        val tier = player?.tacticalComputerCmpOptional?.tier ?: TACTICAL_TIER_NONE
        ringTime = (ringTime + deltaTime) % TARGET_RING_PULSE_WRAP_S
        drawTargetRings()
        drawLockReticle()
        drawTargetLock()
        drawLockOnMarkers()
        drawRocketMarkers()
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
        if (tactical.showIncomingFire) drawIncomingFire()
        color.set(ARENA_BOUNDARY_COLOR).a = TACTICAL_BORDER_ALPHA
        shape.color = color
        drawBorder(player != null)
        shape.end()
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        drawLaserDamageLabel()
    }

    /**
     * The arena border. In pilot view with the aberration boost on (ST-201) it is cut into [PILOT_BAND_SEGMENTS] per side and
     * boosted with the same map as the field band, so it bends with it; otherwise it is the plain rectangle. Needs the
     * [pilotView] latched this frame by `RenderSystem`; the reference grid is a separate computer assist and stays straight.
     */
    private fun drawBorder(hasPlayer: Boolean) {
        val pv = pilotView
        val bend = pv != null && hasPlayer && relativity.pilotView && pv.aberrationEnabled
        val n = buildOutlineVertices(
            borderVerts, mapProvider.mapWidth, mapProvider.mapHeight, if (bend) PILOT_BAND_SEGMENTS else 1, bend,
            pv?.ox ?: 0f, pv?.oy ?: 0f, pv?.betaX ?: 0f, pv?.betaY ?: 0f, borderScratch,
        )
        shape.polyline(borderVerts, 0, n)
    }

    /**
     * Target rings (owner 2026-11-07): a glowing red ring around every level target (asteroids and enemy ships, the set
     * the level-clear check waits for), at its TRUE position (computer assist, owner 2026-11-07; in pilot view it can sit apart from the light-delayed
     * sprite). Independent of
     * the overlay toggle, like the lock reticle. The glow is a few concentric 1 px rings with falling alpha under additive
     * blending (WebGL has no wide lines), whose outer layers pulse gently.
     */
    private fun drawTargetRings() {
        if (!tactical.showTargetRings) return
        val targets = levelTargets ?: return
        if (targets.size() == 0) return
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
            ringPos.set(e.transformCmp.interpolatedPosition)
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
     * Auto-aim lock-on reticle (ST-142): circle plus four ticks on the locked target, constant screen size, at the target's
     * TRUE interpolated position (computer assist, also in pilot view, owner 2026-11-07). Part of
     * auto-aim, so independent of the overlay toggle. Runs after the render system, so it is drawn over the sprites.
     */
    private fun drawLockReticle() {
        val target = playerInput?.autoAimTarget ?: return
        if (removeMapper.has(target)) return
        reticlePos.set(target.transformCmp.interpolatedPosition)
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
     * Slot-2 targeting lock (ST-165), independent of the overlay toggle like the lock reticle, at the target's TRUE
     * interpolated position: LOCKING = an amber diamond closing in on the target ring over the lock time, LOCKED = a
     * red double diamond on the ring.
     */
    private fun drawTargetLock() {
        val player = players?.let { if (it.size() > 0) it[0] else null } ?: return
        val loadout = player.weaponLoadoutCmpOptional ?: return
        if (loadout.lockState == TargetLockState.NONE) return
        val target = loadout.lockTarget ?: return
        if (removeMapper.has(target)) return
        val bodyCmp = target.bodyCmpOptional ?: return
        if (bodyCmp.boundingRadius < 0f) bodyCmp.boundingRadius = computeBoundingRadius(bodyCmp.body)
        viewport.apply()
        wpp = worldPerPixel(camera.viewportWidth, camera.zoom, viewport.screenWidth)
        val ring = targetRingRadius(bodyCmp.boundingRadius, wpp)
        val locked = loadout.lockState == TargetLockState.LOCKED
        val r = if (locked) ring else lockReticleRadius(ring, loadout.lockFraction())
        reticlePos.set(target.transformCmp.interpolatedPosition)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFuncSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_ZERO, GL20.GL_ONE)
        shape.projectionMatrix = camera.combined
        shape.begin(ShapeRenderer.ShapeType.Line)
        shape.color = if (locked) TARGET_LOCKED_COLOR else TARGET_LOCKING_COLOR
        diamond(reticlePos.x, reticlePos.y, r)
        if (locked) diamond(reticlePos.x, reticlePos.y, r + TARGET_LOCK_DIAMOND_GAP_PX * wpp)
        shape.end()
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    /**
     * Lock-on shot markers (ST-177): an open chevron at the TRUE interpolated position of every live lock-on shot, pointing
     * along its heading, in its faction colour, plus a faint line to its target while that is still alive (same check as
     * `LockOnSteeringSystem`). Computer assist for pilot view, where the shot sprite is light-delayed. Independent of the
     * overlay toggle. Draw only, allocation-free.
     */
    private fun drawLockOnMarkers() {
        if (!tactical.showLockOnMarkers) return
        val shots = lockOnShots ?: return
        if (shots.size() == 0) return
        viewport.apply()
        wpp = worldPerPixel(camera.viewportWidth, camera.zoom, viewport.screenWidth)
        val size = LOCK_ON_MARKER_PX * wpp
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFuncSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_ZERO, GL20.GL_ONE)
        shape.projectionMatrix = camera.combined
        shape.begin(ShapeRenderer.ShapeType.Line)
        for (i in 0 until shots.size()) {
            val e = shots[i]
            val tr = e.transformCmp
            val pos = tr.interpolatedPosition
            val faction = e.baseInfoCmp.faction.color
            lockOnChevron(pos.x, pos.y, tr.interpolatedRotationDeg * MathUtils.degreesToRadians, size, chevron)
            color.set(faction).a = 1f
            shape.color = color
            shape.line(chevron[0], chevron[1], chevron[2], chevron[3])
            shape.line(chevron[0], chevron[1], chevron[4], chevron[5])
            val target = liveLockOnTarget(e.lockOnCmp) ?: continue
            val tPos = target.transformCmp.interpolatedPosition
            color.set(faction).a = LOCK_ON_MARKER_LINE_ALPHA
            shape.color = color
            shape.line(pos.x, pos.y, tPos.x, tPos.y)
        }
        shape.end()
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    /**
     * Pod rocket markers (ST-191): the same open chevron as the lock-on shot markers, at the TRUE interpolated position of
     * every rocket in flight, along its heading, in its faction colour. Rockets have no target, so no line. Same toggle
     * as [drawLockOnMarkers] (`tactical.showLockOnMarkers`). Draw only, allocation-free.
     */
    private fun drawRocketMarkers() {
        if (!tactical.showLockOnMarkers) return
        val list = rockets ?: return
        if (list.size() == 0) return
        viewport.apply()
        wpp = worldPerPixel(camera.viewportWidth, camera.zoom, viewport.screenWidth)
        val size = LOCK_ON_MARKER_PX * wpp
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFuncSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_ZERO, GL20.GL_ONE)
        shape.projectionMatrix = camera.combined
        shape.begin(ShapeRenderer.ShapeType.Line)
        for (i in 0 until list.size()) {
            val e = list[i]
            val tr = e.transformCmp
            val pos = tr.interpolatedPosition
            lockOnChevron(pos.x, pos.y, tr.interpolatedRotationDeg * MathUtils.degreesToRadians, size, chevron)
            color.set(e.baseInfoCmp.faction.color).a = 1f
            shape.color = color
            shape.line(chevron[0], chevron[1], chevron[2], chevron[3])
            shape.line(chevron[0], chevron[1], chevron[4], chevron[5])
        }
        shape.end()
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    /** The shot's target while it is the same live entity (id guard and `body.userData` identity), else null. */
    private fun liveLockOnTarget(shot: LockOnComponent): Entity? {
        val target = shot.target ?: return null
        if (removeMapper.has(target)) return null
        val info = baseInfoMapper.get(target) ?: return null
        if (info.id != shot.targetId) return null
        val body = target.bodyCmpOptional?.body ?: return null
        return if (body.userData === target) target else null
    }

    private fun diamond(x: Float, y: Float, r: Float) {
        shape.line(x + r, y, x, y + r)
        shape.line(x, y + r, x - r, y)
        shape.line(x - r, y, x, y - r)
        shape.line(x, y - r, x + r, y)
    }

    /**
     * Intercept markers (ST-125): ring at the point to aim at so the selected weapon hits each of the nearest
     * [TACTICAL_LEAD_MAX] targets, with a line from the target. Uses the true state of shooter and targets. Photon
     * lasers use [trueLeadDirection]; with the pod rocket selected (ST-182) the ring shows where the rocket's real
     * accelerating flight meets the target ([rocketInterceptDir]). Must run inside the shape `Line` batch.
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
        val rockets = player.weaponCmpOptional?.weaponType == WeaponType.ROCKET
        val ringRadius = TACTICAL_LEAD_RING_PX * wpp
        for (k in 0 until n) {
            val target = list[leadIndex[k]]
            val tPos = target.transformCmp.interpolatedPosition
            val tv = target.relativisticCmpOptional?.vSet ?: target.bodyCmp.body.linearVelocity
            // Rockets: the same solve auto-aim fires with (spawn offset, ship velocity, AUTO_AIM_RANGE flight limit).
            // Deliberately AUTO_AIM_RANGE, not tacticalLeadRange(c): the ring shows exactly what auto-aim would fire.
            val t = if (rockets) {
                rocketLaunchAim(sx, sy, pv.x, pv.y, tPos.x, tPos.y, tv.x, tv.y, relativity, AUTO_AIM_RANGE, leadDir)
            } else {
                trueLeadDirection(sx, sy, bx, by, tPos.x, tPos.y, tv.x, tv.y, c, leadDir)
            }
            if (t.isNaN()) continue
            val hitX = tPos.x + tv.x * t
            val hitY = tPos.y + tv.y * t
            val range = hypot(hitX - sx, hitY - sy)
            // A rocket path bends, so the meeting point is not on the launch ray; a photon's is (that is what leadDir
            // encodes), and the photon marker keeps the ST-125 ray placement unchanged.
            val mx = if (rockets) hitX else sx + leadDir.x * range
            val my = if (rockets) hitY else sy + leadDir.y * range
            color.set(target.baseInfoCmp.faction.color).a = TACTICAL_ALPHA
            shape.color = color
            shape.circle(mx, my, ringRadius, TACTICAL_LEAD_RING_SEGMENTS)
            shape.line(tPos.x, tPos.y, mx, my)
        }
    }

    /**
     * Incoming-fire warning (ST-158): for each enemy photon whose closest approach to the player (true state, constant
     * velocities) is within [THREAT_HORIZON_LIGHT_SECONDS] and [THREAT_MISS_RADIUS_FACTOR] player radii, a red line from
     * the photon to its impact point and a cross there; an edge arrow when the photon is off screen. Must run inside the
     * shape `Line` batch. Allocation-free.
     */
    private fun drawIncomingFire() {
        val player = players?.let { if (it.size() > 0) it[0] else null } ?: return
        val list = projectiles ?: return
        val pBody = player.bodyCmp
        if (pBody.boundingRadius < 0f) pBody.boundingRadius = computeBoundingRadius(pBody.body)
        val missRadius = THREAT_MISS_RADIUS_FACTOR * pBody.boundingRadius
        val pPos = player.transformCmp.interpolatedPosition
        val sv = player.relativisticCmpOptional?.vSet ?: pBody.body.linearVelocity
        val halfW = camera.viewportWidth * camera.zoom / 2f
        val halfH = camera.viewportHeight * camera.zoom / 2f
        val margin = THREAT_EDGE_MARGIN_FRACTION * halfH
        val cross = THREAT_CROSS_PX * wpp
        val head = TACTICAL_ARROW_HEAD_PX * wpp
        for (i in 0 until list.size()) {
            val e = list[i]
            if (e.baseInfoCmp.faction != FactionEnum.ENEMY || removeMapper.has(e)) continue
            val pos = e.transformCmp.interpolatedPosition
            val v = e.bodyCmp.body.linearVelocity
            val t = closestApproach(pos.x, pos.y, v.x, v.y, pPos.x, pPos.y, sv.x, sv.y, miss)
            if (t >= THREAT_HORIZON_LIGHT_SECONDS || miss.len() >= missRadius) continue
            val ix = pos.x + v.x * t
            val iy = pos.y + v.y * t
            color.set(FactionEnum.ENEMY.color).a = threatAlpha(t)
            shape.color = color
            shape.line(pos.x, pos.y, ix, iy)
            shape.line(ix - cross, iy - cross, ix + cross, iy + cross)
            shape.line(ix - cross, iy + cross, ix + cross, iy - cross)
            val angle = edgePointer(camera.position.x, camera.position.y, halfW, halfH, pos.x, pos.y, margin, edgePos)
            if (angle.isNaN()) continue
            val back = angle + Math.PI.toFloat() - headAngleRad
            val back2 = angle + Math.PI.toFloat() + headAngleRad
            shape.line(edgePos.x, edgePos.y, edgePos.x + cos(back) * head, edgePos.y + sin(back) * head)
            shape.line(edgePos.x, edgePos.y, edgePos.x + cos(back2) * head, edgePos.y + sin(back2) * head)
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

        /** Gap between the two lines of the LOCKED diamond, in screen pixels (WebGL has no wide lines). */
        const val TARGET_LOCK_DIAMOND_GAP_PX = 2f

        /** Inset of the incoming-fire edge arrows from the screen edge, as a fraction of the half height. */
        const val THREAT_EDGE_MARGIN_FRACTION = 0.08f

        /** [ringTime] wraps at a whole number of pulses so float precision never degrades. */
        const val TARGET_RING_PULSE_WRAP_S = 100f * TARGET_RING_PULSE_PERIOD_S
    }
}
