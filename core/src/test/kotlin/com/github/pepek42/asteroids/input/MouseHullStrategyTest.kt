package com.github.pepek42.asteroids.input

import com.badlogic.gdx.math.Vector2
import com.github.pepek42.asteroids.component.MoveComponent
import com.github.pepek42.asteroids.component.WeaponComponent
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.debug.parseLaunchOptions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MouseHullStrategyTest {
    @Test
    fun copiesInputAndAimsAtMouse() {
        val input = PlayerInputState().apply {
            thrustHeld = 0.7f
            fireHeld = true
            aimWorld.set(10f, 20f)
        }
        val move = MoveComponent()
        val weapon = WeaponComponent(aimAngleRad = 1f)
        MouseHullStrategy().apply(input, Vector2(10f, 10f), 0f, move, weapon, 0.016f)
        assertEquals(Math.PI.toFloat() / 2f, move.targetAngleRad!!, 1e-5f)
        assertEquals(0.7f, move.thrusters)
        assertTrue(weapon.doAttack)
        assertNull(weapon.aimAngleRad)
    }

    @Test
    fun pilotViewConvertsCursorAngleToWorldHullAngle() {
        val input = PlayerInputState().apply {
            pilotView = true
            betaShip.set(0.9f, 0f)
            aimWorld.set(10f, 10f)
        }
        val move = MoveComponent()
        MouseHullStrategy().apply(input, Vector2(), 0f, move, null, 0.016f)
        // tan θ_W = γ·tan θ_P with γ = 1/sqrt(1 − 0.9²), so a 45° cursor gives 66.45°.
        assertEquals(66.45f, Math.toDegrees(move.targetAngleRad!!.toDouble()).toFloat(), 0.05f)

        input.pilotView = false
        MouseHullStrategy().apply(input, Vector2(), 0f, move, null, 0.016f)
        assertEquals(45f, Math.toDegrees(move.targetAngleRad!!.toDouble()).toFloat(), 0.05f)
    }

    @Test
    fun inputGateMatchesHullGate() {
        val s = RelativitySettings().apply { pilotView = true; relativityEnabled = true; pilotAberration = true }
        assertTrue(s.pilotHullTransform)
        s.relativityEnabled = false
        assertFalse(s.pilotHullTransform)
        s.relativityEnabled = true
        s.pilotAberration = false
        assertFalse(s.pilotHullTransform)
        s.pilotAberration = true
        s.pilotView = false
        assertFalse(s.pilotHullTransform)
    }

    @Test
    fun worksWithoutWeapon() {
        MouseHullStrategy().apply(PlayerInputState(), Vector2(), 0f, MoveComponent(), null, 0.016f)
    }

    @Test
    fun onActivateResetsThrustersAndAim() {
        val move = MoveComponent(thrusters = 1f)
        val weapon = WeaponComponent(aimAngleRad = 2f)
        MouseHullStrategy().onActivate(move, weapon, 0f)
        assertEquals(0f, move.thrusters)
        assertNull(weapon.aimAngleRad)
    }

    @Test
    fun flagParsing() {
        assertEquals(ControlScheme.TWIN_STICK, ControlScheme.fromFlag("TWIN"))
        assertNull(ControlScheme.fromFlag(null))
        assertEquals(ControlScheme.AUTO_AIM, parseLaunchOptions(arrayOf("--controls=auto")).controls)
        assertNull(parseLaunchOptions(arrayOf("--controls=foo")).controls)
    }
}
