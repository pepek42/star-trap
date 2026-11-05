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
import com.github.pepek42.asteroids.physics.ObserverState
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.provider.MapProvider
import ktx.ashley.allOf
import ktx.log.logger

private const val ZOOM_SPEED = 10
private const val ZOOM_MIN = 0.5f
private const val ZOOM_MAX = 2f

/** Extra zoom-out at beta = 1: zoom is multiplied by (1 + k * beta^2). */
const val CAMERA_ZOOM_PER_BETA2 = 0.6f

/** Camera zoom from the wheel zoom and the visual speed [beta] (fraction of c). */
fun speedZoom(wheelZoom: Float, beta: Float, k: Float = CAMERA_ZOOM_PER_BETA2): Float =
    (wheelZoom * (1f + k * beta * beta)).coerceIn(ZOOM_MIN, ZOOM_MAX)

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
    private val observer: ObserverState,
    private val settings: RelativitySettings,
) : IteratingSystem(allOf(PlayerComponent::class, TransformComponent::class).get()),
    PlayerInputListener {

    private var zoomDelta = 0f
    private var wheelZoom = camera.zoom

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
        wheelZoom = MathUtils.clamp(wheelZoom + zoomDelta * deltaTime * ZOOM_SPEED, ZOOM_MIN, ZOOM_MAX)
        camera.zoom = speedZoom(wheelZoom, observer.betaVisual.len())
        zoomDelta = 0f
    }

    private fun handleCameraPosition(entity: Entity) {
        val interpolatedPosition = entity.transformCmp.interpolatedPosition
        if (settings.pilotView) {
            // The boosted view has no fixed edges, so the arena clamp does not apply.
            camera.position.set(interpolatedPosition.x, interpolatedPosition.y, camera.position.z)
            return
        }
        val realCamWidthHalved = camera.viewportWidth / 2 * camera.zoom
        val realCamHeightHalved = camera.viewportHeight / 2 * camera.zoom
        val margin = settings.fieldParams.skin / 2
        val camX = clampCameraAxis(interpolatedPosition.x, realCamWidthHalved, mapProvider.mapWidth, margin)
        val camY = clampCameraAxis(interpolatedPosition.y, realCamHeightHalved, mapProvider.mapHeight, margin)
        camera.position.set(camX, camY, camera.position.z)
    }

    override fun zoom(zoomDelta: Float) {
        this.zoomDelta = zoomDelta
    }

    override fun togglePilotView() {
        settings.pilotView = !settings.pilotView
    }

    companion object {
        private val logger = logger<CameraSystem>()
    }
}
