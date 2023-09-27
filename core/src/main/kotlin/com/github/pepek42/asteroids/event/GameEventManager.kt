package com.github.pepek42.asteroids.event

import com.badlogic.gdx.Input
import ktx.app.KtxInputAdapter
import ktx.collections.GdxArray
import ktx.log.logger

class GameEventManager : KtxInputAdapter {
    private val playerInputListeners = GdxArray<PlayerInputListener>()
    fun addInputListener(listener: PlayerInputListener) = playerInputListeners.add(listener)

    fun removeInputListener(listener: PlayerInputListener) = playerInputListeners.removeValue(listener, true)

    init {
        logger.info { "Initialised" }
    }

    override fun keyDown(keycode: Int): Boolean {
        logger.debug { "Player input start $keycode" }
        return false
    }

    override fun keyUp(keycode: Int): Boolean {
        logger.debug { "Player input end $keycode" }
        return false
    }

    override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
        logger.debug { "mouseMoved $screenX, $screenY" }
        return false
    }

    override fun scrolled(amountX: Float, amountY: Float): Boolean {
        logger.debug { "scrolled $amountX, $amountY" }
        return false
    }

    override fun touchCancelled(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        logger.debug { "touchCancelled $screenX $screenY $pointer $button" }
        return false
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        when (button) {
            Input.Buttons.LEFT -> switchFire(true)
            Input.Buttons.RIGHT -> thrusters(1f)
        }
        logger.debug { "touchDown $screenX, $screenY, $pointer, $button" }
        return true
    }

    private fun thrusters(thrusters: Float) {
        playerInputListeners.forEach { it.movement(thrusters) }
    }

    private fun switchFire(start: Boolean) {
        playerInputListeners.forEach { it.fire(start) }
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        logger.debug { "touchDragged $screenX, $screenY, $pointer" }
        return false
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        when (button) {
            Input.Buttons.LEFT -> switchFire(false)
            Input.Buttons.RIGHT -> thrusters(0f)
        }
        logger.debug { "touchUp $screenX, $screenY, $pointer, $button" }
        return true
    }

    companion object {
        val logger = logger<GameEventManager>()
    }
}
