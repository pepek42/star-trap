package com.github.pepek42.asteroids.screen

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.github.pepek42.asteroids.constant.BUTTON_WIDTH
import ktx.app.KtxScreen
import ktx.app.clearScreen
import ktx.assets.disposeSafely
import ktx.log.logger
import ktx.scene2d.actors
import ktx.scene2d.table
import ktx.scene2d.textButton

class MainMenu : KtxScreen {

  private val logger = logger<MainMenu>()
  private val stage: Stage = Stage()

  init {
    stage.actors {
      table {
        setFillParent(true)
        textButton("Single Player") {
          this.width = BUTTON_WIDTH
          it.fillX().uniformX()
          this.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
              logger.info { "go single player" }
            }
          })
        }
        row().pad(10f, 0f, 10f, 10f)
        if (Gdx.app.type != Application.ApplicationType.WebGL) {
          textButton("Exit") {
            this.width = BUTTON_WIDTH
            it.fillX().uniformX()
            this.addListener(object : ChangeListener() {
              override fun changed(event: ChangeEvent?, actor: Actor?) {
                Gdx.app.exit()
              }
            })
          }
          row().pad(10f, 0f, 10f, 10f)
        }
      }
    }
    Gdx.input.inputProcessor = stage
  }

  override fun render(delta: Float) {
    clearScreen(red = 0f, green = 0f, blue = 0f)
    stage.act(delta)
    stage.draw()
  }

  override fun resize(width: Int, height: Int) {
    stage.viewport.update(width, height)
  }

  override fun dispose() {
    stage.disposeSafely()
  }
}
