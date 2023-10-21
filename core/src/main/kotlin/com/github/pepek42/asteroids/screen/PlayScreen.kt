package com.github.pepek42.asteroids.screen

import com.badlogic.ashley.core.PooledEngine
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Cursor.SystemCursor
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.viewport.Viewport
import com.github.pepek42.asteroids.Game
import com.github.pepek42.asteroids.GameState
import com.github.pepek42.asteroids.IS_DEBUG
import com.github.pepek42.asteroids.debug.LoggingUtils.Companion.defaultLoggingUtils
import com.github.pepek42.asteroids.environment.AsteroidSize
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.factory.PlayerEntityFactory
import com.github.pepek42.asteroids.provider.EnvironmentProvider
import com.github.pepek42.asteroids.provider.MapProvider
import com.github.pepek42.asteroids.provider.WeaponProjectileProvider
import com.github.pepek42.asteroids.system.CameraSystem
import com.github.pepek42.asteroids.system.DebugSystem
import com.github.pepek42.asteroids.system.HudSystem
import com.github.pepek42.asteroids.system.MoveSystem
import com.github.pepek42.asteroids.system.PhysicsSystem
import com.github.pepek42.asteroids.system.PlayerInputSystem
import com.github.pepek42.asteroids.system.RemoveSystem
import com.github.pepek42.asteroids.system.RenderSystem
import com.github.pepek42.asteroids.system.WeaponSystem
import com.github.pepek42.asteroids.system.WrapSystem
import com.github.pepek42.asteroids.ui.Hud
import ktx.app.KtxScreen
import ktx.assets.disposeSafely
import ktx.box2d.createWorld
import ktx.log.logger
import ktx.math.vec2

class PlayScreen(
    private val game: Game,
    textures: TextureAtlas
) : KtxScreen {
    private val hud = Hud(game)
    private val mapProvider = game.get<MapProvider>()
    private val camera = game.get<OrthographicCamera>()
    private val viewport: Viewport = game.get<Viewport>()
    private val world = createWorld().apply {
        autoClearForces = false
    }
    private val engine = PooledEngine()
    private val playerEntityFactory = PlayerEntityFactory(engine, world, mapProvider)
    private val environmentProvider = EnvironmentProvider(game.get<TextureAtlas>(), engine, world)
    private val gameState = game.get<GameState>()

    init {
        setupEcs()
        mapProvider.loadMap()
        playerEntityFactory.spawnPlayerEntity(textures.createSprite("spaceship/disc_green"))
        environmentProvider.spawnAsteroid(AsteroidSize.LARGE, vec2(100f, 100f), vec2(7f, 7f), 1f)
    }

    private fun setupEcs() {
        val gameEventManager = game.get<GameEventManager>()
        engine.addSystem(PlayerInputSystem(gameEventManager, camera))
        engine.addSystem(MoveSystem())
        engine.addSystem(WeaponSystem(WeaponProjectileProvider(game.get<TextureAtlas>(), engine, world)))
        engine.addSystem(PhysicsSystem(world, engine))
        engine.addSystem(CameraSystem(camera, gameEventManager, mapProvider))
        engine.addSystem(WrapSystem(mapProvider))
        engine.addSystem(RenderSystem(game, game.get<SpriteBatch>(), viewport))
        if (IS_DEBUG) {
            engine.addSystem(DebugSystem(world, engine, camera))
        }
        engine.addSystem(HudSystem(hud))
        engine.addSystem(RemoveSystem(world, mapProvider))
    }

    override fun show() {
        super.show()
        hud.toggleHud(show = true)
        hud.updateLevel(gameState.level) // TODO increase on win
        Gdx.graphics.setSystemCursor(SystemCursor.Crosshair)
        game.get<GameEventManager>().enablePlayerInputs()
    }

    override fun render(delta: Float) {
        defaultLoggingUtils.onGameLoopTick(delta)
        viewport.apply()
        ScreenUtils.clear(.1f, .1f, .1f, 1f)
        engine.update(delta)
    }

    override fun resize(width: Int, height: Int) {
        logger.info { "Resize event $width x $height" }
        viewport.update(width, height)
        hud.resize(width, height)
    }

    override fun hide() {
        Gdx.graphics.setSystemCursor(SystemCursor.Arrow)
        hud.toggleHud(show = false)
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
