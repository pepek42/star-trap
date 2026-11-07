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
import com.github.pepek42.asteroids.component.BaseInfoComponent
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.EnemyComponent
import com.github.pepek42.asteroids.component.EntityType
import com.github.pepek42.asteroids.component.HealthComponent
import com.github.pepek42.asteroids.component.MoveComponent
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.WeaponComponent
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.healthCmp
import com.github.pepek42.asteroids.component.moveCmp
import com.github.pepek42.asteroids.component.weaponCmp
import com.github.pepek42.asteroids.ecs.GameplayDeps
import com.github.pepek42.asteroids.ecs.buildGameplaySystems
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.faction.FactionEnum
import com.github.pepek42.asteroids.input.ControlSettings
import com.github.pepek42.asteroids.physics.GameClock
import com.github.pepek42.asteroids.physics.ObserverState
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.provider.AsteroidProvider
import com.github.pepek42.asteroids.provider.EnemyShipProvider
import com.github.pepek42.asteroids.provider.MapProvider
import com.github.pepek42.asteroids.provider.PlayerEntityProvider
import com.github.pepek42.asteroids.provider.WeaponProjectileProvider
import com.github.pepek42.asteroids.render.ShimmerImpacts
import com.badlogic.gdx.graphics.OrthographicCamera
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** ST-156: the enemy pilot sees only light-delayed player state and leads honestly. */
class EnemyAiSystemTest {
    private val settings = RelativitySettings()
    private val c = settings.c
    private val dt = PhysicsSystem.PHYSICS_UPDATE_INTERVAL
    private val world = World(Vector2.Zero, true).apply { setAutoClearForces(false) }
    private val engine = PooledEngine()
    private val clock = GameClock()
    private val lightHistory = LightHistorySystem()
    private val ai = EnemyAiSystem(lightHistory, clock, settings)

    init {
        engine.addSystem(lightHistory)
        engine.addSystem(ai)
    }

    @AfterEach
    fun tearDown() = world.dispose()

    private fun body(x: Float, y: Float): com.badlogic.gdx.physics.box2d.Body {
        val b = world.createBody(BodyDef().apply {
            type = BodyDef.BodyType.DynamicBody
            position.set(x, y)
        })
        val shape = CircleShape().apply { radius = 0.5f }
        b.createFixture(shape, 1f)
        shape.dispose()
        return b
    }

    private fun addPlayer(x: Float, y: Float): Entity {
        val e = engine.createEntity()
        e.add(engine.createComponent(BodyComponent::class.java).apply { body = body(x, y) })
        e.add(engine.createComponent(TransformComponent::class.java))
        e.add(engine.createComponent(PlayerComponent::class.java))
        e.add(engine.createComponent(BaseInfoComponent::class.java).apply {
            faction = FactionEnum.PLAYER
            entityType = EntityType.SHIP
        })
        engine.addEntity(e)
        return e
    }

    private fun addEnemy(x: Float, y: Float): Entity {
        val e = engine.createEntity()
        e.add(engine.createComponent(BodyComponent::class.java).apply { body = body(x, y) })
        e.add(engine.createComponent(TransformComponent::class.java))
        e.add(engine.createComponent(EnemyComponent::class.java))
        e.add(engine.createComponent(MoveComponent::class.java))
        e.add(engine.createComponent(WeaponComponent::class.java).apply { shotsPerSecond = 1f })
        e.add(engine.createComponent(BaseInfoComponent::class.java).apply {
            faction = FactionEnum.ENEMY
            entityType = EntityType.SHIP
        })
        engine.addEntity(e)
        return e
    }

    /** Records [steps] physics steps of the arena without stepping Box2D, and keeps the clock equal to the history time. */
    private fun recordSteps(steps: Int) {
        repeat(steps) {
            lightHistory.afterStep(dt)
            clock.worldTime += dt
        }
    }

    @Test
    fun withoutPlayerHistoryTheEnemyNeitherThrustsNorFires() {
        addPlayer(20f, 0f)
        val enemy = addEnemy(0f, 0f)
        enemy.moveCmp.thrusters = 1f
        enemy.weaponCmp.doAttack = true
        ai.update(dt)
        assertEquals(0f, enemy.moveCmp.thrusters)
        assertFalse(enemy.weaponCmp.doAttack)
        assertNull(enemy.weaponCmp.aimAngleRad)
    }

    @Test
    fun withoutAPlayerTheEnemyNeitherThrustsNorFires() {
        val enemy = addEnemy(0f, 0f)
        enemy.moveCmp.thrusters = 1f
        enemy.weaponCmp.doAttack = true
        ai.update(dt)
        assertEquals(0f, enemy.moveCmp.thrusters)
        assertFalse(enemy.weaponCmp.doAttack)
    }

