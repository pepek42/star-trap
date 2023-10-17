package com.github.pepek42.asteroids.ui

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.I18NBundle
import com.github.pepek42.asteroids.Game
import ktx.scene2d.label
import ktx.scene2d.scene2d
import kotlin.math.roundToInt

class Hud(
    game: Game,
) {
    private val stage: Stage = game.get()
    private val i18NBundle: I18NBundle = game.get()
    private val coordinatesLabel: Label = scene2d.label("")
    private val speedLabel: Label = scene2d.label("")
    private val levelLabel: Label = scene2d.label("")
    private val centerLabel: Label = scene2d.label("").apply { isVisible = false }
    private val table: Table = Table()

    init {
        table.setFillParent(true)
        table.defaults().pad(3f).expand().fill()
        val verticalGroup = VerticalGroup().apply {
            addActor(levelLabel)
            addActor(coordinatesLabel)
            addActor(speedLabel)
            columnAlign(Align.topLeft)
            top().left()
            pad(5f, 5f, 0f, 0f)
        }
        table.add(verticalGroup).top().left()
        table.add()
        table.add()
        table.row()
        table.add()
        table.add(centerLabel).align(Align.center)
        table.add()
        table.row()
        table.add()
        table.add()
        table.add()
    }

    fun toggleHud(show: Boolean) {
        if (show) {
            stage.addActor(table)
        } else {
            table.remove()
        }
    }

    // TODO Minimap
    fun updateAndRender(deltaTime: Float) {
        stage.run {
            viewport.apply()
            act(deltaTime.coerceAtMost(MAX_DELTA_TIME))
            draw()
        }
    }

    fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
    }

    fun updatePosition(interpolatedPosition: Vector2) {
        coordinatesLabel.setText(
            i18NBundle.format(
                "hud_coordinates",
                interpolatedPosition.x.roundToInt(),
                interpolatedPosition.y.roundToInt()
            )
        )
    }

    fun updateVelocity(linearVelocity: Vector2) {
        speedLabel.setText(
            i18NBundle.format(
                "hud_velocity",
                linearVelocity.x.roundToInt(),
                linearVelocity.y.roundToInt()
            )
        )
    }

    fun updateLevel(level: Int) {
        levelLabel.setText(i18NBundle.format("hud_level", level))
    }

    companion object {
        private const val MAX_DELTA_TIME = 1f / 30
    }
}
