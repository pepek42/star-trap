package com.github.pepek42.asteroids.input

import com.badlogic.gdx.math.Vector2
import com.github.pepek42.asteroids.component.MoveComponent
import com.github.pepek42.asteroids.component.WeaponComponent
import com.github.pepek42.asteroids.math.shortestAngleDiffRad
import com.github.pepek42.asteroids.physics.pilotHullToWorld

/** Exponent on cos(hull error) for the engine throttle: the engine fires only as the hull lines up. */
const val TWIN_STICK_ALIGN_EXPONENT = 2f

/** Axis lengths at or below this count as "no input". */
const val TWIN_STICK_DEADZONE = 0.1f

/** Engine throttle for a hull error [deltaRad] and axis length [axisLen]. */
fun twinStickThrottle(axisLen: Float, deltaRad: Float): Float =
    axisLen * Math.pow(maxOf(0f, Math.cos(deltaRad.toDouble()).toFloat()).toDouble(), TWIN_STICK_ALIGN_EXPONENT.toDouble()).toFloat()

/**
 * Scheme (b): WASD gives the desired thrust direction in the world frame, the hull turns toward it and the
 * engine fires as it lines up. The mouse aims an independent turret and LMB fires. RMB is ignored.
 */
class TwinStickStrategy : ControlStrategy {
    private var lastTarget = 0f

    override fun onActivate(move: MoveComponent, weapon: WeaponComponent?, shipAngleRad: Float) {
        super.onActivate(move, weapon, shipAngleRad)
        lastTarget = shipAngleRad
    }

    override fun apply(
        input: PlayerInputState,
        shipPos: Vector2,
        shipAngleRad: Float,
        move: MoveComponent,
        weapon: WeaponComponent?,
        dt: Float,
    ) {
        val len = input.moveAxis.len()
        if (len > TWIN_STICK_DEADZONE) {
            var target = Math.atan2(input.moveAxis.y.toDouble(), input.moveAxis.x.toDouble()).toFloat()
            // In pilot view the axis is in the screen (pilot) frame; map its direction into the world frame.
            if (input.pilotView) target = pilotHullToWorld(target, input.betaShip.x, input.betaShip.y)
            lastTarget = target
            move.targetAngleRad = target
            move.thrusters = twinStickThrottle(len, shortestAngleDiffRad(shipAngleRad, target))
        } else {
            move.thrusters = 0f
            move.targetAngleRad = lastTarget
        }
        weapon?.let {
            it.aimAngleRad = Math.atan2(
                (input.aimWorld.y - shipPos.y).toDouble(),
                (input.aimWorld.x - shipPos.x).toDouble(),
            ).toFloat()
            it.doAttack = input.fireHeld
        }
    }
}
