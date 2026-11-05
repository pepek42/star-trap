package com.github.pepek42.asteroids.provider

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.PooledEngine
import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.GdxNativesLoader
import com.github.pepek42.asteroids.component.BaseInfoComponent
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.LifetimeClock
import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.component.WeaponComponent
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.lifetimeCmp
import com.github.pepek42.asteroids.component.photonCmp
import com.github.pepek42.asteroids.component.photonCmpOptional
import com.github.pepek42.asteroids.component.projectileCmp
import com.github.pepek42.asteroids.component.transformCmp
import com.github.pepek42.asteroids.faction.FactionEnum
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.SPEED_OF_LIGHT
import com.github.pepek42.asteroids.physics.aberratePhoton
import com.github.pepek42.asteroids.weapon.PHOTON_WORLD_LIFETIME
import com.github.pepek42.asteroids.weapon.WeaponType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class WeaponProjectileProviderTest {

    private val world = World(Vector2.Zero, true).apply { setAutoClearForces(false) }
    private val engine = PooledEngine()
    private val texture = mock<Texture> {
        on { width } doReturn 10
        on { height } doReturn 10
    }
    private val laserSprite = Sprite(texture)
    private val textureAtlas = mock<TextureAtlas> {
        on { createSprite("player_laser", 1) } doReturn laserSprite
    }
    private val c = SPEED_OF_LIGHT
    private val provider = WeaponProjectileProvider(textureAtlas, engine, world)

    @AfterEach
    fun tearDown() = world.dispose()

    @Test
    fun laserHasWorldLifetime() {
        // given
        val shipBody = world.createBody(BodyDef().apply { type = BodyDef.BodyType.DynamicBody })
        val ship = Entity()
            .add(BodyComponent().apply { body = shipBody })
            .add(BaseInfoComponent(faction = FactionEnum.PLAYER))
        val weapon = WeaponComponent(
            weaponType = WeaponType.LASER,
            projectileSpeed = 10f,
            projectileRadius = 0.1f,
        )

        // when
        provider.spawnProjectile(weapon, ship)

        // then
        val laser = engine.entities.first()
        val lifetime = laser.lifetimeCmp
        assertEquals(PHOTON_WORLD_LIFETIME, lifetime.remaining)
        assertEquals(PHOTON_WORLD_LIFETIME, lifetime.total)
        assertEquals(LifetimeClock.WORLD, lifetime.clock)
    }

    @Test
    fun aimAngleOverridesHullDirection() {
        val shipBody = world.createBody(BodyDef().apply { type = BodyDef.BodyType.DynamicBody })
        val ship = Entity()
            .add(BodyComponent().apply { body = shipBody })
            .add(BaseInfoComponent(faction = FactionEnum.PLAYER))
        val weapon = WeaponComponent(projectileRadius = 0.1f, aimAngleRad = Math.PI.toFloat() / 2f)

        provider.spawnProjectile(weapon, ship)

        val body = engine.entities.first().let { it.bodyCmp.body }
        assertEquals(0f, body.linearVelocity.x, 1e-3f)
        assertTrue(body.linearVelocity.y > 0f)
        assertEquals(0.2f, body.position.y, 1e-4f)
    }

    @Test
    fun nullAimFiresAlongHull() {
        val shipBody = world.createBody(BodyDef().apply { type = BodyDef.BodyType.DynamicBody })
        val ship = Entity()
            .add(BodyComponent().apply { body = shipBody })
            .add(BaseInfoComponent(faction = FactionEnum.PLAYER))
        provider.spawnProjectile(WeaponComponent(projectileRadius = 0.1f), ship)

        val body = engine.entities.first().bodyCmp.body
        assertTrue(body.linearVelocity.x > 0f)
        assertEquals(0f, body.linearVelocity.y, 1e-3f)
    }

    private fun fireFrom(
        provider: WeaponProjectileProvider,
        aimRad: Float?,
        shipVelocity: Vector2? = null,
    ): Entity {
        val shipBody = world.createBody(BodyDef().apply { type = BodyDef.BodyType.DynamicBody })
        val ship = Entity()
            .add(BodyComponent().apply { body = shipBody })
            .add(BaseInfoComponent(faction = FactionEnum.PLAYER))
        if (shipVelocity != null) {
            ship.add(RelativisticComponent().apply { vSet.set(shipVelocity) })
        }
        provider.spawnProjectile(WeaponComponent(projectileRadius = 0.1f, aimAngleRad = aimRad), ship)
        return engine.entities.first { it.bodyCmp.body !== shipBody && it.photonCmpOptional != null }
    }

    @Test
    fun laserFromRestingShipFliesAtCAlongAim() {
        val laser = fireFrom(provider, 0f)

        val v = laser.bodyCmp.body.linearVelocity
        assertEquals(c, v.x, 1e-3f)
        assertEquals(0f, v.y, 1e-3f)
        assertEquals(0f, laser.transformCmp.prevRotationDeg, 1e-3f)
        assertEquals(1f, laser.photonCmp.dir.x, 1e-4f)
    }

    @Test
    fun laserRecordsShooterBetaAtEmission() {
        val laser = fireFrom(provider, 0f, Vector2(0.6f * c, -0.3f * c))

        assertEquals(0.6f, laser.projectileCmp.shooterBetaX, 1e-4f)
        assertEquals(-0.3f, laser.projectileCmp.shooterBetaY, 1e-4f)
    }

    @Test
    fun laserFromFastShipAberratesForward() {
        val laser = fireFrom(provider, Math.PI.toFloat() / 2f, Vector2(0.9f * c, 0f))

        val body = laser.bodyCmp.body
        val deg = MathUtils.atan2(body.linearVelocity.y, body.linearVelocity.x) * MathUtils.radiansToDegrees
        assertEquals(25.84f, deg, 0.1f)
        assertEquals(c, body.linearVelocity.len(), 1e-3f)
        assertEquals(25.84f, laser.transformCmp.prevRotationDeg, 0.1f)
        assertEquals(25.84f, body.angle * MathUtils.radiansToDegrees, 0.1f)
        assertEquals(1f, laser.photonCmp.dir.len(), 1e-4f)
    }

    @Test
    fun hullFallbackFiresAlongShooterFrameHullAngle() {
        val beta = 0.9f
        val shipBody = world.createBody(BodyDef().apply {
            type = BodyDef.BodyType.DynamicBody
            angle = 66.45f * MathUtils.degreesToRadians
        })
        val ship = Entity()
            .add(BodyComponent().apply { body = shipBody })
            .add(BaseInfoComponent(faction = FactionEnum.PLAYER))
            .add(RelativisticComponent().apply { vSet.set(beta * c, 0f) })
        provider.spawnProjectile(WeaponComponent(projectileRadius = 0.1f), ship)

        val laser = engine.entities.first { it.bodyCmp.body !== shipBody && it.photonCmpOptional != null }
        val expected = aberratePhoton(beta, 0f, MathUtils.cosDeg(45f), MathUtils.sinDeg(45f), Vector2())
        val expectedDeg = MathUtils.atan2(expected.y, expected.x) * MathUtils.radiansToDegrees
        val dir = laser.photonCmp.dir
        assertEquals(expectedDeg, MathUtils.atan2(dir.y, dir.x) * MathUtils.radiansToDegrees, 0.1f)
    }

    @Test
    fun muzzleOffsetFollowsFlightLine() {
        val laser = fireFrom(provider, Math.PI.toFloat() / 2f, Vector2(0.9f * c, 0f))

        val dir = laser.photonCmp.dir
        val pos = laser.bodyCmp.body.position
        // The photon starts exactly on the line from the ship (at the origin) along its flight direction, 2 radii out.
        assertEquals(dir.x * 0.2f, pos.x, 1e-4f)
        assertEquals(dir.y * 0.2f, pos.y, 1e-4f)
    }

    @Test
    fun laserIgnoresShipVelocityWhenRelativityDisabled() {
        val plain = WeaponProjectileProvider(textureAtlas, engine, world, RelativitySettings(relativityEnabled = false))
        val laser = fireFrom(plain, Math.PI.toFloat() / 2f, Vector2(0.9f * c, 0f))

        val v = laser.bodyCmp.body.linearVelocity
        assertEquals(0f, v.x, 1e-3f)
        assertEquals(c, v.y, 1e-3f)
        assertEquals(90f, laser.transformCmp.prevRotationDeg, 1e-2f)
    }

    @Test
    fun laserSpeedFollowsRuntimeC() {
        val settings = RelativitySettings(c = 40f)
        val laser = fireFrom(WeaponProjectileProvider(textureAtlas, engine, world, settings), 0f)

        assertEquals(settings.c, laser.bodyCmp.body.linearVelocity.len(), 1e-3f)
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
