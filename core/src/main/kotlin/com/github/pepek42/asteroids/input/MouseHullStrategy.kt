package com.github.pepek42.asteroids.input

import com.badlogic.gdx.math.Vector2
import com.github.pepek42.asteroids.component.MoveComponent
import com.github.pepek42.asteroids.component.WeaponComponent
import com.github.pepek42.asteroids.physics.pilotHullToWorld

/**
 * Scheme (a): the hull faces the mouse, RMB thrusts along the hull, LMB fires along the hull.
 *
 * In pilot view the cursor direction is an angle in the pilot's frame (P), where the hull is drawn at
 * `worldHullToPilot(body.angle)`. The body angle is a W angle, so the target is `pilotHullToWorld(θ, betaShip)`.
 */
class MouseHullStrategy : ControlStrategy {
    override fun apply(
        input: PlayerInputState,
        shipPos: Vector2,
        shipAngleRad: Float,
        move: MoveComponent,
        weapon: WeaponComponent?,
        dt: Float,
    ) {
        move.thrusters = input.thrustHeld
        val cursorAngle = Math.atan2(
            (input.aimWorld.y - shipPos.y).toDouble(),
            (input.aimWorld.x - shipPos.x).toDouble(),
        ).toFloat()
        move.targetAngleRad = if (input.pilotView) {
            pilotHullToWorld(cursorAngle, input.betaShip.x, input.betaShip.y)
        } else {
            cursorAngle
        }
        weapon?.let {
            it.doAttack = input.fireHeld
            it.aimAngleRad = null
        }
    }
}
