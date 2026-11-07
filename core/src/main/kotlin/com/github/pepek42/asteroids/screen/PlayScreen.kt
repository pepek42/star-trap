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
import com.badlogic.gdx.math.Vector2
import com.github.pepek42.asteroids.debug.LoggingUtils.Companion.defaultLoggingUtils
import com.github.pepek42.asteroids.debug.SCREENSHOT_FRAME_DELTA
import com.github.pepek42.asteroids.audio.ComputerSoundQueue
import com.github.pepek42.asteroids.audio.HullSoundQueue
import com.github.pepek42.asteroids.audio.SoundBank
import com.github.pepek42.asteroids.settings.SoundSettings
import com.github.pepek42.asteroids.system.ComputerSoundSystem
import com.github.pepek42.asteroids.system.HullSoundSystem
import com.github.pepek42.asteroids.ecs.clearLevel
import com.github.pepek42.asteroids.ecs.GameplayDeps
import com.github.pepek42.asteroids.ecs.buildGameplaySystems
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.input.ControlSettings
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.provider.AsteroidProvider
import com.github.pepek42.asteroids.provider.BlackHoleProvider
import com.github.pepek42.asteroids.provider.MapProvider
import com.github.pepek42.asteroids.provider.DecorationProvider
import com.github.pepek42.asteroids.provider.PlanetProvider
import com.github.pepek42.asteroids.provider.PlayerEntityProvider
import com.github.pepek42.asteroids.provider.WeaponProjectileProvider
import com.github.pepek42.asteroids.render.FieldShimmerRenderer
import com.github.pepek42.asteroids.render.LensingPass
import com.github.pepek42.asteroids.render.LitSpriteRenderer
import com.github.pepek42.asteroids.render.SCREEN_CLEAR_GRAY
import com.github.pepek42.asteroids.debug.HOLE_NEAR_DISTANCE
import com.github.pepek42.asteroids.environment.AsteroidSize
import com.badlogic.gdx.math.MathUtils
import ktx.math.vec2
import com.github.pepek42.asteroids.render.PilotViewProjector
import com.github.pepek42.asteroids.render.PlanetTextures
import com.github.pepek42.asteroids.render.ReferenceGridRenderer
import com.github.pepek42.asteroids.render.ShimmerImpacts
import com.github.pepek42.asteroids.render.StarLight
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
    private val blackHoleProvider = BlackHoleProvider(engine, world, mapProvider)
    private val decorationProvider = DecorationProvider(engine, world, mapProvider) { game.get<TextureAtlas>().createSprite("debris") }
    private val planetProvider = PlanetProvider(engine, world, mapProvider, settings, game.get<PlanetTextures>()::createSprite)
    private val gameState = game.get<GameState>()
    private val minimapViewport = MinimapViewport()
    private val star = StarLight()
    private val debugSettings = game.get<DebugSettings>()
    private val debugPanel = DebugPanel(
        game.get<Skin>(), settings, debugSettings, game.get<ControlSettings>(),
    ) { visible -> Gdx.graphics.setSystemCursor(if (visible) SystemCursor.Arrow else SystemCursor.Crosshair) }

    private val hullSounds = HullSoundQueue()
    private lateinit var hullSoundSystem: HullSoundSystem
    private val computerSounds = ComputerSoundQueue(game.get<SoundSettings>())
    private lateinit var computerSoundSystem: ComputerSoundSystem
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
            computerSounds = computerSounds,
            invulnerablePlayer = game.options.invulnerable,
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
                        litSprites = LitSpriteRenderer(game.get<TextureAtlas>()),
                        lensing = LensingPass(),
                        star = star,
                    )
                )
                hullSoundSystem = HullSoundSystem(game.get<SoundBank>(), hullSounds, game.get<SoundSettings>(), settings)
                addSystem(hullSoundSystem)
                computerSoundSystem = ComputerSoundSystem(
                    game.get<SoundBank>(), computerSounds, game.get<SoundSettings>(), settings, observerState, clock,
                )
                addSystem(computerSoundSystem)
                addSystem(DebugVectorSystem(camera, settings, debugSettings))
                addSystem(TacticalOverlaySystem(camera, viewport, mapProvider, settings, game.get<TacticalSettings>(), gameEventManager, game.get<ControlSettings>(), batch = spriteBatch, font = game.get<Skin>().getFont("font"), pilotView = pilotViewProjector))
                addSystem(DebugSystem(world, engine, camera, debugSettings))
                addSystem(DebugPanelSystem(debugPanel, observerState, clock))
                addSystem(HudSystem(hud, camera, viewport, debugSettings, gridRenderer, observerState, clock, settings, pilotViewProjector))
                addSystem(MinimapSystem(viewport, minimapViewport, camera, mapProvider, debugSettings, settings, panelWidthPx = { debugPanel.widthInScreenPx() }, star = star))
            }
        }
    }

    /** `--hole-near`: where the first black hole goes (`--hole-near=<u>` right of the player spawn), else null. */
    private fun holeNearSpawn(): Vector2? =
        (game.options.holeNear ?: if (game.options.shadowDemo) HOLE_NEAR_DISTANCE else null)
            ?.let { mapProvider.playerSpawnLocation().add(it, 0f) }

    /**
     * `--shadow-demo` (ST-098): with the hole at [HOLE_NEAR_DISTANCE] right of the player, a planet [SHADOW_DEMO_PLANET_DX] u from
     * the player toward the hole shadows the player and an asteroid [SHADOW_DEMO_ROCK_DX] u from the player toward the planet; a control asteroid
     * sits at the same distance from the hole, [SHADOW_DEMO_CONTROL_ANGLE_DEG] off the axis, outside the shadow. All at rest.
     */
    private fun spawnShadowDemo() {
        val spawn = mapProvider.playerSpawnLocation()
        planetProvider.spawnPlanet(spawn.x + SHADOW_DEMO_PLANET_DX, spawn.y, 0)
        val rockX = spawn.x + SHADOW_DEMO_ROCK_DX
        asteroidProvider.spawnAsteroid(AsteroidSize.LARGE, vec2(rockX, spawn.y), vec2(), 0f)
        val holeX = spawn.x + HOLE_NEAR_DISTANCE
        val dist = holeX - rockX
        val a = SHADOW_DEMO_CONTROL_ANGLE_DEG * MathUtils.degreesToRadians
        asteroidProvider.spawnAsteroid(
            AsteroidSize.LARGE, vec2(holeX - dist * MathUtils.cos(a), spawn.y + dist * MathUtils.sin(a)), vec2(), 0f,
        )
    }

    override fun show() {
        super.show()
        observerState.reset()
        clock.reset()
        lightHistory.clear()
        computerSounds.clear()
        gameState.nextLevel()
        mapProvider.loadMap()
        playerEntityProvider.spawnPlayerEntity()
        val holes = if (settings.blackHolesEnabled) blackHoleProvider.spawn(gameState.level, game.get<RandomXS128>(), holeNearSpawn()) else FloatArray(0)
        val planets = if (settings.planetsEnabled) {
            planetProvider.spawn(gameState.level, game.get<RandomXS128>(), holes, holes.size / 2)
        } else FloatArray(0)
        asteroidProvider.spawnAsteroids(gameState.level, mapProvider)
        if (game.options.shadowDemo) spawnShadowDemo()
        if (game.options.nearAsteroids > 0) {
            asteroidProvider.spawnNearAsteroids(game.options.nearAsteroids, mapProvider.playerSpawnLocation())
        }
        if (settings.decorationEnabled) {
            val sources = holes + planets
            decorationProvider.spawn(gameState.level, game.get<RandomXS128>(), sources, sources.size / 2)
        }
        // Drawn last so the star never shifts the layouts of the spawns above (ST-099).
        star.roll(game.get<RandomXS128>())
        game.options.starAngleDeg?.let { star.set(it * MathUtils.degreesToRadians, star.kelvin) }
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
        ScreenUtils.clear(SCREEN_CLEAR_GRAY, SCREEN_CLEAR_GRAY, SCREEN_CLEAR_GRAY, 1f)
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
        computerSoundSystem.paused = true
    }

    override fun resume() {
        hullSoundSystem.paused = false
        computerSoundSystem.paused = false
    }

    override fun hide() {
        hullSoundSystem.silence()
        computerSoundSystem.silence()
        Gdx.graphics.setSystemCursor(SystemCursor.Arrow)
        hud.toggleHud(show = false)
        debugPanel.hide()
        game.get<GameEventManager>().removeDebugListener(debugPanel)
        clearLevel(engine, world)
        super.hide()
    }

    override fun dispose() {
        if (::hullSoundSystem.isInitialized) hullSoundSystem.silence()
        if (::computerSoundSystem.isInitialized) computerSoundSystem.silence()
        engine.systems.forEach { if (it is Disposable) it.disposeSafely() }
        world.disposeSafely()
        super.dispose()
    }

    companion object {
        private val logger = logger<PlayScreen>()
        private const val SHADOW_DEMO_PLANET_DX = 15f
        private const val SHADOW_DEMO_ROCK_DX = 7f
        private const val SHADOW_DEMO_CONTROL_ANGLE_DEG = 55f
    }
}
