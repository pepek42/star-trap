package com.github.pepek42.asteroids.ui

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.I18NBundle
import com.github.pepek42.asteroids.Game
import ktx.actors.plusAssign
import ktx.scene2d.label
import ktx.scene2d.scene2d

class Hud(
    game: Game,
) {
    private val stage: Stage = game.get()
    private val i18NBundle: I18NBundle = game.get()
    private val coordinatesLabel: Label = scene2d.label("")
    private val speedLabel: Label = scene2d.label("")
    private val levelLabel: Label = scene2d.label("")
    private val group: Group = Group()

    init {
        group += levelLabel
        group += coordinatesLabel
        group += speedLabel

        stage += group
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
        coordinatesLabel.setText(i18NBundle.format("hud_coordinates", interpolatedPosition.x, interpolatedPosition.y))
    }

    fun updateVelocity(linearVelocity: Vector2?) {
        speedLabel.setText(i18NBundle.format("hud_velocity", linearVelocity?.x, linearVelocity?.y))
    }

    fun updateLevel(level: Int) {
        levelLabel.setText(i18NBundle.format("hud_level", level))
    }

    companion object {
        private const val MAX_DELTA_TIME = 1f / 30
    }
}