    @Test
    fun theEnemyAimsAtTheRetardedEstimateNotTheTruePosition() {
        val player = addPlayer(20f, 0f)
        val enemy = addEnemy(0f, 0f)
        recordSteps(10)
        ai.update(dt)
        val first = enemy.weaponCmp.aimAngleRad
        assertNotNull(first)
        assertTrue(enemy.weaponCmp.doAttack)
        assertEquals(0f, first!!, 1e-3f)

        // Teleport the true body without recording: only the light history may drive the enemy.
        player.bodyCmp.body.setTransform(0f, 20f, 0f)
        ai.update(dt)
        assertEquals(first, enemy.weaponCmp.aimAngleRad)
        assertEquals(0f, enemy.moveCmp.targetAngleRad!!, 1e-3f)
    }

    @Test
    fun theEnemyLeadsAMovingPlayerFromItsRetardedVelocity() {
        val player = addPlayer(20f, 0f)
        val enemy = addEnemy(0f, 0f)
        player.bodyCmp.body.setLinearVelocity(0f, 0.2f * c)
        recordSteps(10)
        ai.update(dt)
        val aim = enemy.weaponCmp.aimAngleRad
        assertNotNull(aim)
        // The target crosses upward, so the lead points above the bearing to its position.
        assertTrue(aim!! > 0.01f)
    }

    @Test
    fun theEnemyHoldsFireBeyondTheFireRange() {
        addPlayer(0f, ENEMY_FIRE_RANGE_LIGHT_SECONDS * c + 10f)
        val enemy = addEnemy(0f, 0f)
        recordSteps(10)
        ai.update(dt)
        assertFalse(enemy.weaponCmp.doAttack)
    }

    @Test
    fun theEnemyHoldsFireWhileTheHullIsOutsideTheFireCone() {
        addPlayer(0f, 20f) // in range, but the hull (angle 0) points 90 degrees away from the lead direction
        val enemy = addEnemy(0f, 0f)
        recordSteps(10)
        ai.update(dt)
        assertFalse(enemy.weaponCmp.doAttack)
    }

    /** ST-156 integration: real world and the gameplay systems, a resting player 30 u away is approached and shot. */
    @Test
    fun aRestingPlayerThirtyUnitsAwayIsApproachedAndShot() {
        val real = PooledEngine()
        val events = GameEventManager()
        val map = MapProvider(events, 200f, 200f)
        val texture = mock<Texture> {
            on { width } doReturn 10
            on { height } doReturn 10
        }
        val atlas = mock<TextureAtlas>().also { a ->
            whenever(a.createSprite(any<String>())).thenAnswer { Sprite(texture) }
            whenever(a.createSprite(any<String>(), any())).thenAnswer { Sprite(texture) }
        }
        val deps = GameplayDeps(
            engine = real, world = world, settings = settings, mapProvider = map, clock = GameClock(),
            observerState = ObserverState(), gameEventManager = events, camera = mock<OrthographicCamera>(),
            controlSettings = ControlSettings(),
            weaponProjectileProvider = WeaponProjectileProvider(atlas, real, world, settings),
            asteroidProvider = mock<AsteroidProvider>(), random = RandomXS128(1), shimmerImpacts = ShimmerImpacts(),
            onLevelCleared = {}, onPlayerDeath = {},
        )
        buildGameplaySystems(deps)
        PlayerEntityProvider(real, world, map, atlas, settings).spawnPlayerEntity()
        EnemyShipProvider(real, world, atlas, settings).spawn(com.github.pepek42.asteroids.provider.ENEMY_FIRST_LEVEL, RandomXS128(3), map, FloatArray(0))
        val player = real.entities.first { it.getComponent(PlayerComponent::class.java) != null }
        val enemy = real.entities.first { it.getComponent(EnemyComponent::class.java) != null }
        // A resting target: no input from the player's side, so the enemy is the only actor.
        player.remove(WeaponComponent::class.java)
        player.remove(MoveComponent::class.java)
        player.healthCmp.health = 1000f
        val start = player.healthCmp.health
        val p = player.bodyCmp.body.position
        enemy.bodyCmp.body.setTransform(p.x + 30f, p.y, 0f)

        var steps = 0
        while (steps < (10f / dt).toInt() && player.healthCmp.health >= start) {
            real.update(dt)
            steps++
        }
        assertTrue(player.healthCmp.health < start, "player was never hit in 10 s")
        // It closed in from 30 u towards the 1 light-second standoff.
        assertTrue(enemy.bodyCmp.body.position.dst(p) < 30f)
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
