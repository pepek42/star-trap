package com.github.pepek42.asteroids.physics

import com.badlogic.gdx.math.Vector2
import kotlin.math.exp

/** Time constant (s) of the low-pass filter on the visual β, so field bounces don't flash the screen. */
const val VISUAL_BETA_SMOOTHING = 0.15f

/** The player's kinematics as seen by the HUD, camera and Doppler visuals. Written by ObserverSystem. */
class ObserverState {
    val position = Vector2()
    val betaRaw = Vector2()
    var gammaRaw = 1f
    val betaVisual = Vector2()
    var gammaVisual = 1f
    var tau = 0f
    var worldTime = 0f
    var rapidity = 0f

    /** The player's gravitational clock factor (`RelativisticComponent.gravFactor`, 1 without one); not smoothed (ST-195). */
    var gravFactor = 1f

    fun reset() {
        position.setZero()
        betaRaw.setZero()
        gammaRaw = 1f
        betaVisual.setZero()
        gammaVisual = 1f
        tau = 0f
        worldTime = 0f
        rapidity = 0f
        gravFactor = 1f
    }
}

/** Exponential low-pass step toward [target]. Stable for any dt (factor stays in [0, 1]). */
fun smoothToward(current: Float, target: Float, dt: Float, timeConstant: Float): Float {
    if (timeConstant <= 0f || dt <= 0f) return if (timeConstant <= 0f) target else current
    return current + (target - current) * (1f - exp(-dt / timeConstant))
}
