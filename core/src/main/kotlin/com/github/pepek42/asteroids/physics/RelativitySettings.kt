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
    var gammaMassEnabled: Boolean = GAMMA_MASS_ENABLED,
    photonDopplerDamage: Boolean = true,
    gravityEnabled: Boolean = GRAVITY_ENABLED,
    sourceCoupling: Float = SOURCE_COUPLING_DEFAULT,
    playerGm: Float = PLAYER_GM,
    var blackHolesEnabled: Boolean = true,
    var planetsEnabled: Boolean = true,
    var decorationEnabled: Boolean = true,
    var lightingEnabled: Boolean = true,
    var shadowsEnabled: Boolean = true,
    var starLightEnabled: Boolean = true,
    var lensingEnabled: Boolean = true,
) {
    var relativityEnabled: Boolean = relativityEnabled

    // lightingEnabled (constructor property, ST-097): accretion disks light ships, asteroids, debris and planets (render/LitSpriteRenderer).
    // starLightEnabled (constructor property, ST-099): the level's distant star lights sprites as a directional light and is shown on the minimap.
    // lensingEnabled (constructor property, ST-088): black holes bend the starfield and nebula into an Einstein ring (render/LensingPass).
    // shadowsEnabled (constructor property, ST-098): planets and large asteroids shadow lit sprites; needs lightingEnabled.

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

    /**
     * Photon damage scales with the Doppler factor shooter to target, clamped to [PHOTON_DAMAGE_D_MIN, PHOTON_DAMAGE_D_MAX]
     * (ST-081, research E12). Only applies while [relativityEnabled].
     */
    var photonDopplerDamage: Boolean = photonDopplerDamage

    /** Gravity sources act on bodies and photons (ST-083). Off: no body or photon feels any pull. */
    var gravityEnabled: Boolean = gravityEnabled

    /** Scale of the source-to-source pull, 0..1 (ST-083); 0 leaves the sources at rest. FAKE, see [SOURCE_COUPLING_DEFAULT]. */
    var sourceCoupling: Float = sourceCoupling.coerceIn(0f, 1f)
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    /** `GM` of the player as a source, pulling only gravity sources (ST-083), clamped to 0..[PLAYER_GM_MAX]. */
    var playerGm: Float = playerGm.coerceIn(0f, PLAYER_GM_MAX)
        set(value) {
            field = value.coerceIn(0f, PLAYER_GM_MAX)
        }

    // gammaMassEnabled (constructor property, ST-047): Box2D mass = min(γ, GAMMA_MASS_CAP)·m₀ while relativity is on.

    /** Upper bound of the pilot clock's time warp (ST-068). */
    var timeWarpMax: Float = timeWarpMax.coerceIn(1f, TIME_WARP_MAX_LIMIT)
        set(value) {
            field = value.coerceIn(1f, TIME_WARP_MAX_LIMIT)
        }

    /** Main view shows the pilot's apparent world (ST-074); false draws true arena positions. Toggled with V. */
    var pilotView: Boolean = PILOT_VIEW_DEFAULT

    /** Debug switch for the aberration half of [pilotView]: boost the retarded positions into the pilot frame. */
    var pilotAberration: Boolean = true

    /**
     * The single gate for the player's hull angle: drawn at `worldHullToPilot(body.angle)` and the cursor mapped back with
     * `pilotHullToWorld`. Sprite projection of other entities is gated separately by [pilotView], [pilotAberration], [pilotDelay].
     */
    val pilotHullTransform: Boolean get() = pilotView && relativityEnabled && pilotAberration

    /** Debug switch for the light delay half of [pilotView]: draw others where their light left them. */
    var pilotDelay: Boolean = true

    /** Speed cap as a fraction of c implied by [gammaMaxMassive]. */
    val betaMaxMassive: Float get() = sqrt(1f - 1f / (gammaMaxMassive * gammaMaxMassive))

    var fieldDamping: Float
        get() = fieldParams.damping
        set(value) {
            val v = value.coerceIn(0f, FIELD_DAMPING_MAX)
            if (v != fieldParams.damping) fieldParams = fieldParams.copy(damping = v)
        }
}
