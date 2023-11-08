package com.github.pepek42.asteroids.screen

import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.I18NBundle
import com.github.pepek42.asteroids.Game
import com.github.pepek42.asteroids.GameState
import com.github.pepek42.asteroids.event.GameEventManager
import ktx.actors.onClick
import ktx.actors.plusAssign
import ktx.app.KtxScreen
import ktx.app.clearScreen
import ktx.log.logger
import ktx.scene2d.label
import ktx.scene2d.scene2d
import ktx.scene2d.textButton

class LevelClearedScreen(
    game: Game,
) : KtxScreen {
    private val i18NBundle = game.get<I18NBundle>()
    private val gameState: GameState = game.get<GameState>()
    private val table: Table = Table()
    private val centerLabel: Label = scene2d.label("").apply { isVisible = false }
    private val stage = game.get<Stage>()
    private val nextLevelButton = scene2d.textButton(i18NBundle["next_level_button"]) {
        onClick { game.setScreen<PlayScreen>() }
    }
    private val gameEventManager = game.get<GameEventManager>()

    init {
        table.setFillParent(true)
        table.defaults().pad(3f).expand().fill()
        table.add()
        table.add()
        table.add()
        table.row()
        table.add()
        table.add(centerLabel).align(Align.center)
        table.add()
        table.row()
        table.add()
        table.add()
        table.add(nextLevelButton)
    }

    override fun show() {
        centerLabel.isVisible = false
        centerLabel.setText(i18NBundle.format("level_cleared_message", gameState.level))
        gameEventManager.ignorePlayerInputs()
        stage += table
    }

    override fun resize(width: Int, height: Int) {
        logger.info { "Resizing screen to ${width}x$height" }
        stage.viewport.update(width, height, true)
    }

    override fun render(delta: Float) {
        clearScreen(red = 0f, green = 0f, blue = 0f)
        stage.act()
        stage.draw()
    }

    override fun hide() {
        table.remove()
    }

    companion object {
        private val logger = logger<LevelClearedScreen>()
    }
}
