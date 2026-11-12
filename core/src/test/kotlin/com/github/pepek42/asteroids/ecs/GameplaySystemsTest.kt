package com.github.pepek42.asteroids.ecs

import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.core.PooledEngine
import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.RandomXS128
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.GdxNativesLoader
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.input.ControlSettings
import com.github.pepek42.asteroids.physics.GameClock
import com.github.pepek42.asteroids.physics.ObserverState
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.provider.AsteroidProvider
import com.github.pepek42.asteroids.provider.MapProvider
import com.github.pepek42.asteroids.provider.WeaponProjectileProvider
import com.github.pepek42.asteroids.render.ShimmerImpacts
import com.github.pepek42.asteroids.system.BoundaryFieldSystem
import com.github.pepek42.asteroids.system.ContactSystem
import com.github.pepek42.asteroids.system.DeathSystem
import com.github.pepek42.asteroids.system.EndLevelSystem
import com.github.pepek42.asteroids.system.EnemyAiSystem
import com.github.pepek42.asteroids.system.DiskDamageSystem
import com.github.pepek42.asteroids.system.GravitySystem
import com.github.pepek42.asteroids.system.LifetimeSystem
import com.github.pepek42.asteroids.system.LightHistorySystem
import com.github.pepek42.asteroids.system.LockOnSteeringSystem
import com.github.pepek42.asteroids.system.MoveSystem
import com.github.pepek42.asteroids.system.ObserverSystem
import com.github.pepek42.asteroids.system.PhysicsSystem
import com.github.pepek42.asteroids.system.PlayerInputSystem
import com.github.pepek42.asteroids.system.ProjectileSweepSystem
import com.github.pepek42.asteroids.system.RelativisticDynamics
import com.github.pepek42.asteroids.system.RemoveSystem
import com.github.pepek42.asteroids.system.TimeWarpSystem
import com.github.pepek42.asteroids.system.WeaponSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class GameplaySystemsTest {
    private val world = World(Vector2.Zero, true)
    private val engine = PooledEngine()
    private val events = GameEventManager()
    private val deps = GameplayDeps(
        engine = engine,
        world = world,
        settings = RelativitySettings(),
        mapProvider = MapProvider(events),
        clock = GameClock(),
        observerState = ObserverState(),
        gameEventManager = events,
        camera = OrthographicCamera(),
        controlSettings = ControlSettings(),
        weaponProjectileProvider = mock<WeaponProjectileProvider>(),
        asteroidProvider = mock<AsteroidProvider>(),
        random = RandomXS128(1),
        shimmerImpacts = ShimmerImpacts(),
        onLevelCleared = {},
        onPlayerDeath = {},
    )

    @AfterEach
    fun tearDown() = world.dispose()

    private class PresentationMarker : EntitySystem()

    @Test
    fun registersSystemsInExecutionOrderWithPresentationBetweenObserverAndLifetime() {
        var seen: GameplaySystems? = null
        val marker = PresentationMarker()
        val systems = buildGameplaySystems(deps) {
            seen = it
            engine.addSystem(marker)
        }
        assertSame(systems, seen)
        val expected = listOf(
            TimeWarpSystem::class, EndLevelSystem::class, PlayerInputSystem::class, EnemyAiSystem::class, MoveSystem::class, WeaponSystem::class,
            ContactSystem::class, BoundaryFieldSystem::class, GravitySystem::class, DiskDamageSystem::class, LockOnSteeringSystem::class, ProjectileSweepSystem::class,
            RelativisticDynamics::class, LightHistorySystem::class, PhysicsSystem::class, ObserverSystem::class, PresentationMarker::class,
            LifetimeSystem::class, DeathSystem::class, RemoveSystem::class,
        )
        assertEquals(expected, engine.systems.map { it::class })
    }

    @Test
    fun worksWithoutPresentation() {
        buildGameplaySystems(deps)
        assertEquals(19, engine.systems.size())
    }

    @Test
    fun worldTimeStandsStillWhilePausedAndAdvancesAfterResume() {
        buildGameplaySystems(deps)
        val clock = deps.clock
        val dt = PhysicsSystem.PHYSICS_UPDATE_INTERVAL
        setWorldPaused(engine, clock, true)
        repeat(10) { engine.update(dt) }
        assertEquals(0f, clock.worldTime)
        assertEquals(0f, clock.worldDt)
        assertEquals(0f, clock.realDt)
        assertEquals(0f, deps.observerState.worldTime)

        // PlayerInputSystem needs a GL camera; the unpaused half of this test only needs the clock and physics.
        engine.removeSystem(engine.getSystem(PlayerInputSystem::class.java))
        setWorldPaused(engine, clock, false)
        repeat(10) { engine.update(dt) }
        assertEquals(10 * dt, clock.worldTime, 1e-4f)
    }

    @Test
    fun pauseOnlyStopsWorldSystems() {
        val marker = PresentationMarker()
        buildGameplaySystems(deps) { engine.addSystem(marker) }
        setWorldPaused(engine, deps.clock, true)
        assertFalse(engine.getSystem(EnemyAiSystem::class.java).checkProcessing())
        assertTrue(engine.getSystem(PhysicsSystem::class.java).checkProcessing())
        assertFalse(engine.getSystem(WeaponSystem::class.java).checkProcessing())
        assertFalse(engine.getSystem(LifetimeSystem::class.java).checkProcessing())
        assertTrue(engine.getSystem(TimeWarpSystem::class.java).checkProcessing())
        assertTrue(engine.getSystem(PresentationMarker::class.java).checkProcessing())
        assertTrue(engine.getSystem(RemoveSystem::class.java).checkProcessing())
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
