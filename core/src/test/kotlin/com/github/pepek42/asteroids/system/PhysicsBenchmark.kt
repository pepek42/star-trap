package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.PooledEngine
import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.math.RandomXS128
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.physics.box2d.CircleShape
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.GdxNativesLoader
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.MoveComponent
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.environment.AsteroidSize
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.provider.AsteroidProvider
import com.github.pepek42.asteroids.provider.MapProvider
import com.github.pepek42.asteroids.render.ShimmerImpacts
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Perf check for the physics tick rate (ST-106). Disabled by default; run with
 * `BENCH=1 ./gradlew core:test --tests "*PhysicsBenchmark*" -i` and read the `[bench]` line.
 */
@EnabledIfEnvironmentVariable(named = "BENCH", matches = "1")
class PhysicsBenchmark {
    private val settings = RelativitySettings()
    private val world = World(Vector2.Zero, true).apply { setAutoClearForces(false) }
    private val engine = PooledEngine()
    private val texture = mock<Texture> {
        on { width } doReturn 10
        on { height } doReturn 10
    }
    private val sprite = Sprite(texture)
    private val atlas = mock<TextureAtlas> {
        on { createSprite(AsteroidSize.LARGE.textureRegionName) } doReturn sprite
    }

    @Test
    fun level5MeanStepTime() {
        val map = MapProvider(mock<GameEventManager>())
        AsteroidProvider(atlas, engine, world, RandomXS128(BENCH_SEED), settings).spawnAsteroids(BENCH_LEVEL, map)
        val asteroidCount = engine.entities.size()
        addPlayer(map)

        val move = MoveSystem(settings)
        val field = BoundaryFieldSystem(map, ShimmerImpacts(), settings)
        val dynamics = RelativisticDynamics(settings)
        engine.addSystem(move)
        engine.addSystem(field)
        engine.addSystem(dynamics)
        val listeners = listOf(move, field, dynamics)
        val dt = PhysicsSystem.PHYSICS_UPDATE_INTERVAL

        fun step(): Long {
            listeners.forEach { it.beforeStep(dt) }
            val t0 = System.nanoTime()
            world.step(dt, 6, 2)
            val elapsed = System.nanoTime() - t0
            listeners.forEach { it.afterStep(dt) }
            world.clearForces()
            return elapsed
        }

        repeat(WARMUP_STEPS) { step() }
        var total = 0L
        repeat(BENCH_STEPS) { total += step() }
        val meanMs = total / BENCH_STEPS / 1e6
        val stepsPerFrame = PhysicsSystem.MAX_STEPS_PER_FRAME
        println(
            "[bench] asteroids=$asteroidCount steps=$BENCH_STEPS tick=${PhysicsSystem.PHYSICS_TICK_RATE} Hz " +
                "mean world.step = %.4f ms; %d steps/frame = %.3f ms of a %.2f ms frame at 60 fps"
                    .format(meanMs, stepsPerFrame, meanMs * stepsPerFrame, 1000.0 / 60.0),
        )
        world.dispose()
    }

    private fun addPlayer(map: MapProvider) {
        val body = world.createBody(BodyDef().apply {
            type = BodyDef.BodyType.DynamicBody
            position.set(map.mapWidth / 2f, map.mapHeight / 2f)
            allowSleep = false
        })
        val shape = CircleShape().apply { radius = 0.5f }
        body.createFixture(shape, 1f)
        shape.dispose()
        engine.addEntity(
            Entity().add(BodyComponent().apply { this.body = body }).add(TransformComponent())
                .add(RelativisticComponent()).add(MoveComponent(thrusters = 1f)).add(PlayerComponent())
        )
    }

    companion object {
        private const val BENCH_LEVEL = 5
        private const val BENCH_SEED = 42L
        private const val WARMUP_STEPS = 120
        private const val BENCH_STEPS = 600

        @JvmStatic
        @BeforeAll
        fun init() {
            GdxNativesLoader.load()
            Box2D.init()
            Gdx.app = mock<Application> { on { logLevel } doReturn Application.LOG_NONE }
        }
    }
}
