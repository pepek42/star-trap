package com.github.pepek42.asteroids.input

import com.badlogic.gdx.math.Vector2
import com.github.pepek42.asteroids.component.MoveComponent
import com.github.pepek42.asteroids.component.WeaponComponent

interface ControlStrategy {
    fun apply(
        input: PlayerInputState,
        shipPos: Vector2,
        shipAngleRad: Float,
        move: MoveComponent,
        weapon: WeaponComponent?,
        dt: Float,
    )

    /** Called when this strategy becomes the active one. */
    fun onActivate(move: MoveComponent, weapon: WeaponComponent?, shipAngleRad: Float) {
        move.thrusters = 0f
        weapon?.aimAngleRad = null
    }
}
