package com.github.pepek42.asteroids.event

import com.github.pepek42.asteroids.debug.LoggingUtils.Companion.defaultLoggingUtils
import com.github.pepek42.asteroids.input.Binding
import com.github.pepek42.asteroids.input.InputAction
import com.github.pepek42.asteroids.input.InputBindings
import com.github.pepek42.asteroids.input.buttonAction
import com.github.pepek42.asteroids.input.keyAction
import ktx.app.KtxInputAdapter
import ktx.collections.GdxArray
import ktx.log.logger

class GameEventManager(private val bindings: List<Binding> = InputBindings.DEFAULT) : KtxInputAdapter {
    private val playerInputListeners = GdxArray<PlayerInputListener>()
    private val mapEventListener = GdxArray<MapEventListener>()
    private val debugListeners = GdxArray<DebugInputListener>()
    private var ignoreInput = false
    private var up = false
    private var down = false
    private var left = false
    private var right = false

    fun addInputListener(listener: PlayerInputListener) = playerInputListeners.add(listener)

    fun removeInputListener(listener: PlayerInputListener) = playerInputListeners.removeValue(listener, true)

    fun addMapEventListener(listener: MapEventListener) = mapEventListener.add(listener)

    fun removeMapEventListener(listener: MapEventListener) = mapEventListener.removeValue(listener, true)

    fun addDebugListener(listener: DebugInputListener) = debugListeners.add(listener)

    fun removeDebugListener(listener: DebugInputListener) = debugListeners.removeValue(listener, true)

    init {
        logger.info { "Init finished" }
    }

    fun ignorePlayerInputs() {
        ignoreInput = true
        up = false
        down = false
        left = false
        right = false
        dispatchMoveAxis()
    }

    fun enablePlayerInputs() {
        ignoreInput = false
    }

    override fun keyDown(keycode: Int): Boolean {
        val action = bindings.keyAction(keycode)
        if (action == InputAction.DEBUG_PANEL) {
            debugListeners.forEach { it.toggleDebugPanel() }
            return true
        }
        if (ignoreInput) return false
        return when (action) {
            InputAction.BLOCK -> {
                block()
                true
            }
            InputAction.TOGGLE_PILOT_VIEW -> {
                playerInputListeners.forEach { it.togglePilotView() }
                true
            }
            InputAction.TOGGLE_TACTICAL_OVERLAY -> {
                playerInputListeners.forEach { it.toggleTacticalOverlay() }
                true
            }
            InputAction.QUIT_TO_MENU -> {
                playerInputListeners.forEach { it.quitToMenu() }
                true
            }
            // Move keys are consumed in every scheme; only TwinStickStrategy reads moveAxis today.
            InputAction.MOVE_UP, InputAction.MOVE_DOWN, InputAction.MOVE_LEFT, InputAction.MOVE_RIGHT ->
                setMoveKey(action, true)
            else -> false
        }
    }

    override fun keyUp(keycode: Int): Boolean {
        if (ignoreInput) return false
        return when (val action = bindings.keyAction(keycode)) {
            InputAction.MOVE_UP, InputAction.MOVE_DOWN, InputAction.MOVE_LEFT, InputAction.MOVE_RIGHT ->
                setMoveKey(action, false)
            else -> false
        }
    }

    private fun setMoveKey(action: InputAction, pressed: Boolean): Boolean {
        when (action) {
            InputAction.MOVE_UP -> up = pressed
            InputAction.MOVE_DOWN -> down = pressed
            InputAction.MOVE_LEFT -> left = pressed
            else -> right = pressed
        }
        dispatchMoveAxis()
        return true
    }

    private fun dispatchMoveAxis() {
        var x = (if (right) 1f else 0f) - (if (left) 1f else 0f)
        var y = (if (up) 1f else 0f) - (if (down) 1f else 0f)
        val len = Math.sqrt((x * x + y * y).toDouble()).toFloat()
        if (len > 1f) {
            x /= len
            y /= len
        }
        playerInputListeners.forEach { it.moveAxis(x, y) }
    }

    override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
        if (ignoreInput) return false
        aimPoint(screenX, screenY)
        return true
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (ignoreInput) return false
        when (bindings.buttonAction(button)) {
            InputAction.FIRE -> fire(true)
            InputAction.THRUST -> thrusters(1f)
            else -> {}
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
        when (bindings.buttonAction(button)) {
            InputAction.FIRE -> fire(false)
            InputAction.THRUST -> thrusters(0f)
            else -> {}
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

    fun thrusters(thrusters: Float) {
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
