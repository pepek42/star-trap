package com.github.pepek42.asteroids.ui

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.utils.viewport.FitViewport

class MinimapViewport(
    camera: Camera = OrthographicCamera()
) : FitViewport(16f, 9f, camera) {
    fun updateViewportSize(width: Int, height: Int) {
        super.update(width / MINIMAP_SIZE_SCALE, height / MINIMAP_SIZE_SCALE)
    }

    companion object {
        private const val MINIMAP_SIZE_SCALE = 1
    }
}
