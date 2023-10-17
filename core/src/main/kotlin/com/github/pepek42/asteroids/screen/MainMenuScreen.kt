package com.github.pepek42.asteroids.screen

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.I18NBundle
import com.github.pepek42.asteroids.Game
import com.github.pepek42.asteroids.event.GameEventManager
import ktx.actors.onClick
import ktx.app.KtxScreen
import ktx.app.clearScreen
import ktx.log.logger
import ktx.scene2d.scene2d
import ktx.scene2d.textButton
import ktx.scene2d.verticalGroup

class MainMenuScreen(private val game: Game) : KtxScreen {
    private val i18n = game.get<I18NBundle>()
    private val stage = game.get<Stage>()
    private val menu: Actor = scene2d.verticalGroup {
        setFillParent(true)
        align(Align.center)
        space(16f)
        touchable = Touchable.enabled
        textButton(i18n["menu_single_player"]) {
            onClick {
                game.setScreen<PlayScreen>()
            }
        }
        if (Gdx.app.type != Application.ApplicationType.WebGL) {
            textButton(i18n["menu_exit"]) {
                onClick {
                    logger.info { "Exiting application" }
                    Gdx.app.exit()
                }
            }
        }


    }

    override fun show() {
        game.get<GameEventManager>().ignorePlayerInputs()
        stage.addActor(menu)
    }

    override fun render(delta: Float) {
        clearScreen(red = 0f, green = 0f, blue = 0f)
        stage.act()
        stage.draw()
    }

    override fun resize(width: Int, height: Int) {
        logger.info { "Resizing screen to ${width}x$height" }
        stage.viewport.update(width, height, true)
    }

    override fun hide() {
        menu.remove()
    }

    companion object {
        private val logger = logger<MainMenuScreen>()
    }
}
