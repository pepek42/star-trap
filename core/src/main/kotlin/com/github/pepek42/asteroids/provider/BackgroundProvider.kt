package com.github.pepek42.asteroids.provider

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion
import com.badlogic.gdx.utils.Disposable
import com.github.pepek42.asteroids.UNIT_SCALE
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.event.MapEventListener
import kotlin.math.ceil

class BackgroundProvider(
    private val batch: Batch,
    private val gameEventManager: GameEventManager,
    textureAtlas: TextureAtlas,
) : MapEventListener, Disposable {
    private val bg1: AtlasRegion
    private val bg2: AtlasRegion
    private val bg3: AtlasRegion
    private var textureUnitsWidth: Float
    private var textureUnitsHeight: Float
    private var xRepeats: Int = 0
    private var yRepeats: Int = 0
    private var t2Scroll: Float = 0f
    private var t3Scroll: Float = 0f

    init {
        gameEventManager.addMapEventListener(this)
        bg1 = textureAtlas.findRegion("background", 1)
        bg2 = textureAtlas.findRegion("background", 2)
        bg3 = textureAtlas.findRegion("background", 3)
        textureUnitsWidth = bg1.regionWidth.toFloat() / UNIT_SCALE
        textureUnitsHeight = bg1.regionHeight.toFloat() / UNIT_SCALE
    }

    override fun onNewMap(mapWidth: Float, mapHeight: Float) {
        xRepeats = ceil(mapWidth / textureUnitsWidth).toInt() + 1
        yRepeats = ceil(mapHeight / textureUnitsHeight).toInt()
    }

    override fun dispose() {
        gameEventManager.removeMapEventListener(this)
    }

    fun renderBg(deltaTime: Float) {
        t2Scroll += deltaTime * T2_SCROLL_SPEED
        t3Scroll += deltaTime * T3_SCROLL_SPEED
        t2Scroll %= textureUnitsWidth
        t3Scroll %= textureUnitsWidth
        for (i in -1..xRepeats) {
            for (j in 0..yRepeats) {
                val x = i * textureUnitsWidth
                val y = j * textureUnitsHeight
                batch.draw(
                    bg1, x, y, textureUnitsWidth, textureUnitsHeight
                )
                batch.draw(
                    bg2, x + t2Scroll, y, textureUnitsWidth, textureUnitsHeight
                )
                batch.draw(
                    bg3, x + t3Scroll, y, textureUnitsWidth, textureUnitsHeight
                )
            }
        }
    }

    companion object {
        private const val T2_SCROLL_SPEED = 0.3f
        private const val T3_SCROLL_SPEED = 0.7f
    }
}
