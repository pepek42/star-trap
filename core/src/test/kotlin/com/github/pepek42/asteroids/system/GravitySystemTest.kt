package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.physics.box2d.CircleShape
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.GdxNativesLoader
import com.github.pepek42.asteroids.component.BaseInfoComponent
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.GravitySourceComponent
import com.github.pepek42.asteroids.component.GravitySourceKind
import com.github.pepek42.asteroids.component.PhotonComponent
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.physics.BH_RS
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.bendPhoton
import com.github.pepek42.asteroids.physics.gammaFromU
import com.github.pepek42.asteroids.physics.gmFromRs
import com.github.pepek42.asteroids.physics.gravityAccelTau
import com.github.pepek42.asteroids.physics.newtonAccel
import com.github.pepek42.asteroids.physics.uFromV
import com.github.pepek42.asteroids.system.PhysicsSystem.Companion.PHYSICS_UPDATE_INTERVAL
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Real `World`, sources on dynamic sensor bodies. Uses the runtime `settings.c` and `BH_RS`, steps with
 * [PHYSICS_UPDATE_INTERVAL]; geometry-only expectations (deflection `2 r_s / b`) don't depend on c.
 */
class GravitySystemTest {
    private val settings = RelativitySettings()
    private val c get() = settings.c
    private val gmHole get() = gmFromRs(BH_RS, c)
    private val world = World(Vector2.Zero, true).apply { setAutoClearForces(false) }
    private val engine = Engine()
    private val gravity = GravitySystem(settings)
    private val dynamics = RelativisticDynamics(settings)
    private val physics: PhysicsSystem

    /** Half a second of game steps. */
    private val halfSecondSteps = (0.5f / PHYSICS_UPDATE_INTERVAL).toInt()

    init {
        engine.addSystem(gravity)
        engine.addSystem(dynamics)
        physics = PhysicsSystem(world, engine, listOf(gravity, dynamics))
    }

    @AfterEach
    fun tearDown() = world.dispose()

    private fun makeBody(type: BodyDef.BodyType, x: Float, y: Float, vx: Float = 0f, vy: Float = 0f, radius: Float = 0.5f): Body {
        val body = world.createBody(BodyDef().apply {
            this.type = type
            position.set(x, y)
            linearVelocity.set(vx, vy)
            allowSleep = false
        })
        val shape = CircleShape().apply { this.radius = radius }
        body.createFixture(shape, 1f).isSensor = true
        shape.dispose()
        return body
    }

    private fun addSource(
        x: Float,
        y: Float,
        kind: GravitySourceKind = GravitySourceKind.BLACK_HOLE,
        gm: Float = 0f,
        radius: Float = 0f,
    ): Body {
        val body = makeBody(BodyDef.BodyType.DynamicBody, x, y)
        engine.addEntity(
            Entity()
                .add(BodyComponent().apply { this.body = body })
                .add(TransformComponent())
                .add(GravitySourceComponent().apply { this.kind = kind; this.gm = gm; this.radius = radius })
        )
        return body
    }

    /** A massive body on the relativistic path (the player or an asteroid). */
    private fun addRelativistic(x: Float, y: Float, vx: Float = 0f, vy: Float = 0f, player: Boolean = false): RelativisticComponent {
        val body = makeBody(BodyDef.BodyType.DynamicBody, x, y, vx, vy)
        val rel = RelativisticComponent()
        rel.captureRestMass(body)
        uFromV(vx, vy, c, settings.betaMaxMassive, rel.u)
        val e = Entity()
            .add(BodyComponent().apply { this.body = body })
            .add(TransformComponent())
            .add(BaseInfoComponent())
            .add(rel)
        if (player) e.add(PlayerComponent())
        engine.addEntity(e)
        return rel
    }

    private fun addBodyOnly(type: BodyDef.BodyType, x: Float, y: Float, vx: Float = 0f, vy: Float = 0f, player: Boolean = false): Body {
        val body = makeBody(type, x, y, vx, vy)
        val e = Entity()
            .add(BodyComponent().apply { this.body = body })
            .add(TransformComponent())
            .add(BaseInfoComponent())
        if (player) e.add(PlayerComponent())
        engine.addEntity(e)
        return body
    }

    private fun addPhoton(x: Float, y: Float, dirX: Float, dirY: Float): PhotonComponent {
        val body = makeBody(BodyDef.BodyType.KinematicBody, x, y, dirX * c, dirY * c, radius = 0.1f)
        val photon = PhotonComponent().apply { dir.set(dirX, dirY) }
        engine.addEntity(
            Entity()
                .add(BodyComponent().apply { this.body = body })
                .add(TransformComponent())
                .add(BaseInfoComponent())
                .add(photon)
        )
        return photon
    }

