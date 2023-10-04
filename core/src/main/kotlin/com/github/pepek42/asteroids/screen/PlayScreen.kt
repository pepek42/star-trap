package com.github.pepek42.asteroids.screen

import com.badlogic.ashley.core.PooledEngine
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Cursor.SystemCursor
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.github.pepek42.asteroids.AsteroidsCoop
import com.github.pepek42.asteroids.IS_DEBUG
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.factory.PlayerEntityFactory
import com.github.pepek42.asteroids.provider.MapProvider
import com.github.pepek42.asteroids.system.CameraSystem
import com.github.pepek42.asteroids.system.MoveSystem
import com.github.pepek42.asteroids.system.DebugSystem
import com.github.pepek42.asteroids.system.PhysicsSystem
import com.github.pepek42.asteroids.system.PlayerInputSystem
import com.github.pepek42.asteroids.system.RemoveSystem
import com.github.pepek42.asteroids.system.RenderSystem
import com.github.pepek42.asteroids.system.WrapSystem
import ktx.app.KtxScreen
import ktx.box2d.createWorld
import ktx.log.logger

class PlayScreen(
    private val game: AsteroidsCoop,
    textures: TextureAtlas
) : KtxScreen {
    private val mapProvider = game.get<MapProvider>()
    private val viewport: Viewport
    private val camera = game.get<OrthographicCamera>()
    private val batch = game.get<SpriteBatch>()
    private val world = createWorld()
    private val engine = PooledEngine()
    private val playerEntityFactory = PlayerEntityFactory(engine, world, mapProvider)

    init {
        val gameEventManager = game.get<GameEventManager>()
        viewport = FitViewport(16f, 9f, camera)
        engine.addSystem(PlayerInputSystem(gameEventManager))
        engine.addSystem(MoveSystem())
        engine.addSystem(PhysicsSystem(world, engine))
        engine.addSystem(CameraSystem(camera, gameEventManager))
        engine.addSystem(WrapSystem(mapProvider))
        engine.addSystem(RenderSystem(game, batch, viewport))
        if (IS_DEBUG) {
            engine.addSystem(DebugSystem(world, engine, camera))
        }
        engine.addSystem(RemoveSystem(world))

        playerEntityFactory.addPlayerEntity(textures.createSprite("spaceship/disc_green"))

    }

    override fun show() {
        super.show()
        Gdx.graphics.setSystemCursor(SystemCursor.Crosshair)
        game.get<GameEventManager>().enablePlayerInputs()
    }

    override fun render(delta: Float) {
        viewport.apply()
        ScreenUtils.clear(.1f, .1f, .1f, 1f)
        engine.update(delta)
    }

    override fun resize(width: Int, height: Int) {
        logger.info { "Resize event $width x $height" }
        viewport.update(width, height)
    }

    override fun hide() {
        Gdx.graphics.setSystemCursor(SystemCursor.Arrow)
        super.hide()
    }

    companion object {
        private val logger = logger<PlayScreen>()
    }
}
