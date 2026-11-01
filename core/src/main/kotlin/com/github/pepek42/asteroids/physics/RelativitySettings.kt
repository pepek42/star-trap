package com.github.pepek42.asteroids.physics

import kotlin.math.sqrt

private const val ENGINE_ACCEL_MIN = 5f
private const val ENGINE_ACCEL_MAX = 120f
private const val GAMMA_MAX_MIN = 2f
private const val FIELD_DAMPING_MAX = 5f

/**
 * Runtime-tunable relativity and arena field settings (ST-056). Lives in DI; the constants in `PhysicsConstants.kt`
 * and `BoundaryField.kt` are only its defaults. Every setter clamps to a safe range.
 *
 * Changing [c] mid-flight keeps the proper velocity u, so v = u/γ(u, c) changes on the next step. Photons already
 * in flight keep their speed until they bounce.
 */
class RelativitySettings(
    relativityEnabled: Boolean = RELATIVITY_ENABLED,
    c: Float = SPEED_OF_LIGHT,
    engineProperAccel: Float = ENGINE_PROPER_ACCEL,
    var fieldEnabled: Boolean = true,
    var fieldParams: BoundaryFieldParams = BoundaryFieldParams(),
    gammaMaxMassive: Float = GAMMA_MAX_MASSIVE,
) {
    var relativityEnabled: Boolean = relativityEnabled

    var c: Float = c.coerceIn(C_MIN, C_MAX_BOX2D_SAFE)
        set(value) {
            field = value.coerceIn(C_MIN, C_MAX_BOX2D_SAFE)
        }

    var engineProperAccel: Float = engineProperAccel.coerceIn(ENGINE_ACCEL_MIN, ENGINE_ACCEL_MAX)
        set(value) {
            field = value.coerceIn(ENGINE_ACCEL_MIN, ENGINE_ACCEL_MAX)
        }

    var gammaMaxMassive: Float = gammaMaxMassive.coerceIn(GAMMA_MAX_MIN, GAMMA_MAX_MASSIVE)
        set(value) {
            field = value.coerceIn(GAMMA_MAX_MIN, GAMMA_MAX_MASSIVE)
        }

    /** Speed cap as a fraction of c implied by [gammaMaxMassive]. */
    val betaMaxMassive: Float get() = sqrt(1f - 1f / (gammaMaxMassive * gammaMaxMassive))

    var fieldDamping: Float
        get() = fieldParams.damping
        set(value) {
            val v = value.coerceIn(0f, FIELD_DAMPING_MAX)
            if (v != fieldParams.damping) fieldParams = fieldParams.copy(damping = v)
        }
}
