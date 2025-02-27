package com.github.pepek42.asteroids

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.utils.I18NBundle
import com.badlogic.gdx.utils.Logger
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.provider.BackgroundProvider
import com.github.pepek42.asteroids.provider.MapProvider
import com.github.pepek42.asteroids.screen.LevelClearedScreen
import com.github.pepek42.asteroids.screen.MainMenuScreen
import com.github.pepek42.asteroids.screen.PlayScreen
import ktx.app.KtxGame
import ktx.app.KtxScreen
import ktx.assets.disposeSafely
import ktx.inject.Context
import ktx.inject.register
import ktx.log.logger
import ktx.scene2d.Scene2DSkin

const val IS_DEBUG = true

class Game : KtxGame<KtxScreen>() {
    val ctx = Context()
    override fun create() {
        logger.info { "Starting setup" }

        Box2D.init()
        if (IS_DEBUG) {
            Gdx.app.logLevel = Application.LOG_DEBUG
        } else {
            Gdx.app.logLevel = Application.LOG_ERROR
        }
        val assetManager = AssetManager();
        assetManager.logger.level = Logger.ERROR
        assetManager.load("i18n/messages", I18NBundle::class.java)
        assetManager.load("ui/uiskin.atlas", TextureAtlas::class.java)
        assetManager.load("textures/textures.atlas", TextureAtlas::class.java)
        assetManager.finishLoading()

        ctx.register {
            bindSingleton(GameState())
            bindSingleton<OrthographicCamera>(OrthographicCamera())
            bindSingleton(GameEventManager())
            bindSingleton(SpriteBatch())
            bindSingleton(assetManager.get<TextureAtlas>("textures/textures.atlas"))
            bindSingleton(Skin(Gdx.files.internal("ui/uiskin.json"), assetManager["ui/uiskin.atlas"]))
            Scene2DSkin.defaultSkin = ctx.inject<Skin>()
            bindSingleton(assetManager.get<I18NBundle>("i18n/messages"))
            bindSingleton(this@Game)
            bindSingleton<Viewport>(FitViewport(16f * 3, 9f * 3, inject<OrthographicCamera>()))
            bindSingleton(Stage(ScreenViewport()))
            bindSingleton(MainMenuScreen(inject<Game>()))
            bindSingleton(assetManager)
            bindSingleton(MapProvider(inject<GameEventManager>()))
            bindSingleton(BackgroundProvider(inject<SpriteBatch>(), inject<GameEventManager>(), inject<TextureAtlas>()))
            bindSingleton(PlayScreen(inject<Game>()))
            bindSingleton(LevelClearedScreen(inject<Game>()))
        }
        Gdx.input.inputProcessor = InputMultiplexer(ctx.inject<GameEventManager>(), ctx.inject<Stage>())

        addScreen(ctx.inject<MainMenuScreen>())
        addScreen(ctx.inject<PlayScreen>())
        addScreen(ctx.inject<LevelClearedScreen>())

        Gdx.graphics.setTitle(TITLE)
        logger.info { "Finished setup" }
        setScreen<MainMenuScreen>()
    }

    fun finishLevel() {
        setScreen<LevelClearedScreen>()
    }

    inline fun <reified Type : Any> get(): Type = ctx.inject<Type>()

    override fun dispose() {
        logger.info { "Dispose" }
        removeScreen<LevelClearedScreen>()
        removeScreen<PlayScreen>()
        removeScreen<MainMenuScreen>()
        ctx.remove<Game>()
        ctx.disposeSafely()
        super.dispose()
    }

    companion object {
        private val logger = logger<Game>()
    }
}

