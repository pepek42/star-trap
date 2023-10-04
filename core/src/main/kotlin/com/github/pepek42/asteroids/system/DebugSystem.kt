package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.profiling.GLProfiler
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.Disposable
import com.github.pepek42.asteroids.component.SpriteComponent
import com.github.pepek42.asteroids.component.spriteMapper
import ktx.ashley.get
import ktx.ashley.oneOf
import ktx.graphics.use

class DebugSystem(
    private val world: World,
    private val engine: Engine,
    private val camera: Camera,
) : IteratingSystem(oneOf(SpriteComponent::class).get()), Disposable {
    private val shapeRenderer = ShapeRenderer()
    private val box2DDebugRenderer by lazy { Box2DDebugRenderer() }
    private val profiler by lazy { GLProfiler(Gdx.graphics).apply { enable() } }

    override fun update(deltaTime: Float) {
        shapeRenderer.use(ShapeRenderer.ShapeType.Line, camera) {
            super.update(deltaTime)
        }
        Gdx.graphics.setTitle(
            buildString {
                append("FPS:${Gdx.graphics.framesPerSecond},")
                append("DrawCalls:${profiler.drawCalls},")
                append("Binds:${profiler.textureBindings},")
                append("EcsEntitiesCount:${engine.entities.count()}")
                append("Box2DCount:${ world.bodyCount}")
            }
        )
        box2DDebugRenderer.render(world, camera.combined)
        profiler.reset()
    }

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val spriteColor = Color.WHITE
        val sprite = entity[spriteMapper]!!.sprite
        shapeRenderer.rect(sprite.x, sprite.y, sprite.width, sprite.height, spriteColor, spriteColor, spriteColor, spriteColor)
    }

    override fun dispose() {
        box2DDebugRenderer.dispose()
        shapeRenderer.dispose()
    }
}
