package com.github.pepek42.asteroids.provider

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.PooledEngine
import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.GdxNativesLoader
import com.github.pepek42.asteroids.component.BaseInfoComponent
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.LifetimeClock
import com.github.pepek42.asteroids.component.WeaponComponent
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.lifetimeCmp
import com.github.pepek42.asteroids.faction.FactionEnum
import com.github.pepek42.asteroids.weapon.PHOTON_MAX_BOUNCES
import com.github.pepek42.asteroids.weapon.PHOTON_WORLD_LIFETIME
import com.github.pepek42.asteroids.weapon.WeaponType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Assertions.assertTrue
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
    private val provider = WeaponProjectileProvider(textureAtlas, engine, world)

    @AfterEach
    fun tearDown() = world.dispose()

    @Test
    fun laserHasWorldLifetimeAndBounces() {
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
        assertEquals(PHOTON_MAX_BOUNCES, lifetime.bouncesLeft)
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
