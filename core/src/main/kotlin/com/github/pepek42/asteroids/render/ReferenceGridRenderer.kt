package com.github.pepek42.asteroids.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Disposable
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

const val GRID_ENABLED = true
const val CENTRE_POINTER_ENABLED = true

/** Distance between grid lines in world units. */
const val GRID_STEP = 20f
const val GRID_ALPHA = 0.07f
const val GRID_AXIS_ALPHA = 0.18f
private const val GRID_EPS = 1e-3f

/** Multiples of [step] inside [lo, hi] (inclusive). Pure, for culling and tests. */
fun gridLinePositions(lo: Float, hi: Float, step: Float, out: MutableList<Float> = ArrayList()): MutableList<Float> {
    var k = ceil(lo / step).toInt()
    val last = floor(hi / step).toInt()
    while (k <= last) {
        out.add(k * step)
        k++
    }
    return out
}

/**
 * Faint reference grid in arena space, with brighter axes through the arena centre. One ShapeRenderer batch, culled to
 * the camera and the arena. Call outside a SpriteBatch begin/end.
 */
class ReferenceGridRenderer : Disposable {
    private val shapes = ShapeRenderer()
    private val positions = ArrayList<Float>()

    fun render(camera: Camera, zoom: Float, mapWidth: Float, mapHeight: Float) {
        val halfW = camera.viewportWidth * zoom / 2f
        val halfH = camera.viewportHeight * zoom / 2f
        val x0 = maxOf(camera.position.x - halfW, 0f)
        val x1 = minOf(camera.position.x + halfW, mapWidth)
        val y0 = maxOf(camera.position.y - halfH, 0f)
        val y1 = minOf(camera.position.y + halfH, mapHeight)
        if (x0 > x1 || y0 > y1) return
        val centreX = mapWidth / 2f
        val centreY = mapHeight / 2f
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        shapes.projectionMatrix = camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Line)
        positions.clear()
        gridLinePositions(x0, x1, GRID_STEP, positions)
        for (x in positions) {
            val axis = abs(x - centreX) < GRID_EPS
            shapes.setColor(1f, 1f, 1f, if (axis) GRID_AXIS_ALPHA else GRID_ALPHA)
            shapes.line(x, y0, x, y1)
        }
        positions.clear()
        gridLinePositions(y0, y1, GRID_STEP, positions)
        for (y in positions) {
            val axis = abs(y - centreY) < GRID_EPS
            shapes.setColor(1f, 1f, 1f, if (axis) GRID_AXIS_ALPHA else GRID_ALPHA)
            shapes.line(x0, y, x1, y)
        }
        // Axes not on a GRID_STEP multiple (odd map sizes) still get drawn.
        if (centreX % GRID_STEP != 0f && centreX in x0..x1) {
            shapes.setColor(1f, 1f, 1f, GRID_AXIS_ALPHA); shapes.line(centreX, y0, centreX, y1)
        }
        if (centreY % GRID_STEP != 0f && centreY in y0..y1) {
            shapes.setColor(1f, 1f, 1f, GRID_AXIS_ALPHA); shapes.line(x0, centreY, x1, centreY)
        }
        shapes.end()
    }

    /** Small arrow at [at] pointing along [angleRad]; size in world units. */
    fun renderArrow(camera: Camera, at: Vector2, angleRad: Float, size: Float) {
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        shapes.projectionMatrix = camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.setColor(1f, 1f, 1f, POINTER_ALPHA)
        val c = kotlin.math.cos(angleRad)
        val s = kotlin.math.sin(angleRad)
        // Tip forward, two back corners.
        shapes.triangle(
            at.x + c * size, at.y + s * size,
            at.x - c * size * 0.6f - s * size * 0.6f, at.y - s * size * 0.6f + c * size * 0.6f,
            at.x - c * size * 0.6f + s * size * 0.6f, at.y - s * size * 0.6f - c * size * 0.6f,
        )
        shapes.end()
    }

    override fun dispose() = shapes.dispose()

    companion object {
        private const val POINTER_ALPHA = 0.7f
    }
}
