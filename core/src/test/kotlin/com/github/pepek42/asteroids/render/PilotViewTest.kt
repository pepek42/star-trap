package com.github.pepek42.asteroids.render

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.physics.box2d.CircleShape
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.GdxNativesLoader
import com.github.pepek42.asteroids.component.BaseInfoComponent
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.SpriteComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.physics.GameClock
import com.github.pepek42.asteroids.physics.ObserverState
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.SPEED_OF_LIGHT
import com.github.pepek42.asteroids.physics.apparentOffset
import com.github.pepek42.asteroids.system.LightHistorySystem
import com.github.pepek42.asteroids.system.PhysicsSystem
import com.github.pepek42.asteroids.system.PhysicsSystem.Companion.PHYSICS_TICK_RATE
import com.github.pepek42.asteroids.system.PhysicsSystem.Companion.PHYSICS_UPDATE_INTERVAL
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class PilotViewTest {
    private val world = World(Vector2.Zero, true).apply { setAutoClearForces(false) }
    private val engine = Engine()
    private val history = LightHistorySystem()
    private val clock = GameClock()
    private val observer = ObserverState()
    private val settings = RelativitySettings()
    private val physics: PhysicsSystem
    private val projector = PilotViewProjector(history, observer, clock, settings)
    private val verts = FloatArray(SPRITE_VERTEX_FLOATS)

    init {
        engine.addSystem(history)
        physics = PhysicsSystem(world, engine, listOf(history), clock)
    }

    @AfterEach
    fun tearDown() = world.dispose()

    private fun addEntity(x: Float, y: Float, player: Boolean): Entity {
        val body = world.createBody(BodyDef().apply {
            type = BodyDef.BodyType.DynamicBody
            position.set(x, y)
        })
        val shape = CircleShape().apply { radius = 0.5f }
        body.createFixture(shape, 1f)
        shape.dispose()
        val entity = engine.createEntity()
        entity.add(BodyComponent().apply { this.body = body })
        entity.add(TransformComponent().apply {
            prevPosition.set(x, y)
            interpolatedPosition.set(x, y)
        })
        entity.add(BaseInfoComponent())
        entity.add(SpriteComponent().apply {
            sprite = Sprite().apply {
                setSize(SPRITE_SIZE, SPRITE_SIZE)
                setOrigin(SPRITE_SIZE / 2, SPRITE_SIZE / 2)
            }
        })
        if (player) entity.add(PlayerComponent())
        body.userData = entity
        engine.addEntity(entity)
        return entity
    }

    private fun step(n: Int) {
        // One step per update: PhysicsSystem caps the catch-up per frame.
        clock.worldDt = PHYSICS_UPDATE_INTERVAL
        repeat(n) { physics.update(0f) }
    }

    private fun seconds(s: Float) = (s * PHYSICS_TICK_RATE).toInt()

    private fun centre(): Vector2 {
        var cx = 0f
        var cy = 0f
        for (i in 0 until SPRITE_VERTEX_FLOATS step SPRITE_VERTEX_STRIDE) {
            cx += verts[i] / 4
            cy += verts[i + 1] / 4
        }
        return Vector2(cx, cy)
    }

    private fun begin(player: Entity) {
        val p = player.getComponent(TransformComponent::class.java).interpolatedPosition
        projector.begin(p.x, p.y)
    }

    @Test
    fun cornerAboveObserverMovesForwardAtHighBeta() {
        val out = Vector2()
        pilotCorner(5f, 20f, 5f, 10f, 0.9f, 0f, true, out)
        assertEquals(5f + 20.65f, out.x, 0.01f)
        assertEquals(20f, out.y, 1e-4f)
    }

    @Test
    fun staticCornerRetardedIsCentrePlusOffset() {
        val out = Vector2()
        cornerRetarded(3f, 4f, 1.5f, -2f, 0f, 0f, -7f, 2f, 0.4f, SPEED_OF_LIGHT, out)
        assertEquals(4.5f, out.x, 0f)
        assertEquals(2f, out.y, 0f)
    }

    @Test
    fun movingCornerSatisfiesLightConeWithinOneNewtonStep() {
        // A test-local c = 60 keeps v = (40, 0) subluminal (the default c is 20); the centre solves |x_c - o| = c·lookback exactly.
        val c = 60f
        val lookback = 0.5f
        val out = Vector2()
        for (qx in floatArrayOf(-2f, 2f)) {
            cornerRetarded(0f, c * lookback, qx, 0f, 40f, 0f, 0f, 0f, lookback, c, out)
            // Recover s from the displacement: p = x_c + q - v s.
            val s = (qx - out.x) / 40f
            val dist = Vector2.len(out.x, out.y)
            assertEquals(c * (lookback + s), dist, 1e-2f)
        }
    }

    @Test
    fun cornerUnchangedWithoutAberration() {
        val out = Vector2()
        pilotCorner(5f, 20f, 5f, 10f, 0.9f, 0f, false, out)
        assertEquals(5f, out.x, 1e-6f)
        assertEquals(20f, out.y, 1e-6f)
    }

    @Test
    fun cornerOfTenUpAtBetaPointNine() {
        // Offset (0, 10) with β = (0.9, 0): γ·β·|r| = 2.294 · 9 = 20.65 along x, 10 across.
        val out = Vector2()
        pilotCorner(0f, 10f, 0f, 0f, 0.9f, 0f, true, out)
        assertEquals(20.65f, out.x, 0.01f)
        assertEquals(10f, out.y, 1e-4f)
    }

    @Test
    fun staticAsteroidAtRestPlayerAppearsAtTruePosition() {
        val player = addEntity(50f, 50f, player = true)
        val asteroid = addEntity(80f, 50f, player = false)
        step(seconds(2f))
        begin(player)
        assertTrue(projector.project(asteroid, verts))
        val c = centre()
        assertEquals(80f, c.x, 0.05f)
        assertEquals(50f, c.y, 0.05f)
    }

    @Test
    fun staticAsteroidShiftsForwardWhenPlayerIsFast() {
        val player = addEntity(50f, 50f, player = true)
        val asteroid = addEntity(80f, 50f, player = false)
        step(seconds(2f))
        val beta = 0.8f
        observer.betaVisual.set(beta, 0f)
        begin(player)
        assertTrue(projector.project(asteroid, verts))
        val expected = Vector2()
        apparentOffset(30f, 0f, beta, 0f, expected)
        val c = centre()
        assertEquals(50f + expected.x, c.x, 0.05f)
        assertEquals(50f + expected.y, c.y, 0.05f)
        // gamma (r + beta r) = 1.6667 * 54 = 90, so it clearly moved forward.
        assertTrue(c.x > 80f + 1f)
    }

    /** Hull angle (deg) of the projected player quad, from its bottom edge. */
    private fun hullAngleDeg(rotationDeg: Float): Float {
        val player = addEntity(50f, 50f, player = true)
        player.getComponent(TransformComponent::class.java).interpolatedRotationDeg = rotationDeg
        observer.betaVisual.set(0.9f, 0f)
        begin(player)
        assertTrue(projector.project(player, verts))
        val d = SPRITE_VERTEX_STRIDE * 3
        return Math.toDegrees(Math.atan2((verts[d + 1] - verts[1]).toDouble(), (verts[d] - verts[0]).toDouble())).toFloat()
    }

    @Test
    fun hullIsTransformedOnlyWithPilotViewRelativityAndAberration() {
        settings.pilotView = true
        settings.relativityEnabled = true
        settings.pilotAberration = true
        assertEquals(45f, hullAngleDeg(66.45f), 0.05f)
    }

    @Test
    fun hullIsRawWithoutRelativity() {
        settings.pilotView = true
        settings.relativityEnabled = false
        assertEquals(66.45f, hullAngleDeg(66.45f), 0.05f)
    }

    @Test
    fun hullIsRawWithoutAberration() {
        settings.pilotView = true
        settings.relativityEnabled = true
        settings.pilotAberration = false
        assertEquals(66.45f, hullAngleDeg(66.45f), 0.05f)
    }

    @Test
    fun playerIsDrawnAtItsOwnPositionUnboosted() {
        val player = addEntity(50f, 50f, player = true)
        step(seconds(1f))
        observer.betaVisual.set(0.8f, 0f)
        begin(player)
        assertTrue(projector.project(player, verts))
        val c = centre()
        assertEquals(50f, c.x, 1e-3f)
        assertEquals(50f, c.y, 1e-3f)
    }

    @Test
    fun youngAsteroidWhoseLightHasNotArrivedIsNotDrawn() {
        val player = addEntity(50f, 50f, player = true)
        step(10)
        val asteroid = addEntity(80f, 50f, player = false)
        step(1)
        begin(player)
        assertFalse(projector.project(asteroid, verts))
        // Light travel takes 30 / c; after a bit longer it shows up.
        step(seconds(30f / settings.c + 0.5f))
        begin(player)
        assertTrue(projector.project(asteroid, verts))
    }

    @Test
    fun withoutLightDelayYoungAsteroidIsDrawnAtOnce() {
        settings.pilotDelay = false
        val player = addEntity(50f, 50f, player = true)
        step(10)
        val asteroid = addEntity(80f, 50f, player = false)
        step(1)
        begin(player)
        assertTrue(projector.project(asteroid, verts))
    }

    @Test
    fun pointerDirectionFollowsApparentCentreInPilotViewOnly() {
        val player = addEntity(50f, 50f, player = true)
        val asteroid = addEntity(80f, 80f, player = false)
        step(seconds(2f))
        observer.betaVisual.set(0.8f, 0f)
        begin(player)
        val apparent = Vector2()
        assertTrue(projector.apparentCentre(asteroid, apparent))
        val expected = Vector2()
        apparentOffset(30f, 30f, 0.8f, 0f, expected)
        assertEquals(50f + expected.x, apparent.x, 0.05f)
        assertEquals(50f + expected.y, apparent.y, 0.05f)
        val out = Vector2()
        val pilotAngle = edgePointer(50f, 50f, 1f, 1f, apparent.x, apparent.y, 0f, out)
        val trueAngle = edgePointer(50f, 50f, 1f, 1f, 80f, 80f, 0f, out)
        assertEquals(Math.atan2(expected.y.toDouble(), expected.x.toDouble()).toFloat(), pilotAngle, 1e-3f)
        assertTrue(Math.abs(pilotAngle - trueAngle) > 0.3f)
        // World view (beta 0) leaves the direction unchanged.
        observer.betaVisual.set(0f, 0f)
        begin(player)
        assertTrue(projector.apparentCentre(asteroid, apparent))
        assertEquals(trueAngle, edgePointer(50f, 50f, 1f, 1f, apparent.x, apparent.y, 0f, out), 1e-3f)
    }

    @Test
    fun apparentCentreIsFalseWhileLightHasNotArrived() {
        val player = addEntity(50f, 50f, player = true)
        step(10)
        val asteroid = addEntity(80f, 50f, player = false)
        step(1)
        begin(player)
        assertFalse(projector.apparentCentre(asteroid, Vector2()))
    }

    companion object {
        private const val SPRITE_SIZE = 0.2f

        @JvmStatic
        @BeforeAll
        fun loadNatives() {
            GdxNativesLoader.load()
            Box2D.init()
            Gdx.app = mock<Application> { on { logLevel } doReturn Application.LOG_NONE }
        }
    }
}
