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
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.provider.AsteroidProvider
import com.github.pepek42.asteroids.provider.MapProvider
import com.github.pepek42.asteroids.provider.PlayerEntityProvider
import com.github.pepek42.asteroids.provider.WeaponProjectileProvider
import com.github.pepek42.asteroids.system.CameraSystem
import com.github.pepek42.asteroids.system.ContactSystem
import com.github.pepek42.asteroids.system.DeathSystem
import com.github.pepek42.asteroids.system.DebugSystem
import com.github.pepek42.asteroids.system.EndLevelSystem
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
import ktx.ashley.add
import ktx.assets.disposeSafely
import ktx.box2d.createWorld
import ktx.log.logger

class PlayScreen(
    private val game: Game,
) : KtxScreen {
    private val hud = Hud(game)
    private val mapProvider = game.get<MapProvider>()
    private val camera = game.get<OrthographicCamera>()
    private val viewport: Viewport = game.get<Viewport>()
    private val world = createWorld().apply {
        autoClearForces = false
    }
    private val engine = PooledEngine()
    private val playerEntityProvider = PlayerEntityProvider(engine, world, mapProvider, game.get<TextureAtlas>())
    private val asteroidProvider = AsteroidProvider(game.get<TextureAtlas>(), engine, world)
    private val gameState = game.get<GameState>()

    init {
        setupEcs()
    }

    private fun setupEcs() {
        val gameEventManager = game.get<GameEventManager>()
        engine.add {
            addSystem(EndLevelSystem(game))
            addSystem(PlayerInputSystem(gameEventManager, camera))
            addSystem(MoveSystem())
            addSystem(WeaponSystem(WeaponProjectileProvider(game.get<TextureAtlas>(), engine, world)))
            addSystem(ContactSystem(world))
            addSystem(PhysicsSystem(world, engine))
            addSystem(CameraSystem(camera, gameEventManager, mapProvider))
            addSystem(WrapSystem(mapProvider))
            addSystem(RenderSystem(game, game.get<SpriteBatch>(), viewport))
            if (IS_DEBUG) {
                engine.addSystem(DebugSystem(world, engine, camera))
            }
            addSystem(HudSystem(hud))
            addSystem(DeathSystem(engine, asteroidProvider))
            addSystem(RemoveSystem(world, mapProvider))
        }
    }

    override fun show() {
        super.show()
        gameState.nextLevel()
        mapProvider.loadMap()
        playerEntityProvider.spawnPlayerEntity()
        asteroidProvider.spawnAsteroids(gameState.level, mapProvider)
        hud.toggleHud(show = true)
        hud.updateLevel(gameState.level)
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
        // TODO Clear ECS and Box2d
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