    private fun step(n: Int = 1) = repeat(n) { physics.update(PHYSICS_UPDATE_INTERVAL + 1e-4f) }

    @Test
    fun relativisticBodyFallsAtTheIntegratedProperAcceleration() {
        addSource(0f, 0f)
        val rel = addRelativistic(30f, 0f)
        val body = engine.entities.first { it.getComponent(RelativisticComponent::class.java) === rel }
            .getComponent(BodyComponent::class.java).body
        step(halfSecondSteps)

        // Reference: the same law integrated at a tenth of the step, du/dt = a_tau / gamma.
        var x = 30.0
        var u = 0.0
        val a = Vector2()
        val sub = PHYSICS_UPDATE_INTERVAL / 10f
        repeat(halfSecondSteps * 10) {
            gravityAccelTau(x.toFloat(), 0f, u.toFloat(), 0f, gmHole, c, 0.5f, a)
            val gamma = gammaFromU(u.toFloat(), 0f, c)
            u += a.x / gamma * sub
            x += u / gammaFromU(u.toFloat(), 0f, c) * sub
        }
        val expectedSpeed = abs(u / gammaFromU(u.toFloat(), 0f, c))
        assertTrue(body.linearVelocity.x < 0f)
        assertEquals(expectedSpeed, abs(body.linearVelocity.x).toDouble(), 0.05 * expectedSpeed)
        assertEquals(0f, body.linearVelocity.y, 1e-6f)
    }

    @Test
    fun relativisticBodyUsesTheNewtonianPathWithRelativityOff() {
        settings.relativityEnabled = false
        addSource(0f, 0f)
        val body = addBodyOnly(BodyDef.BodyType.DynamicBody, 30f, 0f)
        step(halfSecondSteps)
        val expected = gmHole / (30f * 30f) * halfSecondSteps * PHYSICS_UPDATE_INTERVAL
        assertEquals(-expected, body.linearVelocity.x, 0.03f * expected)
    }

    @Test
    fun planetUsesItsStoredGmAndSurfaceSoftening() {
        val planetGm = 90f
        addSource(0f, 0f, GravitySourceKind.PLANET, gm = planetGm, radius = 3f)
        val far = addBodyOnly(BodyDef.BodyType.DynamicBody, 20f, 0f)
        val inside = addBodyOnly(BodyDef.BodyType.DynamicBody, 0f, 1f)
        step()
        val farExpected = planetGm / (20f * 20f) * PHYSICS_UPDATE_INTERVAL
        assertEquals(-farExpected, far.linearVelocity.x, 1e-4f)
        // Inside the surface radius the pull is evaluated at r = radius, not 1.
        val softened = newtonAccel(0f, 1f, planetGm, 3f, Vector2())
        assertEquals(softened.y * PHYSICS_UPDATE_INTERVAL, inside.linearVelocity.y, 1e-5f)
    }

    @Test
    fun kinematicNonPhotonProjectileGetsAVelocityKick() {
        addSource(0f, 0f)
        val body = addBodyOnly(BodyDef.BodyType.KinematicBody, 30f, 0f, 0f, 5f)
        step()
        assertEquals(5f, body.linearVelocity.y, 1e-6f)
        assertEquals(-gmHole / (30f * 30f) * PHYSICS_UPDATE_INTERVAL, body.linearVelocity.x, 1e-4f)
    }

    @Test
    fun photonPassingAtB40IsDeflectedByTwoRsOverB() {
        addSource(0f, 0f)
        val b = 40f
        val photon = addPhoton(-200f, b, 1f, 0f)
        step((2f * 200f / (c * PHYSICS_UPDATE_INTERVAL)).toInt() + 30)
        val deflection = abs(atan2(photon.dir.y, photon.dir.x))
        val expected = 2f * BH_RS / b
        assertEquals(expected, deflection, 0.15f * expected)
        assertEquals(1f, photon.dir.len(), 1e-4f)
    }

    @Test
    fun photonBodyVelocityIsCAlongDirAndAngleFollowsIt() {
        addSource(0f, 0f)
        val photon = addPhoton(-10f, 6f, 1f, 0f)
        val body = engine.entities.first { it.getComponent(PhotonComponent::class.java) === photon }
            .getComponent(BodyComponent::class.java).body
        step(5)
        assertEquals(c, body.linearVelocity.len(), 1e-3f * c)
        assertEquals(photon.dir.x * c, body.linearVelocity.x, 1e-3f * c)
        assertEquals(atan2(photon.dir.y, photon.dir.x), body.angle, 1e-4f)
        assertTrue(photon.dir.y < 0f)
    }

