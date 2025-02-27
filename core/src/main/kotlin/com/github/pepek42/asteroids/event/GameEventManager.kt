package com.github.pepek42.asteroids.event

import com.badlogic.gdx.Input
import com.badlogic.gdx.Input.Keys
import com.github.pepek42.asteroids.debug.LoggingUtils.Companion.defaultLoggingUtils
import ktx.app.KtxInputAdapter
import ktx.collections.GdxArray
import ktx.log.logger

class GameEventManager : KtxInputAdapter {
    private val playerInputListeners = GdxArray<PlayerInputListener>()
    private val mapEventListener = GdxArray<MapEventListener>()
    private var ignoreInput = false

    fun addInputListener(listener: PlayerInputListener) = playerInputListeners.add(listener)

    fun removeInputListener(listener: PlayerInputListener) = playerInputListeners.removeValue(listener, true)

    fun addMapEventListener(listener: MapEventListener) = mapEventListener.add(listener)

    fun removeMapEventListener(listener: MapEventListener) = mapEventListener.removeValue(listener, true)

    init {
        logger.info { "Init finished" }
    }

    fun ignorePlayerInputs() {
        ignoreInput = true
    }

    fun enablePlayerInputs() {
        ignoreInput = false
    }

    override fun keyDown(keycode: Int): Boolean {
        if (ignoreInput) return false
        when (keycode) {
            Keys.CONTROL_RIGHT -> block()
        }
        return true
    }

    override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
        if (ignoreInput) return false
        aimPoint(screenX, screenY)
        return true
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (ignoreInput) return false
        when (button) {
            Input.Buttons.LEFT -> fire(true)
            Input.Buttons.RIGHT -> thrusters(1f)
        }
        aimPoint(screenX, screenY)
        return true
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        if (ignoreInput) return false
        aimPoint(screenX, screenY)
        return true
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (ignoreInput) return false
        when (button) {
            Input.Buttons.LEFT -> fire(false)
            Input.Buttons.RIGHT -> thrusters(0f)
        }
        aimPoint(screenX, screenY)
        return true
    }

    override fun scrolled(amountX: Float, amountY: Float): Boolean {
        if (ignoreInput) return false

        playerInputListeners.forEach { it.zoom(amountY) }
        defaultLoggingUtils.tryLogging { logger.debug { "scrolled X: $amountX Y: $amountY" } }
        return true
    }

    fun newMap(mapWidth: Float, mapHeight: Float) {
        mapEventListener.forEach { it.onNewMap(mapWidth, mapHeight) }
    }

    private fun aimPoint(screenX: Int, screenY: Int) {
        playerInputListeners.forEach { it.screenAimPoint(screenX, screenY) }
    }

    private fun thrusters(thrusters: Float) {
        playerInputListeners.forEach { it.movement(thrusters) }
    }

    private fun fire(fire: Boolean) {
        playerInputListeners.forEach { it.fire(fire) }
    }

    private fun block() {
        playerInputListeners.forEach { it.block() }
    }

    companion object {
        private val logger = logger<GameEventManager>()
    }
}
