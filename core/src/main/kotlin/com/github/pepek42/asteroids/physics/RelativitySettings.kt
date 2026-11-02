package com.github.pepek42.asteroids.physics

import kotlin.math.sqrt

private const val FIELD_DAMPING_MAX = 5f
private const val BRAKE_BOOST_MAX = 5f

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
    engineAccelPerC: Float = ENGINE_ACCEL_PER_C,
    var fieldEnabled: Boolean = true,
    var fieldParams: BoundaryFieldParams = BoundaryFieldParams(),
    gammaMaxMassive: Float = GAMMA_MAX_MASSIVE,
    brakeBoost: Float = BRAKE_BOOST_K,
    timeWarpEnabled: Boolean = TIME_WARP_ENABLED,
    timeWarpMax: Float = TIME_WARP_MAX,
) {
    var relativityEnabled: Boolean = relativityEnabled

    var c: Float = c.coerceIn(C_MIN, C_MAX_BOX2D_SAFE)
        set(value) {
            field = value.coerceIn(C_MIN, C_MAX_BOX2D_SAFE)
        }

    /** Engine proper acceleration as a fraction of [c], so changing c keeps the feel. */
    var engineAccelPerC: Float = engineAccelPerC.coerceIn(ENGINE_ACCEL_PER_C_MIN, ENGINE_ACCEL_PER_C_MAX)
        set(value) {
            field = value.coerceIn(ENGINE_ACCEL_PER_C_MIN, ENGINE_ACCEL_PER_C_MAX)
        }

    /** Engine proper acceleration (u/s²) at the current c. */
    val engineProperAccel: Float get() = engineAccelPerC * c

    var gammaMaxMassive: Float = gammaMaxMassive.coerceIn(GAMMA_MAX_MIN, GAMMA_MAX_MASSIVE)
        set(value) {
            field = value.coerceIn(GAMMA_MAX_MIN, GAMMA_MAX_MASSIVE)
        }

    /** Braking boost k (ST-066). Engines burning against the motion get a0·(1 + k·max(0, −cos θ)). */
    var brakeBoost: Float = brakeBoost.coerceIn(0f, BRAKE_BOOST_MAX)
        set(value) {
            field = value.coerceIn(0f, BRAKE_BOOST_MAX)
        }

    var timeWarpEnabled: Boolean = timeWarpEnabled

    /** Upper bound of the pilot clock's time warp (ST-068). */
    var timeWarpMax: Float = timeWarpMax.coerceIn(1f, TIME_WARP_MAX_LIMIT)
        set(value) {
            field = value.coerceIn(1f, TIME_WARP_MAX_LIMIT)
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
