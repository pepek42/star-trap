package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.viewport.Viewport
import com.github.pepek42.asteroids.debug.DebugSettings
import com.github.pepek42.asteroids.provider.MapProvider
import com.github.pepek42.asteroids.render.ReferenceGridRenderer
import com.github.pepek42.asteroids.render.edgePointer
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.transformCmp
import com.github.pepek42.asteroids.ui.Hud
import ktx.ashley.allOf

class HudSystem(
    private val hud: Hud,
    private val camera: OrthographicCamera,
    private val viewport: Viewport,
    private val mapProvider: MapProvider,
    private val debugSettings: DebugSettings,
    private val pointerRenderer: ReferenceGridRenderer,
) : IteratingSystem(allOf(PlayerComponent::class, TransformComponent::class, BodyComponent::class).get()) {

    private val pointerPos = Vector2()
    private val screenPos = Vector3()
    private val playerPos = Vector2()
    private var hasPlayer = false

    override fun update(deltaTime: Float) {
        hasPlayer = false
        super.update(deltaTime)
        updateCentrePointer()
        hud.updateAndRender(deltaTime)
    }

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val body = entity.bodyCmp.body
        val transformCmp = entity.transformCmp
        hasPlayer = true
        playerPos.set(transformCmp.interpolatedPosition)
        hud.updatePosition(transformCmp.interpolatedPosition)
        hud.updateVelocity(body.linearVelocity)
    }

    private fun updateCentrePointer() {
        if (!debugSettings.centrePointerEnabled || !hasPlayer || !hud.isShown) {
            hud.hideCentrePointer()
            return
        }
        val halfW = camera.viewportWidth * camera.zoom / 2f
        val halfH = camera.viewportHeight * camera.zoom / 2f
        val cx = mapProvider.mapWidth / 2f
        val cy = mapProvider.mapHeight / 2f
        val margin = POINTER_MARGIN_FRACTION * halfH
        val angle = edgePointer(camera.position.x, camera.position.y, halfW, halfH, cx, cy, margin, pointerPos)
        if (angle.isNaN()) {
            hud.hideCentrePointer()
            return
        }
        pointerRenderer.renderArrow(camera, pointerPos, angle, POINTER_SIZE_FRACTION * halfH)
        // Project into letterboxed game-viewport pixels (y-down), which Hud maps into stage coordinates.
        screenPos.set(pointerPos.x, pointerPos.y, 0f)
        camera.project(screenPos, viewport.screenX.toFloat(), viewport.screenY.toFloat(),
            viewport.screenWidth.toFloat(), viewport.screenHeight.toFloat())
        // Viewport pixels are back-buffer pixels; input/stage coordinates are logical (HiDPI differs).
        val scale = Gdx.graphics.width.toFloat() / Gdx.graphics.backBufferWidth
        hud.showCentrePointer(screenPos.x * scale, Gdx.graphics.height - screenPos.y * scale, playerPos.dst(cx, cy))
    }

    companion object {
        private const val POINTER_MARGIN_FRACTION = 0.1f
        private const val POINTER_SIZE_FRACTION = 0.04f
    }
}
