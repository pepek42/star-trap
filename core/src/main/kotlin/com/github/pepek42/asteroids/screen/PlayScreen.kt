package com.github.pepek42.asteroids.screen

import com.badlogic.ashley.core.PooledEngine
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Cursor.SystemCursor
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.math.RandomXS128
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.viewport.Viewport
import com.github.pepek42.asteroids.Game
import com.github.pepek42.asteroids.GameState
import com.github.pepek42.asteroids.debug.DebugSettings
import com.github.pepek42.asteroids.debug.LoggingUtils.Companion.defaultLoggingUtils
import com.github.pepek42.asteroids.debug.SCREENSHOT_FRAME_DELTA
import com.github.pepek42.asteroids.audio.HullSoundQueue
import com.github.pepek42.asteroids.audio.SoundBank
import com.github.pepek42.asteroids.settings.SoundSettings
import com.github.pepek42.asteroids.system.HullSoundSystem
import com.github.pepek42.asteroids.ecs.clearLevel
import com.github.pepek42.asteroids.ecs.GameplayDeps
import com.github.pepek42.asteroids.ecs.buildGameplaySystems
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.input.ControlSettings
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.provider.AsteroidProvider
import com.github.pepek42.asteroids.provider.MapProvider
import com.github.pepek42.asteroids.provider.PlayerEntityProvider
import com.github.pepek42.asteroids.provider.WeaponProjectileProvider
import com.github.pepek42.asteroids.render.FieldShimmerRenderer
import com.github.pepek42.asteroids.render.PilotViewProjector
import com.github.pepek42.asteroids.render.ReferenceGridRenderer
import com.github.pepek42.asteroids.render.ShimmerImpacts
import com.github.pepek42.asteroids.system.CameraSystem
import com.github.pepek42.asteroids.system.LightHistorySystem
import com.github.pepek42.asteroids.system.DebugPanelSystem
import com.github.pepek42.asteroids.physics.GameClock
import com.github.pepek42.asteroids.system.DebugSystem
import com.github.pepek42.asteroids.system.DebugVectorSystem
import com.github.pepek42.asteroids.system.TacticalOverlaySystem
import com.github.pepek42.asteroids.settings.TacticalSettings
import com.github.pepek42.asteroids.system.HudSystem
import com.github.pepek42.asteroids.system.MinimapSystem
import com.github.pepek42.asteroids.physics.ObserverState
import com.github.pepek42.asteroids.system.RenderSystem
import com.github.pepek42.asteroids.ui.DebugPanel
import com.github.pepek42.asteroids.ui.Hud
import com.github.pepek42.asteroids.ui.MinimapViewport
import ktx.app.KtxScreen
import ktx.ashley.add
import ktx.assets.disposeSafely
import ktx.box2d.createWorld
import ktx.log.logger

