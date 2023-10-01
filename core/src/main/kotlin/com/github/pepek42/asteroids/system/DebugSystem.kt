package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.EntitySystem
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer
import com.badlogic.gdx.physics.box2d.World

class DebugSystem(
    private val world: World,
    private val camera: Camera,
    private val box2DDebugRenderer: Box2DDebugRenderer = Box2DDebugRenderer()
) : EntitySystem() {

    override fun update(deltaTime: Float) {
        super.update(deltaTime)
        box2DDebugRenderer.render(world, camera.combined)
    }
}
