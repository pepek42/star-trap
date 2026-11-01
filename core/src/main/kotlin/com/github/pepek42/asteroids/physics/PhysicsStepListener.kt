package com.github.pepek42.asteroids.physics

/** Hook called by [com.github.pepek42.asteroids.system.PhysicsSystem] around every Box2D step. */
interface PhysicsStepListener {
    fun beforeStep(dt: Float)
    fun afterStep(dt: Float)
}
