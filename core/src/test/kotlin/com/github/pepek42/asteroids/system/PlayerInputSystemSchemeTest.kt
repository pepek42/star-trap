package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.GdxNativesLoader
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.MoveComponent
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.input.ControlScheme
import com.github.pepek42.asteroids.input.ControlSettings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class PlayerInputSystemSchemeTest {

    private val world = World(Vector2.Zero, true)
    private val engine = Engine()
    private val controls = ControlSettings(ControlScheme.AUTO_AIM)
    private val system = PlayerInputSystem(mock<GameEventManager>(), mock<Camera>(), controls)

    @AfterEach
    fun tearDown() = world.dispose()

    @Test
    fun switchingSchemeCallsOnActivateOfNewStrategy() {
        val shipAngle = 0.7f
        val body = world.createBody(BodyDef().apply {
            type = BodyDef.BodyType.DynamicBody
            angle = shipAngle
        })
        val move = MoveComponent()
        engine.addSystem(system)
        engine.addEntity(
            Entity().add(PlayerComponent()).add(move).add(BodyComponent().apply { this.body = body })
        )

        // AUTO_AIM passes the held thrust through and turns toward the cursor (at the origin here).
        // No light history in this engine, so it does not pick targets.
        system.movement(1f)
        engine.update(0.016f)
        assertEquals(1f, move.thrusters, 1e-6f)

        // TWIN_STICK onActivate zeroes the thrusters and holds the current hull heading (0.7), not the cursor (0).
        controls.scheme = ControlScheme.TWIN_STICK
        engine.update(0.016f)
        assertEquals(0f, move.thrusters, 1e-6f)
        assertEquals(shipAngle, move.targetAngleRad!!, 1e-5f)
    }

    @Test
    fun schemeChangeClearsMoveAxis() {
        val shipAngle = 0.7f
        val body = world.createBody(BodyDef().apply {
            type = BodyDef.BodyType.DynamicBody
            angle = shipAngle
        })
        val move = MoveComponent()
        engine.addSystem(system)
        engine.addEntity(
            Entity().add(PlayerComponent()).add(move).add(BodyComponent().apply { this.body = body })
        )
        engine.update(0.016f)

        // Axis pressed in AUTO_AIM, then the scheme switches: the stale axis must not steer TWIN_STICK.
        system.moveAxis(0f, 1f)
        controls.scheme = ControlScheme.TWIN_STICK
        engine.update(0.016f)
        assertEquals(0f, move.thrusters, 1e-6f)
        assertEquals(shipAngle, move.targetAngleRad!!, 1e-5f)
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
