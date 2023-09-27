package com.github.pepek42.asteroids.screen

import com.badlogic.ashley.core.PooledEngine
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.github.pepek42.asteroids.AsteroidsCoop
import com.github.pepek42.asteroids.IS_DEBUG
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.SpriteComponent
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.system.MoveSystem
import com.github.pepek42.asteroids.system.PhysicsDebugSystem
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


class PlayScreen(
    game: AsteroidsCoop,
    private val textures: TextureAtlas
) : KtxScreen {
    private val logger = logger<PlayScreen>()
    private val viewport: Viewport
    private val camera = OrthographicCamera()
    private val batch = game.ctx.inject<SpriteBatch>()
    private val background = Sprite(textures.findRegion("background", 1))
    private val world = createWorld()
    private val engine = PooledEngine()

    init {
        viewport = FitViewport(100f, 100f, camera)
        engine.addSystem(PlayerInputSystem(game.ctx.inject<GameEventManager>()))
        engine.addSystem(MoveSystem())
        engine.addSystem(PhysicsSystem(world))
        engine.addSystem(RenderSystem(batch, viewport))
        if (IS_DEBUG) {
            engine.addSystem(PhysicsDebugSystem(world, camera))
        }
        engine.addSystem(RemoveSystem(world))
        engine.add {
            entity {
                with<BodyComponent> {
                    body = world.body(BodyDef.BodyType.DynamicBody) {
                        box(21f, 23f)
                    }
                }
                with<SpriteComponent>() {
                    sprite = Sprite(TextureRegion(textures.findRegion("spaceships"), 86, 85, 21, 23))
                }
            }
        }
        background.texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat)
    }

    override fun render(delta: Float) {
        viewport.apply()
        if (Gdx.input.isKeyPressed(Keys.NUMPAD_ADD)) {
            camera.zoom -= delta * 0.5f
            if (camera.zoom < 0.01) {
                camera.zoom = 0.01f
            }
        }
        if (Gdx.input.isKeyPressed(Keys.NUMPAD_SUBTRACT)) {
            camera.zoom += delta * 0.5f
        }
        camera.update()
        ScreenUtils.clear(.1f, .1f, .1f, 1f)
        batch.projectionMatrix = viewport.camera.combined
        batch.begin()
        batch.draw(background, 0f, 0f)
        batch.end()
        // TODO
//        camera.moveTo(vec2(player.x, player.y))
        engine.update(delta)
    }

    override fun resize(width: Int, height: Int) {
        logger.info { "Resize event $width x $height" }
        viewport.update(width, height, true)
    }
}
