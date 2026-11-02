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
import com.github.pepek42.asteroids.IS_DEBUG
import com.github.pepek42.asteroids.debug.DebugSettings
import com.github.pepek42.asteroids.debug.LoggingUtils.Companion.defaultLoggingUtils
import com.github.pepek42.asteroids.debug.SCREENSHOT_FRAME_DELTA
import com.github.pepek42.asteroids.ecs.clearLevel
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.input.ControlSettings
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.provider.AsteroidProvider
import com.github.pepek42.asteroids.provider.MapProvider
import com.github.pepek42.asteroids.provider.PlayerEntityProvider
import com.github.pepek42.asteroids.provider.WeaponProjectileProvider
import com.github.pepek42.asteroids.render.FieldShimmerRenderer
import com.github.pepek42.asteroids.render.ReferenceGridRenderer
import com.github.pepek42.asteroids.render.ShimmerImpacts
import com.github.pepek42.asteroids.system.BoundaryFieldSystem
import com.github.pepek42.asteroids.system.CameraSystem
import com.github.pepek42.asteroids.system.ContactSystem
import com.github.pepek42.asteroids.system.DeathSystem
import com.github.pepek42.asteroids.system.DebugPanelSystem
import com.github.pepek42.asteroids.system.DebugSystem
import com.github.pepek42.asteroids.system.EndLevelSystem
import com.github.pepek42.asteroids.system.HudSystem
import com.github.pepek42.asteroids.system.LifetimeSystem
import com.github.pepek42.asteroids.system.MinimapSystem
import com.github.pepek42.asteroids.system.MoveSystem
import com.github.pepek42.asteroids.physics.ObserverState
import com.github.pepek42.asteroids.system.ObserverSystem
import com.github.pepek42.asteroids.system.PhysicsSystem
import com.github.pepek42.asteroids.system.PlayerInputSystem
import com.github.pepek42.asteroids.system.ProjectileSweepSystem
import com.github.pepek42.asteroids.system.RemoveSystem
import com.github.pepek42.asteroids.system.RenderSystem
import com.github.pepek42.asteroids.system.RelativisticDynamics
import com.github.pepek42.asteroids.system.WeaponSystem
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

    init {
        setupEcs()
    }

    private fun setupEcs() {
        val gameEventManager = game.get<GameEventManager>()
        val spriteBatch = game.get<SpriteBatch>()
        val moveSystem = MoveSystem(settings)
        val gridRenderer = ReferenceGridRenderer()
        // Shared by the boundary field (emits) and the shimmer renderer (draws).
        val shimmerImpacts = ShimmerImpacts()
        val boundaryFieldSystem = BoundaryFieldSystem(mapProvider, shimmerImpacts, settings)
        val projectileSweepSystem = ProjectileSweepSystem(world)
        // Must be the last step listener: it integrates the accelerations the others added.
        val relativisticDynamics = RelativisticDynamics(settings)
        engine.add {
            addSystem(EndLevelSystem(game))
            addSystem(PlayerInputSystem(gameEventManager, camera, game.get<ControlSettings>()))
            addSystem(moveSystem)
            addSystem(WeaponSystem(WeaponProjectileProvider(game.get<TextureAtlas>(), engine, world, settings)))
            addSystem(ContactSystem(world))
            addSystem(boundaryFieldSystem)
            addSystem(projectileSweepSystem)
            addSystem(relativisticDynamics)
            addSystem(
                PhysicsSystem(
                    world, engine,
                    listOf(moveSystem, boundaryFieldSystem, projectileSweepSystem, relativisticDynamics),
                )
            )
            addSystem(ObserverSystem(observerState, settings))
            addSystem(CameraSystem(camera, gameEventManager, mapProvider))
            addSystem(
                RenderSystem(
                    game, spriteBatch, viewport,
                    FieldShimmerRenderer(shimmerImpacts, mapProvider, settings),
                    gridRenderer, mapProvider, debugSettings,
                )
            )
            if (IS_DEBUG) {
                engine.addSystem(DebugSystem(world, engine, camera))
            }
            addSystem(DebugPanelSystem(debugPanel, observerState))
            addSystem(HudSystem(hud, camera, viewport, mapProvider, debugSettings, gridRenderer))
            addSystem(MinimapSystem(viewport, minimapViewport, camera, mapProvider))
            addSystem(LifetimeSystem())
            addSystem(DeathSystem(engine, asteroidProvider, game.get<RandomXS128>()))
            addSystem(RemoveSystem(world, mapProvider))
        }
    }

    override fun show() {
        super.show()
        observerState.reset()
        gameState.nextLevel()
        mapProvider.loadMap()
        playerEntityProvider.spawnPlayerEntity()
        asteroidProvider.spawnAsteroids(gameState.level, mapProvider)
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
        minimapViewport.resize(width, height, mapProvider.mapWidth, mapProvider.mapHeight)
    }

    override fun hide() {
        Gdx.graphics.setSystemCursor(SystemCursor.Arrow)
        hud.toggleHud(show = false)
        debugPanel.hide()
        game.get<GameEventManager>().removeDebugListener(debugPanel)
        clearLevel(engine, world)
        super.hide()
    }

    override fun dispose() {
        engine.systems.forEach { if (it is Disposable) it.disposeSafely() }
        world.disposeSafely()
        super.dispose()
    }

    companion object {
        private val logger = logger<PlayScreen>()
    }
}
