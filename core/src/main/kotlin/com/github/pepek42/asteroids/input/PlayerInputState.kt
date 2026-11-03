package com.github.pepek42.asteroids.input

import com.badlogic.gdx.math.Vector2

/** Raw player input collected from [com.github.pepek42.asteroids.event.PlayerInputListener] callbacks. */
class PlayerInputState {
    var thrustHeld = 0f
    var fireHeld = false
    /** Aim point in world coordinates. */
    val aimWorld = Vector2()
    /** Movement axis for schemes with decoupled movement; zero until ST-061. */
    val moveAxis = Vector2()
    /** The ship's visual velocity over c in W (`ObserverState.betaVisual`), the boost into the pilot frame. */
    val betaShip = Vector2()
    /** True when the screen shows the pilot's frame (pilot view with relativity on), so aim angles are pilot-frame angles. */
    var pilotView = false
}
