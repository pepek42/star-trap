package com.github.pepek42.asteroids.component

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.utils.Pool
import ktx.ashley.mapperFor

class BodyComponent : Component, Pool.Poolable {
    lateinit var body: Body

    override fun reset() {
        body.world.destroyBody(body)
        body.userData = null
    }
    companion object {
        val mapper = mapperFor<BodyComponent>()
    }
}
