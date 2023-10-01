package com.github.pepek42.asteroids.screen

import com.badlogic.ashley.core.PooledEngine
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Cursor.SystemCursor
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.github.pepek42.asteroids.AsteroidsCoop
import com.github.pepek42.asteroids.IS_DEBUG
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.MoveComponent
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.SpriteComponent
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.system.CameraSystem
import com.github.pepek42.asteroids.system.MoveSystem
import com.github.pepek42.asteroids.system.DebugSystem
import com.github.pepek42.asteroids.system.PhysicsSystem
import com.github.pepek42.asteroids.system.PlayerInputSystem
import com.github.pepek42.asteroids.system.RemoveSystem
import com.github.pepek42.asteroids.system.RenderSystem
import ktx.app.KtxScreen
import ktx.ashley.add
import ktx.ashley.entity
import ktx.ashley.with
import ktx.box2d.body
import ktx.box2d.box
import ktx.box2d.createWorld
import ktx.log.logger

private const val MAP_WIDTH = 1000f
private const val MAP_HEIGHT = 1000f

class PlayScreen(
    private val game: AsteroidsCoop,
    textures: TextureAtlas
) : KtxScreen {
    private val logger = logger<PlayScreen>()
    private val viewport: Viewport
    private val camera = game.ctx.inject<OrthographicCamera>()
    private val batch = game.ctx.inject<SpriteBatch>()
    private val world = createWorld()
    private val engine = PooledEngine()

    init {
        viewport = FitViewport(100f, 100f, camera)
        engine.addSystem(PlayerInputSystem(game.ctx.inject<GameEventManager>()))
        engine.addSystem(MoveSystem())
        engine.addSystem(PhysicsSystem(world))
        engine.addSystem(CameraSystem(camera))
        engine.addSystem(RenderSystem(game, batch, viewport))
        if (IS_DEBUG) {
            engine.addSystem(DebugSystem(world, camera))
        }
        engine.addSystem(RemoveSystem(world))
        val playerSprite = textures.createSprite("spaceship/disc_green")
        engine.add {
            entity {
                with<BodyComponent> {
                    body = world.body(BodyDef.BodyType.DynamicBody) {
                        box(
                            playerSprite.width,
                            playerSprite.height,
//                            Vector2(MAP_WIDTH / 2, MAP_HEIGHT / 2),
                        )
                    }
                }
                with<SpriteComponent>() {
                    sprite = playerSprite
                }
                with<MoveComponent>()
                with<PlayerComponent>()
            }
        }

    }

    override fun show() {
        super.show()
        Gdx.graphics.setSystemCursor(SystemCursor.Crosshair)
        game.ctx.inject<GameEventManager>().enablePlayerInputs()
    }

    override fun render(delta: Float) {
        viewport.apply()
        ScreenUtils.clear(.1f, .1f, .1f, 1f)
        engine.update(delta)
    }

    override fun resize(width: Int, height: Int) {
        logger.info { "Resize event $width x $height" }
        viewport.update(width, height, true)
    }

    override fun hide() {
        Gdx.graphics.setSystemCursor(SystemCursor.Arrow)
        super.hide()
    }
}
