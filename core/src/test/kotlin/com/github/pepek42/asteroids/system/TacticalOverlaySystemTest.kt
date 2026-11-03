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
import com.github.pepek42.asteroids.component.relativisticCmp
import com.github.pepek42.asteroids.component.tacticalComputerCmp
import com.github.pepek42.asteroids.environment.AsteroidSize
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.provider.AsteroidProvider
import com.github.pepek42.asteroids.provider.MapProvider
import com.github.pepek42.asteroids.provider.PlayerEntityProvider
import com.github.pepek42.asteroids.render.TACTICAL_CIRCLE_SEGMENTS
import com.github.pepek42.asteroids.render.TACTICAL_MIN_ARROW_BETA
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
    private val tactical = TacticalSettings()
    private val shape = mock<ShapeRenderer>()
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
    }
    private val system = TacticalOverlaySystem(OrthographicCamera(48f, 27f), viewport, map, settings, tactical, shape)

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
        val before = linesWithAsteroidVelocity(0f)
        asteroid().add(engine.createComponent(RemoveComponent::class.java))
        org.mockito.Mockito.clearInvocations(shape)
        system.update(0.016f)
        assertEquals(before - TACTICAL_CIRCLE_SEGMENTS, lineCalls())
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
}
