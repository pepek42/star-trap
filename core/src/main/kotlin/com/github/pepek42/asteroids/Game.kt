package com.github.pepek42.asteroids

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.RandomXS128
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
import com.github.pepek42.asteroids.debug.DebugSettings
import com.github.pepek42.asteroids.debug.LaunchOptions
import com.github.pepek42.asteroids.debug.ScreenshotHarness
import com.github.pepek42.asteroids.debug.ScreenshotScene
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.input.ControlScheme
import com.github.pepek42.asteroids.input.ControlSettings
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.provider.BackgroundProvider
import com.github.pepek42.asteroids.provider.MapProvider
import com.github.pepek42.asteroids.screen.LevelClearedScreen
import com.github.pepek42.asteroids.screen.MainMenuScreen
import com.github.pepek42.asteroids.screen.PlayScreen
import com.github.pepek42.asteroids.settings.GdxPreferencesStore
import com.github.pepek42.asteroids.settings.KeyValueStore
import com.github.pepek42.asteroids.settings.MemoryKeyValueStore
import com.github.pepek42.asteroids.settings.MenuSettings
import com.github.pepek42.asteroids.settings.loadSettings
import com.github.pepek42.asteroids.settings.menuSettings
import ktx.app.KtxGame
import ktx.app.KtxScreen
import ktx.assets.disposeSafely
import ktx.inject.Context
import ktx.inject.register
import ktx.log.logger
import ktx.scene2d.Scene2DSkin

const val IS_DEBUG = true

class Game(val options: LaunchOptions = LaunchOptions()) : KtxGame<KtxScreen>() {
    val ctx = Context()
    private val screenshotHarness = ScreenshotHarness(options)
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
            bindSingleton(RandomXS128(options.seed ?: System.nanoTime()))
            bindSingleton(GameState())
            bindSingleton(RelativitySettings())
            bindSingleton(ControlSettings(options.controls ?: ControlScheme.MOUSE_HULL))
            bindSingleton(DebugSettings(panelAllowed = IS_DEBUG || options.debugPanel, panelVisible = options.debugPanel))
            // Screenshot runs must not depend on a developer's saved prefs.
            bindSingleton<KeyValueStore>(
                if (options.screenshotPath != null) MemoryKeyValueStore()
                else GdxPreferencesStore(Gdx.app.getPreferences(PREFERENCES_NAME))
            )
            bindSingleton(menuSettings(inject<RelativitySettings>(), inject<ControlSettings>(), inject<DebugSettings>()))
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
        loadSettings(ctx.inject<KeyValueStore>(), ctx.inject<MenuSettings>().specs)
        // The command line wins over saved prefs.
        options.controls?.let { ctx.inject<ControlSettings>().scheme = it }

        // Stage goes first so UI widgets get clicks; it returns true only when a widget handled the event.
        // GameEventManager comes second so clicks on empty space still reach the game.
        Gdx.input.inputProcessor = InputMultiplexer(ctx.inject<Stage>(), ctx.inject<GameEventManager>())

        addScreen(ctx.inject<MainMenuScreen>())
        addScreen(ctx.inject<PlayScreen>())
        addScreen(ctx.inject<LevelClearedScreen>())

        Gdx.graphics.setTitle(TITLE)
        logger.info { "Finished setup" }
        if (options.screenshotPath != null && options.scene == ScreenshotScene.PLAY) {
            setScreen<PlayScreen>()
            if (options.thrust) ctx.inject<GameEventManager>().thrusters(1f)
        } else {
            setScreen<MainMenuScreen>()
        }
    }

    override fun render() {
        super.render()
        screenshotHarness.afterRender()
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

