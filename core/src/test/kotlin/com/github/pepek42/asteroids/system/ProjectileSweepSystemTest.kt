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
import com.github.pepek42.asteroids.component.EntityType
import com.github.pepek42.asteroids.component.HealthComponent
import com.github.pepek42.asteroids.component.LaserProjectile
import com.github.pepek42.asteroids.component.ProjectileComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.projectileCmp
import com.github.pepek42.asteroids.component.removeMapper
import com.github.pepek42.asteroids.component.transformCmp
import com.github.pepek42.asteroids.faction.FactionEnum
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class ProjectileSweepSystemTest {
    private val world = World(Vector2.Zero, true).apply { setAutoClearForces(false) }
    private val engine = Engine()
    private val sweep = ProjectileSweepSystem(world)
    private val contact = ContactSystem(world)
    private val physics: PhysicsSystem

    init {
        engine.addSystem(contact)
        engine.addSystem(sweep)
        physics = PhysicsSystem(world, engine, listOf(sweep))
    }

    @AfterEach
    fun tearDown() = world.dispose()

    private fun addCircle(
        type: BodyDef.BodyType,
        x: Float,
        y: Float,
        vx: Float,
        radius: Float,
        sensor: Boolean,
        faction: FactionEnum,
        entityType: EntityType,
    ): Entity {
        val body: Body = world.createBody(BodyDef().apply {
            this.type = type
            position.set(x, y)
            linearVelocity.set(vx, 0f)
        })
        val shape = CircleShape().apply { this.radius = radius }
        val fixture = body.createFixture(shape, if (type == BodyDef.BodyType.DynamicBody) 1f else 0f)
        fixture.isSensor = sensor
        fixture.filterData = fixture.filterData.apply {
            categoryBits = faction.ownCategoryBit
            maskBits = faction.collidesWithMask
        }
        shape.dispose()
        val entity = engine.createEntity()
        entity.add(BodyComponent().apply { this.body = body })
        entity.add(TransformComponent())
        entity.add(BaseInfoComponent(faction = faction, entityType = entityType))
        body.userData = entity
        engine.addEntity(entity)
        return entity
    }

    private fun addProjectile(x: Float, vx: Float, faction: FactionEnum = FactionEnum.PLAYER): Entity {
        val e = addCircle(BodyDef.BodyType.KinematicBody, x, 0f, vx, 0.1f, true, faction, EntityType.PROJECTILE)
        e.add(ProjectileComponent().apply { projectile = LaserProjectile().apply { damage = 10f } })
        return e
    }

    private fun addTarget(
        type: BodyDef.BodyType,
        x: Float,
        faction: FactionEnum = FactionEnum.NEUTRAL,
    ): Entity {
        val e = addCircle(type, x, 0f, 0f, 0.3f, false, faction, EntityType.ASTEROID)
        e.add(HealthComponent().apply { health = 100f })
        return e
    }

    private fun step() = physics.update(PhysicsSystem.PHYSICS_UPDATE_INTERVAL + 1e-4f)

    @Test
    fun fastProjectileThatStraddlesStaticTargetHitsOnce() {
        // 89 u/s at 45 Hz = ~1.98 u per step: steps land at x=0 and x~1.98 and x~3.96; target at 1 (r 0.3).
        val target = addTarget(BodyDef.BodyType.StaticBody, 1f)
        val projectile = addProjectile(0f, 89f)
        // Projectile positions skip the target: [-0.1,0.1] then [1.88,2.08] never overlap [0.7,1.3].
        repeat(3) { step() }
        assertEquals(90f, target.getComponent(HealthComponent::class.java).health, 1e-4f)
        assertTrue(projectile.projectileCmp.spent)
        assertTrue(removeMapper.get(projectile) != null)
    }

    private val dt = PhysicsSystem.PHYSICS_UPDATE_INTERVAL

    @Test
    fun overlapThenSweepInSameStepDamageOnlyOnce() {
        val target = addTarget(BodyDef.BodyType.DynamicBody, 1.2f)
        // Starts left of the target, ends the step well inside it (x = 1.39 after the first step).
        val projectile = addProjectile(0.5f, 40f)
        projectile.transformCmp.prevPosition.set(0.5f, 0f)
        // Box2D discovers the new pair at the end of step 1 and fires beginContact at the start of step 2.
        world.step(dt, 6, 2)
        world.step(dt, 6, 2)
        assertTrue(projectile.projectileCmp.spent, "beginContact should have fired")
        assertEquals(90f, target.getComponent(HealthComponent::class.java).health, 1e-4f)
        val remove = removeMapper.get(projectile)
        assertNotNull(remove)
        sweep.afterStep(dt)
        assertEquals(90f, target.getComponent(HealthComponent::class.java).health, 1e-4f)
        assertSame(remove, removeMapper.get(projectile))
    }

    @Test
    fun sweepHitBeforeStepContactDamagesOnlyOnce() {
        val target = addTarget(BodyDef.BodyType.DynamicBody, 1.2f)
        val projectile = addProjectile(0.5f, 40f)
        applyProjectileHit(engine, projectile, target)
        val remove = removeMapper.get(projectile)
        assertNotNull(remove)
        world.step(dt, 6, 2)
        sweep.afterStep(dt)
        assertEquals(90f, target.getComponent(HealthComponent::class.java).health, 1e-4f)
        assertSame(remove, removeMapper.get(projectile))
    }

    @Test
    fun sweepIgnoresSameFactionTarget() {
        val target = addTarget(BodyDef.BodyType.StaticBody, 1f, FactionEnum.PLAYER)
        val projectile = addProjectile(0f, 89f, FactionEnum.PLAYER)
        repeat(3) { step() }
        assertEquals(100f, target.getComponent(HealthComponent::class.java).health, 1e-4f)
        assertFalse(projectile.projectileCmp.spent)
        assertTrue(removeMapper.get(projectile) == null)
    }

    @Test
    fun spentProjectileDoesNotHitAgain() {
        val a = addTarget(BodyDef.BodyType.StaticBody, 1f)
        val projectile = addProjectile(0f, 89f)
        repeat(3) { step() }
        val b = addTarget(BodyDef.BodyType.StaticBody, 1.2f)
        val remove = removeMapper.get(projectile)
        applyProjectileHit(engine, projectile, b)
        assertEquals(90f, a.getComponent(HealthComponent::class.java).health, 1e-4f)
        assertEquals(100f, b.getComponent(HealthComponent::class.java).health, 1e-4f)
        assertSame(remove, removeMapper.get(projectile))
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
