package com.github.pepek42.asteroids.input

import com.badlogic.gdx.math.Vector2
import com.github.pepek42.asteroids.component.MoveComponent
import com.github.pepek42.asteroids.component.WeaponComponent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TwinStickStrategyTest {
    private val halfPi = Math.PI.toFloat() / 2f

    private fun run(s: TwinStickStrategy, input: PlayerInputState, angle: Float, move: MoveComponent, w: WeaponComponent? = null) =
        s.apply(input, Vector2(), angle, move, w, 0.016f)

    @Test
    fun throttleFollowsHullAlignment() {
        val input = PlayerInputState().apply { moveAxis.set(0f, 1f) }
        val s = TwinStickStrategy()
        val move = MoveComponent()
        run(s, input, 0f, move)
        assertEquals(halfPi, move.targetAngleRad!!, 1e-5f)
        assertEquals(0f, move.thrusters, 1e-4f)
        run(s, input, halfPi, move)
        assertEquals(1f, move.thrusters, 1e-4f)
        run(s, input, halfPi / 2f, move)
        assertEquals(0.5f, move.thrusters, 1e-4f)
    }

    @Test
    fun releasedAxisHoldsLastHeading() {
        val input = PlayerInputState().apply { moveAxis.set(0f, 1f) }
        val s = TwinStickStrategy()
        val move = MoveComponent()
        run(s, input, 0f, move)
        input.moveAxis.set(0f, 0f)
        run(s, input, 0.3f, move)
        assertEquals(0f, move.thrusters)
        assertEquals(halfPi, move.targetAngleRad!!, 1e-5f)
    }

    @Test
    fun turretFollowsMouseWhateverTheHull() {
        val input = PlayerInputState().apply { aimWorld.set(0f, -5f); fireHeld = true }
        val weapon = WeaponComponent()
        val move = MoveComponent()
        val s = TwinStickStrategy()
        for (angle in listOf(0f, 1f, 3f)) {
            run(s, input, angle, move, weapon)
            assertEquals(-halfPi, weapon.aimAngleRad!!, 1e-5f)
            assertTrue(weapon.doAttack)
        }
    }

    @Test
    fun pilotViewMapsScreenAxisToWorldFrame() {
        val s = TwinStickStrategy()
        val move = MoveComponent()
        val input = PlayerInputState().apply {
            pilotView = true
            betaShip.set(0.6f, 0f)
            moveAxis.set(0f, 1f)
        }
        // Boost along x leaves a screen-up axis (perpendicular to β) unchanged.
        run(s, input, 0f, move)
        assertEquals(halfPi, move.targetAngleRad!!, 1e-5f)

        // A diagonal axis is rescaled by the pilot-frame transform, not left at its screen angle.
        input.moveAxis.set(1f, 1f)
        run(s, input, 0f, move)
        val expected = com.github.pepek42.asteroids.physics.pilotHullToWorld(halfPi / 2f, 0.6f, 0f)
        assertEquals(expected, move.targetAngleRad!!, 1e-5f)
        assertTrue(move.targetAngleRad!! > halfPi / 2f)
    }

    @Test
    fun outsidePilotViewUsesWorldAxisDirectly() {
        val s = TwinStickStrategy()
        val move = MoveComponent()
        val input = PlayerInputState().apply {
            betaShip.set(0.6f, 0f)
            moveAxis.set(1f, 1f)
        }
        run(s, input, 0f, move)
        assertEquals(halfPi / 2f, move.targetAngleRad!!, 1e-5f)
    }

    @Test
    fun onActivateHoldsShipHeading() {
        val s = TwinStickStrategy()
        val move = MoveComponent()
        s.onActivate(move, null, 1.2f)
        run(s, PlayerInputState(), 0f, move)
        assertEquals(1.2f, move.targetAngleRad!!, 1e-5f)
    }
}
