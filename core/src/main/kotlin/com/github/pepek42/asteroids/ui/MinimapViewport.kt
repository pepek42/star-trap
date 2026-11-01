package com.github.pepek42.asteroids.ui

import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.viewport.Viewport

const val MINIMAP_HEIGHT_FRACTION = 0.22f
const val MINIMAP_MARGIN_PX = 12f

/**
 * Screen-space (pixels, origin bottom-left) rectangle of the square minimap panel,
 * anchored to the bottom-right corner of the window.
 */
fun minimapBounds(screenW: Int, screenH: Int): Rectangle {
    val size = screenH * MINIMAP_HEIGHT_FRACTION
    return Rectangle(screenW - MINIMAP_MARGIN_PX - size, MINIMAP_MARGIN_PX, size, size)
}

/**
 * Viewport showing exactly the whole map inside [minimapBounds].
 */
class MinimapViewport(
    camera: OrthographicCamera = OrthographicCamera(),
) : Viewport() {
    init {
        setCamera(camera)
    }

    private var lastScreenW = -1
    private var lastScreenH = -1
    private var lastMapW = -1f
    private var lastMapH = -1f

    /**
     * Takes logical window size ([com.badlogic.gdx.Graphics.getWidth]); [apply] handles HiDPI scaling.
     * Bounds are recomputed only when the inputs change.
     */
    fun resize(screenW: Int, screenH: Int, mapWidth: Float, mapHeight: Float) {
        if (screenW == lastScreenW && screenH == lastScreenH && mapWidth == lastMapW && mapHeight == lastMapH) return
        lastScreenW = screenW
        lastScreenH = screenH
        lastMapW = mapWidth
        lastMapH = mapHeight
        setWorldSize(mapWidth, mapHeight)
        val b = minimapBounds(screenW, screenH)
        setScreenBounds(b.x.toInt(), b.y.toInt(), b.width.toInt(), b.height.toInt())
        apply(true)
    }
}
