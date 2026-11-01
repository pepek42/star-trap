package com.github.pepek42.asteroids.input

import com.badlogic.gdx.math.Vector2
import com.github.pepek42.asteroids.component.MoveComponent
import com.github.pepek42.asteroids.component.WeaponComponent

/** Scheme (a): the hull faces the mouse, RMB thrusts along the hull, LMB fires along the hull. */
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
        move.targetAngleRad = Math.atan2(
            (input.aimWorld.y - shipPos.y).toDouble(),
            (input.aimWorld.x - shipPos.x).toDouble(),
        ).toFloat()
        weapon?.let {
            it.doAttack = input.fireHeld
            it.aimAngleRad = null
        }
    }
}
