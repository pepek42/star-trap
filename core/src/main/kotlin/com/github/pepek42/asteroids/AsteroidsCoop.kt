package com.github.pepek42.asteroids

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.utils.I18NBundle
import com.badlogic.gdx.utils.Logger
import com.github.pepek42.asteroids.screen.MainMenuScreen
import com.github.pepek42.asteroids.screen.PlayScreen
import ktx.app.KtxGame
import ktx.app.KtxScreen
import ktx.assets.disposeSafely
import ktx.inject.Context
import ktx.inject.register
import ktx.log.logger
import ktx.scene2d.Scene2DSkin

class AsteroidsCoop : KtxGame<KtxScreen>() {
  private val logger = logger<AsteroidsCoop>()
  val context = Context()
  override fun create() {
    val assetManager = AssetManager();
    assetManager.logger.level = Logger.ERROR
    assetManager.load("i18n/messages", I18NBundle::class.java)

    assetManager.finishLoading()

    context.register {
      bindSingleton(TextureAtlas("ui/uiskin.atlas"))
      bindSingleton(Skin(Gdx.files.internal("ui/uiskin.json"), inject<TextureAtlas>()))
      Scene2DSkin.defaultSkin = context.inject<Skin>()
      bindSingleton(assetManager.get<I18NBundle>("i18n/messages"))
      bindSingleton(this@AsteroidsCoop)
      bindSingleton(MainMenuScreen(inject<AsteroidsCoop>()))
      bindSingleton(PlayScreen(inject<AsteroidsCoop>()))
      bindSingleton(assetManager)
    }

    addScreen(context.inject<MainMenuScreen>())
    addScreen(context.inject<PlayScreen>())

    logger.info { "Finished setup" }
    setScreen<MainMenuScreen>()
  }

  override fun dispose() {
    context.remove<AsteroidsCoop>()
    context.disposeSafely()
    super.dispose()
  }
}

