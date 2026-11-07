package com.github.pepek42.asteroids.event

interface PlayerInputListener {
    fun movement(thrusters: Float) = Unit

    fun screenAimPoint(screenX: Int, screenY: Int) = Unit

    /** WASD / arrow keys as a world-frame desired-thrust axis, length at most 1. */
    fun moveAxis(x: Float, y: Float) = Unit

    fun fire(fire: Boolean) = Unit

    fun block() = Unit

    fun zoom(zoomDelta: Float) = Unit

    fun togglePilotView() = Unit

    fun toggleTacticalOverlay() = Unit

    /** Leave the run and go back to the main menu. */
    fun quitToMenu() = Unit
}
