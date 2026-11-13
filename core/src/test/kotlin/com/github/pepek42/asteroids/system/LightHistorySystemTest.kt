package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.physics.box2d.CircleShape
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.GdxNativesLoader
import com.github.pepek42.asteroids.component.BaseInfoComponent
import com.github.pepek42.asteroids.component.GravitySourceComponent
import com.github.pepek42.asteroids.component.GravitySourceKind
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.SpriteComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.physics.LIGHT_HISTORY_CAPACITY
import com.github.pepek42.asteroids.system.PhysicsSystem.Companion.PHYSICS_TICK_RATE
import com.github.pepek42.asteroids.system.PhysicsSystem.Companion.PHYSICS_UPDATE_INTERVAL
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class LightHistorySystemTest {
    private val world = World(Vector2.Zero, true).apply { setAutoClearForces(false) }
    private val engine = Engine()
    private val history = LightHistorySystem()
    private val physics: PhysicsSystem

    init {
        engine.addSystem(history)
        physics = PhysicsSystem(world, engine, listOf(history))
    }

    @AfterEach
    fun tearDown() = world.dispose()

    private fun addBody(vx: Float): Entity {
        val body = world.createBody(BodyDef().apply {
            type = BodyDef.BodyType.DynamicBody
            linearVelocity.set(vx, 0f)
        })
        val shape = CircleShape().apply { radius = 0.5f }
        body.createFixture(shape, 1f)
        shape.dispose()
        val entity = engine.createEntity()
        entity.add(BodyComponent().apply { this.body = body })
        entity.add(TransformComponent())
        entity.add(BaseInfoComponent())
        body.userData = entity
        engine.addEntity(entity)
        return entity
    }

    private fun step(n: Int = 1) = repeat(n) { physics.update(PHYSICS_UPDATE_INTERVAL + 1e-4f) }

    private fun idOf(e: Entity) = e.getComponent(BaseInfoComponent::class.java).id

    @Test
    fun recordsOneSamplePerStep() {
        val speed = 10f
        val e = addBody(speed)
        val steps = PHYSICS_TICK_RATE.toInt()
        step(steps)
        val h = history.historyOf(idOf(e))!!
        val expectedTime = steps * PHYSICS_UPDATE_INTERVAL
        assertEquals(steps, h.size)
        assertTrue(steps < LIGHT_HISTORY_CAPACITY)
        assertEquals(expectedTime, h.t[h.slot(h.size - 1)], 1e-5f)
        assertEquals(expectedTime, history.now, 1e-5f)
        assertEquals(speed * expectedTime, h.x[h.slot(h.size - 1)], 1e-3f)
    }

    @Test
    fun eternalOnlyForEntitiesPresentAtStart() {
        val early = addBody(1f)
        step(20)
        val late = addBody(1f)
        step(1)
        assertTrue(history.historyOf(idOf(early))!!.eternal)
        assertFalse(history.historyOf(idOf(late))!!.eternal)
    }

    @Test
    fun removedHistoryStaysThenIsPruned() {
        val e = addBody(1f)
        val id = idOf(e)
        step(5)
        assertTrue(history.removedAt(id).isNaN())
        val removeTime = history.now
        world.destroyBody(e.getComponent(BodyComponent::class.java).body)
        e.getComponent(BodyComponent::class.java).body.userData = null
        engine.removeEntity(e)
        assertEquals(removeTime, history.removedAt(id), 1e-6f)
        step(5)
        assertNotNull(history.historyOf(id))
        step(LIGHT_HISTORY_CAPACITY + 2)
        assertNull(history.historyOf(id))
        assertTrue(history.removedAt(id).isNaN())
    }

    @Test
    fun ghostInfoIsKeptThenFreedWhenHistoryIsPruned() {
        val e = addBody(1f)
        val id = idOf(e)
        e.add(SpriteComponent().apply {
            sprite = Sprite(mock<Texture> {
                on { width } doReturn 1
                on { height } doReturn 1
            }).apply { setSize(2f, 3f) }
        })
        step(5)
        world.destroyBody(e.getComponent(BodyComponent::class.java).body)
        e.getComponent(BodyComponent::class.java).body.userData = null
        engine.removeEntity(e)
        var seen = 0
        var ghost: GhostInfo? = null
        history.forEachGhost { gid, _, g, _ -> if (gid == id) { seen++; ghost = g } }
        assertEquals(1, seen)
        assertEquals(2f, ghost!!.width)
        assertEquals(3f, ghost!!.height)
        step(LIGHT_HISTORY_CAPACITY + 2)
        seen = 0
        history.forEachGhost { _, _, _, _ -> seen++ }
        assertEquals(0, seen)
        // Freed to the pool: reset cleared the region.
        assertNull(ghost!!.region.texture)
    }

    private fun spriteCmp() = SpriteComponent().apply {
        sprite = Sprite(mock<Texture> {
            on { width } doReturn 1
            on { height } doReturn 1
        }).apply { setSize(2f, 3f) }
    }

    private fun remove(e: com.badlogic.ashley.core.Entity) {
        world.destroyBody(e.getComponent(BodyComponent::class.java).body)
        e.getComponent(BodyComponent::class.java).body.userData = null
        engine.removeEntity(e)
    }

    @Test
    fun ghostKeepsLightingData() {
        val e = addBody(1f)
        val id = idOf(e)
        e.add(spriteCmp())
        e.add(GravitySourceComponent().apply { kind = GravitySourceKind.PLANET })
        step(3)
        remove(e)
        var ghost: GhostInfo? = null
        history.forEachGhost { _, _, g, _ -> ghost = g }
        assertEquals(id, ghost!!.entityId)
        assertTrue(ghost!!.sphereNormals)
    }

    @Test
    fun removingThePlayerCreatesNoGhost() {
        val e = addBody(1f)
        e.add(spriteCmp())
        e.add(PlayerComponent())
        step(3)
        remove(e)
        var seen = 0
        history.forEachGhost { _, _, _, _ -> seen++ }
        assertEquals(0, seen)
    }

    @Test
    fun clearResetsTimeAndHistories() {
        val e = addBody(1f)
        step(3)
        history.clear()
        assertEquals(0f, history.now)
        assertNull(history.historyOf(idOf(e)))
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadNatives() {
            GdxNativesLoader.load()
            Box2D.init()
            Gdx.app = mock<Application> { on { logLevel } doReturn Application.LOG_NONE }
        }
    }
}
