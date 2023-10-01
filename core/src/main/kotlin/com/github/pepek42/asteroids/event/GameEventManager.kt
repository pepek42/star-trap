package com.github.pepek42.asteroids.event

import com.badlogic.gdx.Input
import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.math.Vector3
import ktx.app.KtxInputAdapter
import ktx.collections.GdxArray
import ktx.log.logger

class GameEventManager(
    private val camera: Camera
) : KtxInputAdapter {
    private val playerInputListeners = GdxArray<PlayerInputListener>()

    fun addInputListener(listener: PlayerInputListener) = playerInputListeners.add(listener)
    private var ignoreInput = false

    fun removeInputListener(listener: PlayerInputListener) = playerInputListeners.removeValue(listener, true)

    init {
        logger.info { "Initialised" }
    }

    fun ignorePlayerInputs() {
        ignoreInput = true
    }

    fun enablePlayerInputs() {
        ignoreInput = false
    }

    override fun keyDown(keycode: Int): Boolean {
        // TODO ZOOM
//        if (Gdx.input.isKeyPressed(Keys.NUMPAD_ADD)) {
//            camera.zoom -= delta * 0.5f
//            if (camera.zoom < 0.01) {
//                camera.zoom = 0.01f
//            }
//        }
//        if (Gdx.input.isKeyPressed(Keys.NUMPAD_SUBTRACT)) {
//            camera.zoom += delta * 0.5f
//        }
//        camera.update()

        if (ignoreInput) return false
        when (keycode) {
            Keys.CONTROL_RIGHT -> block()
        }
        // logger.debug { "Player input start $keycode" }
        return true
    }

    override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
        aimPoint(screenX, screenY)
        //logger.debug { "mouseMoved $screenX, $screenY" }
        return true
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (ignoreInput) return false
        when (button) {
            Input.Buttons.LEFT -> switchFire(true)
            Input.Buttons.RIGHT -> thrusters(1f)
        }
        aimPoint(screenX, screenY)
        // logger.debug { "touchDown $screenX, $screenY, $button" }
        return true
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        aimPoint(screenX, screenY)
        // logger.debug { "touchDragged $screenX, $screenY" }
        return true
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (ignoreInput) return false
        when (button) {
            Input.Buttons.LEFT -> switchFire(false)
            Input.Buttons.RIGHT -> thrusters(0f)
        }
        aimPoint(screenX, screenY)
        // logger.debug { "touchUp $screenX, $screenY, $button" }
        return true
    }

    private fun aimPoint(screenX: Int, screenY: Int) {
        val gameCoordinates = camera.unproject(
            Vector3(
                screenX.toFloat(),
                screenY.toFloat(),
                0f
            )
        )
        playerInputListeners.forEach {
            it.aimPoint(gameCoordinates)
        }
    }

    private fun thrusters(thrusters: Float) {
        playerInputListeners.forEach { it.movement(thrusters) }
    }

    private fun switchFire(start: Boolean) {
        playerInputListeners.forEach { it.fire(start) }
    }

    private fun block() {
        playerInputListeners.forEach { it.block() }
    }

    companion object {
        val logger = logger<GameEventManager>()
    }
}
