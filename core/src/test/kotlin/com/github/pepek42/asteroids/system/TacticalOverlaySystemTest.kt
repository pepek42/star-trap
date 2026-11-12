package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.PooledEngine
import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.RandomXS128
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.GdxNativesLoader
import com.badlogic.gdx.utils.viewport.Viewport
import com.github.pepek42.asteroids.component.RemoveComponent
import com.github.pepek42.asteroids.component.TACTICAL_COMPUTER_DEFAULT_TIER
import com.github.pepek42.asteroids.component.TacticalComputerComponent
import com.github.pepek42.asteroids.component.asteroidMapper
import com.github.pepek42.asteroids.component.playerCmpOptional
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.relativisticCmp
import com.github.pepek42.asteroids.component.transformCmp
import com.github.pepek42.asteroids.component.tacticalComputerCmp
import com.github.pepek42.asteroids.component.weaponCmp
import com.github.pepek42.asteroids.environment.AsteroidSize
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.input.ControlScheme
import com.github.pepek42.asteroids.input.ControlSettings
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.provider.AsteroidProvider
import com.github.pepek42.asteroids.provider.MapProvider
import com.github.pepek42.asteroids.provider.PlayerEntityProvider
import com.github.pepek42.asteroids.render.TACTICAL_CIRCLE_SEGMENTS
import com.github.pepek42.asteroids.render.TACTICAL_LEAD_MAX
import com.github.pepek42.asteroids.render.TACTICAL_MIN_ARROW_BETA
import com.github.pepek42.asteroids.render.TACTICAL_TIER_WIREFRAME
import com.github.pepek42.asteroids.render.tacticalLeadRange
import com.github.pepek42.asteroids.render.TACTICAL_TIER_INTERCEPT
import com.github.pepek42.asteroids.render.TACTICAL_TIER_NONE
import com.github.pepek42.asteroids.render.TacticalOverlayMode
import com.github.pepek42.asteroids.settings.TacticalSettings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class TacticalOverlaySystemTest {
    private val world = World(Vector2.Zero, true).apply { setAutoClearForces(false) }
    private val engine = PooledEngine()
    private val settings = RelativitySettings()
    // Target rings have their own test below; off here so the shape-call counts cover only the overlay.
    private val tactical = TacticalSettings().apply { showTargetRings = false }
    private val shape = mock<ShapeRenderer>()
    private val controls = ControlSettings()
    private val viewport = mock<Viewport> { on { screenWidth } doReturn 1280 }
    private val map = MapProvider(mock<GameEventManager>(), 200f, 200f)
    private val texture = mock<Texture> {
        on { width } doReturn 10
        on { height } doReturn 10
    }
    private val sprite = Sprite(texture)
    private val atlas = mock<TextureAtlas> {
        on { createSprite(AsteroidSize.LARGE.textureRegionName) } doReturn sprite
        on { createSprite("spaceship/disc_green") } doReturn sprite
        on { createSprite("player_lock_on") } doReturn sprite
    }
    private val system = TacticalOverlaySystem(OrthographicCamera(48f, 27f), viewport, map, settings, tactical, GameEventManager(), controls, shape)

    init {
        PlayerEntityProvider(engine, world, map, atlas, settings).spawnPlayerEntity()
        AsteroidProvider(atlas, engine, world, RandomXS128(1L), settings)
            .spawnAsteroid(AsteroidSize.LARGE, Vector2(100f, 100f), Vector2.Zero, 0f)
        engine.addSystem(system)
    }

    @AfterEach
    fun tearDown() = world.dispose()

    private fun asteroid(): Entity = engine.entities.first { asteroidMapper.get(it) != null }
    private fun player(): Entity = engine.entities.first { it.playerCmpOptional != null }

    private fun lineCalls(): Int {
        val captor = org.mockito.Mockito.mockingDetails(shape).invocations
            .count { it.method.name == "line" && it.arguments.size == 4 }
        return captor
    }

    private fun linesWithAsteroidVelocity(speed: Float): Int {
        asteroid().relativisticCmp.vSet.set(speed, 0f)
        org.mockito.Mockito.clearInvocations(shape)
        system.update(0.016f)
        return lineCalls()
    }

    @Test
    fun playerHasDefaultTierAndResetClearsIt() {
        assertEquals(TACTICAL_COMPUTER_DEFAULT_TIER, player().tacticalComputerCmp.tier)
        val c = TacticalComputerComponent().apply { tier = 2 }
        c.reset()
        assertEquals(TACTICAL_TIER_NONE, c.tier)
    }

    @Test
    fun drawsCircleOutlineOncePerFrame() {
        system.update(0.016f)
        verify(shape, times(1)).begin(ShapeRenderer.ShapeType.Line)
        verify(shape, times(1)).end()
        verify(shape, atLeast(TACTICAL_CIRCLE_SEGMENTS)).line(any<Float>(), any<Float>(), any<Float>(), any<Float>())
    }

    @Test
    fun noComputerOrModeOffDrawsNothing() {
        player().tacticalComputerCmp.tier = TACTICAL_TIER_NONE
        system.update(0.016f)
        verify(shape, never()).begin(ShapeRenderer.ShapeType.Line)
        player().tacticalComputerCmp.tier = TACTICAL_COMPUTER_DEFAULT_TIER
        tactical.mode = TacticalOverlayMode.OFF
        system.update(0.016f)
        verify(shape, never()).begin(ShapeRenderer.ShapeType.Line)
    }

    @Test
    fun fastAsteroidGetsThreeArrowSegments() {
        val rest = linesWithAsteroidVelocity(0f)
        val slow = linesWithAsteroidVelocity(TACTICAL_MIN_ARROW_BETA * settings.c * 0.5f)
        val fast = linesWithAsteroidVelocity(0.5f * settings.c)
        assertEquals(rest, slow)
        assertEquals(rest + 3, fast)
    }

    @Test
    fun removedEntityIsNotDrawn() {
        // Wireframe tier: no intercept marker lines, so only the outline segments of the asteroid count.
        player().tacticalComputerCmp.tier = TACTICAL_TIER_WIREFRAME
        val before = linesWithAsteroidVelocity(0f)
        asteroid().add(engine.createComponent(RemoveComponent::class.java))
        org.mockito.Mockito.clearInvocations(shape)
        system.update(0.016f)
        assertEquals(before - TACTICAL_CIRCLE_SEGMENTS, lineCalls())
    }

    private fun circleCalls(): Int =
        org.mockito.Mockito.mockingDetails(shape).invocations.count { it.method.name == "circle" }

    private fun moveAsteroidNearPlayer() {
        val p = player().transformCmp.interpolatedPosition
        asteroid().transformCmp.interpolatedPosition.set(p.x + 10f, p.y)
    }

    @Test
    fun wireframeTierDrawsNoMarkersAndInterceptTierDoes() {
        moveAsteroidNearPlayer()
        player().tacticalComputerCmp.tier = TACTICAL_TIER_WIREFRAME
        org.mockito.Mockito.clearInvocations(shape)
        system.update(0.016f)
        assertEquals(0, circleCalls())
        player().tacticalComputerCmp.tier = TACTICAL_TIER_INTERCEPT
        org.mockito.Mockito.clearInvocations(shape)
        system.update(0.016f)
        assertEquals(1, circleCalls())
    }

    @Test
    fun markerRingSitsOnTheLineToAStaticTarget() {
        // Shooter and target at rest: n' points at the target, so the ring is at the target (outside view, mouse-hull too).
        moveAsteroidNearPlayer()
        controls.scheme = ControlScheme.MOUSE_HULL
        org.mockito.Mockito.clearInvocations(shape)
        system.update(0.016f)
        val circle = org.mockito.Mockito.mockingDetails(shape).invocations.first { it.method.name == "circle" }
        assertEquals(asteroid().transformCmp.interpolatedPosition.x, circle.arguments[0] as Float, 1e-3f)
        assertEquals(asteroid().transformCmp.interpolatedPosition.y, circle.arguments[1] as Float, 1e-3f)
    }

    @Test
    fun markerRingSitsOnTheMuzzleToTargetLine() {
        // ST-137: the shooter point is the muzzle (hull centre + weaponPosition), not the hull centre.
        moveAsteroidNearPlayer()
        controls.scheme = ControlScheme.MOUSE_HULL
        val weaponOffset = Vector2(0f, 2f)
        player().weaponCmp.weaponPosition.set(weaponOffset)
        val playerTr = player().transformCmp
        val angle = playerTr.interpolatedRotationDeg * MathUtils.degreesToRadians
        val muzzleX = playerTr.interpolatedPosition.x + weaponOffset.x * MathUtils.cos(angle) - weaponOffset.y * MathUtils.sin(angle)
        val muzzleY = playerTr.interpolatedPosition.y + weaponOffset.x * MathUtils.sin(angle) + weaponOffset.y * MathUtils.cos(angle)
        org.mockito.Mockito.clearInvocations(shape)
        system.update(0.016f)
        val circle = org.mockito.Mockito.mockingDetails(shape).invocations.first { it.method.name == "circle" }
        val ringX = circle.arguments[0] as Float
        val ringY = circle.arguments[1] as Float
        val targetX = asteroid().transformCmp.interpolatedPosition.x
        val targetY = asteroid().transformCmp.interpolatedPosition.y
        // Static target: the ring sits on the target, which lies on the muzzle-to-target line (zero cross product, same side).
        assertEquals(targetX, ringX, 1e-3f)
        assertEquals(targetY, ringY, 1e-3f)
        val ax = ringX - muzzleX
        val ay = ringY - muzzleY
        val bx = targetX - muzzleX
        val by = targetY - muzzleY
        assertEquals(0f, ax * by - ay * bx, 1e-2f)
        assertTrue(ax * bx + ay * by > 0f)
    }

    @Test
    fun interceptMarkersToggleOffDrawsNoMarkers() {
        moveAsteroidNearPlayer()
        player().tacticalComputerCmp.tier = TACTICAL_TIER_INTERCEPT
        tactical.showInterceptMarkers = false
        org.mockito.Mockito.clearInvocations(shape)
        system.update(0.016f)
        assertEquals(0, circleCalls())
        tactical.showInterceptMarkers = true
        org.mockito.Mockito.clearInvocations(shape)
        system.update(0.016f)
        assertEquals(1, circleCalls())
    }

    @Test
    fun atMostLeadMaxMarkersAndOnlyWithinRange() {
        val p = player().transformCmp.interpolatedPosition
        repeat(9) { i ->
            AsteroidProvider(atlas, engine, world, RandomXS128(2L + i), settings)
                .spawnAsteroid(AsteroidSize.LARGE, Vector2(p.x + 5f + i, p.y + 3f), Vector2.Zero, 0f)
        }
        engine.entities.filter { asteroidMapper.get(it) != null }.forEach {
            val pos = it.bodyCmp.body.position
            it.transformCmp.interpolatedPosition.set(pos)
        }
        player().transformCmp.interpolatedPosition.set(p)
        org.mockito.Mockito.clearInvocations(shape)
        system.update(0.016f)
        assertEquals(TACTICAL_LEAD_MAX, circleCalls())

        // Far targets get no marker.
        engine.entities.filter { asteroidMapper.get(it) != null }.forEach {
            it.transformCmp.interpolatedPosition.set(p.x + tacticalLeadRange(settings.c) * 1.5f, p.y)
        }
        org.mockito.Mockito.clearInvocations(shape)
        system.update(0.016f)
        assertEquals(0, circleCalls())
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun init() {
            GdxNativesLoader.load()
            Box2D.init()
            Gdx.app = mock<Application> { on { logLevel } doReturn Application.LOG_NONE }
            Gdx.gl = mock<GL20>()
        }
    }

    @Test
    fun targetRingsCircleLevelTargetsEvenWithTheOverlayOff() {
        tactical.showTargetRings = true
        tactical.mode = com.github.pepek42.asteroids.render.TacticalOverlayMode.OFF
        engine.update(1f / 60f)
        val circles = org.mockito.Mockito.mockingDetails(shape).invocations.count { it.method.name == "circle" }
        // One asteroid target (the player is not one): a core ring plus two rings per glow layer.
        assertEquals(1 + 2 * (com.github.pepek42.asteroids.render.TARGET_RING_LAYER_ALPHA.size - 1), circles)
    }

    @Test
    fun targetRingsToggleOffDrawsNone() {
        tactical.mode = com.github.pepek42.asteroids.render.TacticalOverlayMode.OFF
        engine.update(1f / 60f)
        assertEquals(0, org.mockito.Mockito.mockingDetails(shape).invocations.count { it.method.name == "circle" })
    }

    private fun spawnShot(): Entity {
        val p = player()
        return com.github.pepek42.asteroids.provider.LockOnProvider(atlas, engine, world, settings, map)
            .spawnLockOn(p, asteroid(), p.weaponCmp)
    }

    private fun markerLines(): Int {
        tactical.mode = TacticalOverlayMode.OFF
        org.mockito.Mockito.clearInvocations(shape)
        system.update(0.016f)
        return lineCalls()
    }

    @Test
    fun lockOnMarkerDrawsChevronAndTargetLineWithoutMovingTheShot() {
        val shot = spawnShot()
        val pos = Vector2(shot.transformCmp.interpolatedPosition)
        val v = Vector2(shot.bodyCmp.body.linearVelocity)
        tactical.showLockOnMarkers = false
        assertEquals(0, markerLines())
        tactical.showLockOnMarkers = true
        // Two chevron lines plus one faint line to the live target.
        assertEquals(3, markerLines())
        assertEquals(pos, shot.transformCmp.interpolatedPosition)
        assertEquals(v, shot.bodyCmp.body.linearVelocity)
    }

    @Test
    fun lockOnMarkerHasNoTargetLineWhenTargetIsGone() {
        val shot = spawnShot()
        shot.getComponent(com.github.pepek42.asteroids.component.LockOnComponent::class.java).targetId = -99
        assertEquals(2, markerLines())
    }
}