class PlayScreen(
    private val game: Game,
) : KtxScreen {
    private val hud = Hud(game)
    private val settings = game.get<RelativitySettings>()
    private val observerState = ObserverState()
    private val clock = GameClock()
    private val mapProvider = game.get<MapProvider>()
    private val camera = game.get<OrthographicCamera>()
    private val viewport: Viewport = game.get<Viewport>()
    private val world = createWorld().apply {
        autoClearForces = false
    }
    private val engine = PooledEngine()
    private val playerEntityProvider = PlayerEntityProvider(engine, world, mapProvider, game.get<TextureAtlas>(), settings)
    private val asteroidProvider = AsteroidProvider(game.get<TextureAtlas>(), engine, world, game.get<RandomXS128>(), settings)
    private val gameState = game.get<GameState>()
    private val minimapViewport = MinimapViewport()
    private val debugSettings = game.get<DebugSettings>()
    private val debugPanel = DebugPanel(
        game.get<Skin>(), settings, debugSettings, game.get<ControlSettings>(),
    ) { visible -> Gdx.graphics.setSystemCursor(if (visible) SystemCursor.Arrow else SystemCursor.Crosshair) }

    private val hullSounds = HullSoundQueue()
    private lateinit var hullSoundSystem: HullSoundSystem
    private lateinit var lightHistory: LightHistorySystem

    init {
        setupEcs()
    }

    private fun setupEcs() {
        val gameEventManager = game.get<GameEventManager>()
        val spriteBatch = game.get<SpriteBatch>()
        val gridRenderer = ReferenceGridRenderer()
        val shimmerImpacts = ShimmerImpacts()
        val deps = GameplayDeps(
            engine = engine,
            world = world,
            settings = settings,
            mapProvider = mapProvider,
            clock = clock,
            observerState = observerState,
            gameEventManager = gameEventManager,
            camera = camera,
            controlSettings = game.get<ControlSettings>(),
            weaponProjectileProvider = WeaponProjectileProvider(game.get<TextureAtlas>(), engine, world, settings),
            asteroidProvider = asteroidProvider,
            random = game.get<RandomXS128>(),
            shimmerImpacts = shimmerImpacts,
            onLevelCleared = {
                logger.info { "Level ${gameState.level} finished" }
                game.finishLevel()
            },
            onPlayerDeath = { game.endRun() },
            hullSounds = hullSounds,
        )
        // Execution order lives in buildGameplaySystems (shared with the headless balance runner); the presentation
        // systems below are inserted between the observer and the lifetime system.
        buildGameplaySystems(deps) { systems ->
            lightHistory = systems.lightHistory
            val pilotViewProjector = PilotViewProjector(lightHistory, observerState, clock, settings)
            engine.add {
                addSystem(CameraSystem(camera, gameEventManager, mapProvider, observerState, settings))
                addSystem(
                    RenderSystem(
                        game, spriteBatch, viewport,
                        FieldShimmerRenderer(shimmerImpacts, mapProvider, settings),
                        gridRenderer, mapProvider, debugSettings, settings,
                        pilotViewProjector, observerState,
                    )
                )
                hullSoundSystem = HullSoundSystem(game.get<SoundBank>(), hullSounds, game.get<SoundSettings>(), settings)
                addSystem(hullSoundSystem)
                addSystem(DebugVectorSystem(camera, settings, debugSettings))
                addSystem(TacticalOverlaySystem(camera, viewport, mapProvider, settings, game.get<TacticalSettings>(), gameEventManager, game.get<ControlSettings>(), batch = spriteBatch, font = game.get<Skin>().getFont("font")))
                addSystem(DebugSystem(world, engine, camera, debugSettings))
                addSystem(DebugPanelSystem(debugPanel, observerState, clock))
                addSystem(HudSystem(hud, camera, viewport, debugSettings, gridRenderer, observerState, clock, settings, pilotViewProjector))
                addSystem(MinimapSystem(viewport, minimapViewport, camera, mapProvider, debugSettings, settings, panelWidthPx = { debugPanel.widthInScreenPx() }))
            }
        }
    }

    override fun show() {
        super.show()
        observerState.reset()
        clock.reset()
        lightHistory.clear()
        gameState.nextLevel()
        mapProvider.loadMap()
        playerEntityProvider.spawnPlayerEntity()
        asteroidProvider.spawnAsteroids(gameState.level, mapProvider)
        if (game.options.nearAsteroids > 0) {
            asteroidProvider.spawnNearAsteroids(game.options.nearAsteroids, mapProvider.playerSpawnLocation())
        }
        hud.toggleHud(show = true)
        hud.updateLevel(gameState.level)
        if (debugSettings.panelAllowed) {
            debugPanel.show(game.get<Stage>())
            game.get<GameEventManager>().addDebugListener(debugPanel)
        }
        val panelShown = debugSettings.panelAllowed && debugSettings.panelVisible
        Gdx.graphics.setSystemCursor(if (panelShown) SystemCursor.Arrow else SystemCursor.Crosshair)
        game.get<GameEventManager>().enablePlayerInputs()
    }

    override fun render(delta: Float) {
        defaultLoggingUtils.onGameLoopTick(delta)
        viewport.apply()
        ScreenUtils.clear(.1f, .1f, .1f, 1f)
        val baseDelta = if (game.options.screenshotPath != null) SCREENSHOT_FRAME_DELTA else delta
        engine.update(baseDelta * game.get<DebugSettings>().timeScale)
    }

    override fun resize(width: Int, height: Int) {
        logger.info { "Resize event $width x $height" }
        viewport.update(width, height)
        hud.resize(width, height)
        minimapViewport.resize(width, height, mapProvider.mapWidth, mapProvider.mapHeight,
            if (debugSettings.panelVisible) debugPanel.widthInScreenPx() else 0f)
    }

    override fun pause() {
        hullSoundSystem.paused = true
    }

    override fun resume() {
        hullSoundSystem.paused = false
    }

    override fun hide() {
        hullSoundSystem.silence()
        Gdx.graphics.setSystemCursor(SystemCursor.Arrow)
        hud.toggleHud(show = false)
        debugPanel.hide()
        game.get<GameEventManager>().removeDebugListener(debugPanel)
        clearLevel(engine, world)
        super.hide()
    }

    override fun dispose() {
        if (::hullSoundSystem.isInitialized) hullSoundSystem.silence()
        engine.systems.forEach { if (it is Disposable) it.disposeSafely() }
        world.disposeSafely()
        super.dispose()
    }

    companion object {
        private val logger = logger<PlayScreen>()
    }
}
