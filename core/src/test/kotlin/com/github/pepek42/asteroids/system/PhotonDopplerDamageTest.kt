package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.physics.box2d.CircleShape
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.GdxNativesLoader
import com.github.pepek42.asteroids.component.BaseInfoComponent
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.EntityType
import com.github.pepek42.asteroids.component.HealthComponent
import com.github.pepek42.asteroids.component.LaserProjectile
import com.github.pepek42.asteroids.component.PhotonComponent
import com.github.pepek42.asteroids.component.ProjectileComponent
import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.faction.FactionEnum
import com.github.pepek42.asteroids.physics.PHOTON_DAMAGE_D_MAX
import com.github.pepek42.asteroids.physics.PHOTON_DAMAGE_D_MIN
import com.github.pepek42.asteroids.physics.RelativitySettings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class PhotonDopplerDamageTest {
    private val settings = RelativitySettings()
    private val c = settings.c
    private val world = World(Vector2.Zero, true)
    private val engine = Engine()

    @AfterEach
    fun tearDown() = world.dispose()

    private fun addTarget(vx: Float): Entity {
        val body = world.createBody(BodyDef().apply { type = BodyDef.BodyType.DynamicBody })
        val shape = CircleShape().apply { radius = 1f }
        body.createFixture(shape, 1f)
        shape.dispose()
        val entity = engine.createEntity()
        entity.add(BodyComponent().apply { this.body = body })
        entity.add(HealthComponent().apply { this.health = 300f })
        entity.add(RelativisticComponent().apply { vSet.set(vx, 0f) })
        body.userData = entity
        engine.addEntity(entity)
        return entity
    }

    /** A photon flying along +x with damage 50, fired from a shooter with velocity [shooterBetaX]·c. */
    private fun photon(shooterBetaX: Float = 0f, withPhoton: Boolean = true, shooterGrav: Float = 1f): Entity {
        val entity = engine.createEntity()
        entity.add(BaseInfoComponent().apply { this.faction = FactionEnum.PLAYER; this.entityType = EntityType.PROJECTILE })
        entity.add(ProjectileComponent().apply {
            projectile = LaserProjectile().apply { damage = 50f }
            this.shooterBetaX = shooterBetaX
            this.shooterGravFactor = shooterGrav
        })
        if (withPhoton) entity.add(PhotonComponent().apply { dir.set(1f, 0f) })
        return entity
    }

    private fun lost(target: Entity) = 300f - target.getComponent(HealthComponent::class.java).health

    @Test
    fun headOnTargetTakesDoubleDamage() {
        val target = addTarget(-0.6f * c)
        applyProjectileHit(engine, photon(), target, settings)
        assertEquals(100f, lost(target), 0.1f)
    }

    @Test
    fun headOnAtPointNineIsNotClampedToFour() {
        val target = addTarget(-0.9f * c)
        applyProjectileHit(engine, photon(), target, settings)
        assertEquals(50f * 4.3589f, lost(target), 0.05f)
    }

    @Test
    fun gravitationalTermScalesByShooterOverTarget() {
        val target = addTarget(0f)
        target.getComponent(RelativisticComponent::class.java).gravFactor = 0.5f
        applyProjectileHit(engine, photon(shooterGrav = 1f), target, settings)
        assertEquals(100f, lost(target), 0.1f)
    }

    @Test
    fun disabledSettingKeepsBaseDamage() {
        val target = addTarget(-0.6f * c)
        applyProjectileHit(engine, photon(), target, RelativitySettings(photonDopplerDamage = false))
        assertEquals(50f, lost(target), 1e-3f)
    }

    @Test
    fun fleeingTargetTakesHalfDamage() {
        val target = addTarget(0.6f * c)
        applyProjectileHit(engine, photon(), target, settings)
        assertEquals(25f, lost(target), 0.1f)
    }

    @Test
    fun shooterMovingAlongPhotonDoublesDamage() {
        val target = addTarget(0f)
        applyProjectileHit(engine, photon(shooterBetaX = 0.6f), target, settings)
        assertEquals(100f, lost(target), 0.1f)
    }

    @Test
    fun factorIsClamped() {
        // Head-on at 0.99c would give about 14: clamped to the maximum. Fleeing at 0.99c gives about 0.07: clamped to the minimum.
        val fast = addTarget(-0.99f * c)
        applyProjectileHit(engine, photon(), fast, settings)
        assertEquals(50f * PHOTON_DAMAGE_D_MAX, lost(fast), 1e-3f)
        val away = addTarget(0.99f * c)
        applyProjectileHit(engine, photon(), away, settings)
        assertEquals(50f * PHOTON_DAMAGE_D_MIN, lost(away), 1e-3f)
    }

    @Test
    fun projectileWithoutPhotonComponentKeepsBaseDamage() {
        val target = addTarget(-0.6f * c)
        applyProjectileHit(engine, photon(withPhoton = false), target, settings)
        assertEquals(50f, lost(target), 1e-3f)
    }

    @Test
    fun relativityOffKeepsBaseDamage() {
        val target = addTarget(-0.6f * c)
        applyProjectileHit(engine, photon(), target, RelativitySettings(relativityEnabled = false))
        assertEquals(50f, lost(target), 1e-3f)
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadNatives() {
            GdxNativesLoader.load()
            Box2D.init()
            if (Gdx.app == null) Gdx.app = mock<Application> { on { logLevel } doReturn Application.LOG_NONE }
        }
    }
}