    @Test
    fun photonNearTheHoleMatchesAFineReferenceIntegration() {
        addSource(0f, 0f)
        val b = 3f * BH_RS
        val startX = -40f
        val photon = addPhoton(startX, b, 1f, 0f)
        step((2f * 40f / (c * PHYSICS_UPDATE_INTERVAL)).toInt() + 10)

        // Reference: a hundredth of the step, the same photon law, stopped at the same flight time.
        val fine = PHYSICS_UPDATE_INTERVAL / 100f
        val dir = Vector2(1f, 0f)
        var x = startX
        var y = b
        repeat(((2f * 40f / (c * PHYSICS_UPDATE_INTERVAL)).toInt() + 10) * 100) {
            bendPhoton(x, y, dir.x, dir.y, gmHole, c, fine, dir)
            x += c * dir.x * fine
            y += c * dir.y * fine
        }
        val expected = abs(atan2(dir.y, dir.x))
        assertTrue(expected > 0.5f, "strong bend expected, got $expected")
        assertEquals(expected, abs(atan2(photon.dir.y, photon.dir.x)), 0.05f * expected)
    }

    @Test
    fun twoSourcesFallTowardEachOtherSymmetrically() {
        val a = addSource(-30f, 0f)
        val b = addSource(30f, 0f)
        step(halfSecondSteps)
        val expected = settings.sourceCoupling * gmHole / (60f * 60f) * halfSecondSteps * PHYSICS_UPDATE_INTERVAL
        assertTrue(a.linearVelocity.x > 0f)
        assertEquals(-a.linearVelocity.x, b.linearVelocity.x, 0.01f * expected)
        assertEquals(expected, a.linearVelocity.x, 0.05f * expected)
        assertEquals(0f, a.linearVelocity.y, 1e-6f)
    }

    @Test
    fun zeroCouplingLeavesSourcesAtRest() {
        settings.sourceCoupling = 0f
        val a = addSource(-30f, 0f)
        val b = addSource(30f, 0f)
        step(halfSecondSteps)
        assertEquals(0f, a.linearVelocity.len(), 0f)
        assertEquals(0f, b.linearVelocity.len(), 0f)
    }

    @Test
    fun playerPullsASourceAtPlayerGmOverRSquared() {
        val source = addSource(0f, 0f)
        // A static player stays put, so r stays 10 u.
        addBodyOnly(BodyDef.BodyType.StaticBody, 10f, 0f, player = true)
        step(halfSecondSteps)
        val expected = settings.playerGm / 100f * halfSecondSteps * PHYSICS_UPDATE_INTERVAL
        assertEquals(expected, source.linearVelocity.x, 0.05f * expected)
        assertEquals(0f, source.linearVelocity.y, 1e-6f)
    }

    @Test
    fun asteroidDoesNotMoveTheSource() {
        val source = addSource(0f, 0f)
        addRelativistic(1.5f, 0f)
        addBodyOnly(BodyDef.BodyType.DynamicBody, 0f, 2f)
        step(halfSecondSteps)
        assertEquals(0f, source.linearVelocity.len(), 0f)
    }

    @Test
    fun noSourcesMeansNoChange() {
        val rel = addRelativistic(5f, 0f)
        val body = addBodyOnly(BodyDef.BodyType.DynamicBody, 0f, 5f)
        val photon = addPhoton(0f, 0f, 0f, 1f)
        step(10)
        assertEquals(0f, rel.accel.len(), 0f)
        assertEquals(0f, body.linearVelocity.len(), 0f)
        assertEquals(1f, photon.dir.y, 0f)
    }

    @Test
    fun gravityDisabledChangesNoVelocity() {
        settings.gravityEnabled = false
        val s1 = addSource(-30f, 0f)
        val s2 = addSource(30f, 0f)
        val rel = addRelativistic(0f, 10f)
        val dyn = addBodyOnly(BodyDef.BodyType.DynamicBody, 0f, -10f)
        val proj = addBodyOnly(BodyDef.BodyType.KinematicBody, 5f, 5f, 3f, 0f)
        addBodyOnly(BodyDef.BodyType.StaticBody, 10f, 0f, player = true)
        val photon = addPhoton(-10f, 6f, 1f, 0f)
        step(halfSecondSteps)
        for (v in listOf(s1.linearVelocity, s2.linearVelocity, dyn.linearVelocity)) assertEquals(0f, v.len(), 0f)
        assertEquals(0f, rel.u.len(), 0f)
        assertEquals(3f, proj.linearVelocity.x, 0f)
        assertEquals(0f, proj.linearVelocity.y, 0f)
        assertEquals(1f, photon.dir.x, 0f)
        assertEquals(0f, photon.dir.y, 0f)
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
