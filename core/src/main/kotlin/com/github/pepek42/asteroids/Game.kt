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
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.github.pepek42.asteroids.audio.SoundBank
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
import com.github.pepek42.asteroids.render.PlanetTextures
import com.github.pepek42.asteroids.screen.LevelClearedScreen
import com.github.pepek42.asteroids.screen.MainMenuScreen
import com.github.pepek42.asteroids.screen.PlayScreen
import com.github.pepek42.asteroids.settings.DisplaySettings
import com.github.pepek42.asteroids.settings.TacticalSettings
import com.github.pepek42.asteroids.settings.SoundSettings
import com.github.pepek42.asteroids.settings.GdxPreferencesStore
import com.github.pepek42.asteroids.settings.KeyValueStore
import com.github.pepek42.asteroids.settings.MemoryKeyValueStore
import com.github.pepek42.asteroids.settings.MenuSettings
import com.github.pepek42.asteroids.settings.loadSettings
import com.github.pepek42.asteroids.debug.DEBUG_MODE_DEFAULT
import com.github.pepek42.asteroids.debug.LoggingUtils.Companion.defaultLoggingUtils
import com.github.pepek42.asteroids.settings.menuSettings
import ktx.app.KtxGame
import ktx.app.KtxScreen
import ktx.assets.disposeSafely
import ktx.inject.Context
import ktx.inject.register
import ktx.log.logger
import ktx.scene2d.Scene2DSkin

class Game(val options: LaunchOptions = LaunchOptions()) : KtxGame<KtxScreen>() {
    val ctx = Context()
    private val screenshotHarness = ScreenshotHarness(options)
    override fun create() {
        logger.info { "Starting setup" }

        Box2D.init()
        applyDebugMode(DEBUG_MODE_DEFAULT)
        // Screenshot runs must not depend on a developer's saved prefs.
        val store: KeyValueStore =
            if (options.screenshotPath != null) MemoryKeyValueStore()
            else GdxPreferencesStore(Gdx.app.getPreferences(PREFERENCES_NAME))
        val display = DisplaySettings()
        val assetManager = AssetManager();
        assetManager.logger.level = Logger.ERROR
        assetManager.load("i18n/messages", I18NBundle::class.java)
        assetManager.load("ui/uiskin.atlas", TextureAtlas::class.java)
        assetManager.load("textures/textures.atlas", TextureAtlas::class.java)
        SoundBank.queueLoading(assetManager)
        assetManager.finishLoading()

        ctx.register {
            bindSingleton(RandomXS128(options.seed ?: System.nanoTime()))
            bindSingleton(GameState())
            bindSingleton(RelativitySettings())
            bindSingleton(ControlSettings(options.controls ?: ControlScheme.AUTO_AIM))
            bindSingleton(DebugSettings(panelVisible = options.debugPanel, forcePanel = options.debugPanel))
            bindSingleton(store)
            bindSingleton(display)
            bindSingleton(TacticalSettings())
            bindSingleton(SoundSettings())
            bindSingleton(SoundBank(assetManager))
            bindSingleton(
                menuSettings(
                    inject<RelativitySettings>(), inject<ControlSettings>(), inject<DebugSettings>(),
                    display, Gdx.app.type == Application.ApplicationType.Desktop, ::applyFullscreen,
                    inject<TacticalSettings>(), ::applyDebugMode, inject<SoundSettings>(),
                )
            )
            bindSingleton<OrthographicCamera>(OrthographicCamera())
            bindSingleton(GameEventManager())
            bindSingleton(SpriteBatch())
            bindSingleton(assetManager.get<TextureAtlas>("textures/textures.atlas"))
            bindSingleton(PlanetTextures(inject<RandomXS128>()))
            bindSingleton(Skin(Gdx.files.internal("ui/uiskin.json"), assetManager["ui/uiskin.atlas"]))
            Scene2DSkin.defaultSkin = ctx.inject<Skin>()
            bindSingleton(assetManager.get<I18NBundle>("i18n/messages"))
            bindSingleton(this@Game)
            bindSingleton<Viewport>(ExtendViewport(16f * 3, 9f * 3, inject<OrthographicCamera>()))
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
        if (options.pilotView) ctx.inject<RelativitySettings>().pilotView = true
        if (options.noShadows) ctx.inject<RelativitySettings>().shadowsEnabled = false
        if (options.noStar) ctx.inject<RelativitySettings>().starLightEnabled = false

        // Stage goes first so UI widgets get clicks; it returns true only when a widget handled the event.
        // GameEventManager comes second so clicks on empty space still reach the game.
        Gdx.input.inputProcessor = InputMultiplexer(ctx.inject<Stage>(), ctx.inject<GameEventManager>())

        addScreen(ctx.inject<MainMenuScreen>())
        addScreen(ctx.inject<PlayScreen>())
        addScreen(ctx.inject<LevelClearedScreen>())

        Gdx.graphics.setTitle(TITLE)
        logger.info { "Finished setup" }
        if (options.play || (options.screenshotPath != null && options.scene == ScreenshotScene.PLAY)) {
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

    /** Debug mode switches the libGDX log level and the throttled debug logging. */
    private fun applyDebugMode(on: Boolean) {
        Gdx.app.logLevel = if (on) Application.LOG_DEBUG else Application.LOG_ERROR
        defaultLoggingUtils.enabled = on
    }

    private fun applyFullscreen(on: Boolean) {
        if (on) Gdx.graphics.setFullscreenMode(Gdx.graphics.displayMode)
        else Gdx.graphics.setWindowedMode(BASE_WIDTH, BASE_HEIGHT)
    }

    fun finishLevel() {
        setScreen<LevelClearedScreen>()
    }

    /** Player died: back to the main menu with a message. Deferred so we never switch screens inside `engine.update`. */
    fun endRun() {
        ctx.inject<GameEventManager>().ignorePlayerInputs()
        Gdx.app.postRunnable {
            val gameState = ctx.inject<GameState>()
            gameState.resetGame()
            gameState.lastRunEnded = true
            setScreen<MainMenuScreen>()
        }
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

