package com.github.pepek42.asteroids.provider

import com.badlogic.ashley.core.PooledEngine
import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.math.RandomXS128
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.GdxNativesLoader
import com.github.pepek42.asteroids.component.AsteroidComponent
import com.github.pepek42.asteroids.component.BaseInfoComponent
import com.github.pepek42.asteroids.component.EntityType
import com.github.pepek42.asteroids.component.HealthComponent
import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.component.RemoveComponent
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.system.DeathSystem
import com.github.pepek42.asteroids.system.EndLevelSystem
import ktx.ashley.allOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.math.hypot

class DecorationProviderTest {
    private val mapW = 200f
    private val mapH = 200f
    private val world = World(Vector2.Zero, true).apply { setAutoClearForces(false) }
    private val engine = PooledEngine()
    private val map = MapProvider(mock<GameEventManager>(), mapW, mapH)
    private val texture = mock<Texture> {
        on { width } doReturn 10
        on { height } doReturn 10
    }
    private val holes = BlackHoleProvider(engine, world, map)
    private val planets = PlanetProvider(engine, world, map, RelativitySettings()) { Sprite(texture) }
    private val debris = DecorationProvider(engine, world, map) { Sprite(texture) }

    @AfterEach
    fun tearDown() = world.dispose()

    @Test
    fun levelTwoSpawnsSixRestingPiecesObeyingPlacementRules() {
        val random = RandomXS128(42L)
        val holePos = holes.spawn(2, random)
        val planetPos = planets.spawn(2, random, holePos, holePos.size / 2)
        val sources = holePos + planetPos
        val pos = debris.spawn(2, random, sources, sources.size / 2)

        val pieces = engine.entities.filter { it.getComponent(BaseInfoComponent::class.java)?.entityType == EntityType.DEBRIS }
        assertEquals(6, pieces.size)
        assertEquals(12, pos.size)
        for (e in pieces) {
            assertEquals(0f, e.bodyCmp.body.linearVelocity.len())
            assertNull(e.getComponent(RelativisticComponent::class.java))
            assertNull(e.getComponent(AsteroidComponent::class.java))
        }
        for (i in 0 until 6) {
            val x = pos[2 * i]
            val y = pos[2 * i + 1]
            assertTrue(x >= DEBRIS_WALL_MARGIN && x <= mapW - DEBRIS_WALL_MARGIN)
            assertTrue(y >= DEBRIS_WALL_MARGIN && y <= mapH - DEBRIS_WALL_MARGIN)
            assertTrue(hypot(x - mapW / 2f, y - mapH / 2f) >= DEBRIS_CENTER_CLEARANCE)
            for (s in 0 until sources.size / 2) {
                assertTrue(hypot(x - sources[2 * s], y - sources[2 * s + 1]) >= DEBRIS_SOURCE_CLEARANCE)
            }
            for (j in i + 1 until 6) {
                assertTrue(hypot(x - pos[2 * j], y - pos[2 * j + 1]) >= DEBRIS_MIN_SEPARATION)
            }
        }
    }

    @Test
    fun levelWithOnlyDebrisIsClearedAndDeadDebrisIsRemovedWithoutChildren() {
        debris.spawn(2, RandomXS128(42L), FloatArray(0), 0)
        var cleared = 0
        engine.addSystem(EndLevelSystem({ cleared++ }))
        engine.addSystem(DeathSystem(engine, mock<AsteroidProvider>(), RandomXS128(1)))
        engine.update(0.016f)
        assertEquals(1, cleared)

        val before = engine.entities.size()
        val piece = engine.entities.first()
        piece.getComponent(HealthComponent::class.java).health = 0f
        engine.update(0.016f)
        assertTrue(piece.getComponent(RemoveComponent::class.java) != null)
        assertEquals(before, engine.entities.size())
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun init() {
            GdxNativesLoader.load()
            Box2D.init()
            Gdx.app = mock<Application> { on { logLevel } doReturn Application.LOG_NONE }
        }
    }
}
