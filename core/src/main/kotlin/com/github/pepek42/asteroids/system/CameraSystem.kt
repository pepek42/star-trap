package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.MathUtils
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.transformCmp
import com.github.pepek42.asteroids.debug.LoggingUtils.Companion.defaultLoggingUtils
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.event.PlayerInputListener
import com.github.pepek42.asteroids.physics.FIELD_SKIN
import com.github.pepek42.asteroids.provider.MapProvider
import ktx.ashley.allOf
import ktx.log.logger

private const val ZOOM_SPEED = 10
private const val CAMERA_ARENA_MARGIN = FIELD_SKIN / 2

/**
 * Clamps the camera centre on one axis so the view stays within the map, allowed to extend [margin] past each edge.
 * If the view is larger than the map, centres on the map.
 */
fun clampCameraAxis(target: Float, halfView: Float, mapSize: Float, margin: Float): Float {
    val lo = halfView - margin
    val hi = mapSize - halfView + margin
    return if (lo > hi) mapSize / 2 else target.coerceIn(lo, hi)
}

class CameraSystem(
    private val camera: OrthographicCamera,
    private val gameEventManager: GameEventManager,
    private val mapProvider: MapProvider,
) : IteratingSystem(allOf(PlayerComponent::class, TransformComponent::class).get()),
    PlayerInputListener {

    private var zoomDelta = 0f

    init {
        logger.info { "Init finished" }
    }

    override fun addedToEngine(engine: Engine?) {
        super.addedToEngine(engine)
        gameEventManager.addInputListener(this)
    }

    override fun removedFromEngine(engine: Engine?) {
        gameEventManager.removeInputListener(this)
        super.removedFromEngine(engine)
    }

    override fun processEntity(entity: Entity, deltaTime: Float) {
        handleCameraZoom(deltaTime)
        handleCameraPosition(entity)

        defaultLoggingUtils.tryLogging {
            logger.debug {
                """
                 Camera position:   -> ${camera.position}
                 Camera width       -> ${camera.viewportWidth}
                 Camera height      -> ${camera.viewportHeight}
                 Camera zoom        -> ${camera.zoom}
                """.trimIndent()
            }
        }
        camera.update()
    }

    private fun handleCameraZoom(deltaTime: Float) {
        camera.zoom = MathUtils.clamp(camera.zoom + zoomDelta * deltaTime * ZOOM_SPEED, 0.5f, 2f)
        zoomDelta = 0f
    }

    private fun handleCameraPosition(entity: Entity) {
        val interpolatedPosition = entity.transformCmp.interpolatedPosition
        val realCamWidthHalved = camera.viewportWidth / 2 * camera.zoom
        val realCamHeightHalved = camera.viewportHeight / 2 * camera.zoom
        val camX = clampCameraAxis(interpolatedPosition.x, realCamWidthHalved, mapProvider.mapWidth, CAMERA_ARENA_MARGIN)
        val camY = clampCameraAxis(interpolatedPosition.y, realCamHeightHalved, mapProvider.mapHeight, CAMERA_ARENA_MARGIN)
        camera.position.set(camX, camY, camera.position.z)
    }

    override fun zoom(zoomDelta: Float) {
        this.zoomDelta = zoomDelta
    }

    companion object {
        private val logger = logger<CameraSystem>()
    }
}
