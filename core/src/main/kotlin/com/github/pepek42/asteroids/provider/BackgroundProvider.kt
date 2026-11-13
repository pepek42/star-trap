package com.github.pepek42.asteroids.provider

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion
import com.badlogic.gdx.utils.Disposable
import com.github.pepek42.asteroids.UNIT_SCALE
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.event.MapEventListener
import com.github.pepek42.asteroids.physics.ObserverState
import com.github.pepek42.asteroids.render.PilotViewProjector
import com.github.pepek42.asteroids.render.Starfield
import kotlin.math.ceil

class BackgroundProvider(
    private val batch: Batch,
    private val gameEventManager: GameEventManager,
    textureAtlas: TextureAtlas,
) : MapEventListener, Disposable {
    private val bg1: AtlasRegion = textureAtlas.findRegion("background", 1)
    private val textureUnitsWidth: Float = bg1.regionWidth.toFloat() / UNIT_SCALE
    private val textureUnitsHeight: Float = bg1.regionHeight.toFloat() / UNIT_SCALE
    private var xRepeats: Int = 0
    private var yRepeats: Int = 0
    private var starfield: Starfield? = null

    init {
        gameEventManager.addMapEventListener(this)
    }

    override fun onNewMap(mapWidth: Float, mapHeight: Float) {
        xRepeats = ceil(mapWidth / textureUnitsWidth).toInt() + 1
        yRepeats = ceil(mapHeight / textureUnitsHeight).toInt()
        // Regenerate the (seeded, hence identical) star set per map.
        starfield?.dispose()
        starfield = Starfield()
    }

    override fun dispose() {
        gameEventManager.removeMapEventListener(this)
        starfield?.dispose()
        starfield = null
    }

    /** [pilotView] non-null = the pilot view's aberration is on: the stars are drawn at their aberrated directions (ST-077). */
    fun renderBg(camera: Camera, zoom: Float, logicalWidthPx: Int, observer: ObserverState, pilotView: PilotViewProjector? = null) {
        val oldColor = batch.packedColor
        batch.setColor(1f, 1f, 1f, NEBULA_ALPHA)
        for (i in -1..xRepeats) {
            for (j in 0..yRepeats) {
                batch.draw(bg1, i * textureUnitsWidth, j * textureUnitsHeight, textureUnitsWidth, textureUnitsHeight)
            }
        }
        batch.packedColor = oldColor
        if (pilotView != null) {
            starfield?.render(batch, camera, zoom, logicalWidthPx, observer, true, pilotView.ox, pilotView.oy, pilotView.betaX, pilotView.betaY)
        } else {
            starfield?.render(batch, camera, zoom, logicalWidthPx, observer)
        }
    }

    companion object {
        const val NEBULA_ALPHA = 0.35f
    }
}
