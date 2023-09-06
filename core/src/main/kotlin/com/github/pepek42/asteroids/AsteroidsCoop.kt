package com.github.pepek42.asteroids

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.github.pepek42.asteroids.screen.MainMenu
import ktx.app.KtxGame
import ktx.app.KtxScreen
import ktx.scene2d.Scene2DSkin

class AsteroidsCoop : KtxGame<KtxScreen>() {
  override fun create() {
    Scene2DSkin.defaultSkin = Skin(Gdx.files.internal("ui/uiskin.json"), TextureAtlas("ui/uiskin.atlas"))
    addScreen(MainMenu())
    setScreen<MainMenu>()
  }
}

