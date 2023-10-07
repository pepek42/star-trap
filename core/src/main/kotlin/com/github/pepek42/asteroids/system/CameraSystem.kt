package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.MathUtils
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.transformMapper
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.event.PlayerInputListener
import ktx.ashley.allOf
import ktx.ashley.get
import ktx.log.logger

private const val ZOOM_SPEED = 10

class CameraSystem(
    private val camera: OrthographicCamera,
    private val gameEventManager: GameEventManager,
) : IteratingSystem(allOf(PlayerComponent::class, TransformComponent::class).get()), PlayerInputListener {
    private var zoomAmount = 0f
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
        // TODO limit to map size
        val interpolatedPosition = entity[transformMapper]!!.interpolatedPosition
        camera.position.set(
            interpolatedPosition.x,
            interpolatedPosition.y,
            camera.position.z
        )
        camera.zoom = MathUtils.clamp(camera.zoom + zoomAmount * deltaTime * ZOOM_SPEED, 0.3f, 5f)
//        logger.debug { "Camera zoom: ${camera.zoom}, camera position: ${camera.position}" }
        camera.update()
        zoomAmount = 0f
    }

    override fun zoom(zoomAmount: Float) {
        this.zoomAmount = zoomAmount
    }

    companion object {
        private val logger = logger<CameraSystem>()
    }
}
