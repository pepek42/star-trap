package com.github.pepek42.asteroids.component

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.utils.Pool
import ktx.ashley.mapperFor
import ktx.ashley.optionalPropertyFor
import ktx.ashley.propertyFor

class BodyComponent : Component, Pool.Poolable {
    lateinit var body: Body
    var moveForcesApplied = false

    override fun reset() {
        body.world.destroyBody(body)
        body.userData = null
    }
}

val bodyMapper = mapperFor<BodyComponent>()
val Entity.bodyCmp by propertyFor(bodyMapper)
val Entity.bodyCmpOptional by optionalPropertyFor(bodyMapper)
